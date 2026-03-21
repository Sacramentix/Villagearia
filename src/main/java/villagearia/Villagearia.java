package villagearia;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.component.VillageZone;
import villagearia.interaction.GivePropertyDeedFilled;
import villagearia.interaction.PlaceBlockWithVillageZone;
import villagearia.interaction.PlacePropertyDeed;
import villagearia.ai.AiHousedNpc;
import villagearia.ai.AiHousedNpcSystem;
import villagearia.component.PropertyDeed;
import villagearia.component.PropertyDeedRef;
import villagearia.system.VillageZoneDebugSystem;
import villagearia.system.VillageZoneSystem;
import villagearia.system.VillageZoneBreakBlockSystem;

public class Villagearia extends JavaPlugin {
    private static Villagearia instance;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ComponentType<EntityStore, VillageZone> villageZoneComponentType;
    public ComponentType<EntityStore, VillageZone> getVillageZoneComponentType() {
        return villageZoneComponentType;
    };

    private ComponentType<EntityStore, PropertyDeed> propertyDeedComponentType;
    public ComponentType<EntityStore, PropertyDeed> getPropertyDeedComponentType() {
        return propertyDeedComponentType;
    };

    private ComponentType<EntityStore, villagearia.component.PropertyDeedRef> propertyDeedRefComponentType;
    public ComponentType<EntityStore, villagearia.component.PropertyDeedRef> getPropertyDeedRefComponentType() {
        return propertyDeedRefComponentType;
    };

    private ComponentType<EntityStore, villagearia.ai.AiHousedNpc> aiHousedNpcComponentType;
    public ComponentType<EntityStore, villagearia.ai.AiHousedNpc> getAiHousedNpcComponentType() {
        return aiHousedNpcComponentType;
    };



    public static Villagearia get() {
        return instance;
    }

    public Villagearia(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;

        LOGGER.atInfo().log("Villagearia STARTED initializing!");

        this.villageZoneComponentType       = EntityStore.REGISTRY.registerComponent(VillageZone.class,     "VillageZone",      VillageZone.CODEC);
        this.propertyDeedComponentType      = EntityStore.REGISTRY.registerComponent(PropertyDeed.class,    "PropertyDeed",    PropertyDeed.CODEC);
        this.propertyDeedRefComponentType   = EntityStore.REGISTRY.registerComponent(PropertyDeedRef.class, "PropertyDeedRef", PropertyDeedRef.CODEC);
        this.aiHousedNpcComponentType       = EntityStore.REGISTRY.registerComponent(AiHousedNpc.class,     "AiHousedNpc",     AiHousedNpc.CODEC);

        this.getCodecRegistry(Interaction.CODEC)
            .register("PlacePropertyDeed", PlacePropertyDeed.class, PlacePropertyDeed.CODEC)
            .register("GivePropertyDeedFilled", GivePropertyDeedFilled.class, GivePropertyDeedFilled.CODEC)
            .register("PlaceBlockWithVillageZone", PlaceBlockWithVillageZone.class, PlaceBlockWithVillageZone.CODEC);
            
        LOGGER.atInfo().log("Villagearia FINISHED initializing!");
    }

    @Override
    protected void setup() {
        this.entitySystem()
            .register(new VillageZoneDebugSystem())
            .register(new VillageZoneSystem())
            .register(new VillageZoneBreakBlockSystem())
            .register(new AiHousedNpcSystem());
    }


    public interface RegisterSystemChain {
        RegisterSystemChain register(@Nonnull ISystem<EntityStore> system);
    }

    public RegisterSystemChain entitySystem() {
        var registry = this.getEntityStoreRegistry();
        return new RegisterSystemChain() {
            public RegisterSystemChain register(@Nonnull ISystem<EntityStore> system) {
                registry.registerSystem(system);
                return this;
            };
        };
    }
}
