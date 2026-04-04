package villagearia.system;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import villagearia.component.VillageZone;
import villagearia.component.VillageZoneResource;

public class VillageZoneBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private Query<EntityStore> villageZoneQuery;;

    public VillageZoneBreakBlockSystem() {
        super(BreakBlockEvent.class);
        villageZoneQuery = VillageZone.getComponentType();
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @SuppressWarnings("null")
    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
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

        List<UUID> toRemove = new ArrayList<>();

        var resource = (VillageZoneResource) world.getEntityStore().getStore().getResource(villagearia.Villagearia.getInstance().getVillageZoneResourceType());
        if (resource != null) {
            for (var entry : resource.getZones().entrySet()) {
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
                villagearia.VillageZoneManager.removeVillageZone(world.getEntityStore().getStore(), id);
            }
        }
    }
}
