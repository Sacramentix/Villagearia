package villagearia.system;

import villagearia.component.VillageZone;
import villagearia.event.VillageZoneUpdateEvent;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System to manage updates to VillageZoneComponent when an entity's position or zone changes.
 */
public class VillageZoneSystem extends EntityEventSystem<EntityStore, VillageZoneUpdateEvent> {

    private final Query<EntityStore> query;

    public VillageZoneSystem() {
        super(VillageZoneUpdateEvent.class);
        this.query = Query.and(VillageZone.getComponentType());
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void handle(
        int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull VillageZoneUpdateEvent event
    ) {
        var transformType   = TransformComponent.getComponentType();
        var villageZoneType = VillageZone.getComponentType();
        var uuidType        = UUIDComponent.getComponentType();

        var entityTargetRef = archetypeChunk.getReferenceTo(index);
        var transform       = commandBuffer.getComponent(entityTargetRef, transformType);
        var villageZone     = commandBuffer.getComponent(entityTargetRef, villageZoneType);
        var uuidComponent   = commandBuffer.getComponent(entityTargetRef, uuidType);

        if (transform == null) return;
        if (villageZone == null) return;
        if (uuidComponent == null) return;

        var uuid = uuidComponent.getUuid();
        var pos = transform.getPosition();
        var world = commandBuffer.getExternalData().getWorld();
        // Need a ConcurrentHashMap to avoid dead lock on forEachEntityParallel
        Set<Ref<EntityStore>> entitySet = ConcurrentHashMap.newKeySet();
        // Retrieve all VillageZone
        world.getEntityStore().getStore().forEachEntityParallel(
            this.query,
            (int i, ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb) -> {
                entitySet.add(chunk.getReferenceTo(i));
            }
        );
        entitySet.remove(entityTargetRef);

        // Loop on VillageZone to update/create connection between nearby VillageZone to create a graph of VillageZone
        entitySet.parallelStream()
        .forEach(entityRef_other -> {

            var transform_other      = commandBuffer.getComponent(entityRef_other, transformType);
            var villageZone_other    = commandBuffer.getComponent(entityRef_other, villageZoneType);
            var uuidComponent_other  = commandBuffer.getComponent(entityRef_other, uuidType);

            if (transform_other == null) return;
            if (villageZone_other == null) return;
            if (uuidComponent_other == null) return;

            var uuid_other = uuidComponent_other.getUuid();
            var pos_other = transform_other.getPosition();

            var distance = pos.distanceTo(pos_other);
            // The graph is undirected so if one of the 2 VillageZone contain the other we consider them connected to each other.
            var maxRadius = Math.max(villageZone.getRadius(), villageZone_other.getRadius());

            if (distance <= maxRadius) {
                // node are connected
                villageZone_other.addConnection(uuid);
                villageZone.addConnection(uuid_other);
            } else {
                // node are disconnected
                villageZone_other.removeConnection(uuid);
                villageZone.removeConnection(uuid_other);
            }
        });

    }
}