package villagearia.system;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import villagearia.Villagearia;
import villagearia.ai.HousedNpcEntity;
import villagearia.component.VillageZone;
import villagearia.component.VillageZoneResource;
import villagearia.utils.JomlUtils;

public class VillageZoneDebugSystem extends TickingSystem<EntityStore> {

    public VillageZoneDebugSystem() {
        super();
    }

    @SuppressWarnings("null")
    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull Store<EntityStore> store
    ) {
        var world = store.getExternalData().getWorld();
        
        var villageZoneResourceType = Villagearia.getInstance().getVillageZoneResourceType();
        var resource = (VillageZoneResource) world.getEntityStore().getStore().getResource(villageZoneResourceType);
        if (resource == null) return;
        
        for (var entry : resource.getZones().entrySet()) {
            var villageZone = entry.getValue();
            var radius = villageZone.getRadius();
            var pos    = JomlUtils.toHytale(villageZone.center);
            
            // Debug sphere for the VillageZone origin
            if (world.getTick() % 100 == 0) DebugUtils.addSphere(world, pos, DebugUtils.COLOR_BLUE, 0.5, 1.1f);
            if (world.getTick() % 100 == 0) DebugUtils.addSphere(world, pos, DebugUtils.COLOR_OLIVE, radius*2, 1.1f);
        }
        
        if (world.getTick() % 5 != 0) return;

        // Debug the active paths of all housed NPCs
        var npcQuery = Query.and(
            HousedNpcEntity.getComponentType(),
            TransformComponent.getComponentType()
        );

        store.forEachChunk(npcQuery, (ArchetypeChunk<EntityStore> npcChunk, CommandBuffer<EntityStore> cb) -> {
            for (int i = 0; i < npcChunk.size(); i++) {
                var housedNpc = npcChunk.getComponent(i, HousedNpcEntity.getComponentType());
                var t = npcChunk.getComponent(i, TransformComponent.getComponentType());
                if (housedNpc == null || t == null) continue;

                var session = housedNpc.getPathSession();
                if (housedNpc.lastGreetedNpc != null) {
                    DebugUtils.addSphere(world, t.getPosition(), DebugUtils.COLOR_RED, 0.5, 1.1f);
                }
                if (session != null && session.aStar != null) {
                    var node = session.aStar.getPath();
                    if (node != null) {
                        var currentPos = t.getPosition();
                        com.hypixel.hytale.math.vector.Vector3d prevPos = currentPos;
                        
                        // Iterate over the path and draw spheres & lines
                        int count = 0;
                        while (node != null && count < 100) { // Limit to 100 to prevent infinite loop
                            var nodePos = node.getPosition();
                            
                            // Draw a sphere at the node
                            DebugUtils.addSphere(world, nodePos, DebugUtils.COLOR_MAGENTA, 0.2, 1.1f);
                            
                            // Draw a line from the previous position to this node
                            double startX = prevPos.getX();
                            double startY = prevPos.getY();
                            double startZ = prevPos.getZ();
                            double endX = nodePos.getX();
                            double endY = nodePos.getY();
                            double endZ = nodePos.getZ();
                            DebugUtils.addLine(world, startX, startY, startZ, endX, endY, endZ, DebugUtils.COLOR_MAGENTA, 0.05, 1.1f, 1);
                            
                            prevPos = nodePos;
                            node = node.next();
                            count++;
                        }
                    }
                }
            }
        });
    }
}
