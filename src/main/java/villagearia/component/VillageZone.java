package villagearia.component;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;
import villagearia.utils.JomlCodecs;

/**
 * Component representing the zone of a village.
 */
public class VillageZone implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<VillageZone> CODEC = BuilderCodec.builder(
            VillageZone.class, VillageZone::new
        )
        .append(new KeyedCodec<>("Center", JomlCodecs.VEC3D_CODEC),
            (zone, vector) -> zone.center = vector, 
            zone -> zone.center).add()
        .append(new KeyedCodec<>("RadiusSquared",  Codec.INTEGER), 
            (zone, r2) -> zone.radiusSquared = r2, 
            zone -> zone.radiusSquared).add()
        .build();
    
    public Vector3d center;

    // Squared radius for distance performance
    public int radiusSquared;

    // Default constructor for serialization.
    public VillageZone() {
    }

    public VillageZone clone() {
        return new VillageZone(center, radiusSquared);
    }

    public VillageZone(Vector3d center, int radiusSquared) {
        this.center = center;
        this.radiusSquared = radiusSquared;
    }

    // use radiusSquared when possible for performance
    public double getRadius() {
        return Math.sqrt(radiusSquared);
    }


    public static ComponentType<EntityStore, VillageZone> getComponentType() {
        return Villagearia.instance().getVillageZoneComponentType();
    }

}