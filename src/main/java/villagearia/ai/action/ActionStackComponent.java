package villagearia.ai.action;

import java.util.ArrayDeque;
import java.util.Deque;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;

import com.hypixel.hytale.codec.builder.BuilderCodec;

public class ActionStackComponent implements Component<EntityStore> {
    
    public Deque<String> stack = new ArrayDeque<>();

    // Keep it entirely transient
    public static final BuilderCodec<ActionStackComponent> CODEC = BuilderCodec.builder(
        ActionStackComponent.class, ActionStackComponent::new
    ).build();

    public ActionStackComponent() {}

    public static ComponentType<EntityStore, ActionStackComponent> getComponentType() {
        return Villagearia.instance().getActionStackComponentType();
    }
    
    @Override
    public ActionStackComponent clone() {
        var cloned = new ActionStackComponent();
        cloned.stack = new ArrayDeque<>(this.stack);
        return cloned;
    }
}
