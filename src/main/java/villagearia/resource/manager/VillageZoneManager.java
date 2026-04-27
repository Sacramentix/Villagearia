package villagearia.resource.manager;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.component.VillageZone;
import villagearia.graph.VillageZoneGraph;
import villagearia.resource.BlockOfInterestStore;
import villagearia.resource.VillageZoneStore;

import java.util.UUID;
import java.util.stream.Stream;

import org.joml.Vector3d;

public class VillageZoneManager {

   public record VillageZoneWithUuid(UUID uuid, VillageZone zone) {}

   public static Stream<VillageZoneWithUuid> getVillageZoneInRange(Store<EntityStore> store, Vector3d pos) {
      return store.getResource(VillageZoneStore.getResourceType())
         .getZones().entrySet().stream()
         .filter((entry) -> {
            var villageZone = entry.getValue();
            var radiusSquared = villageZone.radiusSquared;
            var center = villageZone.center;
            return center.distanceSquared(pos) <= radiusSquared;
         })
         .map(entry -> new VillageZoneWithUuid(entry.getKey(), entry.getValue()));
   }

   public static void addVillageZone(Store<EntityStore> store, UUID uuid, VillageZone zone) {
      var villageZoneStore = store.getResource(VillageZoneStore.getResourceType());
      store.getResource(VillageZoneStore.getResourceType()).getZones().put(uuid, zone);
      VillageZoneGraph.addNode(uuid);
      for (var otherEntry : villageZoneStore.getZones().entrySet()) {
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
      store.getResource(VillageZoneStore.getResourceType())
         .getZones().remove(uuid);
      store.getResource(BlockOfInterestStore.getResourceType())
         .getIndex().remove(uuid);
      VillageZoneGraph.removeNode(uuid);
   }

   public static void initGraph(Store<EntityStore> store) {
      var villageZoneStore = store.getResource(VillageZoneStore.getResourceType());
      
      for (var entry : villageZoneStore.getZones().entrySet()) {
         var uuid = entry.getKey();
         var zone = entry.getValue();
         if (!VillageZoneGraph.graph.containsKey(uuid)) {
            VillageZoneGraph.addNode(uuid);
         }
         for (var otherEntry : villageZoneStore.getZones().entrySet()) {
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

   public static void clearGraphForWorld(Store<EntityStore> store) {
      var villageZoneStore = store.getResource(VillageZoneStore.getResourceType());
      for (UUID uuid : villageZoneStore.getZones().keySet()) {
         VillageZoneGraph.removeNode(uuid);
      }
   }
   
   public static VillageZone getVillageZone(Store<EntityStore> store, UUID uuid) {
      return store.getResource(VillageZoneStore.getResourceType())
         .getZones().get(uuid);
   }
}
