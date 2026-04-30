package villagearia.ai.action.interrupt;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import villagearia.Villagearia;
import villagearia.ai.HousedNpcEntity;
import villagearia.ai.InjectHousedNpcAI;
import villagearia.ai.action.work.WorksAvailable;

public class WaveNearbyNpc {

    // Radius squared within which an NPC spots another to wave
    private static final double GREET_RADIUS_SQ = 6.0 * 6.0;
    // 2.0 seconds lock for greeting animation
    private static final double GREET_ANIMATION_DURATION = 2000; // 2 seconds

    public static record WaveNearbyNpcContext(
        
        Ref<EntityStore> meRef,
        HousedNpcEntity housedNpc,
        NPCEntity npc,
        WorldTimeResource worldTime,
        TransformComponent transform,
        HeadRotation headRotation,
        Vector3d myPos,
        Store<EntityStore> store,
        
        CommandBuffer<EntityStore> cb
    ) {}

    /**
     * @return true if the NPC is currently locked in the greet animation, false otherwise.
     */
    public static boolean behavior(WaveNearbyNpcContext ctx) {
        var worldTime = ctx.worldTime();
        var housedNpc = ctx.housedNpc();
        var meRef = ctx.meRef();
        var store = ctx.store();
        var myPos = ctx.myPos();
        var npc = ctx.npc();
        var cb = ctx.cb();

        var query = Query.and(
            Query.or(
                Player.getComponentType(),
                HousedNpcEntity.getComponentType()
            ),
            TransformComponent.getComponentType(),
            UUIDComponent.getComponentType()
        );

        var currentTime = System.currentTimeMillis();
        var lastWave = cb.ensureAndGetComponent(meRef, WaveNearbyNpcComponent.getComponentType());
        
        // Distribute compute over 20 ticks (approx 1000ms). meRef.getIndex() staggers which tick this entity acts on.
        boolean shouldScan = (meRef.getIndex() + InjectHousedNpcAI.totalTicks) % 20 == 0;

        if (shouldScan) {
            store.forEachChunk(query, (BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>>) (chunk, chunkCb) -> {
                if (lastWave.lastGreetedNpc != null) return; // Short-circuit if we already found someone

                for (var i = 0; i < chunk.size(); i++) {
                    var otherRef = chunk.getReferenceTo(i);
                    if (otherRef.getIndex() == meRef.getIndex()) continue;
                    
                    var otherTransform = chunk.getComponent(i, TransformComponent.getComponentType());
                    var otherPos = otherTransform.getPosition();
                    var distSq = myPos.distanceSquaredTo(otherPos);
                    if (distSq > GREET_RADIUS_SQ) continue;
                    
                    try {
                        var canSee = npc.getRole().getPositionCache().hasLineOfSight(meRef, otherRef, chunkCb);
                        if (!canSee) continue; // Blocked by wall
                    } catch (Exception e) {
                        continue;
                    }
                    
                    var uuidComp = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uuidComp == null) continue;

                    var otherUuid = uuidComp.getUuid();
                    var wavedThisDay = cb.ensureAndGetComponent(meRef, WavedThisDayComponent.getComponentType());
                    
                    if (
                        !wavedThisDay.wavedNpcUuids.contains(otherUuid) &&
                        (currentTime - lastWave.lastGreetTime) > 15_000 // 15 seconds of cooldown
                    ) {
                        lastWave.lastGreetTime = currentTime;
                        lastWave.lastGreetedNpc = otherUuid;
                        wavedThisDay.wavedNpcUuids.add(otherUuid);
                        break; // Stop searching this chunk since we found a greet target
                    }
                    wavedThisDay.wavedNpcUuids.add(otherUuid);
                }
            });
        }
        
        // 1) Are we already currently locked in an animation?
        if (lastWave.lastGreetedNpc == null) return false;
        if ((currentTime - lastWave.lastGreetTime) < GREET_ANIMATION_DURATION) {
            // Set animation directly if not playing
            var activeAnimComp = cb.getComponent(meRef, ActiveAnimationComponent.getComponentType());
            if (activeAnimComp != null) {
                var currentAnim = activeAnimComp.getActiveAnimations()[AnimationSlot.Action.ordinal()];
                if (!"Wave".equals(currentAnim)) {
                    AnimationUtils.playAnimation(meRef, AnimationSlot.Action, "Wave", cb);
                    activeAnimComp.setPlayingAnimation(AnimationSlot.Action, "Wave");
                }
            }
            
            var otherNpcRef = store.getExternalData().getRefFromUUID(lastWave.lastGreetedNpc);
            var otherTransform = store.getComponent(otherNpcRef, TransformComponent.getComponentType());
            if (otherTransform == null) return true;
            var otherPos = otherTransform.getPosition();
            rotateTowards(ctx, otherPos);
            return true;
        } else {
            // Done greeting
            lastWave.lastGreetedNpc = null;
            
            // reset animation once
            var activeAnimComp = cb.getComponent(meRef, ActiveAnimationComponent.getComponentType());
            if (activeAnimComp != null) {
                var currentAnim = activeAnimComp.getActiveAnimations()[AnimationSlot.Action.ordinal()];
                if (!"Idle".equals(currentAnim)) {
                    AnimationUtils.playAnimation(meRef, AnimationSlot.Action, "Idle", cb);
                    activeAnimComp.setPlayingAnimation(AnimationSlot.Action, "Idle");
                }
            }
            return false;
        }
        
    }
    
    private static void rotateTowards(WaveNearbyNpcContext ctx, Vector3d targetPos) {
        var myPos = ctx.myPos();
        var dx = (float)(targetPos.x - myPos.x);
        var dy = (float)(targetPos.y - myPos.y);
        var dz = (float)(targetPos.z - myPos.z);
        var dist  = (float) Math.sqrt(dx*dx + dz*dz);
        var pitch = (float) Math.atan2(dy, dist);
        var yaw   = (float) Math.atan2(-dx, -dz);
        
        var store = ctx.store();
        var meRef = ctx.meRef();
        
        var isMounted = store.getComponent(meRef, MountedComponent.getComponentType()) != null;
        var myNpc = store.getComponent(meRef, NPCEntity.getComponentType());
        var role = myNpc.getRole();
        if (role != null) {
            if (!isMounted) {
                role.getBodySteering().setYaw(yaw).setPitch(0.0f);
            }
            role.getHeadSteering().setYaw(yaw).setPitch(pitch);
            return;
        }
        
        var targetRot = new Vector3f(pitch, yaw, 0.0f);
        
        var headComp = ctx.headRotation();
        if (headComp != null) {
            headComp.setRotation(targetRot);
        }
        
        if (!isMounted) {
            var transformComp = ctx.transform();
            if (transformComp != null) {
                transformComp.setRotation(targetRot);
            }
        }
    }

    public static class WaveNearbyNpcComponent implements Component<EntityStore> {
        public UUID lastGreetedNpc = null; // Set to null when we are no longer greeting
        public long lastGreetTime = 0;
        
        public static ComponentType<EntityStore, WaveNearbyNpcComponent> getComponentType() {
            return Villagearia.instance().getWaveNearbyNpcComponentType();
        }
        WaveNearbyNpcComponent() {}
        WaveNearbyNpcComponent(UUID lastGreetedNpc, long lastGreetTime) {
            this.lastGreetedNpc = lastGreetedNpc;
            this.lastGreetTime = lastGreetTime;
        }

        @Override
        public WaveNearbyNpcComponent clone() {
            var cloned = new WaveNearbyNpcComponent();
            cloned.lastGreetedNpc = this.lastGreetedNpc;
            cloned.lastGreetTime = this.lastGreetTime;
            return cloned;
        }
    }

    public static class WavedThisDayComponent implements Component<EntityStore> {
        public static final BuilderCodec<WavedThisDayComponent> CODEC = BuilderCodec.builder(
                WavedThisDayComponent.class, WavedThisDayComponent::new
            )
            .append(new KeyedCodec<>("WavedNpcUuids", new ArrayCodec<>(Codec.UUID_BINARY, UUID[]::new)),
                (comp, val) -> comp.wavedNpcUuids = (val == null ? new HashSet<>() : new HashSet<>(Arrays.asList(val))),
                comp -> comp.wavedNpcUuids == null ? new UUID[0] : comp.wavedNpcUuids.toArray(new UUID[0])).add()
            .build();

        public Set<UUID> wavedNpcUuids = new HashSet<>();

        WavedThisDayComponent() {}

        public static ComponentType<EntityStore, WavedThisDayComponent> getComponentType() {
            return Villagearia.instance().getWavedThisDayComponentType();
        }

        @Override
        public WavedThisDayComponent clone() {
            var cloned = new WavedThisDayComponent();
            cloned.wavedNpcUuids.addAll(this.wavedNpcUuids);
            return cloned;
        }
    }
}
