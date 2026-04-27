package villagearia.ai.action;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
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
import villagearia.ai.action.work.TannerWork;
import villagearia.ai.action.work.WorksAvailable;
import villagearia.ai.action.work.TannerWork.TannerWorkComponent;
import villagearia.graph.VillageZoneGraph;
import villagearia.utils.JomlUtils;

public class Work {

    public static record WorkContext(
        
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

    public static boolean behavior(WorkContext ctx) {
        var housedNpc = ctx.housedNpc();
        var store = ctx.store();
        var meRef = ctx.meRef();
        var npc = ctx.npc();
        var transform = ctx.transform();
        var myPos = ctx.myPos();
        var dt = ctx.dt();
        var headRotation = ctx.headRotation();
        var cb = ctx.cb();
        var world = store.getExternalData().getWorld();
        housedNpc.workDuration += ctx.dt();
        var worldTime = store.getResource(WorldTimeResource.getResourceType());
        var daytimeSeconds = world.getDaytimeDurationSeconds();
        var nighttimeSeconds = world.getNighttimeDurationSeconds();
        var totalDayDurationSeconds = daytimeSeconds + nighttimeSeconds;
        var workHoursAmount = housedNpc.workDuration / totalDayDurationSeconds * 24;
        if (workHoursAmount > housedNpc.workDurationGoal) {
            return false;
        }
        var workComponent = cb.getComponent(meRef, WorkComponent.getComponentType());
        if (workComponent != null) {
            var work = workComponent.currentWork;
            switch (work) {
                case WorksAvailable.TANNER_WORK:
                    try {
                        var running = TannerWork.behavior(ctx);
                        if (!running) {
                            cb.tryRemoveComponent(meRef, WorkComponent.getComponentType());
                            cb.tryRemoveComponent(meRef, TannerWorkComponent.getComponentType());
                        }
                    } catch(PathfindError e) {
                        cb.tryRemoveComponent(meRef, WorkComponent.getComponentType());
                        cb.tryRemoveComponent(meRef, TannerWorkComponent.getComponentType());
                    }

                    return true;
                default:
                    break;
            }
            return true;
        } else {
            return findWork(ctx);
        }

    }

    /**
     * 
     * @param ctx
     * @return true if a work is found in range
     */
    public static boolean findWork(WorkContext ctx) {
        var transform = ctx.transform();
        var pos = transform.getPosition();
        var store = ctx.store();
        var meRef = ctx.meRef();
        var cb = ctx.cb();
        var currentZone = VillageZoneGraph.getNearestVillageZone(JomlUtils.toJoml(pos), store);
        var list = VillageZoneGraph.getVillageZonesWithinDepth(currentZone, 5, store);
        Collections.shuffle(list, ThreadLocalRandom.current());
        
        var works = Arrays.asList(WorksAvailable.values());
        Collections.shuffle(works, ThreadLocalRandom.current());

        workLoop: for (var work : works) {
            switch (work) {
                case WorksAvailable.TANNER_WORK:
                    var workSite = TannerWork.findValidWorkSite(store, list.stream()).findAny().orElse(null);
                    if (workSite == null) continue workLoop;
                    cb.addComponent(meRef, WorkComponent.getComponentType(), new WorkComponent(WorksAvailable.TANNER_WORK));
                    cb.addComponent(meRef, TannerWorkComponent.getComponentType(), new TannerWorkComponent(workSite));
                    return true;
            }
        }

        return false; // No work available in range
    }

    public static class WorkComponent implements Component<EntityStore> {

        public static final BuilderCodec<WorkComponent> CODEC = BuilderCodec.builder(
                WorkComponent.class, WorkComponent::new
            )
            .append(
                new KeyedCodec<>("CurrentWork", Codec.STRING),
                (comp, val) -> comp.currentWork = val != null ? WorksAvailable.valueOf(val) : null,
                comp -> comp.currentWork != null ? comp.currentWork.name() : null
            ).add()
            .build();

        public WorksAvailable currentWork;

        public WorkComponent() {

        }

        public WorkComponent(WorksAvailable currentWork) {
            this.currentWork = currentWork;
        }

        public static ComponentType<EntityStore, WorkComponent> getComponentType() {
            return Villagearia.instance().getWorkComponentType();
        }

        public WorkComponent clone() {
            var clone = new WorkComponent();
            clone.currentWork = this.currentWork;
            return clone;
        }
    }
}
