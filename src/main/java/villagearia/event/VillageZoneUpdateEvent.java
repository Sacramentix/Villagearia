package villagearia.event;

import com.hypixel.hytale.component.system.EcsEvent;

/**
 * Event representing an update to a VillageZoneComponent.
 */
public class VillageZoneUpdateEvent extends EcsEvent {
    
    // Entity reference is automatically determined via the CommandBuffer invoke mechanism 
    // and resolved inside the EntityEventSystem handlers via `archetypeChunk.getReferenceTo(index)`.

    public VillageZoneUpdateEvent() {
    }
}