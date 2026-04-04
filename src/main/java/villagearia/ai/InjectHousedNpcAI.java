package villagearia.ai;

import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;

import villagearia.component.VillageZone;

public class InjectHousedNpcAI extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public HousedNpcEntityInjection injection = new HousedNpcEntityInjection();
    
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

    public InjectHousedNpcAI() {
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


        var aiHousedType     = HousedNpcEntity.getComponentType();
        var npcType          = NPCEntity.getComponentType();
        var transformType    = TransformComponent.getComponentType();
        var query = Query.and(aiHousedType, npcType, transformType);

        store.forEachChunk(query, consumer);
    }

    public void tickOneHousedNpc(
        float dt, int systemIndex, @Nonnull Store<EntityStore> store,
        ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb,
        int i
    ) {
        var aiHousedType     = HousedNpcEntity.getComponentType();
        var npcType          = NPCEntity.getComponentType();

        var aiComponent = chunk.getComponent(i, aiHousedType);
        if (aiComponent == null) return;
        
        var npc          = chunk.getComponent(i, npcType);
        if (npc == null) return;

        var npcRole = npc.getRole();
        if (npcRole == null) return;
        var vanillaState = npcRole.getStateSupport().getStateName();
        
        // Let vanilla Hytale handle Fleeing, Alerted, or special Interactions!
        if (vanillaState.startsWith("Alerted") || vanillaState.startsWith("Flee")) {
            // Do not hijack! Let the NPC use vanilla logic to escape danger.
            return; 
        }
        
        // Otherwise, if they are just "Idle", we can safely hijack them to farm or sleep.
        if (vanillaState.startsWith("Idle")) {

            
            // Execute your custom Java Task (Pathfinding to bed, sitting on bench, etc.)
            injection.overrideIdleStateBehavior(new InjectHousedNpcAiContext(dt, systemIndex, store, chunk, cb, i, aiComponent, npc));
        }
    }

    public static record InjectHousedNpcAiContext (
        float dt, int systemIndex, @Nonnull Store<EntityStore> store,
        ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb,
        int i, HousedNpcEntity housedNpc, NPCEntity npc
    ) {}

}
