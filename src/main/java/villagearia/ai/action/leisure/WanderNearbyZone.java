package villagearia.ai.action.leisure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;
import villagearia.ai.HousedNpcPathfinder;
import villagearia.ai.action.Leisure.LeisureContext;
import villagearia.resource.BlockOfInterest;
import villagearia.resource.BlockOfInterestStore;
import villagearia.resource.manager.VillageZoneManager;
import villagearia.utils.JomlUtils;

public class WanderNearbyZone {

    public static record WanderZoneSite(UUID zoneUuid) {}

    public static Stream<WanderZoneSite> findValidZoneToWander(Store<EntityStore> store, Stream<UUID> villageZonesUuid) {
        return villageZonesUuid.map(WanderZoneSite::new);
    }

    /**
     * @return true if the behavior run successfully, false if work can't be done and need to be stopped
     */
    public static boolean behavior(LeisureContext ctx) {
        var meRef = ctx.meRef();
        var store = ctx.store();
        var transform = ctx.transform();
        var pos = transform.getPosition();
        var housedNpc = ctx.housedNpc();
        var npc = ctx.npc();
        var headRotation = ctx.headRotation();
        var cb = ctx.cb();
        
        var wanderComp = cb.getComponent(meRef, WanderNearbyZoneComponent.getComponentType());
        if (wanderComp == null) return false;
        
        var targetZoneUuid = wanderComp.targetZoneUuid;
        if (targetZoneUuid == null) return false;
        
        if (wanderComp.targetPos == null) {
            var zone = VillageZoneManager.getVillageZone(store, targetZoneUuid);
            if (zone == null) return false;
            
            var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType()).getIndex();
            var blockOfInterests = blockOfInterestIndex.get(targetZoneUuid);
            
            if (
                blockOfInterests != null &&
                blockOfInterests.containsKey(BlockOfInterest.BENCH) &&
                !blockOfInterests.get(BlockOfInterest.BENCH).isEmpty()
            ) {
                var benches = new ArrayList<>(blockOfInterests.get(BlockOfInterest.BENCH));
                Collections.shuffle(benches);
                wanderComp.targetPos = benches.get(0); // Pick a random bench
                wanderComp.isBench = true;
            } else {
                wanderComp.targetPos = new Vector3i((int) zone.center.x, (int) zone.center.y, (int) zone.center.z); // Just wander to the center
                wanderComp.isBench = false;
            }
        }
        
        var pos_3d = new org.joml.Vector3d(pos.x, pos.y, pos.z);
        var target_pos_3d = new org.joml.Vector3d(wanderComp.targetPos.x, wanderComp.targetPos.y, wanderComp.targetPos.z);
        
        HousedNpcPathfinder.multiVillageZonePathFindTo(housedNpc, store, pos_3d, target_pos_3d, meRef, headRotation, cb, npc);
        
        return true;
    }

    public static class WanderNearbyZoneComponent implements Component<EntityStore> {

        public static final BuilderCodec<WanderNearbyZoneComponent> CODEC = BuilderCodec.builder(
                WanderNearbyZoneComponent.class, WanderNearbyZoneComponent::new
            )
            .append(new KeyedCodec<>("TargetZone", Codec.UUID_BINARY), (x, v) -> x.targetZoneUuid = v, (x) -> x.targetZoneUuid).add()
            .append(new KeyedCodec<>("TargetPos", Vector3i.CODEC), (x, v) -> x.targetPos = v, (x) -> x.targetPos).add()
            .append(new KeyedCodec<>("IsBench", Codec.BOOLEAN), (x, v) -> x.isBench = v, (x) -> x.isBench).add()
            .build();

        public UUID targetZoneUuid;
        public Vector3i targetPos;
        public boolean isBench;

        public WanderNearbyZoneComponent() {

        }

        public WanderNearbyZoneComponent(WanderZoneSite site) {
            this.targetZoneUuid = site.zoneUuid();
        }

        public static ComponentType<EntityStore, WanderNearbyZoneComponent> getComponentType() {
            return Villagearia.instance().getWanderNearbyZoneComponentType();
        }

        public WanderNearbyZoneComponent clone() {
            var clone = new WanderNearbyZoneComponent();
            clone.targetZoneUuid = this.targetZoneUuid;
            clone.targetPos = this.targetPos;
            clone.isBench = this.isBench;
            return clone;
        }
    }
}
