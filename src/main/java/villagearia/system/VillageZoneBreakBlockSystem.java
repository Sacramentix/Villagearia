package villagearia.system;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import villagearia.component.VillageZone;

public class VillageZoneBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    private final Query<EntityStore> query;

    public VillageZoneBreakBlockSystem() {
        super(BreakBlockEvent.class);
        this.query = Query.and(VillageZone.getComponentType(), TransformComponent.getComponentType());
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {

        var transformType   = TransformComponent.getComponentType();
        var villageZoneType =        VillageZone.getComponentType();

        var brokenBlock = event.getTargetBlock();
        var world = store.getExternalData().getWorld();

        // Find what the center of the broken block would be
        var originType = world.getBlockType(brokenBlock.getX(), brokenBlock.getY(), brokenBlock.getZ());
        var expectedCenterRaw = new Vector3d();
        var foundCenter = false;

        if (originType != null && originType != BlockType.EMPTY) {

            var rotationIndex = world.getBlockRotationIndex(brokenBlock.getX(), brokenBlock.getY(), brokenBlock.getZ());

            originType.getBlockCenter(rotationIndex, expectedCenterRaw);

            expectedCenterRaw.setX(expectedCenterRaw.getX() + brokenBlock.getX());
            expectedCenterRaw.setY(expectedCenterRaw.getY() + brokenBlock.getY());
            expectedCenterRaw.setZ(expectedCenterRaw.getZ() + brokenBlock.getZ());

            foundCenter = true;
            // LOGGER.at(infoLevel).log("Calculated expected center: %s", expectedCenterRaw);
        }

        final var hasExpectedCenter = foundCenter;
        final var expectedCenter = expectedCenterRaw;

        List<Ref<EntityStore>> toRemove = new ArrayList<>();

        store.forEachChunk(
            this.query,
            (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb) -> {
                // LOGGER.at(infoLevel).log("Iterating chunk with %d valid entities...", chunk.size());
                for (var i = 0; i < chunk.size(); i++) {
                    var entityRef = chunk.getReferenceTo(i);

                    var transform = cb.getComponent(entityRef, transformType);

                    if (transform == null) return;

                    var pos = transform.getPosition();
                    var coversBrokenBlock = false;

                    if (hasExpectedCenter) {
                        var dx = Math.abs(pos.getX() - expectedCenter.getX());
                        var dy = Math.abs(pos.getY() - expectedCenter.getY());
                        var dz = Math.abs(pos.getZ() - expectedCenter.getZ());
                        
                        // Check if entity is aligned with block center
                        if (dx < 0.1 && dy < 0.1 && dz < 0.1) {
                            // LOGGER.at(infoLevel).log("-> Center position match.");
                            coversBrokenBlock = true;
                        }
                    }

                    // Fallback: if block data was missing, check if it's within the block grid of the broken block.
                    if (!hasExpectedCenter) {
                        var entityBlockPos = 
                            new Vector3i(
                                MathUtil.floor(pos.getX()),
                                MathUtil.floor(pos.getY()),
                                MathUtil.floor(pos.getZ())
                            );
                        if (entityBlockPos.equals(brokenBlock)) {
                            // LOGGER.at(infoLevel).log("-> Fallback block match.");
                            coversBrokenBlock = true;
                        }
                    }

                    if (coversBrokenBlock) {
                        // LOGGER.at(infoLevel).log("-> VillageZone is queued for removal.");
                        toRemove.add(entityRef);
                    }
                }
            }
        );

        // LOGGER.at(infoLevel).log("Finished iterating properties. Entities to remove: %d", toRemove.size());
        for (var ref : toRemove) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);   
        }
        // LOGGER.at(infoLevel).log("=== BreakBlockEvent Handled ===");
    }
}
