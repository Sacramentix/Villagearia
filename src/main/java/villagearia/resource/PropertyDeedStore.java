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
import villagearia.component.PropertyDeed;

public class PropertyDeedStore implements Resource<EntityStore> {
   
   public static final BuilderCodec<PropertyDeedStore> CODEC = BuilderCodec.builder(PropertyDeedStore.class, PropertyDeedStore::new)
      .append(
         new KeyedCodec<>("Deeds", new MapCodec<>(PropertyDeed.CODEC, HashMap::new, false)),
         (resource, deeds) -> {
            resource.deeds.clear();
            for (var entry : deeds.entrySet()) {
               resource.deeds.put(UUID.fromString(entry.getKey()), entry.getValue());
            }
         },
         resource -> {
            var map = new HashMap<String, PropertyDeed>();
            for (var entry : resource.deeds.entrySet()) {
               map.put(entry.getKey().toString(), entry.getValue());
            }
            return map;
         }
      )
      .add()
      .build();

   
   private Map<UUID, PropertyDeed> deeds = new HashMap<>();

   public PropertyDeedStore() {
   }

   
   public Map<UUID, PropertyDeed> getDeeds() {
      return this.deeds;
   }

   
   @Override
   public Resource<EntityStore> clone() {
      var propertyDeedStore = new PropertyDeedStore();
      propertyDeedStore.deeds = new HashMap<>(this.deeds);
      return propertyDeedStore;
   }

   public static ResourceType<EntityStore, PropertyDeedStore> getResourceType() {
      return Villagearia.instance().getPropertyDeedStoreResourceType();
   }

}
