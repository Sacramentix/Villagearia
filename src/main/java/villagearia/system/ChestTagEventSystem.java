package villagearia.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.component.VillageZone;
// import villagearia.component.villageZone.VillageZoneTaggedChests;
import villagearia.event.ChestTaggedEvent;
import villagearia.event.ChestUntaggedEvent;
import villagearia.graph.VillageZoneGraph;

import java.util.UUID;
import javax.annotation.Nonnull;

public class ChestTagEventSystem {
    
    public static class Tagged extends EntityEventSystem<EntityStore, ChestTaggedEvent> {
        public Tagged() {
            super(ChestTaggedEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return null; // Handle event without necessarily querying on the entity itself if it's external, wait, usually query is for the entity. Wait, if it returns null, does it accept any?
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> entityStore, @Nonnull CommandBuffer<EntityStore> cb, @Nonnull ChestTaggedEvent event) {
            Vector3i pos = event.pos;
            Vector3d pos3d = new Vector3d(pos.x, pos.y, pos.z);
            
            for (UUID uuid : VillageZoneGraph.graph.keySet()) {
                Ref<EntityStore> zoneRef = entityStore.getExternalData().getRefFromUUID(uuid);
                if (zoneRef != null) {
                    TransformComponent transform = entityStore.getComponent(zoneRef, TransformComponent.getComponentType());
                    VillageZone zone = entityStore.getComponent(zoneRef, VillageZone.getComponentType());
                    if (transform != null && zone != null) {
                        double dist = transform.getPosition().distanceTo(pos3d);
                        if (dist <= zone.getRadius()) {
                            // VillageZoneTaggedChests taggedChests = entityStore.getComponent(zoneRef, VillageZoneTaggedChests.getComponentType());
                            // if (taggedChests == null) {
                            //     taggedChests = new VillageZoneTaggedChests();
                            // }
                            
                            // VillageZoneTaggedChests newComponent = taggedChests.clone();
                            // newComponent.addChest(pos);
                            // cb.addComponent(zoneRef, VillageZoneTaggedChests.getComponentType(), newComponent);
                        }
                    }
                }
            }
        }
    }

    public static class Untagged extends EntityEventSystem<EntityStore, ChestUntaggedEvent> {
        public Untagged() {
            super(ChestUntaggedEvent.class);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return null;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> entityStore, @Nonnull CommandBuffer<EntityStore> cb, @Nonnull ChestUntaggedEvent event) {
            Vector3i pos = event.pos;
            
            for (UUID uuid : VillageZoneGraph.graph.keySet()) {
                Ref<EntityStore> zoneRef = entityStore.getExternalData().getRefFromUUID(uuid);
                if (zoneRef != null) {
                    // VillageZoneTaggedChests taggedChests = entityStore.getComponent(zoneRef, VillageZoneTaggedChests.getComponentType());
                    // if (taggedChests != null) {
                    //     VillageZoneTaggedChests newComponent = taggedChests.clone();
                    //     newComponent.removeChest(pos);
                    //     // cb.addComponent(zoneRef, VillageZoneTaggedChests.getComponentType(), newComponent);
                    // }
                }
            }
        }
    }
}
