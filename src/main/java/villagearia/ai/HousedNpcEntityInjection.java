package villagearia.ai;

import java.util.LinkedList;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import com.hypixel.hytale.server.npc.navigation.AStarEvaluator;
import com.hypixel.hytale.server.npc.navigation.AStarNode;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProviderSimple;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;

import villagearia.VillageZoneManager;
import villagearia.ai.action.WaveNearbyNpc;
import villagearia.component.VillageZone;
import villagearia.graph.VillageZoneGraph;
import villagearia.utils.JomlUtils;
import villagearia.utils.MyBlockUtils;

public class HousedNpcEntityInjection {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static class SimpleTargetEvaluator implements AStarEvaluator {
        @Nonnull
        private final Vector3d target;
        public SimpleTargetEvaluator(@Nonnull Vector3d t) { this.target = t; }

        @Override
        public boolean isGoalReached(
            Ref<EntityStore> ref, AStarBase aStarBase, AStarNode node, MotionController mc, ComponentAccessor<EntityStore> ca
        ) {
            var nodePos = node.getPosition();
            var dx = nodePos.x - target.x;
            var dy = nodePos.y - target.y;
            var dz = nodePos.z - target.z;
            return (dx*dx + dy*dy + dz*dz) < 1.0;
        }

        @Override
        public float estimateToGoal(AStarBase aStarBase, Vector3d pos, MotionController mc) {
            return (float) pos.distanceTo(target);
        }
    }

    public HousedNpcPathfinder pathfinder = new HousedNpcPathfinder();

    public HousedNpcEntityInjection() {
        super();
    }


    public void overrideIdleStateBehavior(
        InjectHousedNpcAI.InjectHousedNpcAiContext ctx
    ) {
        
        var store = ctx.store();
        var chunk = ctx.chunk();
        var npc = ctx.npc();
        var housedNpc = ctx.housedNpc();
        var tickIndex = ctx.i();
        var cb = ctx.cb();

        wipeRoleBehavior(npc);

        var housedNpcType    = HousedNpcEntity.getComponentType();
        var npcType          = NPCEntity.getComponentType();
        var headRotationType = HeadRotation.getComponentType();
        var transformType    = TransformComponent.getComponentType();
        var mountedType      = MountedComponent.getComponentType();
        
        var world = store.getExternalData().getWorld();
        
        var worldTime = store.getResource(WorldTimeResource.getResourceType());
        var daytimeSeconds = world.getDaytimeDurationSeconds();
        var nighttimeSeconds = world.getNighttimeDurationSeconds();
        var totalDayDurationSeconds = daytimeSeconds + nighttimeSeconds;
        var isNight = worldTime != null && (worldTime.getDayProgress() > 0.8 || worldTime.getDayProgress() < 0.2);
        var isDay = !isNight;

        var entityRef = chunk.getReferenceTo(tickIndex);

        var transform = chunk.getComponent(tickIndex, transformType);
        var headRotation = chunk.getComponent(tickIndex, headRotationType);
        if (headRotation == null) return;

        var pos = transform.getPosition();

        if (WaveNearbyNpc.tryWaveAtNearby(
            new WaveNearbyNpc.WaveNearbyNpcContext(entityRef, housedNpc, worldTime, transform, headRotation, pos, store, cb)
        )) {
            return;
        }

        var pathSession = housedNpc.getPathSession();
        if (
            isNight &&
            !housedNpc.getState().equals("SLEEPING") &&
            !housedNpc.getState().equals("PATHING_TOWARD_BED")
        ) {
            housedNpc.setState("PATHING_TOWARD_BED");
            housedNpc.setTargetPos((Vector3i) null);
            housedNpc.setPathQueue(new LinkedList<>());
            pathSession.currentTarget = (Vector3i) null;
            standup(entityRef, cb);
        }
        if (
            isDay && (
                housedNpc.getState().equals("SLEEPING") ||
                housedNpc.getState().equals("PATHING_TOWARD_BED")
            )
        ) {
            housedNpc.setState("PATHING_RANDOM");
            housedNpc.setTargetPos((Vector3i) null);
            housedNpc.setPathQueue(new LinkedList<>());
            pathSession.currentTarget = (Vector3i) null;
            standup(entityRef, cb);
        }

        // set state
        var state = housedNpc.getState();

        if ("PATHING_RANDOM".equals(state)) {
            var queue = housedNpc.getPathQueue();
            if (housedNpc.getTargetPos() == null && queue.isEmpty()) {
                refillQueueWithRandomVillageZonePath(store, pos, housedNpc);
                // LOGGER.atInfo().log(
                //     "QUEUE REFILL " +
                //     queue.stream().map((uuid) -> {
                //         var ref = store.getExternalData().getRefFromUUID(uuid);
                //         var t = ref.getStore().getComponent(ref, transformType);
                //         return "(|"+uuid+"|"+ t.getPosition().toString()+")";
                //     }).collect(Collectors.joining(",\n ", "[\n", "\n]"))
                // );
            }
            if (housedNpc.getTargetPos() == null) {
                setPathfindTargetToNext(store, housedNpc);
                // LOGGER.atInfo().log("SET TARGET " + housedNpc.getTargetPos().toString());
                // LOGGER.atInfo().log(
                //     "QUEUE CHECK " +
                //     queue.stream().map((uuid) -> {
                //         var ref = store.getExternalData().getRefFromUUID(uuid);
                //         var t = ref.getStore().getComponent(ref, transformType);
                //         return "(|"+uuid+"|"+ t.getPosition().toString()+")";
                //     }).collect(Collectors.joining(",\n ", "[\n", "\n]"))
                // );
            }
            
            var tPos = housedNpc.getTargetPos();
            if (tPos == null) return;
            
            var actualTarget = new Vector3d(tPos.x + 0.5, tPos.y, tPos.z + 0.5);
            
            pathSession.world = store.getExternalData().getWorld();
            var provider = store.getResource(AStarNodePoolProviderSimple.getResourceType());
            pathfinder.pathfindTo(npc, pathSession, provider, store, tPos, pos, entityRef, actualTarget, headRotation, cb);

            double dx = actualTarget.x - pos.x;
            double dy = actualTarget.y - pos.y;
            double dz = actualTarget.z - pos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance < 3.0 && !queue.isEmpty()) {
                LOGGER.atInfo().log("NULL TARGET " + housedNpc.getTargetPos().toString() + " | " + distance);

                housedNpc.setTargetPos((Vector3i) null);
                // if (!success) {
                //     housedNpc.setState("SEATING");
                //     return;
                // }
            } else if (distance < 1.5 && queue.isEmpty()) {
                housedNpc.setState("SEATING");

            }
            
        } else if ("SEATING".equals(state)) {
            if (entityRef.getStore().getComponent(entityRef, mountedType) == null) {
                sitOnNearestBench(entityRef, store.getExternalData().getWorld(), pos, cb);
            }
            housedNpc.increaseWaitTicks();
            if (housedNpc.getWaitTicks() > 500) {
                housedNpc.setState("PATHING_RANDOM");
                housedNpc.setTargetPos((Vector3i) null);
                housedNpc.setPathQueue(new LinkedList<>());
                housedNpc.setWaitTicks(0);
                standup(entityRef, cb);
            }


        } else if ("PATHING_TOWARD_BED".equals(state)) {
            var queue = housedNpc.getPathQueue();
            if (housedNpc.getTargetPos() == null && queue.isEmpty()) {
                refillQueueWithVillageZonePathTowardBed(store, pos, housedNpc);
                // LOGGER.atInfo().log(
                //     "QUEUE REFILL " +
                //     queue.stream().map((uuid) -> {
                //         var ref = store.getExternalData().getRefFromUUID(uuid);
                //         var t = ref.getStore().getComponent(ref, transformType);
                //         return "(|"+uuid+"|"+ t.getPosition().toString()+")";
                //     }).collect(Collectors.joining(",\n ", "[\n", "\n]"))
                // );
            }
            if (housedNpc.getTargetPos() == null) {
                setPathfindTargetToNext(store, housedNpc);
            }
            
            var tPos = housedNpc.getTargetPos();
            if (tPos == null) return;
            
            var actualTarget = new Vector3d(tPos.x + 0.5, tPos.y, tPos.z + 0.5);
            
            pathSession.world = store.getExternalData().getWorld();
            var provider = store.getResource(AStarNodePoolProviderSimple.getResourceType());
            pathfinder.pathfindTo(npc, pathSession, provider, store, tPos, pos, entityRef, actualTarget, headRotation, cb);

            var distance = actualTarget.distanceTo(pos);

            if (distance < 3.0 && !queue.isEmpty()) {
                LOGGER.atInfo().log("NULL TARGET " + housedNpc.getTargetPos().toString() + " | " + distance);

                housedNpc.setTargetPos((Vector3i) null);
                // if (!success) {
                //     housedNpc.setState("SEATING");
                //     return;
                // }
            } else if (distance < 1.5 && queue.isEmpty()) {
                housedNpc.setTargetPos(housedNpc.getHomeBed());                
            }
            if (
                housedNpc.getTargetPos() != null &&
                housedNpc.getTargetPos().distanceTo((int) pos.x, (int) pos.y, (int) pos.z) < 1.5
            ) {
                housedNpc.setState("SLEEPING");
            }
        } else if ("SLEEPING".equals(state)) {
            if (entityRef.getStore().getComponent(entityRef, mountedType) == null) {
                sleepOnNearestBed(entityRef, store.getExternalData().getWorld(), pos, cb);
            }
            AnimationUtils.playAnimation(entityRef, AnimationSlot.Status, "Sleep", cb);
        } else {
            housedNpc.setState("PATHING_RANDOM");
        }
        

    }


    public void refillQueueWithRandomVillageZonePath(Store<EntityStore> store, Vector3d pos, HousedNpcEntity housedNpc) {
        var queue = housedNpc.getPathQueue();
        var startingZone = VillageZoneGraph.getNearestVillageZone(JomlUtils.toJoml(pos), store);
        if (startingZone == null) return;
        var newPath = VillageZoneGraph.getRandomVillageZonePath(startingZone, 3, store);
        if (newPath != null) queue.addAll(newPath);
    }

    public void refillQueueWithVillageZonePathTowardBed(Store<EntityStore> store, Vector3d pos, HousedNpcEntity housedNpc) {
        var queue = housedNpc.getPathQueue();
        var startingZone = VillageZoneGraph.getNearestVillageZone(JomlUtils.toJoml(pos), store);
        if (startingZone == null) return;
        var endingZone = housedNpc.getVillageZoneUuid();
        var newPath = VillageZoneGraph.getShortestPath(startingZone, endingZone, store);
        if (newPath != null) queue.addAll(newPath);
    }

    public boolean setPathfindTargetToNext(Store<EntityStore> store, HousedNpcEntity housedNpc) {;
        var queue = housedNpc.getPathQueue();
        var nextZone = queue.poll();
        if (nextZone == null) return false;
        var villageZone = VillageZoneManager.getVillageZone(store, nextZone);
        if (villageZone == null) return false;
        housedNpc.setTargetPos(JomlUtils.toHytale(villageZone.center));
        return true;
    }

    private void sitOnNearestBench(Ref<EntityStore> entityRef, World world, Vector3d pos, CommandBuffer<EntityStore> cb) {
        if (world == null || pos == null || entityRef == null) return;
        
        int radius = 3;
        int minX = (int) Math.floor(pos.x) - radius;
        int maxX = (int) Math.floor(pos.x) + radius;
        int minY = (int) Math.floor(pos.y) - 2;
        int maxY = (int) Math.floor(pos.y) + 2;
        int minZ = (int) Math.floor(pos.z) - radius;
        int maxZ = (int) Math.floor(pos.z) + radius;
        
        Vector3i nearestBench = null;
        double minDistance = Double.MAX_VALUE;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Vector3i blockPos = new Vector3i(x, y, z);
                    var blockType = MyBlockUtils.getSafeBlockType(world, blockPos);
                    if (blockType != null) {
                        boolean isSeat = false;
                        if (blockType.getSeats() != null) {
                            isSeat = true;
                        } else if (blockType.getId() != null) {
                            String id = blockType.getId().toLowerCase();
                            if (id.contains("bench") || id.contains("chair") || id.contains("stool") || id.contains("seat") || id.contains("couch")) {
                                if (blockType.getSeats() != null) {
                                    isSeat = true;
                                }
                            }
                        }
                        
                        if (isSeat) {
                            var chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
                            if (chunk != null) {
                                int filler = MyBlockUtils.getSafeBlockFiller(world, x, y, z);
                                if (filler != 0) {
                                    blockPos = new Vector3i(
                                        x - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackX(filler),
                                        y - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackY(filler),
                                        z - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackZ(filler)
                                    );
                                }
                            }
                            
                            double dx = blockPos.x + 0.5 - pos.x;
                            double dy = blockPos.y + 0.5 - pos.y;
                            double dz = blockPos.z + 0.5 - pos.z;
                            double distSq = dx*dx + dy*dy + dz*dz;
                            if (distSq < minDistance) {
                                minDistance = distSq;
                                nearestBench = blockPos;
                            }
                        }
                    }
                }
            }
        }
        
        if (nearestBench != null) {
            var hitPos = new Vector3f(nearestBench.x + 0.5f, nearestBench.y + 0.5f, nearestBench.z + 0.5f);
            BlockMountAPI.mountOnBlock(entityRef, cb, nearestBench, hitPos);
            AnimationUtils.playAnimation(entityRef, AnimationSlot.Status, "Sit", cb);
        }
    }

    private void standup(Ref<EntityStore> entityRef, CommandBuffer<EntityStore> cb) {
        var mountedType = MountedComponent.getComponentType();
        if (cb.getComponent(entityRef, mountedType) != null) {
            cb.removeComponent(entityRef, mountedType);
        }
        AnimationUtils.stopAnimation(entityRef, AnimationSlot.Status, true, cb);
    }

    private void sleepOnNearestBed(Ref<EntityStore> entityRef, World world, Vector3d pos, CommandBuffer<EntityStore> cb) {
        if (world == null || pos == null || entityRef == null) return;
        
        var radius = 3;
        var minX = (int) Math.floor(pos.x) - radius;
        var maxX = (int) Math.floor(pos.x) + radius;
        var minY = (int) Math.floor(pos.y) - 2;
        var maxY = (int) Math.floor(pos.y) + 2;
        var minZ = (int) Math.floor(pos.z) - radius;
        var maxZ = (int) Math.floor(pos.z) + radius;
        
        Vector3i nearestBench = null;
        var minDistance = Double.MAX_VALUE;
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Vector3i blockPos = new Vector3i(x, y, z);
                    
                    var blockType = MyBlockUtils.getSafeBlockType(world, blockPos);
                    if (blockType != null) {
                        boolean isBed = false;
                        if (blockType.getBeds() != null) {
                            isBed = true;
                        }
                        
                        if (isBed) {
                            var chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
                            if (chunk != null) {
                                int filler = MyBlockUtils.getSafeBlockFiller(world, x, y, z);
                                if (filler != 0) {
                                    blockPos = new Vector3i(
                                        x - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackX(filler),
                                        y - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackY(filler),
                                        z - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackZ(filler)
                                    );
                                }
                            }
                            
                            double dx = blockPos.x + 0.5 - pos.x;
                            double dy = blockPos.y + 0.5 - pos.y;
                            double dz = blockPos.z + 0.5 - pos.z;
                            double distSq = dx*dx + dy*dy + dz*dz;
                            if (distSq < minDistance) {
                                minDistance = distSq;
                                nearestBench = blockPos;
                            }
                        }
                    }
                }
            }
        }
        
        if (nearestBench != null) {
            var hitPos = new Vector3f(nearestBench.x + 0.5f, nearestBench.y + 0.5f, nearestBench.z + 0.5f);
            BlockMountAPI.mountOnBlock(entityRef, cb, nearestBench, hitPos);
            AnimationUtils.playAnimation(entityRef, AnimationSlot.Status, "Sleep", cb);
        }
    }

    public void wipeRoleBehavior(NPCEntity npc) {
        if (npc == null) return;           
        npc.getRole().clearOnceIfNeeded(); // Prevent JSON instructional loop
        npc.getRole().getBodySteering().clear();
        npc.getRole().getHeadSteering().clear();
    }
}
