package villagearia.system;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;

import villagearia.Villagearia;
import villagearia.component.VillageZone;
import villagearia.component.VillageZoneResource;
import villagearia.component.HousedNpcBlockOfInterest.HousedNpcBlockOfInterest;
import villagearia.component.HousedNpcBlockOfInterest.HousedNpcBlockOfInterestComponent;
import villagearia.graph.VillageZoneGraph;
import com.hypixel.hytale.component.AddReason;

public class PlaceBlockListener extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public PlaceBlockListener() {
        super(PlaceBlockEvent.class);
        villageZoneQuery = Query.and(
            VillageZone.getComponentType(),
            UUIDComponent.getComponentType()
        );
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    private static final Int2ReferenceOpenHashMap<HousedNpcBlockOfInterest> blockOfInterestMap = new Int2ReferenceOpenHashMap<>();

    private static void createHousedNpcBlockOfInterestIdMap() {
        
        var map = BlockType.getAssetMap();
        for (var entry : map.getAssetMap().entrySet()) {
            String blockName = entry.getKey();
            int index = map.getIndex(blockName);
            if (index < 0) continue;

            for (HousedNpcBlockOfInterest type : HousedNpcBlockOfInterest.values()) {
                if (type.pattern.matcher(blockName).matches()) {
                    blockOfInterestMap.put(index, type);
                    break;
                }
            }
        }
    }

    private Query<EntityStore> villageZoneQuery;

    // Fast-lookup index: Village UUID -> (BlockOfInterestType -> Set of Coordinates)
    private static final Object2ObjectOpenHashMap<UUID, EnumMap<HousedNpcBlockOfInterest, ObjectOpenHashSet<Vector3i>>> villageBlocksIndex = new Object2ObjectOpenHashMap<>();

    // Quick public getter for O(1) reads elsewhere
    public static ObjectOpenHashSet<Vector3i> getBlocksOfTypeInVillage(UUID villageId, HousedNpcBlockOfInterest type) {
        var blocksInVillage = villageBlocksIndex.get(villageId);
        if (blocksInVillage != null) {
            var blocksOfType = blocksInVillage.get(type);
            if (blocksOfType != null) {
                return blocksOfType;
            }
        }
        return new ObjectOpenHashSet<>();
    }

    /*
        We track all of this id
        *Chest*
        Bench_Furnace
        Coop_Chicken
        Bench_Tannery
    
    */

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> cb,
        @Nonnull PlaceBlockEvent event
    ) {

        var blockPos = event.getTargetBlock();
        int placedBlockId = cb.getExternalData().getWorld().getBlock(blockPos);

        HousedNpcBlockOfInterest type = blockOfInterestMap.get(placedBlockId);

        if (type == null) return;

        var blockPos_Vector3d = new org.joml.Vector3d(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5);
        Set<UUID> affiliatedVillages = new HashSet<>();
        
        var villageZoneResourceType = Villagearia.getInstance().getVillageZoneResourceType();
        var resource = (VillageZoneResource) cb.getExternalData().getWorld().getEntityStore().getStore().getResource(villageZoneResourceType);
        if (resource != null) {
            for (var entry : resource.getZones().entrySet()) {
                var villageUuid = entry.getKey();
                var villageZone = entry.getValue();
                var radiusSquared = villageZone.radiusSquared;
                var center = villageZone.center;
                
                if (center.distanceSquared(blockPos_Vector3d) > radiusSquared) continue;
                
                // Retrieve or initialize village map
                var blocksForVillage = villageBlocksIndex.computeIfAbsent(villageUuid, k -> new EnumMap<>(HousedNpcBlockOfInterest.class));
                
                // Retrieve or initialize the typed set
                var blocksOfType = blocksForVillage.computeIfAbsent(type, k -> new ObjectOpenHashSet<>());
                blocksOfType.add(new Vector3i(blockPos.x, blockPos.y, blockPos.z));

                affiliatedVillages.add(villageUuid);
            }
        }

        // Spawn actual ECS Entity if it belongs to ANY village
        if (!affiliatedVillages.isEmpty()) {
            var holder = EntityStore.REGISTRY.newHolder();
            
            // Assign a Unique ID to this Block of Interest
            holder.addComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
            
            // Add generalized transform so engine systems can locate it in 3D 
            var transform = holder.ensureAndGetComponent(TransformComponent.getComponentType());
            transform.setPosition(new Vector3d(blockPos_Vector3d.x, blockPos_Vector3d.y, blockPos_Vector3d.z));

            // Add our specialized unified component holding Type and Relationships
            var boi = new HousedNpcBlockOfInterestComponent(type, affiliatedVillages);
            // UNCOMMENT ONCE REGISTERED:
            // holder.addComponent(HousedNpcBlockOfInterestComponent.getComponentType(), boi);
            
            cb.addEntity(holder, AddReason.SPAWN);
        }

        switch (type) {
            case CHEST:
                // handle chest
                break;
            case BENCH_FURNACE:
                // handle furnace
                break;
            case BENCH_TANNERY:
                // handle tannery
                break;
            case COOP_CHICKEN:
                // handle chicken coop
                break;
        }
    }

}
