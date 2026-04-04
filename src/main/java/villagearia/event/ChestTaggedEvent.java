package villagearia.event;

import com.hypixel.hytale.component.system.EcsEvent;
import com.hypixel.hytale.math.vector.Vector3i;

public class ChestTaggedEvent extends EcsEvent {
    public final Vector3i pos;

    public ChestTaggedEvent(Vector3i pos) {
        this.pos = pos;
    }
}
