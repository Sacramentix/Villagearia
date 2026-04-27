package villagearia.system;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import villagearia.resource.BlockOfInterest;
import villagearia.resource.BlockOfInterestStore;
import villagearia.resource.manager.VillageZoneManager;

public class PlaceBlockListener extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public PlaceBlockListener() {
        super(PlaceBlockEvent.class);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    public static final Int2ReferenceOpenHashMap<BlockOfInterest> blockOfInterestMap = new Int2ReferenceOpenHashMap<>();

    public static void createHousedNpcBlockOfInterestIdMap() {
        
        var map = BlockType.getAssetMap();
        for (var entry : map.getAssetMap().entrySet()) {
            var blockName = entry.getKey();
            var index = map.getIndex(blockName);
            if (index < 0) continue;

            for (var type : BlockOfInterest.values()) {
                if (type.pattern.matcher(blockName).matches()) {
                    blockOfInterestMap.put(index, type);
                    break;
                }
            }
        }
    }

    // Quick public getter for O(1) reads elsewhere
    public static ObjectOpenHashSet<Vector3i> getBlocksOfTypeInVillage(Store<EntityStore> store, UUID villageId, BlockOfInterest type) {
        var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType())
            .getIndex();
        var blocksInVillage = blockOfInterestIndex.get(villageId);
        if (blocksInVillage == null) return new ObjectOpenHashSet<>();
        var blocksOfType = blocksInVillage.get(type);
        if (blocksOfType == null) return new ObjectOpenHashSet<>();
        return blocksOfType;     
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
        ArchetypeChunk<EntityStore> archetypeChunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> cb,
        PlaceBlockEvent event
    ) {

        var blockPos = event.getTargetBlock();
        
        int placedBlockId = BlockType.getAssetMap().getIndex(event.getItemInHand().getBlockKey());

        if (DoorTracker.isDoor(placedBlockId)) {
            DoorTracker.addDoor(new Vector3i(blockPos.x, blockPos.y, blockPos.z));
            return;
        }

        var type = blockOfInterestMap.get(placedBlockId);

        if (type == null) return;

        var blockPos_Vector3d = new org.joml.Vector3d(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5);
        Set<UUID> affiliatedVillages = new HashSet<>();
        
        var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType())
            .getIndex();
        VillageZoneManager.getVillageZoneInRange(store, blockPos_Vector3d).forEach(match -> {
            var villageUuid = match.uuid();
            
            // Retrieve or initialize village map
            var blocksForVillage = blockOfInterestIndex.computeIfAbsent(villageUuid, k -> new EnumMap<>(BlockOfInterest.class));
            
            // Retrieve or initialize the typed set
            var blocksOfType = blocksForVillage.computeIfAbsent(type, k -> new ObjectOpenHashSet<>());
            blocksOfType.add(new Vector3i(blockPos.x, blockPos.y, blockPos.z));

            affiliatedVillages.add(villageUuid);
        });
    }

}
