package villagearia.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.ProbeMoveData;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import com.hypixel.hytale.server.npc.navigation.AStarEvaluator;
import com.hypixel.hytale.server.npc.navigation.AStarNode;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProvider;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProviderSimple;
import com.hypixel.hytale.server.npc.navigation.AStarWithTarget;
import com.hypixel.hytale.server.npc.navigation.PathFollower;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;

public class AiHousedNpcSystem extends TickingSystem<EntityStore> {
    

    private final Random random = new Random();

    public static class PathSession {
        public World world;
        public AStarWithTarget aStar = new AStarWithTarget() {
            @Override
            protected float measureWalkCost(Vector3d fromPosition, Vector3d toPosition, @Nonnull MotionController motionController) {
                float distCost = super.measureWalkCost(fromPosition, toPosition, motionController);
                if (world != null) {
                    BlockType block = world.getBlockType((int)Math.floor(toPosition.getX()), (int)Math.floor(toPosition.getY() - 1), (int)Math.floor(toPosition.getZ()));
                    if (block != null && block.getId() != null) {
                        String id = block.getId().toLowerCase();
                        if (id.contains("cobble") || id.contains("path") || id.contains("gravel")) {
                            return distCost * 0.5f; // Highly prefer paths
                        } else if (id.contains("grass") || id.contains("dirt")) {
                            return distCost * 1.5f; // Avoid grass/dirt slightly
                        }
                    }
                }
                return distCost;
            }
        };
        public PathFollower pathFollower = new PathFollower();
        public ProbeMoveData probeMoveData = new ProbeMoveData();
        public Vector3i currentTarget = null;
        public long lastComputeTime = 0;

        public PathSession() {

            pathFollower.setRelativeSpeed(0.4);
            pathFollower.setRelativeSpeedWaypoint(0.4);
            pathFollower.setWaypointRadius(0.5);
            pathFollower.setPathSmoothing(3);
        }
    }

    private final Map<Integer, PathSession> sessions = new HashMap<>();

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

    public Set<Dependency<EntityStore>> dependencies = Set.of(
        /*FIRST*/  new SystemDependency<EntityStore,RoleSystems.BehaviourTickSystem>(Order.AFTER, RoleSystems.BehaviourTickSystem.class),
        /*SECOND*/ // THIS
        /*THIRD*/  new SystemDependency<EntityStore,SteeringSystem>(Order.BEFORE, SteeringSystem.class)
    );

    @Nonnull
    @SuppressWarnings("null")
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    public AiHousedNpcSystem() {
        super();
    }

    @SuppressWarnings({"null"})
    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> consumer = (chunk, cb) -> {
            for (int i = 0; i < chunk.size(); i++) {
                tickOneHousedNpc(dt, systemIndex, store, chunk, cb, i);
            }
        };


        var aiHousedType     = AiHousedNpc.getComponentType();
        var query = Query.and(aiHousedType);

        store.forEachChunk(query, consumer);
    }

    public void tickOneHousedNpc(
        float dt, int systemIndex, @Nonnull Store<EntityStore> store,
        ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb,
        int i
    ) {

        var aiHousedType     = AiHousedNpc.getComponentType();
        var npcType          = NPCEntity.getComponentType();
        var headRotationType = HeadRotation.getComponentType();
        var transformType    = TransformComponent.getComponentType();
        
        
        
        var worldTime = store.getResource(WorldTimeResource.getResourceType());
        var isNight = worldTime != null && (worldTime.getDayProgress() > 0.70 || worldTime.getDayProgress() < 0.25);

        var aiComponent = chunk.getComponent(i, aiHousedType);
        if (aiComponent == null) return;
        
        var entityRef = chunk.getReferenceTo(i);
        aiComponent.decreaseWaitTicks();
        
        var npc          = store.getComponent(entityRef, npcType);
        if (npc == null) return;

        var transform    = store.getComponent(entityRef, transformType);
        if (transform == null) return;

        var headRotation = store.getComponent(entityRef, headRotationType);
        if (headRotation == null) return;
        
        aiComponent.setTargetPos(new Vector3i(-666, 123, 403));
        
        if (isNight) {
            if (!"NIGHT_SLEEP".equals(aiComponent.getState())) {
                aiComponent.setState("NIGHT_SLEEP");
                aiComponent.setTargetPos(aiComponent.getHomeBed());
            }
        } else {
            if ("NIGHT_SLEEP".equals(aiComponent.getState()) || aiComponent.getWaitTicks() <= 0) {
                aiComponent.setState("DAY_WANDER");
                aiComponent.setWaitTicks(random.nextInt(30 * 20) + 30 * 20); // 30-60 secs random wait
                
                // DEBUG: Force pathfinding to specific coordinates during the day
                aiComponent.setTargetPos(new Vector3i(-666, 123, 403));
            }
        }

        // Every single tick override Wander if we have a target location
        var tPos = aiComponent.getTargetPos();
        
        // We wipe whatever motion the Vanilla behaviour just queued up

        if (tPos == null) return;
        var pos = transform.getPosition();
        var actualTarget = new Vector3d(tPos.x + 0.5, tPos.y, tPos.z + 0.5);
        
        var session = sessions.computeIfAbsent(entityRef.getIndex(), id -> new PathSession());
        session.world = store.getExternalData().getWorld();
        var provider = store.getResource(AStarNodePoolProviderSimple.getResourceType());

        double dx = actualTarget.x - pos.x;
        double dz = actualTarget.z - pos.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        // If far away enough, inject steering via PathFollower
        if (distance > 1.0) {

        } else {
            // Reached target
            session.aStar.clearPath();
            session.pathFollower.clearPath();

        }
        // Clear the active JSON instructions so the NPC doesn't get stuck in a "thinking" loop
        if (npc.getRole() != null) {
            npc.getRole().clearOnceIfNeeded();
        }
    }

    public void pathfindTo(
        NPCEntity npc, PathSession session, AStarNodePoolProvider provider, @Nonnull Store<EntityStore> store,
        Vector3i pos_target_vector3i, Vector3d pos_current, Ref<EntityStore> entityRef, Vector3d pos_target
    ) {
        var npcRole = npc.getRole();
        if (npcRole == null) return;
        var motionController = npc.getRole().getActiveMotionController();
        if (motionController == null) return;
        if (provider == null) return;
        // If target changed or wait duration passed, recompute path
        if (
            session.currentTarget == null ||
            !session.currentTarget.equals(pos_target_vector3i) ||
            (System.currentTimeMillis() - session.lastComputeTime > 3000)
        ) {
            session.currentTarget = pos_target_vector3i;
            // session.aStar -> AStarWithTarget
            session.aStar.clearPath();
            session.aStar.initComputePath(
                entityRef, pos_current, pos_target,
                new SimpleTargetEvaluator(pos_target),
                motionController, session.probeMoveData, provider, store
            );
            
            // Process the path immediately for now
            while(session.aStar.isComputing()) {
                session.aStar.computePath(entityRef, motionController, session.probeMoveData, 5000, store);
            }
            
            var node = session.aStar.getPath();
            if (node == null) {
                session.aStar.buildBestPath(AStarNode::getEstimateToGoal, (oldV, v) -> v < oldV, Float.MAX_VALUE);
                node = session.aStar.getPath();
            }
            
            if (node != null) {
                session.pathFollower.clearPath();
                session.pathFollower.setPath(node, pos_current);
            }
            session.lastComputeTime = System.currentTimeMillis();
        }

        // Advance follower and execute path
        if (session.aStar.getPath() != null) {
            session.pathFollower.updateCurrentTarget(pos_current, motionController);
            session.pathFollower.executePath(pos_current, motionController, npc.getRole().getBodySteering());

            // // Calculate heading
            // var tx = npc.getRole().getBodySteering().getTranslation().x;
            // var tz = npc.getRole().getBodySteering().getTranslation().z;
            // if (tx != 0 || tz != 0) {
            //     var targetYaw = PhysicsMath.headingFromDirection(tx, tz);
            //     npc.getRole().getBodySteering().setYaw(targetYaw);
            // }
        }
    }

    public void wipeBehaviorMotion(NPCEntity npc) {
        if (npc == null) return;
        npc.getRole().getBodySteering().clear();
        npc.getRole().getHeadSteering().clear();
    }
}
