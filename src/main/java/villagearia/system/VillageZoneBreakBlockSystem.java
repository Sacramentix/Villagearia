package villagearia.system;

import java.util.ArrayList;
import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.resource.VillageZoneStore;
import villagearia.resource.manager.VillageZoneManager;

public class VillageZoneBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public VillageZoneBreakBlockSystem() {
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
        CommandBuffer<EntityStore> commandBuffer,
        BreakBlockEvent event
    ) {

        var brokenBlock = event.getTargetBlock();
        
        var world = store.getExternalData().getWorld();

        // Find what the center of the broken block would be
        var originType = world.getBlockType(brokenBlock.getX(), brokenBlock.getY(), brokenBlock.getZ());
        if (originType == null) return;
        if (originType == BlockType.EMPTY) return;
        var expectedCenterRaw = new Vector3d();
        var rotationIndex = world.getBlockRotationIndex(brokenBlock.getX(), brokenBlock.getY(), brokenBlock.getZ());
        try {
            originType.getBlockCenter(rotationIndex, expectedCenterRaw);
        } catch(Exception e) {
            return;
        }

        final double ecX = expectedCenterRaw.x + brokenBlock.getX();
        final double ecY = expectedCenterRaw.y + brokenBlock.getY();
        final double ecZ = expectedCenterRaw.z + brokenBlock.getZ();

        var villageZoneStore = store.getResource(VillageZoneStore.getResourceType());
        var toRemove = new ArrayList<UUID>();
        
        for (var entry : villageZoneStore.getZones().entrySet()) {
            var villageZone = entry.getValue();
            var pos = villageZone.center;

            var dx = pos.x - ecX;
            if (dx > 0.1 || dx < -0.1) continue;
            var dy = pos.y - ecY;
            if (dy > 0.1 || dy < -0.1) continue;
            var dz = pos.z - ecZ;
            if (dz > 0.1 || dz < -0.1) continue;

            toRemove.add(entry.getKey());
        }

        for (var id : toRemove) {
            VillageZoneManager.removeVillageZone(store, id);
        }
    }
}
