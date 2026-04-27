package villagearia.ai.action;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import villagearia.ai.HousedNpcEntity;

public class WaveNearbyNpc {

    // Radius squared within which an NPC spots another to wave
    private static final double GREET_RADIUS_SQ = 6.0 * 6.0;
    // 0.8 represents ~20 hours of in-game time before they can wave again at the same person
    private static final float WAVE_COOLDOWN_DAYS = 0.8f; 
    // 2.0 seconds lock for greeting animation
    private static final double GREET_ANIMATION_LOCKED_SECONDS = 2.0;

    public static record WaveNearbyNpcContext(
        
        Ref<EntityStore> meRef,
        HousedNpcEntity housedNpc,
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
    public static boolean tryWaveAtNearby(WaveNearbyNpcContext ctx) {
        var worldTime = ctx.worldTime();
        var housedNpc = ctx.housedNpc();
        var meRef = ctx.meRef();
        var store = ctx.store();
        var myPos = ctx.myPos();
        var cb = ctx.cb();
        
        if (worldTime == null) return false;
        
        if (store.getComponent(meRef, MountedComponent.getComponentType()) != null) {
            return false; // Skip waving entirely if sitting, as full-body action animations break the sit posture.
        }
        
        // Use true hardware OS time for the precise 3-second lock, avoiding in-game accelerated timescale bugs
        var currentTimeSeconds = System.currentTimeMillis() / 1000.0;
        
        // Convert to days for massive cooldown tracking (0.8 days = ~19.2 Hytale hours)
        var currentTimeDays = (float) (worldTime.getGameTime().getEpochSecond() / 86400.0);

        // 1) Are we already currently locked in an animation?
        if (housedNpc.lastGreetedNpc != null) {
            var timeSinceGreet = currentTimeSeconds - housedNpc.lastGreetTime;
            if (timeSinceGreet < GREET_ANIMATION_LOCKED_SECONDS) {
                // Find the other NPC's location and stare at them
                
                var query = Query.and(UUIDComponent.getComponentType(), TransformComponent.getComponentType());
                store.forEachChunk(query, (chunk, chunkCb) -> {
                    for (var i = 0; i < chunk.size(); i++) {
                        var uuid = chunk.getComponent(i, UUIDComponent.getComponentType()).getUuid();
                        if (!uuid.equals(housedNpc.lastGreetedNpc)) continue;
                        var targetTransform = chunk.getComponent(i, TransformComponent.getComponentType());
                        rotateTowards(ctx, targetTransform.getPosition());
                        break;
                    }
                });
                
                return true; // Lock behavioral pathfinding
            } else {
                // Done greeting
                housedNpc.lastGreetedNpc = null;
                
                var storeAccessor = ctx.cb();
                
                // AnimationUtils.stopAnimation(meRef, AnimationSlot.Action, true, storeAccessor);
                AnimationUtils.playAnimation(meRef, AnimationSlot.Action, "Idle", storeAccessor);
                return true; // Wait for the very next tick before finding the next action
            }
        }

        var query = Query.and(
            HousedNpcEntity.getComponentType(),
            TransformComponent.getComponentType(),
            UUIDComponent.getComponentType()
        );
        
        // We use a mutable container to break out early
        var hasJustStartedGreeting = new boolean[]{false};

        store.forEachChunk(query, (chunk, chunkCb) -> {
            if (hasJustStartedGreeting[0]) return; // Stop looping if we already found someone

            for (var i = 0; i < chunk.size(); i++) {
                var otherRef = chunk.getReferenceTo(i);
                
                // Skip self
                if (otherRef.getIndex() == meRef.getIndex()) continue; 

                var otherTransform = chunk.getComponent(i, TransformComponent.getComponentType());
                if (otherTransform == null) continue;

                // Basic distance check
                var distSq = myPos.distanceSquaredTo(otherTransform.getPosition());
                if (distSq < GREET_RADIUS_SQ) {
                    var myNpc = store.getComponent(meRef, NPCEntity.getComponentType());
                    var theirNpc = chunk.getComponent(i, NPCEntity.getComponentType());

                    if (myNpc != null && myNpc.getRole() != null && theirNpc != null) {
                        try {
                            var role = myNpc.getRole();
                            if (role != null) {
                                var canSee = role.getPositionCache().hasLineOfSight(meRef, otherRef, store);
                                if (!canSee) continue; // Blocked by wall
                            }
                        } catch (Exception e) {
                            // If position cache is not ready, skip checking or gracefully fail
                        }
                    }

                    var uuidComp = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (uuidComp == null) continue;

                    var otherUuid = uuidComp.getUuid();
                    var lastMetDays = housedNpc.npcMeeted.get(otherUuid);

                    // Check if it's the first time meeting them today
                    if (lastMetDays == null || (currentTimeDays - lastMetDays) > WAVE_COOLDOWN_DAYS) {
                        // Mark as met!
                        housedNpc.npcMeeted.put(otherUuid, currentTimeDays);
                        housedNpc.lastGreetedNpc = otherUuid;
                        housedNpc.lastGreetTime = currentTimeSeconds;
                        hasJustStartedGreeting[0] = true;
                        
                        AnimationUtils.playAnimation(meRef, AnimationSlot.Action, "Wave", cb);
                        
                        // Rotate towards them
                        rotateTowards(ctx, otherTransform.getPosition());
                        
                        return; // Done
                    }
                }
            }
        });

        return hasJustStartedGreeting[0];
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
}
