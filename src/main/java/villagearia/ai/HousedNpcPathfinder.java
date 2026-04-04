package villagearia.ai;

import java.util.ArrayList;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.CommandBuffer;
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
import com.hypixel.hytale.server.npc.navigation.AStarBase.Progress;
import com.hypixel.hytale.server.npc.navigation.AStarNode;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProvider;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import villagearia.ai.HousedNpcEntityInjection.SimpleTargetEvaluator;
import villagearia.utils.MathSegmentUtils;
import villagearia.utils.MyBlockUtils;

public class HousedNpcPathfinder {
    public void pathfindTo(
        NPCEntity npc, HousedNpcPathSession session, AStarNodePoolProvider provider, @Nonnull Store<EntityStore> store,
        Vector3i pos_target_vector3i, Vector3d pos_current, Ref<EntityStore> entityRef, Vector3d pos_target,
        HeadRotation headRotation, CommandBuffer<EntityStore> cb
    ) {
        var npcRole = npc.getRole();
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
            
            
            java.util.List<Vector3i> openedDoors = openDoorsBeforePathing(session.world, pos_current, pos_target);

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
            if (node == null && session.aStar.getProgress().equals(Progress.ACCOMPLISHED)) {
                
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
        this.handleDoor(session, pos_current, cb);
    }

    // region door ignore pathfinder

    private static it.unimi.dsi.fastutil.ints.IntOpenHashSet doorBlockIds = null;

    private static void initDoorBlockIdsIfNeeded() {
        if (doorBlockIds != null) return;
        doorBlockIds = new IntOpenHashSet();
        var map = BlockType.getAssetMap();
        for (var entry : map.getAssetMap().entrySet()) {
            if (entry.getKey().toLowerCase().contains("door")) {
                int index = map.getIndex(entry.getKey());
                if (index >= 0) doorBlockIds.add(index);
            }
        }
    }


    private java.util.List<Vector3i> openDoorsBeforePathing(World world, Vector3d start, Vector3d end) {
        var openedDoors = new ArrayList<Vector3i>();
        if (world == null) return openedDoors;
        
        initDoorBlockIdsIfNeeded();

        final var offset = 15;

        int minX = (int) Math.floor(Math.min(start.x, end.x)) - offset;
        int maxX = (int) Math.floor(Math.max(start.x, end.x)) + offset;
        int minY = (int) Math.floor(Math.min(start.y, end.y)) - offset;
        int maxY = (int) Math.floor(Math.max(start.y, end.y)) + offset;
        int minZ = (int) Math.floor(Math.min(start.z, end.z)) - offset;
        int maxZ = (int) Math.floor(Math.max(start.z, end.z)) + offset;

        // Cap to reasonable limits if path is too long to prevent gigantic lag spikes
        if (maxX - minX > 150 || maxZ - minZ > 150) return openedDoors; 

        int minCX = minX >> 5;
        int maxCX = maxX >> 5;
        int minCZ = minZ >> 5;
        int maxCZ = maxZ >> 5;
        int minCY = Math.max(0, minY >> 5);
        int maxCY = Math.min(9, maxY >> 5);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                for (int cy = minCY; cy <= maxCY; cy++) {
                    var sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(cx << 5, cy << 5, cz << 5);
                    if (sectionRef == null) continue;
                    var section = world.getChunkStore().getStore().getComponent(sectionRef, com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection.getComponentType());
                    if (section == null) continue;

                    boolean hasDoor = false;
                    it.unimi.dsi.fastutil.ints.IntIterator chunkIt = section.getChunkSection().values().iterator();
                    while (chunkIt.hasNext()) {
                        if (doorBlockIds.contains(chunkIt.nextInt())) {
                            hasDoor = true;
                            break;
                        }
                    }
                    if (!hasDoor) continue;

                    int sMinX = Math.max(minX, cx << 5);
                    int sMaxX = Math.min(maxX, (cx << 5) + 31);
                    int sMinY = Math.max(minY, cy << 5);
                    int sMaxY = Math.min(maxY, (cy << 5) + 31);
                    int sMinZ = Math.max(minZ, cz << 5);
                    int sMaxZ = Math.min(maxZ, (cz << 5) + 31);

                    for (int x = sMinX; x <= sMaxX; x++) {
                        for (int y = sMinY; y <= sMaxY; y++) {
                            for (int z = sMinZ; z <= sMaxZ; z++) {
                                int localX = x & 31;
                                int localY = y & 31;
                                int localZ = z & 31;

                                int blockId = section.get(localX, localY, localZ);
                                if (doorBlockIds.contains(blockId)) {
                                    Vector3i pos = new Vector3i(x, y, z);
                                    int filler = section.getFiller(localX, localY, localZ);
                                    if (filler != 0) {
                                        pos = new Vector3i(
                                            x - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackX(filler),
                                            y - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackY(filler),
                                            z - com.hypixel.hytale.server.core.util.FillerBlockUtil.unpackZ(filler)
                                        );
                                    }

                                    var block = MyBlockUtils.getSafeBlockType(world, pos);
                                    if (block != null) {
                                        var state = block.getStateForBlock(block);
                                        boolean isOpen = state != null && state.contains("OpenDoor");
                                        if (!isOpen) {
                                            setSilentBlockInteractionState(world, pos, block, "OpenDoorIn");
                                            if (!openedDoors.contains(pos)) {
                                                openedDoors.add(pos);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return openedDoors;
    }

    private void restoreDoorsAfterPathing(World world, java.util.List<Vector3i> doorsToClose) {
        if (world == null || doorsToClose.isEmpty()) return;
        for (Vector3i pos : doorsToClose) {
            var block = MyBlockUtils.getSafeBlockType(world, pos);
            if (block != null) {
                setSilentBlockInteractionState(world, pos, block, "CloseDoorIn");
            }
        }
    }

    private void setSilentBlockInteractionState(World world, Vector3i pos, BlockType blockType, String state) {
        if (blockType.getData() == null) return;
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) return;
        var newState = blockType.getBlockForState(state);
        if (newState == null) return;
        var currentRotation = chunk.getRotationIndex(pos.x, pos.y, pos.z);
        var section = chunk.getBlockChunk().getSectionAtBlockY(pos.y);
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

    public void handleDoor(HousedNpcPathSession session, Vector3d pos, CommandBuffer<EntityStore> cb) {
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

    private boolean openDoor(World world, Vector3i pos, CommandBuffer<EntityStore> cb) {
        return interactDoor(world, pos, true, cb);
    }

    private boolean closeDoor(World world, Vector3i pos, CommandBuffer<EntityStore> cb) {
        return interactDoor(world, pos, false, cb);
    }


    private boolean interactDoor(World world, Vector3i pos, boolean open, CommandBuffer<EntityStore> cb) {
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

    private AStarNode preventBacktracking(AStarNode head, Vector3d npcPos) {
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
