package villagearia;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import villagearia.component.PropertyDeed;
import villagearia.component.PropertyDeedResource;

public class PropertyDeedManager {
    public static void addPropertyDeed(World world, UUID uuid, PropertyDeed deed) {
        PropertyDeedResource resource = world.getEntityStore().getStore().getResource(Villagearia.getInstance().getPropertyDeedResourceType());
        if (resource != null) {
            resource.getDeeds().put(uuid, deed);
        }
    }

    public static PropertyDeed getPropertyDeed(World world, UUID uuid) {
        PropertyDeedResource resource = world.getEntityStore().getStore().getResource(Villagearia.getInstance().getPropertyDeedResourceType());
        if (resource != null) {
            return resource.getDeeds().get(uuid);
        }
        return null;
    }
}
