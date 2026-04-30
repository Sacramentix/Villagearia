package villagearia.ai.action.routine;

import java.util.stream.Collectors;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProviderSimple;

import villagearia.ai.HousedNpcEntity;
import villagearia.ai.HousedNpcPathfinder;
import villagearia.ai.action.Routine.RoutineContext;
import villagearia.graph.VillageZoneGraph;
import villagearia.resource.manager.VillageZoneManager;
import villagearia.utils.JomlUtils;
import villagearia.utils.MyBlockUtils;

public class SleepRoutine {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static boolean behavior(RoutineContext ctx) {
        var housedNpc = ctx.housedNpc();
        var store = ctx.store();
        var pos = ctx.myPos();
        var cb = ctx.cb();
        var npc = ctx.npc();
        var headRotation = ctx.headRotation();
        var entityRef = ctx.meRef();
        var pathSession = housedNpc.getPathSession();
        var dt = ctx.dt();
        var state = housedNpc.getState();
        
        // If not already in a valid routine state, initialize it
        if (!"PATHING_TOWARD_BED".equals(state) && !"SLEEPING".equals(state)) {
            housedNpc.setState("PATHING_TOWARD_BED");
            state = "PATHING_TOWARD_BED";
        }
        
        if ("PATHING_TOWARD_BED".equals(state)) {
            var queue = housedNpc.getPathQueue();
            if (housedNpc.getTargetPos() == null && queue.isEmpty()) {
                refillQueueWithVillageZonePathTowardBed(store, pos, housedNpc);
            }
            if (housedNpc.getTargetPos() == null) {
                setPathfindTargetToNext(store, housedNpc);
            }
            
            var tPos = housedNpc.getTargetPos();
            if (tPos == null) return true; // keep holding behavior
            
            var actualTarget = new Vector3d(tPos.x + 0.5, tPos.y, tPos.z + 0.5);
            
            pathSession.world = store.getExternalData().getWorld();
            var provider = store.getResource(AStarNodePoolProviderSimple.getResourceType());
            HousedNpcPathfinder.pathfindTo(npc, pathSession, provider, store, tPos, pos, entityRef, actualTarget, headRotation, cb);

            var distance = actualTarget.distanceTo(pos);

            if (distance < 3.0 && !queue.isEmpty()) {
                housedNpc.setTargetPos((Vector3i) null);
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
            housedNpc.workDuration -= dt;
            if (entityRef.getStore().getComponent(entityRef, MountedComponent.getComponentType()) == null) {
                sleepOnNearestBed(entityRef, store.getExternalData().getWorld(), pos, cb);
            }
            AnimationUtils.playAnimation(entityRef, AnimationSlot.Status, "Sleep", cb);
        }
        
        return true; 
    }
    
    private static void refillQueueWithVillageZonePathTowardBed(Store<EntityStore> store, Vector3d pos, HousedNpcEntity housedNpc) {
        var queue = housedNpc.getPathQueue();
        var startingZone = VillageZoneGraph.getNearestVillageZone(JomlUtils.toJoml(pos), store);
        if (startingZone == null) return;
        var endingZone = housedNpc.getVillageZoneUuid();
        var newPath = VillageZoneGraph.getShortestPath(startingZone, endingZone, store);
        if (newPath != null) queue.addAll(newPath);
    }

    private static boolean setPathfindTargetToNext(Store<EntityStore> store, HousedNpcEntity housedNpc) {;
        var queue = housedNpc.getPathQueue();
        var nextZone = queue.poll();
        if (nextZone == null) return false;
        var villageZone = VillageZoneManager.getVillageZone(store, nextZone);
        if (villageZone == null) return false;
        housedNpc.setTargetPos(JomlUtils.toHytale(villageZone.center));
        return true;
    }
    
    private static void sleepOnNearestBed(Ref<EntityStore> entityRef, World world, Vector3d pos, CommandBuffer<EntityStore> cb) {
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
            var blockPos = new Vector3i(x, y, z);
            
            var blockType = MyBlockUtils.getSafeBlockType(world, blockPos);
            if (blockType == null) continue;
            var isBed = false;
            if (blockType.getBeds() != null) {
                isBed = true;
            }
            
            if (!isBed) continue;
            var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk != null) {
                var filler = MyBlockUtils.getSafeBlockFiller(world, x, y, z);
                if (filler != 0) {
                    blockPos = new Vector3i(
                        x - FillerBlockUtil.unpackX(filler),
                        y - FillerBlockUtil.unpackY(filler),
                        z - FillerBlockUtil.unpackZ(filler)
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
        
        if (nearestBench != null) {
            var hitPos = new Vector3f(nearestBench.x + 0.5f, nearestBench.y + 0.5f, nearestBench.z + 0.5f);
            BlockMountAPI.mountOnBlock(entityRef, cb, nearestBench, hitPos);
            AnimationUtils.playAnimation(entityRef, AnimationSlot.Status, "Sleep", cb);
        }
    }
}