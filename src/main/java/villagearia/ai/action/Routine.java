package villagearia.ai.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import villagearia.Villagearia;
import villagearia.ai.HousedNpcEntity;
import villagearia.ai.HousedNpcPathfinder.PathfindError;
import villagearia.ai.action.routine.RoutinesAvailable;
import villagearia.ai.action.routine.SleepRoutine;

public class Routine {

    public static record RoutineContext(
        Ref<EntityStore> meRef,
        HousedNpcEntity housedNpc,
        NPCEntity npc,
        WorldTimeResource worldTime,
        TransformComponent transform,
        HeadRotation headRotation,
        Vector3d myPos,
        Store<EntityStore> store,
        float dt,
        CommandBuffer<EntityStore> cb
    ) {}

    public static boolean behavior(RoutineContext ctx) {
        var meRef = ctx.meRef();
        var cb = ctx.cb();

        var routineComponent = cb.getComponent(meRef, RoutineComponent.getComponentType());
        if (routineComponent != null) {
            var routine = routineComponent.currentRoutine;
            switch (routine) {
                case RoutinesAvailable.SLEEP_ROUTINE:
                    try {
                        var running = SleepRoutine.behavior(ctx);
                        if (!running) {
                            cb.tryRemoveComponent(meRef, RoutineComponent.getComponentType());
                        }
                    } catch (PathfindError e) {
                        cb.tryRemoveComponent(meRef, RoutineComponent.getComponentType());
                    }
                    return true;
                default:
                    break;
            }
            return true;
        }

        return false;
    }

    public static class RoutineComponent implements Component<EntityStore> {

        public static final BuilderCodec<RoutineComponent> CODEC = BuilderCodec.builder(
                RoutineComponent.class, RoutineComponent::new
            )
            .append(
                new KeyedCodec<>("CurrentRoutine", Codec.STRING),
                (comp, val) -> comp.currentRoutine = val != null ? RoutinesAvailable.valueOf(val) : null,
                comp -> comp.currentRoutine != null ? comp.currentRoutine.name() : null
            ).add()
            .build();

        public RoutinesAvailable currentRoutine;

        public RoutineComponent() {
        }

        public RoutineComponent(RoutinesAvailable currentRoutine) {
            this.currentRoutine = currentRoutine;
        }

        public static ComponentType<EntityStore, RoutineComponent> getComponentType() {
            return Villagearia.instance().getRoutineComponentType();
        }

        public RoutineComponent clone() {
            var clone = new RoutineComponent();
            clone.currentRoutine = this.currentRoutine;
            return clone;
        }
    }
}