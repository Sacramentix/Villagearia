package villagearia.component;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

public class VillageZoneResource implements Resource<EntityStore> {
   @Nonnull
   public static final BuilderCodec<VillageZoneResource> CODEC = BuilderCodec.builder(VillageZoneResource.class, VillageZoneResource::new)
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

   @Nonnull
   private Map<UUID, VillageZone> zones = new HashMap<>();

   public VillageZoneResource() {
   }

   @Nonnull
   public Map<UUID, VillageZone> getZones() {
      return this.zones;
   }

   @Nonnull
   @Override
   public Resource<EntityStore> clone() {
      VillageZoneResource resource = new VillageZoneResource();
      resource.zones = new HashMap<>(this.zones);
      return resource;
   }
}
