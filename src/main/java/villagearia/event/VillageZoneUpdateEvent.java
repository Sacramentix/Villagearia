package villagearia.event;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.server.core.entity.Entity;

/**
 * Event representing an update to a VillageZoneComponent.
 */
public class VillageZoneUpdateEvent extends EcsEvent {

    private final Ref<Entity> entityRef;

    public VillageZoneUpdateEvent(Ref<Entity> entityRef) {
        this.entityRef = entityRef;
    }

    public Ref<Entity> getEntityRef() {
        return entityRef;
    }
}