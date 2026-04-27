package villagearia.system;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.resource.BlockOfInterestStore;

public class BreakBlockListener extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    
    public BreakBlockListener() {
        super(BreakBlockEvent.class);
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(
        int index,
        ArchetypeChunk<EntityStore> archetypeChunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> cb,
        BreakBlockEvent event
    ) {
        var blockPos = event.getTargetBlock();
        var world = store.getExternalData().getWorld();
        int brokenBlockId = world.getBlock(blockPos);
        var posVec = new Vector3i(blockPos.getX(), blockPos.getY(), blockPos.getZ());

        // Mimic PlaceBlockListener door tracking logic
        DoorTracker.removeDoor(posVec);

        var type = PlaceBlockListener.blockOfInterestMap.get(brokenBlockId);
        if (type == null) return;

        var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType()).getIndex();
        
        // Remove from any village zone that registered this block of interest
        for (var blocksForVillage : blockOfInterestIndex.values()) {
            var blocksOfType = blocksForVillage.get(type);
            if (blocksOfType != null) {
                blocksOfType.remove(posVec);
            }
        }
    }
}