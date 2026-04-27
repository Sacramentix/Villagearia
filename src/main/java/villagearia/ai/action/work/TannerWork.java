package villagearia.ai.action.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProviderSimple;

import villagearia.Villagearia;
import villagearia.ai.HousedNpcPathfinder;
import villagearia.ai.action.Work.WorkContext;
import villagearia.resource.BlockOfInterest;
import villagearia.resource.BlockOfInterestStore;
import villagearia.resource.manager.VillageZoneManager;
import villagearia.utils.ItemTagHelper;
import villagearia.utils.JomlUtils;

public class TannerWork {

    public static record TannerWorkSite(UUID zoneUuid, Vector3i tannerBenchPos) {}

    public static Stream<TannerWorkSite> findValidWorkSite(Store<EntityStore> store, Stream<UUID> villageZonesUuid) {
        var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType()).getIndex();

        return villageZonesUuid.flatMap(uuid -> {
            var blockOfInterests = blockOfInterestIndex.get(uuid);
            if (blockOfInterests == null) return Stream.empty();
            
            var benches = blockOfInterests.get(BlockOfInterest.BENCH_TANNERY);
            if (benches == null || benches.isEmpty()) return Stream.empty();
            
            var chests = blockOfInterests.get(BlockOfInterest.CHEST);
            if (chests == null || chests.isEmpty()) return Stream.empty();

            var benchList = new ArrayList<>(benches);
            Collections.shuffle(benchList);

            return benchList.stream().filter(benchPos -> {
                for (var chestPos : chests) {
                    var distFromTannerSq = Math.pow(chestPos.x - benchPos.x, 2) + 
                                           Math.pow(chestPos.y - benchPos.y, 2) + 
                                           Math.pow(chestPos.z - benchPos.z, 2);
                    if (distFromTannerSq <= 5 * 5) return true;
                }
                return false;
            }).map(benchPos -> new TannerWorkSite(uuid, benchPos));
        });
    }

    /**
     * @return true if the behavior run successfully, false if work can't be done and need to be stopped
     */
    public static boolean behavior(WorkContext ctx) {
        var meRef = ctx.meRef();
        var store = ctx.store();
        var transform = ctx.transform();
        var pos = transform.getPosition();
        var housedNpc = ctx.housedNpc();
        var npc = ctx.npc();
        var headRotation = ctx.headRotation();
        var cb = ctx.cb();
        var tannerComp = cb.getComponent(meRef, TannerWorkComponent.getComponentType());
        if (tannerComp == null) return false;
        var bench_pos = tannerComp.tannerBenchPos;
        if (bench_pos == null) return false;
        var pos_3d = new org.joml.Vector3d(pos.x, pos.y, pos.z);
        var bench_pos_3d = new org.joml.Vector3d(bench_pos.x, bench_pos.y, bench_pos.z);

        HousedNpcPathfinder.multiVillageZonePathFindTo(housedNpc, store, pos_3d, bench_pos_3d, meRef, headRotation, cb, npc);
        var targetChest = tannerComp.targetChest;

        if (targetChest != null) {
            return toChestBehavior(ctx, targetChest, pos);
        }

        

        var processingBench = getProcessingBenchBlock(store, bench_pos);
        if (processingBench == null) return false;

        var hotbar = cb.ensureAndGetComponent(meRef, InventoryComponent.Hotbar.getComponentType());
        var hotbarContainer = hotbar.getInventory();

        var leatherItemsId  = ItemTagHelper.getLeatherIngredients();
        
        if (hasItemInHotbar(hotbarContainer, leatherItemsId)) {
            tannerComp.targetChest = findChestToDepositLeather(ctx, pos, bench_pos, leatherItemsId);
            if (tannerComp.targetChest == null) {
                // PANIC!!!! NPC now have item that he can't dispose off :(
            }
            return true;
        }

        var hideItemsId  = ItemTagHelper.getHideIngredients();

        if (hasItemInHotbar(hotbarContainer, hideItemsId)) {
            toTannerBenchBehavior(ctx, bench_pos, pos);
            return true;
        }

        var targetChestForHide = findChestToPickupHide(ctx, pos, bench_pos, hideItemsId);
        if (targetChestForHide != null) {
            tannerComp.targetChest = targetChestForHide;
            return true;
        }

        return false;
    }

    private static void transferHideFromHotbarToTannerBench(ItemContainer hotbarContainer, ItemContainer inputContainer) {
        for (short i = 0; i < hotbarContainer.getCapacity(); i++) {
            var item = hotbarContainer.getItemStack(i);
            if (item == null) continue;
            if (item.isEmpty()) continue;
            if (!item.getItemId().toLowerCase().contains("hide")) continue;
            var tx = inputContainer.addItemStack(item, false, false, true);
            if (!tx.succeeded()) continue;
            
            var rem = tx.getRemainder();
            var actualAdded = item.getQuantity() - (rem != null ? rem.getQuantity() : 0);
            if (actualAdded > 0) {
                hotbarContainer.removeItemStackFromSlot(i, actualAdded, false, true);
            }
        }
    }

    private static void transferLeatherFromTannerBenchToHotbar(ItemContainer hotbarContainer, ItemContainer outputContainer) {
        for (short i = 0; i < outputContainer.getCapacity(); i++) {
            var item = outputContainer.getItemStack(i);
            if (item == null) continue;
            if (item.isEmpty()) continue;
            if (!item.getItemId().toLowerCase().contains("leather")) continue;
            var tx = hotbarContainer.addItemStack(item, false, false, true);
            if (!tx.succeeded()) continue;
            
            var rem = tx.getRemainder();
            var actualAdded = item.getQuantity() - (rem != null ? rem.getQuantity() : 0);
            if (actualAdded > 0) {
                outputContainer.removeItemStackFromSlot(i, actualAdded, false, true);
            }
        }
    }

    private static boolean hasItemInHotbar(ItemContainer hotbarContainer, Set<String> validIds) {
        var possessedId = new HashSet<String>();
        for (short i = 0; i < hotbarContainer.getCapacity(); i++) {
            var item = hotbarContainer.getItemStack(i);
            if (item == null) continue;
            if (item.isEmpty()) continue;
            var itemId = item.getItemId();
            if (!validIds.contains(itemId)) continue;
            possessedId.add(itemId);
        }
        return !possessedId.isEmpty();
    }

    public static Vector3i findChestToDepositLeather(WorkContext ctx, Vector3d pos, Vector3i tannerBenchPos, Set<String> leatherItemsId) {
        var store = ctx.store();
        Vector3i targetChest = null;
        int bestScore = -1; // 3 = same leather & not full, 2 = completely empty, 1 = any empty slot
        double bestDist = Double.MAX_VALUE;

        var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType()).getIndex();
        var villageZones = VillageZoneManager.getVillageZoneInRange(store, JomlUtils.vector3itoJoml(tannerBenchPos)).collect(Collectors.toList());
        
        for (var zone : villageZones) {
            var blockOfInterests = blockOfInterestIndex.get(zone.uuid());
            if (blockOfInterests == null) continue;
            var chests = blockOfInterests.get(BlockOfInterest.CHEST);
            if (chests == null) continue;
            for (var chestPos : chests) {
                var distFromTannerSq =  Math.pow(chestPos.x - tannerBenchPos.x, 2) + 
                                        Math.pow(chestPos.y - tannerBenchPos.y, 2) + 
                                        Math.pow(chestPos.z - tannerBenchPos.z, 2);
                                
                if (distFromTannerSq > 5*5) continue;
                var chestContainer = getContainerFromPos(store, chestPos);
                if (chestContainer == null) continue;
                var score = 0;
                var hasSameLeather = false;
                var isNotFull = false;
                var isCompletelyEmpty = true;
                var hasEmptySlot = false;

                for (short j = 0; j < chestContainer.getCapacity(); j++) {
                    var chestItem = chestContainer.getItemStack(j);
                    if (chestItem == null) {
                        hasEmptySlot = true;
                        isNotFull = true;
                    } else if (chestItem.isEmpty()) {
                        hasEmptySlot = true;
                        isNotFull = true;
                    } else {
                        isCompletelyEmpty = false;
                        if (leatherItemsId.contains(chestItem.getItemId()) && chestItem.getQuantity() < chestItem.getItem().getMaxStack()) {
                            hasSameLeather = true;
                            isNotFull = true;
                        }
                    }
                }

                if (hasSameLeather && isNotFull) {
                    score = 3;
                } else if (isCompletelyEmpty) {
                    score = 2;
                } else if (hasEmptySlot) {
                    score = 1;
                }
                var distSq = Math.pow(chestPos.x - pos.x, 2) + 
                             Math.pow(chestPos.y - pos.y, 2) + 
                             Math.pow(chestPos.z - pos.z, 2);
                if (score > 0) {
                    if (score > bestScore || (score == bestScore && distSq < bestDist)) {
                        bestScore = score;
                        bestDist = distSq;
                        targetChest = chestPos;
                    }
                }
            }
        }
        return targetChest;
    }

    public static Vector3i findChestToPickupHide(WorkContext ctx, Vector3d pos, Vector3i tannerBenchPos, Set<String> hideItemsId) {
        var store = ctx.store();
        var inputContainer = getTannerBenchInputContainer(store, tannerBenchPos);
        if (inputContainer == null) return null;

        var acceptedHides = new HashSet<String>();
        var anyHideAccepted = false;
        
        for (short i = 0; i < inputContainer.getCapacity(); i++) {
            var item = inputContainer.getItemStack(i);
            if (item == null) {
                anyHideAccepted = true;
                continue;
            }
            if (item.isEmpty()) {
                anyHideAccepted = true;
                continue;
            }
            if (hideItemsId.contains(item.getItemId()) && item.getQuantity() < item.getItem().getMaxStack()) {
                acceptedHides.add(item.getItemId());
            }
        }

        if (!anyHideAccepted && acceptedHides.isEmpty()) {
            return null; // Bench is full or has unaccepted items
        }

        Vector3i bestChest = null;
        var leastAmount = Integer.MAX_VALUE;
        var bestDist = Double.MAX_VALUE;

        var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType()).getIndex();
        var villageZones = VillageZoneManager.getVillageZoneInRange(store, JomlUtils.vector3itoJoml(tannerBenchPos)).collect(Collectors.toList());

        for (var zone : villageZones) {
            var blockOfInterests = blockOfInterestIndex.get(zone.uuid());
            if (blockOfInterests == null) continue;
            var chests = blockOfInterests.get(BlockOfInterest.CHEST);
            if (chests == null) continue;
            
            for (var chestPos : chests) {
                var distFromTannerSq = Math.pow(chestPos.x - tannerBenchPos.x, 2) + 
                                       Math.pow(chestPos.y - tannerBenchPos.y, 2) + 
                                       Math.pow(chestPos.z - tannerBenchPos.z, 2);
                if (distFromTannerSq > 5 * 5) continue;

                var chestContainer = getContainerFromPos(store, chestPos);
                if (chestContainer == null) continue;

                var compatibleAmount = 0;
                for (short j = 0; j < chestContainer.getCapacity(); j++) {
                    var chestItem = chestContainer.getItemStack(j);
                    if (chestItem == null) continue;
                    if (chestItem.isEmpty()) continue;
                    
                    var itemId = chestItem.getItemId();
                    if (!hideItemsId.contains(itemId)) continue;
                    if (anyHideAccepted || acceptedHides.contains(itemId)) {
                        compatibleAmount += chestItem.getQuantity();
                    }
                }

                if (compatibleAmount <= 0) continue;

                var distSq = Math.pow(chestPos.x - pos.x, 2) + 
                             Math.pow(chestPos.y - pos.y, 2) + 
                             Math.pow(chestPos.z - pos.z, 2);
                
                if (compatibleAmount < leastAmount || (compatibleAmount == leastAmount && distSq < bestDist)) {
                    leastAmount = compatibleAmount;
                    bestDist = distSq;
                    bestChest = chestPos;
                }
            }
        }
        return bestChest;
    }

    public static boolean toChestBehavior(WorkContext ctx, Vector3i targetChest, Vector3d pos) {
        if (!pathfindToPos(ctx, targetChest, pos)) {
            return true;
        }

        var store = ctx.store();
        var cb = ctx.cb();
        var meRef = ctx.meRef();

        var tannerComp = cb.ensureAndGetComponent(meRef, TannerWorkComponent.getComponentType());

        var chestContainer = getContainerFromPos(store, targetChest);
        if (chestContainer == null) {
            tannerComp.targetChest = null;
            return true;
        }
        
        var hotbar = cb.ensureAndGetComponent(meRef, InventoryComponent.Hotbar.getComponentType());
        var hotbarContainer = hotbar.getInventory();
        var leatherItemsId = ItemTagHelper.getLeatherIngredients();
        
        depositLeatherIntoChest(chestContainer, hotbarContainer, leatherItemsId);
        if (tannerComp.tannerBenchPos == null) {
            tannerComp.targetChest = null;
            return true;
        }

        var inputContainer = getTannerBenchInputContainer(store, tannerComp.tannerBenchPos);
        if (inputContainer == null) {
            tannerComp.targetChest = null;
            return true;
        }

        grabHideToRefillTannerBenchFromChest(inputContainer, chestContainer, hotbarContainer);
        tannerComp.targetChest = null;
        return true;
    }

    public static boolean toTannerBenchBehavior(WorkContext ctx, Vector3i tannerBenchPos, Vector3d pos) {
        if (!pathfindToPos(ctx, tannerBenchPos, pos)) {
            return true;
        }

        var store = ctx.store();
        var cb = ctx.cb();
        var meRef = ctx.meRef();

        var tannerComp = store.getComponent(meRef, TannerWorkComponent.getComponentType());
        if (tannerComp == null) return true;

        var hotbar = cb.ensureAndGetComponent(meRef, InventoryComponent.Hotbar.getComponentType());
        var hotbarContainer = hotbar.getInventory();

        var tannerBenchHolder = getProcessingBenchBlock(store, tannerBenchPos);
        if (tannerBenchHolder == null) return true;

        if (tannerComp.tannerBenchPos == null) return true;

        var inputContainer = getTannerBenchInputContainer(store, tannerBenchPos);
        var outputContainer = getTannerBenchOutputContainer(store, tannerBenchPos);
        if (inputContainer == null || outputContainer == null) return true;

        transferLeatherFromTannerBenchToHotbar(hotbarContainer, outputContainer);
        transferHideFromHotbarToTannerBench(hotbarContainer, inputContainer);

        return true;
    }

    private static boolean pathfindToPos(WorkContext ctx, Vector3i targetPos, Vector3d pos) {
        var housedNpc = ctx.housedNpc();
        var npc = ctx.npc();
        var headRotation = ctx.headRotation();
        var meRef = ctx.meRef();
        var store = ctx.store();
        var cb = ctx.cb();
        do {
            var dx = pos.x - targetPos.x;
            if (dx > 2 || dx < -2) break;
            var dy = pos.y - targetPos.y;
            if (dy > 2 || dy < -2) break;
            var dz = pos.z - targetPos.z;
            if (dz > 5 || dz < -5) break;
            housedNpc.setTargetPos((Vector3i) null);
            return true;
        } while (false);
        housedNpc.setPathQueue(new ArrayDeque<>());
        housedNpc.setTargetPos(targetPos);
        var tpos = housedNpc.getTargetPos();
        var tpos3d = new Vector3d(tpos.x, tpos.y, tpos.z);
        var provider = store.getResource(AStarNodePoolProviderSimple.getResourceType());
        HousedNpcPathfinder.pathfindTo(
            npc, housedNpc.getPathSession(), provider, store,
            tpos, pos, meRef, tpos3d, headRotation, cb
        );
        return false;
    }

    private static Ref<ChunkStore> getBlockEntityRef(Store<EntityStore> store, Vector3i targetPos) {
        var world = store.getExternalData().getWorld();
        var chunkIndex = com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(targetPos.x, targetPos.z);
        var chunk = world.getChunk(chunkIndex);
        if (chunk == null) return null;
        
        var blockRef = chunk.getBlockComponentEntity(targetPos.x, targetPos.y, targetPos.z);
        if (blockRef == null) {
            var blockType = chunk.getBlockType(targetPos.x, targetPos.y, targetPos.z);
            var rotation = chunk.getRotation(targetPos.x, targetPos.y, targetPos.z).index();
            var holder = chunk.getBlockComponentHolder(targetPos.x, targetPos.y, targetPos.z);
            if (holder != null) {
                chunk.setState(targetPos.x, targetPos.y, targetPos.z, blockType, rotation, holder);
                blockRef = chunk.getBlockComponentEntity(targetPos.x, targetPos.y, targetPos.z);
            }
        }
        return blockRef;
    }

    private static ProcessingBenchBlock getProcessingBenchBlock(Store<EntityStore> store, Vector3i benchPos) {
        var blockRef = getBlockEntityRef(store, benchPos);
        if (blockRef == null) return null;

        return store.getExternalData().getWorld().getChunkStore().getStore().getComponent(blockRef, ProcessingBenchBlock.getComponentType());
    }

    private static ItemContainer getContainerFromPos(Store<EntityStore> store, Vector3i targetPos) {
        var blockRef = getBlockEntityRef(store, targetPos);
        if (blockRef == null) return null;
        
        var itemContainerBlock = store.getExternalData().getWorld().getChunkStore().getStore().getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (itemContainerBlock == null) return null;
        
        return itemContainerBlock.getItemContainer();
    }

    private static ItemContainer getTannerBenchInputContainer(Store<EntityStore> store, Vector3i benchPos) {
        var processingBench = getProcessingBenchBlock(store, benchPos);
        if (processingBench == null) return null;

        return processingBench.getInputContainer();
    }

    private static ItemContainer getTannerBenchOutputContainer(Store<EntityStore> store, Vector3i benchPos) {
        var processingBench = getProcessingBenchBlock(store, benchPos);
        if (processingBench == null) return null;

        return processingBench.getOutputContainer();
    }

    private static void depositLeatherIntoChest(ItemContainer chestContainer, ItemContainer hotbarContainer, Set<String> leatherItemsId) {
        var chestLeatherTypes = new HashSet<String>();
        for (short j = 0; j < chestContainer.getCapacity(); j++) {
            var chestItem = chestContainer.getItemStack(j);
            if (chestItem == null) continue;
            if (chestItem.isEmpty()) continue;
            if (leatherItemsId.contains(chestItem.getItemId())) {
                chestLeatherTypes.add(chestItem.getItemId());
            }
        }
        
        for (short i = 0; i < hotbarContainer.getCapacity(); i++) {
            var item = hotbarContainer.getItemStack(i);
            if (item == null) continue;
            if (item.isEmpty()) continue;
            if (!leatherItemsId.contains(item.getItemId())) continue;
            
            if (!chestLeatherTypes.isEmpty()) {
                if (!chestLeatherTypes.contains(item.getItemId())) continue; // if the chest has some leather, only place matching leather
            }
            
            var tx = chestContainer.addItemStack(item, false, false, true);
            if (!tx.succeeded()) continue;
            
            var rem = tx.getRemainder();
            var actualAdded = item.getQuantity() - (rem != null ? rem.getQuantity() : 0);
            if (actualAdded > 0) {
                hotbarContainer.removeItemStackFromSlot(i, actualAdded, false, true);
            }
        }
    }

    private static void grabHideToRefillTannerBenchFromChest(ItemContainer inputContainer, ItemContainer chestContainer, ItemContainer hotbarContainer) {
        int emptyInputSlots = 0;
        var missingForExisting = new HashMap<String, Integer>();
        // check how much empty slot the tanner bench have
        // and how much leather of each type we need for non emptyh input slot
        for (short i = 0; i < inputContainer.getCapacity(); i++) {
            var bitem = inputContainer.getItemStack(i);
            if (bitem == null) {
                emptyInputSlots++;
                continue;
            }
            if (bitem.isEmpty()) {
                emptyInputSlots++;
                continue;
            }
            
            int maxStack = bitem.getItem().getMaxStack();
            int missing = maxStack - bitem.getQuantity();
            if (missing > 0) {
                missingForExisting.merge(bitem.getItemId(), missing, Integer::sum);
            }
        }

        var hideItemsId = ItemTagHelper.getHideIngredients();
        var availableHides = new HashMap<String, Integer>();
        // We count all the leather type available in chest with their quantity
        for (short i = 0; i < chestContainer.getCapacity(); i++) {
            var citem = chestContainer.getItemStack(i);
            if (citem == null) continue;
            if (citem.isEmpty()) continue;
            if (!hideItemsId.contains(citem.getItemId())) continue;
            
            availableHides.merge(citem.getItemId(), citem.getQuantity(), Integer::sum);
        }

        BiConsumer<String, Integer> takeFromChest = (hideId, quantityToTake) -> {
            var remainingToTake = quantityToTake;
            for (short i = 0; i < chestContainer.getCapacity(); i++) {
                if (remainingToTake <= 0) break;
                var citem = chestContainer.getItemStack(i);
                if (citem == null) continue;
                if (citem.isEmpty()) continue;
                if (!citem.getItemId().equals(hideId)) continue;
                
                var takeFromSlot = Math.min(remainingToTake, citem.getQuantity());
                var takeStack = citem.withQuantity(takeFromSlot);
                if (takeStack == null) break;
                
                var tx = hotbarContainer.addItemStack(takeStack, false, false, true);
                if (!tx.succeeded()) break;

                var rem = tx.getRemainder();
                var actualTaken = takeFromSlot - (rem != null ? rem.getQuantity() : 0);
                remainingToTake -= actualTaken;
                chestContainer.removeItemStackFromSlot(i, actualTaken, false, true);
            }
        };
        
        for (var entry : missingForExisting.entrySet()) {
            var hideId = entry.getKey();
            var missing = entry.getValue();
            var available = availableHides.getOrDefault(hideId, 0);
            if (missing <= 0) continue;
            if (available <= 0) continue;
            
            var toTake = Math.min(missing, available);
            takeFromChest.accept(hideId, toTake);
            availableHides.put(hideId, available - toTake);
        }

        for (short e = 0; e < emptyInputSlots; e++) {
            String bestHide = null;
            var maxCount = 0;
            for (var entry : availableHides.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    bestHide = entry.getKey();
                }
            }
            
            if (bestHide == null) continue;

            var maxStack = 64;
            for (short i = 0; i < chestContainer.getCapacity(); i++) {
                var citem = chestContainer.getItemStack(i);
                if (citem == null) continue;
                if (citem.isEmpty()) continue;
                if (!citem.getItemId().equals(bestHide)) continue;
                
                maxStack = citem.getItem().getMaxStack();
                break;
            }
            var toTake = Math.min(maxCount, maxStack);
            takeFromChest.accept(bestHide, toTake);
            availableHides.put(bestHide, maxCount - toTake);
        }
    }

    public static class TannerWorkComponent implements Component<EntityStore> {

    
        public static final BuilderCodec<TannerWorkComponent> CODEC = BuilderCodec.builder(
                TannerWorkComponent.class, TannerWorkComponent::new
            )
            .append(new KeyedCodec<>("BenchPosition", Vector3i.CODEC), (x, v) -> x.tannerBenchPos = v, (x) -> x.tannerBenchPos).add()
            .append(new KeyedCodec<>("TargetChest", Vector3i.CODEC), (x, v) -> x.targetChest = v, (x) -> x.targetChest).add()
            .append(new KeyedCodec<>("VillageZone", Codec.UUID_BINARY), (x, v) -> x.villageZone = v, (x) -> x.villageZone).add()
            .build();

        public Vector3i tannerBenchPos;
        public Vector3i targetChest;
        public UUID villageZone;
        // TODO handle unable to pathfind to specific chest

        public TannerWorkComponent() {

        }

        public TannerWorkComponent(TannerWorkSite worksite) {
            this.tannerBenchPos = worksite.tannerBenchPos();
            this.villageZone = worksite.zoneUuid();
        }

        public static ComponentType<EntityStore, TannerWorkComponent> getComponentType() {
            return Villagearia.instance().getTannerWorkComponentType();
        }

        public TannerWorkComponent clone() {
            var clone = new TannerWorkComponent();
            clone.tannerBenchPos = this.tannerBenchPos;
            clone.targetChest = this.targetChest;
            clone.villageZone = this.villageZone;
            return clone;
        }
    }
}
