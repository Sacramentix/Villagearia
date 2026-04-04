package villagearia.ai;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.ProbeMoveData;
import com.hypixel.hytale.server.npc.navigation.AStarWithTarget;
import com.hypixel.hytale.server.npc.navigation.PathFollower;

import villagearia.utils.MyBlockUtils;

public class HousedNpcPathSession {

    public World world;
    public AStarWithTarget aStar = new AStarWithTarget() {
        @Override
        protected float measureWalkCost(Vector3d fromPosition, Vector3d toPosition, MotionController motionController) {
            float distCost = super.measureWalkCost(fromPosition, toPosition, motionController);
            if (world == null) return distCost;
            var block = MyBlockUtils.getSafeBlockType(
                world,
                (int)Math.floor(toPosition.getX()),
                (int)Math.floor(toPosition.getY() - 1),
                (int)Math.floor(toPosition.getZ())
            );
            if (block != null && block.getId() != null) {
                var id = block.getId().toLowerCase();
                var y_change = toPosition.getY() - fromPosition.getY();
                var abs_y_change = Math.abs(y_change);
                // Strong penalty for changing elevation without stairs or slabs
                if (abs_y_change > 0.1) {
                    if (!id.contains("stair") && !id.contains("slab")) {
                        distCost *= 15.0f;
                    }
                }
                if (abs_y_change > 0.75) {
                    distCost *= 3.0f;
                }
                if (abs_y_change > 1.1) {
                    distCost *= 15.0f;
                }
                if (y_change < -1.1) {
                    // LOGGER.atInfo().log("BIG DROP FROM : " + fromPosition.toString() + " | TO : " + toPosition.toString());
                    // LOGGER.atInfo().log("BIG DROP FROM : " + fromPosition.toString() + " | TO : " + toPosition.toString());

                }

                if (id.contains("cobble") || id.contains("path") || id.contains("gravel")) {
                    return distCost * 0.5f; // Highly prefer paths
                } else if (id.contains("grass") || id.contains("dirt")) {
                    return distCost * 1.5f; // Avoid grass/dirt slightly
                }
            }
            return distCost;
        }
    };

    public PathFollower pathFollower = new PathFollower();
    public ProbeMoveData probeMoveData = new ProbeMoveData();
    public Vector3i currentTarget = null;
    public long lastComputeTime = 0;
    // recompute path after milliseconds
    public long recomputeAfterMs = 3000;
    public java.util.List<Vector3i> recentlyOpenedDoors = new java.util.ArrayList<>();

    public HousedNpcPathSession() {
        aStar.setTotalNodesLimit(25_000);
        aStar.setOpenNodesLimit(5000);
        aStar.setMaxPathLength(500); // 500 blocks straight depth max limit
        pathFollower.setRelativeSpeed(0.4);
        pathFollower.setRelativeSpeedWaypoint(0.4);
        pathFollower.setWaypointRadius(0.5);
        pathFollower.setRejectionWeight(5);
        pathFollower.setBlendHeading(0.15);
        // pathFollower.setPathSmoothing(3);
    }
}