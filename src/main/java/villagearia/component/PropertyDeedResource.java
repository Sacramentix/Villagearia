package villagearia.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;

public class PropertyDeedResource implements Resource<EntityStore> {
   @Nonnull
   public static final BuilderCodec<PropertyDeedResource> CODEC = BuilderCodec.builder(PropertyDeedResource.class, PropertyDeedResource::new)
      .append(
         new KeyedCodec<>("Deeds", new MapCodec<>(PropertyDeed.CODEC, HashMap::new, false)),
         (resource, deeds) -> {
             resource.deeds.clear();
             for (Map.Entry<String, PropertyDeed> entry : deeds.entrySet()) {
                 resource.deeds.put(UUID.fromString(entry.getKey()), entry.getValue());
             }
         },
         resource -> {
             Map<String, PropertyDeed> map = new HashMap<>();
             for (Map.Entry<UUID, PropertyDeed> entry : resource.deeds.entrySet()) {
                 map.put(entry.getKey().toString(), entry.getValue());
             }
             return map;
         }
      )
      .add()
      .build();

   @Nonnull
   private Map<UUID, PropertyDeed> deeds = new HashMap<>();

   public PropertyDeedResource() {
   }

   @Nonnull
   public Map<UUID, PropertyDeed> getDeeds() {
      return this.deeds;
   }

   @Nonnull
   @Override
   public Resource<EntityStore> clone() {
      PropertyDeedResource resource = new PropertyDeedResource();
      resource.deeds = new HashMap<>(this.deeds);
      return resource;
   }
}
