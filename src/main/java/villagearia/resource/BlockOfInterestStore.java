package villagearia.resource;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.EnumMapCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import villagearia.Villagearia;

public class BlockOfInterestStore implements Resource<EntityStore> {

    
    public static final BuilderCodec<BlockOfInterestStore> CODEC = BuilderCodec.builder(BlockOfInterestStore.class, BlockOfInterestStore::new)
        .append(
            new KeyedCodec<>("Index", new MapCodec<>(
                new EnumMapCodec<>(
                    BlockOfInterest.class,
                    new SetCodec<>(Vector3i.CODEC, ObjectOpenHashSet::new, false),
                    () -> new EnumMap<>(BlockOfInterest.class),
                    false
                ),
                Object2ObjectOpenHashMap::new,
                false
            )),
            (resource, map) -> {
                resource.index.clear();
                for (Map.Entry<String, Map<BlockOfInterest, java.util.Set<Vector3i>>> entry : map.entrySet()) {
                    EnumMap<BlockOfInterest, ObjectOpenHashSet<Vector3i>> innerMap = new EnumMap<>(BlockOfInterest.class);
                    for (Map.Entry<BlockOfInterest, java.util.Set<Vector3i>> innerEntry : entry.getValue().entrySet()) {
                        innerMap.put(innerEntry.getKey(), new ObjectOpenHashSet<>(innerEntry.getValue()));
                    }
                    resource.index.put(UUID.fromString(entry.getKey()), innerMap);
                }
            },
            resource -> {
                Object2ObjectOpenHashMap<String, Map<BlockOfInterest, java.util.Set<Vector3i>>> map = new Object2ObjectOpenHashMap<>();
                for (Map.Entry<UUID, EnumMap<BlockOfInterest, ObjectOpenHashSet<Vector3i>>> entry : resource.index.entrySet()) {
                    Map<BlockOfInterest, java.util.Set<Vector3i>> innerMap = new EnumMap<>(BlockOfInterest.class);
                    for (Map.Entry<BlockOfInterest, ObjectOpenHashSet<Vector3i>> innerEntry : entry.getValue().entrySet()) {
                        innerMap.put(innerEntry.getKey(), innerEntry.getValue());
                    }
                    map.put(entry.getKey().toString(), innerMap);
                }
                return map;
            }
        )
        .add()
        .build();

    private final Object2ObjectOpenHashMap<UUID, EnumMap<BlockOfInterest, ObjectOpenHashSet<Vector3i>>> index = new Object2ObjectOpenHashMap<>();

    public BlockOfInterestStore() {
    }

    public Object2ObjectOpenHashMap<UUID, EnumMap<BlockOfInterest, ObjectOpenHashSet<Vector3i>>> getIndex() {
        return index;
    }

    @Override
    public Resource<EntityStore> clone() {
        var cloned = new BlockOfInterestStore();
        for (var entry : this.index.entrySet()) {
            EnumMap<BlockOfInterest, ObjectOpenHashSet<Vector3i>> innerMap = new EnumMap<>(BlockOfInterest.class);
            for (var innerEntry : entry.getValue().entrySet()) {
                innerMap.put(innerEntry.getKey(), new ObjectOpenHashSet<>(innerEntry.getValue()));
            }
            cloned.index.put(entry.getKey(), innerMap);
        }
        return cloned;
    }

    
    public static ResourceType<EntityStore, BlockOfInterestStore> getResourceType() {
        return Villagearia.instance().getBlockOfInterestStoreResourceType();
    }
}
