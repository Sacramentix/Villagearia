package villagearia;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import villagearia.component.VillageZone;
import villagearia.component.VillageZoneResource;
import villagearia.graph.VillageZoneGraph;
import java.util.UUID;

public class VillageZoneManager {
   public static void addVillageZone(Store<EntityStore> store, UUID uuid, VillageZone zone) {
      var resourceType = Villagearia.getInstance().getVillageZoneResourceType();
      var resource = store.getResource(resourceType);
      resource.getZones().put(uuid, zone);
      VillageZoneGraph.addNode(uuid);
      for (var otherEntry : resource.getZones().entrySet()) {
         var otherUuid = otherEntry.getKey();
         if (otherUuid.equals(uuid)) continue;
         var otherZone = otherEntry.getValue();
         var distSq = zone.center.distanceSquared(otherZone.center);
         var maxRadiusSq = Math.max(zone.radiusSquared, otherZone.radiusSquared);
         if (distSq <= maxRadiusSq) {
            VillageZoneGraph.addConnection(uuid, otherUuid);
         }
      }
   }
   
   public static void removeVillageZone(Store<EntityStore> store, UUID uuid) {
      var resourceType = Villagearia.getInstance().getVillageZoneResourceType();
      store.getResource(resourceType).getZones().remove(uuid);
      VillageZoneGraph.removeNode(uuid);
   }

   public static void initGraph(Store<EntityStore> store) {
      var resourceType = Villagearia.getInstance().getVillageZoneResourceType();
      var resource = store.getResource(resourceType);
      
      for (var entry : resource.getZones().entrySet()) {
         var uuid = entry.getKey();
         var zone = entry.getValue();
         if (!VillageZoneGraph.graph.containsKey(uuid)) {
            VillageZoneGraph.addNode(uuid);
         }
         for (var otherEntry : resource.getZones().entrySet()) {
            var otherUuid = otherEntry.getKey();
            if (otherUuid.equals(uuid)) continue;
            var otherZone = otherEntry.getValue();
            var distSq = zone.center.distanceSquared(otherZone.center);
            var maxRadiusSq = Math.max(zone.radiusSquared, otherZone.radiusSquared);
            if (distSq <= maxRadiusSq) {
               VillageZoneGraph.addConnection(uuid, otherUuid);
            }
         }
      }
   }
   
   public static VillageZone getVillageZone(Store<EntityStore> store, UUID uuid) {
      var resourceType = Villagearia.getInstance().getVillageZoneResourceType();
      return store.getResource(resourceType).getZones().get(uuid);
   }
}
