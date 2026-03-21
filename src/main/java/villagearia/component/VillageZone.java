package villagearia.component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;

/**
 * Component representing the zone of a village.
 */
public class VillageZone implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<VillageZone> CODEC = BuilderCodec.builder(
            VillageZone.class, VillageZone::new
        )
        .append(new KeyedCodec<>("Radius",  Codec.INTEGER), (zone, reach) -> zone.radius = reach, zone -> zone.radius).add()
        .append(new KeyedCodec<>("Village_ids", new ArrayCodec<>(Codec.UUID_BINARY, UUID[]::new)), (zone, o) -> {
            zone.village_ids = new HashSet<>();
            Collections.addAll(zone.village_ids, o);
        }, zone -> zone.village_ids.toArray(UUID[]::new)).add()
        .append(new KeyedCodec<>("To_ids", new ArrayCodec<>(Codec.UUID_BINARY, UUID[]::new)), (zone, o) -> {
            zone.to_ids = new HashSet<>();
            Collections.addAll(zone.to_ids, o);
        }, zone -> zone.to_ids.toArray(UUID[]::new)).add()
        .build();

    // The radius of the village zone.
    private int radius;

    private Set<UUID> village_ids;

    // the entity uuid this zone connect to

    private Set<UUID> to_ids;

    /**
     * Default constructor for serialization.
     */
    public VillageZone() {
        this.village_ids = new HashSet<>();
        this.to_ids = new HashSet<>();
    }

    public VillageZone clone() {
        return new VillageZone(radius);
    }

    /**
     * Constructor to initialize the village zone.
     * 
     * @param radius  The radius of the village zone.
     */
    public VillageZone(int radius) {
        this.radius = radius;
        this.village_ids = new HashSet<>();
        this.to_ids = new HashSet<>();
    }

    // Getters and setters

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public boolean addConnection(UUID uuid) {
        return to_ids.add(uuid);
    }

    public boolean removeConnection(UUID uuid) {
        return to_ids.remove(uuid);
    }
    
    public static ComponentType<EntityStore, VillageZone> getComponentType() {
        return Villagearia.get().getVillageZoneComponentType();
    }

}