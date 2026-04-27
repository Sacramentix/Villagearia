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
import villagearia.ai.action.leisure.LeisuresAvailable;
import villagearia.graph.VillageZoneGraph;
import villagearia.utils.JomlUtils;

public class Leisure {

    public static record LeisureContext(
        
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

    public static boolean behavior(LeisureContext ctx) {
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

        housedNpc.leisureDuration += ctx.dt();

        var worldTime = store.getResource(WorldTimeResource.getResourceType());
        var daytimeSeconds = world.getDaytimeDurationSeconds();
        var nighttimeSeconds = world.getNighttimeDurationSeconds();
        var totalDayDurationSeconds = daytimeSeconds + nighttimeSeconds;
        var leisureHoursAmount = housedNpc.leisureDuration / totalDayDurationSeconds * 24;
        if (leisureHoursAmount > housedNpc.leisureDurationGoal) {
            return false;
        }
        var leisureComponent = cb.getComponent(meRef, LeisureComponent.getComponentType());
        if (leisureComponent != null) {
            var leisure = leisureComponent.currentLeisure;
            switch (leisure) {
                case LeisuresAvailable.WANDER_NEARBY_ZONE:

                    return villagearia.ai.action.leisure.WanderNearbyZone.behavior(ctx);
                default:
                    break;
            }
            return true;
        } else {
            return findLeisure(ctx);
        }

    }

    /**
     * 
     * @param ctx
     * @return true if a leisure is found in range
     */
    public static boolean findLeisure(LeisureContext ctx) {
        var transform = ctx.transform();
        var pos = transform.getPosition();
        var store = ctx.store();
        var meRef = ctx.meRef();
        var cb = ctx.cb();
        var currentZone = VillageZoneGraph.getNearestVillageZone(JomlUtils.toJoml(pos), store);
        var list = VillageZoneGraph.getVillageZonesWithinDepth(currentZone, 5, store);
        Collections.shuffle(list, ThreadLocalRandom.current());
        
        var leisures = Arrays.asList(LeisuresAvailable.values());
        Collections.shuffle(leisures, ThreadLocalRandom.current());

        leisureLoop: for (var leisure : leisures) {
            switch (leisure) {
                case LeisuresAvailable.WANDER_NEARBY_ZONE:
                    var zoneList3 = VillageZoneGraph.getVillageZonesWithinDepth(currentZone, 3, store);
                    var wanderSite = villagearia.ai.action.leisure.WanderNearbyZone.findValidZoneToWander(store, zoneList3.stream()).findAny().orElse(null);
                    if (wanderSite == null) continue leisureLoop;

                    cb.addComponent(meRef, LeisureComponent.getComponentType(), new LeisureComponent(LeisuresAvailable.WANDER_NEARBY_ZONE));
                    cb.addComponent(meRef, villagearia.ai.action.leisure.WanderNearbyZone.WanderNearbyZoneComponent.getComponentType(), new villagearia.ai.action.leisure.WanderNearbyZone.WanderNearbyZoneComponent(wanderSite));
                    return true;
            }
        }

        return false; // No work available in range
    }

    public static class LeisureComponent implements Component<EntityStore> {

        public static final BuilderCodec<LeisureComponent> CODEC = BuilderCodec.builder(
                LeisureComponent.class, LeisureComponent::new
            )
            .append(
                new KeyedCodec<>("CurrentLeisure", Codec.STRING),
                (comp, val) -> comp.currentLeisure = val != null ? LeisuresAvailable.valueOf(val) : null,
                comp -> comp.currentLeisure != null ? comp.currentLeisure.name() : null
            ).add()
            .build();

        public LeisuresAvailable currentLeisure;

        public LeisureComponent() {

        }

        public LeisureComponent(LeisuresAvailable currentLeisure) {
            this.currentLeisure = currentLeisure;
        }

        public static ComponentType<EntityStore, LeisureComponent> getComponentType() {
            return Villagearia.instance().getLeisureComponentType();
        }

        public LeisureComponent clone() {
            var clone = new LeisureComponent();
            clone.currentLeisure = this.currentLeisure;
            return clone;
        }
    }
}
