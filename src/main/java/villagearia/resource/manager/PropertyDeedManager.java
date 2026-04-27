package villagearia.resource.manager;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import villagearia.component.PropertyDeed;
import villagearia.resource.PropertyDeedStore;

public class PropertyDeedManager {
    public static void addPropertyDeed(Store<EntityStore> store, UUID uuid, PropertyDeed deed) {
        store.getResource(PropertyDeedStore.getResourceType())
            .getDeeds().put(uuid, deed);
    }

    public static PropertyDeed getPropertyDeed(Store<EntityStore> store, UUID uuid) {
        return store.getResource(PropertyDeedStore.getResourceType())
            .getDeeds().get(uuid);
    }
}
