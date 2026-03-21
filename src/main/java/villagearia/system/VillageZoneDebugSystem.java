package villagearia.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import villagearia.component.VillageZone;

public class VillageZoneDebugSystem extends EntityTickingSystem<EntityStore> {

    @Nonnull
    private final Query<EntityStore> query;

    public VillageZoneDebugSystem() {
        this.query = Query.and(VillageZone.getComponentType());
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        var world   = commandBuffer.getExternalData().getWorld();
        
        if (world.getTick() % 100 != 0) return;
            
        var villageZone = archetypeChunk.getComponent(index, VillageZone.getComponentType());
        var transform   = archetypeChunk.getComponent(index, TransformComponent.getComponentType());

        if (villageZone == null) return;
        if (transform == null) return;

        var radius  = villageZone.getRadius();
        var pos     = transform.getPosition();
        
        // Debug sphere for the VillageZone radius
        DebugUtils.addSphere(world, pos, DebugUtils.COLOR_LIME, radius * 2, 1.1f);

        // Debug sphere for the VillageZone origin
        DebugUtils.addSphere(world, pos, DebugUtils.COLOR_BLUE, 0.5, 1.1f);
        
    }
}
