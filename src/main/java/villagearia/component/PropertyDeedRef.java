package villagearia.component;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.Villagearia;

/**
 * Component representing a reference to a placed PropertyDeed.
 */
public class PropertyDeedRef implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PropertyDeedRef> CODEC = BuilderCodec.builder(
            PropertyDeedRef.class, PropertyDeedRef::new
        )
        .append(new KeyedCodec<>("Position", Vector3i.CODEC), (ref, position) -> ref.position = position, ref -> ref.position).add()
        .append(new KeyedCodec<>("VillageZoneUuid", Codec.UUID_BINARY), (ref, uuid) -> ref.villageZoneUuid = uuid, ref -> ref.villageZoneUuid).add()
        .build();

    @Nullable
    private Vector3i position;
    private UUID villageZoneUuid;

    /**
     * Default constructor for serialization.
     */
    public PropertyDeedRef() {
    }

    public PropertyDeedRef(@Nullable Vector3i position, UUID uuid) {
        this.position = position;
        this.villageZoneUuid = uuid;
    }

    @Nullable
    public Vector3i getPosition() {
        return position;
    }

    public void setPosition(@Nullable Vector3i position) {
        this.position = position;
    }

    public UUID getVillageZoneUuid() {
        return villageZoneUuid;
    }

    public PropertyDeedRef clone() {
        return new PropertyDeedRef(this.position, villageZoneUuid);
    }

    public static ComponentType<EntityStore, PropertyDeedRef> getComponentType() {
        return Villagearia.instance().getPropertyDeedRefComponentType();
    }
}
