package villagearia.ai;

import java.util.ArrayList;
import java.util.List;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import com.hypixel.hytale.server.npc.navigation.AStarBase.Progress;
import com.hypixel.hytale.server.npc.navigation.AStarEvaluator;
import com.hypixel.hytale.server.npc.navigation.AStarNode;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProvider;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProviderSimple;

import villagearia.graph.VillageZoneGraph;
import villagearia.resource.manager.VillageZoneManager;
import villagearia.system.DoorTracker;
import villagearia.utils.JomlUtils;
import villagearia.utils.MathSegmentUtils;
import villagearia.utils.MyBlockUtils;

public class HousedNpcPathfinder {

    public static class SimpleTargetEvaluator implements AStarEvaluator {
        
        private final Vector3d target;
        public SimpleTargetEvaluator( Vector3d t) { this.target = t; }

        @Override
        public boolean isGoalReached(
            Ref<EntityStore> ref, AStarBase aStarBase, AStarNode node, MotionController mc, ComponentAccessor<EntityStore> ca
        ) {
            var nodePos = node.getPosition();
            var dx = nodePos.x - target.x;
            var dy = nodePos.y - target.y;
            var dz = nodePos.z - target.z;
            return dx < 2 && dy < 2 && dz < 2;
        }

        @Override
        public float estimateToGoal(AStarBase aStarBase, Vector3d pos, MotionController mc) {
            return (float) pos.distanceTo(target);
        }
    }
    public enum PathfindErrorType {
        OPEN_NODE_LIMIT_EXCEEDED,
        TOTAL_NODE_LIMIT_EXCEEDED,
        UNKNOWN
    }

    public static class PathfindError extends RuntimeException {
        
        public final PathfindErrorType type;
        public final Vector3d startPos;
        public final Vector3d targetPos;
        public final int openNodeCount;
        public final int totalNodeCount;

        public PathfindError(PathfindErrorType type, Vector3d startPos, Vector3d targetPos, int openNodeCount, int totalNodeCount) {
            super(String.format("Pathfind error: %s (Start: %s, Target: %s, OpenNodes: %d, TotalNodes: %d)", type, startPos, targetPos, openNodeCount, totalNodeCount));
            this.type = type;
            this.startPos = startPos;
            this.targetPos = targetPos;
            this.openNodeCount = openNodeCount;
            this.totalNodeCount = totalNodeCount;
        }
    }

    public static boolean multiVillageZonePathFindTo(
        HousedNpcEntity housedNpc, Store<EntityStore> store, org.joml.Vector3d current_pos, org.joml.Vector3d target_pos,
        Ref<EntityStore> entityRef, HeadRotation headRotation, CommandBuffer<EntityStore> cb, NPCEntity npc
    
    ) {
        do {
            var dx = current_pos.x - target_pos.x;
            if (dx > 2 || dx < -2) break;
            var dy = current_pos.y - target_pos.y;
            if (dy > 2 || dy < -2) break;
            var dz = current_pos.z - target_pos.z;
            if (dz > 2 || dz < -2) break;
            housedNpc.setTargetPos((Vector3i) null);
            return true;
        } while (false);

        if (housedNpc.getTargetPos() == null && housedNpc.getPathQueue().isEmpty()) {
            var currentZone = VillageZoneGraph.getNearestVillageZone(current_pos, store);
            var targetZone = VillageZoneGraph.getNearestVillageZone(target_pos, store);
            var pathZone = VillageZoneGraph.getShortestPath(currentZone, targetZone, store);
            // pathZone.removeLast(); // We remove the last node to avoid pathfinding through the middle of the last VillageZone
            housedNpc.setPathQueue(pathZone);
        }
        if (housedNpc.getTargetPos() == null) {
            var queue = housedNpc.getPathQueue();
            // We skip last zone to pathfind straight to the target position
            if (queue.size() > 1) {
                var villageZone = VillageZoneManager.getVillageZone(store, queue.poll());
                housedNpc.setTargetPos(JomlUtils.toHytale(villageZone.center));
            } else {
                housedNpc.setTargetPos(JomlUtils.toHytale(target_pos));
            }
            
        }
        
        var tPos = housedNpc.getTargetPos();
        
        var actualTarget = new Vector3d(tPos.x + 0.5, tPos.y, tPos.z + 0.5);
        var pathSession = housedNpc.getPathSession();
        pathSession.world = store.getExternalData().getWorld();
        var provider = store.getResource(AStarNodePoolProviderSimple.getResourceType());
        pathfindTo(npc, pathSession, provider, store, tPos, JomlUtils.toHytale(current_pos), entityRef, actualTarget, headRotation, cb);

        do {
            var dx = current_pos.x - target_pos.x;
            if (dx > 2 || dx < -2) break;
            var dy = current_pos.y - target_pos.y;
            if (dy > 2 || dy < -2) break;
            var dz = current_pos.z - target_pos.z;
            if (dz > 2 || dz < -2) break;
            housedNpc.setTargetPos((Vector3i) null);
        } while (false);
        return false;
    }

    public static void pathfindTo(
        NPCEntity npc, HousedNpcPathSession session, AStarNodePoolProvider provider,  Store<EntityStore> store,
        Vector3i pos_target_vector3i, Vector3d pos_current, Ref<EntityStore> entityRef, Vector3d pos_target,
        HeadRotation headRotation, CommandBuffer<EntityStore> cb
    ) {
        // if (npcRole == null) return;
        var motionController = npc.getRole().getActiveMotionController();
        // if (motionController == null) return;
        // if (provider == null) return;
        // If target changed or wait duration passed, recompute path
        if (
            session.currentTarget == null ||
            !session.currentTarget.equals(pos_target_vector3i) ||
            (System.currentTimeMillis() - session.lastComputeTime > 3000)
        ) {
            
            
            var openedDoors = openDoorsBeforePathing(session.world, pos_current, pos_target);

            // session.aStar -> AStarWithTarget
            if (session.currentTarget == null || !session.currentTarget.equals(pos_target_vector3i) ) {
                session.currentTarget = pos_target_vector3i;
                session.aStar.clearPath();
                session.aStar.initComputePath(
                    entityRef, pos_current, pos_target,
                    new SimpleTargetEvaluator(pos_target),
                    motionController, session.probeMoveData, provider, store
                );
            }
            // Process the path immediately for now
            while (session.aStar.isComputing()) {
                session.aStar.computePath(entityRef, motionController, session.probeMoveData, 1000, store);
            }
            
            var node = session.aStar.getPath();
            var progress = session.aStar.getProgress();
            if (progress.equals(Progress.TERMINATED_OPEN_NODE_LIMIT_EXCEEDED)) {
                throw new PathfindError(PathfindErrorType.OPEN_NODE_LIMIT_EXCEEDED, pos_current, pos_target, session.aStar.getOpenCount(), session.aStar.getVisitedBlocks().size());
            } else if (progress.equals(Progress.TERMINATED_TOTAL_NODE_LIMIT_EXCEEDED)) {
                throw new PathfindError(PathfindErrorType.TOTAL_NODE_LIMIT_EXCEEDED, pos_current, pos_target, session.aStar.getOpenCount(), session.aStar.getVisitedBlocks().size());
            }
            if (node == null && progress.equals(Progress.ACCOMPLISHED)) {
                
                session.aStar.buildBestPath(AStarNode::getEstimateToGoal, (oldV, v) -> v < oldV, Float.MAX_VALUE);
                node = session.aStar.getPath();
            }
            
            restoreDoorsAfterPathing(session.world, openedDoors);
            
            if (node != null) {
                node = preventBacktracking(node, pos_current);
                
                // Prevent the NPC from turning around to backtrack to the exact center of the block it's already inside.
                // We advance the path if the NPC has already passed the node in the direction of the next node.
                
                
                session.pathFollower.clearPath();
                session.pathFollower.setPath(node, pos_current);
            }
            session.lastComputeTime = System.currentTimeMillis();
        }

        // Advance follower and execute path
        if (session.aStar.getPath() != null) {
            session.pathFollower.updateCurrentTarget(pos_current, motionController);
            session.pathFollower.executePath(pos_current, motionController, npc.getRole().getBodySteering());
        }
        handleDoor(session, pos_current, cb);
    }

    // region door ignore pathfinder

    private static List<Vector3i> openDoorsBeforePathing(World world, Vector3d start, Vector3d end) {
        var openedDoors = new ArrayList<Vector3i>();
        if (world == null) return openedDoors;

        int startCX = ((int) Math.floor(start.x)) >> 5;
        int startCZ = ((int) Math.floor(start.z)) >> 5;

        int minCX = startCX - 4;
        int maxCX = startCX + 4;
        int minCZ = startCZ - 4;
        int maxCZ = startCZ + 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                long chunkIndex = ChunkUtil.indexChunk(cx, cz);
                var doors = DoorTracker.getDoorsInChunk(chunkIndex);
                if (doors == null || doors.isEmpty()) continue;

                for (var pos : doors) {
                    var block = MyBlockUtils.getSafeBlockType(world, pos);
                    if (block == null) continue;
                    var state = block.getStateForBlock(block);
                    var isOpen = state != null && state.contains("OpenDoor");
                    if (isOpen) continue;
                    
                    setSilentBlockInteractionState(world, pos, block, "OpenDoorIn");
                    openedDoors.add(pos);
                }
            }
        }
        return openedDoors;
    }

    private static void restoreDoorsAfterPathing(World world, List<Vector3i> doorsToClose) {
        if (world == null || doorsToClose.isEmpty()) return;
        for (var pos : doorsToClose) {
            var block = MyBlockUtils.getSafeBlockType(world, pos);
            if (block != null) {
                setSilentBlockInteractionState(world, pos, block, "CloseDoorIn");
            }
        }
    }

    private static void setSilentBlockInteractionState(World world, Vector3i pos, BlockType blockType, String state) {
        if (blockType.getData() == null) return;
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) return;
        var newState = blockType.getBlockForState(state);
        if (newState == null) return;
        var chunkStore = world.getChunkStore();
        var sectionRef = chunkStore.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        if (sectionRef == null) return;
        var section = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) return;
        
        var currentRotation = section.getRotationIndex(pos.x & 31, pos.y & 31, pos.z & 31);
        
        var wasLoaded = section.loaded;
        section.loaded = false;
        try {
            // 543 = 512 (IGNORE_HEIGHTMAP) + 16 (DISABLE_FILLER_DEL) + 8 (DISABLE_FILLER_CRE) + 4 (DISABLE_PARTICLES) + 2 (PREVENT_STATE) + 1 (NO_SURROUND_UPDATE)
            
            chunk.setBlock(pos.x, pos.y, pos.z, 0, BlockType.EMPTY, 0, 0, 543);
            chunk.setBlock(pos.x, pos.y, pos.z, BlockType.getAssetMap().getIndex(newState.getId()), newState, currentRotation, 0, 519);
            // if (!"OpenDoorIn".equals(state)) {
            //     // 519 = 512 (IGNORE_HEIGHTMAP) + 4 (DISABLE_PARTICLES) + 2 (PREVENT_STATE) + 1 (NO_SURROUND_UPDATE)
            //     chunk.setBlock(pos.x, pos.y, pos.z, com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getIndex(newState.getId()), newState, currentRotation, 0, 519);
            // }
        } finally {
            section.loaded = wasLoaded;
        }
    }

    // region move door opening

    public static void handleDoor(HousedNpcPathSession session, Vector3d pos, CommandBuffer<EntityStore> cb) {
        if (session.world == null) return;
        
        var currentWaypoint = session.pathFollower.getCurrentWaypoint();
        if (currentWaypoint == null) return;

        var upcomingPositions = new java.util.ArrayList<Vector3i>();
        
        var iterNode = currentWaypoint;
        int lookAhead = 4; 
        while (iterNode != null && lookAhead > 0) {
            var wpPos = iterNode.getPosition();
            upcomingPositions.add(new Vector3i((int) Math.floor(wpPos.x), (int) Math.floor(wpPos.y), (int) Math.floor(wpPos.z)));
            iterNode = iterNode.next();
            lookAhead--;
        }
        
        upcomingPositions.add(new Vector3i((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z)));

        for (var pathPos : upcomingPositions) {
            for (int dy = 0; dy <= 1; dy++) {
                var targetPos = new Vector3i(pathPos.x, pathPos.y + dy, pathPos.z);
                if (openDoor(session.world, targetPos, cb) && !session.recentlyOpenedDoors.contains(targetPos)) {
                    session.recentlyOpenedDoors.add(targetPos);
                }
            }
        }

        // Close doors that the NPC has moved past
        var iter = session.recentlyOpenedDoors.iterator();
        while (iter.hasNext()) {
            var doorPos = iter.next();
            
            boolean isUpcoming = false;
            for (var u : upcomingPositions) {
                if (u.x == doorPos.x && u.z == doorPos.z && (u.y == doorPos.y || u.y + 1 == doorPos.y)) {
                    isUpcoming = true;
                    break;
                }
            }
            if (isUpcoming) continue;

            var dxDoor = doorPos.x - pos.x;
            var dzDoor = doorPos.z - pos.z;
            // If distance is > 2.5 blocks, close it and stop tracking
            if (dxDoor * dxDoor + dzDoor * dzDoor > 6.25) {
                closeDoor(session.world, doorPos, cb);
                iter.remove();
            }
        }
    }

    private static boolean openDoor(World world, Vector3i pos, CommandBuffer<EntityStore> cb) {
        return interactDoor(world, pos, true, cb);
    }

    private static boolean closeDoor(World world, Vector3i pos, CommandBuffer<EntityStore> cb) {
        return interactDoor(world, pos, false, cb);
    }


    private static boolean interactDoor(World world, Vector3i pos, boolean open, CommandBuffer<EntityStore> cb) {
        if (world == null) return false;
        var chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null || chunk.getBlockChunk() == null) return false;

        var filler = MyBlockUtils.getSafeBlockFiller(world, pos);
        if (filler != 0) {
            pos = new Vector3i(
                pos.x - FillerBlockUtil.unpackX(filler),
                pos.y - FillerBlockUtil.unpackY(filler),
                pos.z - FillerBlockUtil.unpackZ(filler)
            );
        }

        var block = MyBlockUtils.getSafeBlockType(world, pos);
        if (block == null || block.getId() == null || !block.getId().toLowerCase().contains("door")) return false;

        var state = block.getStateForBlock(block);
        var isOpen = state != null && state.contains("OpenDoor");

        String interactionStateToSend = null;

        if (open && !isOpen) {
            interactionStateToSend = "OpenDoorIn";
        } else if (!open && isOpen) {
            interactionStateToSend = "CloseDoorIn";
        }

        if (interactionStateToSend != null) {
            world.setBlockInteractionState(pos, block, interactionStateToSend);
            BlockType newBlockType = block.getBlockForState(interactionStateToSend);
            if (newBlockType != null && cb != null) {
                var actualPos = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
                SoundUtil.playSoundEvent3d(
                    null, newBlockType.getInteractionSoundEventIndex(), actualPos, cb
                );
            }
            return true;
        }
        return false;
    }

    // region prevent back tracking

    private static AStarNode preventBacktracking(AStarNode head, Vector3d npcPos) {
        if (head == null || head.next() == null) {
            return head;
        }

        AStarNode closestNode1 = head;
        AStarNode closestNode2 = head.next();
        double minDistanceSq = Double.MAX_VALUE;
        double closestSegmentLengthSq = 0;

        AStarNode current = head;
        while (current != null && current.next() != null) {
            AStarNode p1 = current;
            AStarNode p2 = current.next();
            Vector3d v1 = p1.getPosition();
            Vector3d v2 = p2.getPosition();

            double distToSegmentSq = MathSegmentUtils.distanceToSegmentSquared(npcPos, v1, v2);

            if (distToSegmentSq < minDistanceSq) {
                minDistanceSq = distToSegmentSq;
                closestNode1 = p1;
                closestNode2 = p2;
                closestSegmentLengthSq = v1.distanceSquaredTo(v2);
            }
            current = current.next();
        }

        if (closestNode1 != null && closestNode2 != null) {
            double distToFurtherNodeSq = npcPos.distanceSquaredTo(closestNode2.getPosition());
            if (distToFurtherNodeSq > closestSegmentLengthSq) {
                return closestNode1;
            } else {
                return closestNode2;
            }
        }

        return head;
    }


}
