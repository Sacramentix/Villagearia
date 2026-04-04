package villagearia;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;

import villagearia.component.VillageZone;
import villagearia.component.VillageZoneResource;
import villagearia.interaction.GivePropertyDeedFilled;
import villagearia.interaction.PlaceBlockWithVillageZone;
import villagearia.interaction.PlacePropertyDeed;
import villagearia.ai.HousedNpcEntity;
import villagearia.ai.InjectHousedNpcAI;
import villagearia.component.PropertyDeed;
import villagearia.component.PropertyDeedRef;
import villagearia.component.PropertyDeedResource;
import villagearia.system.VillageZoneDebugSystem;
import villagearia.system.VillageZoneBreakBlockSystem;

public class Villagearia extends JavaPlugin {
    private static Villagearia instance;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static Villagearia getInstance() {
        return instance;
    }

    @Nonnull
    private ComponentType<EntityStore, VillageZone> villageZoneComponentType;
    public ComponentType<EntityStore, VillageZone> getVillageZoneComponentType() {
        return villageZoneComponentType;
    };
    
    @Nonnull
    private ResourceType<EntityStore, villagearia.component.VillageZoneResource> villageZoneResourceType;
    public ResourceType<EntityStore, villagearia.component.VillageZoneResource> getVillageZoneResourceType() {
        return villageZoneResourceType;
    }

    @Nonnull
    private ResourceType<EntityStore, PropertyDeedResource> propertyDeedResourceType;
    public ResourceType<EntityStore, PropertyDeedResource> getPropertyDeedResourceType() {
        return propertyDeedResourceType;
    }

    @Nonnull
    private ComponentType<EntityStore, PropertyDeed> propertyDeedComponentType;
    public ComponentType<EntityStore, PropertyDeed> getPropertyDeedComponentType() {
        return propertyDeedComponentType;
    };

    @Nonnull
    private ComponentType<EntityStore, villagearia.component.PropertyDeedRef> propertyDeedRefComponentType;
    public ComponentType<EntityStore, villagearia.component.PropertyDeedRef> getPropertyDeedRefComponentType() {
        return propertyDeedRefComponentType;
    };

    // private ComponentType<EntityStore, villagearia.component.villageZone.VillageZoneTaggedChests> villageZoneTaggedChestsComponentType;
    // public ComponentType<EntityStore, villagearia.component.villageZone.VillageZoneTaggedChests> getVillageZoneTaggedChestsComponentType() {
    //     return villageZoneTaggedChestsComponentType;
    // };

    private ComponentType<EntityStore, villagearia.ai.HousedNpcEntity> aiHousedNpcComponentType;
    public ComponentType<EntityStore, villagearia.ai.HousedNpcEntity> getAiHousedNpcComponentType() {
        return aiHousedNpcComponentType;
    };



    public static Villagearia instance() {
        return instance;
    }

    public Villagearia(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;

        LOGGER.atInfo().log("Villagearia STARTED initializing!");

        this.villageZoneComponentType       = EntityStore.REGISTRY.registerComponent(VillageZone.class,     "VillageZone",      VillageZone.CODEC);
        this.propertyDeedComponentType      = EntityStore.REGISTRY.registerComponent(PropertyDeed.class,    "PropertyDeed",    PropertyDeed.CODEC);
        this.propertyDeedRefComponentType   = EntityStore.REGISTRY.registerComponent(PropertyDeedRef.class, "PropertyDeedRef", PropertyDeedRef.CODEC);
        this.aiHousedNpcComponentType       = EntityStore.REGISTRY.registerComponent(HousedNpcEntity.class,     "AiHousedNpc",     HousedNpcEntity.CODEC);
        
        this.villageZoneResourceType = 
            this.getEntityStoreRegistry().registerResource(VillageZoneResource.class, "VillageZoneResource", VillageZoneResource.CODEC);
        this.propertyDeedResourceType = 
            this.getEntityStoreRegistry().registerResource(PropertyDeedResource.class, "PropertyDeedResource", PropertyDeedResource.CODEC);

        this.getCodecRegistry(Interaction.CODEC)
            .register("PlacePropertyDeed", PlacePropertyDeed.class, PlacePropertyDeed.CODEC)
            .register("GivePropertyDeedFilled", GivePropertyDeedFilled.class, GivePropertyDeedFilled.CODEC)
            .register("PlaceBlockWithVillageZone", PlaceBlockWithVillageZone.class, PlaceBlockWithVillageZone.CODEC)
            .register("TagChestForHousedNpc", villagearia.interaction.TagChestForHousedNpc.class, villagearia.interaction.TagChestForHousedNpc.CODEC);
            
        LOGGER.atInfo().log("Villagearia FINISHED initializing!");
    }


    // To prevent crash on hot reload
    public boolean hasBeenSetup = false;

    @Override
    protected void setup() {
        if (hasBeenSetup) return;

        this.entitySystem()
            .register(new VillageZoneDebugSystem())
            .register(new VillageZoneBreakBlockSystem())
            .register(new InjectHousedNpcAI());
            // .register(new villagearia.system.ChestTagEventSystem.Tagged())
            // .register(new villagearia.system.ChestTagEventSystem.Untagged());

        this.getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            for (World world : Universe.get().getWorlds().values()) {
                villagearia.VillageZoneManager.initGraph(world.getEntityStore().getStore());
            }
        });

        hasBeenSetup = true;
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
