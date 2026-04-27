package villagearia.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;
import villagearia.component.VillageZone;

public class VillageZoneStore implements Resource<EntityStore> {
   
   public static final BuilderCodec<VillageZoneStore> CODEC = BuilderCodec.builder(VillageZoneStore.class, VillageZoneStore::new)
      .append(
         new KeyedCodec<>("Zones", new MapCodec<>(VillageZone.CODEC, HashMap::new, false)),
         (resource, zones) -> {
             resource.zones.clear();
             for (Map.Entry<String, VillageZone> entry : zones.entrySet()) {
                 resource.zones.put(UUID.fromString(entry.getKey()), entry.getValue());
             }
         },
         resource -> {
             Map<String, VillageZone> map = new HashMap<>();
             for (Map.Entry<UUID, VillageZone> entry : resource.zones.entrySet()) {
                 map.put(entry.getKey().toString(), entry.getValue());
             }
             return map;
         }
      )
      .add()
      .build();

   
   private Map<UUID, VillageZone> zones = new HashMap<>();

   public VillageZoneStore() {
   }

   
   public Map<UUID, VillageZone> getZones() {
      return this.zones;
   }

   
   @Override
   public Resource<EntityStore> clone() {
      var villageZoneStore = new VillageZoneStore();
      villageZoneStore.zones = new HashMap<>(this.zones);
      return villageZoneStore;
   }

   public static ResourceType<EntityStore, VillageZoneStore> getResourceType() {
      return Villagearia.instance().getVillageZoneStoreResourceType();
   }


}
