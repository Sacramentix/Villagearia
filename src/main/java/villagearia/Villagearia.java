package villagearia;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.ai.HousedNpcEntity;
import villagearia.ai.InjectHousedNpcAI;
import villagearia.ai.action.Work.WorkComponent;
import villagearia.ai.action.work.TannerWork.TannerWorkComponent;
import villagearia.ai.action.leisure.WanderNearbyZone.WanderNearbyZoneComponent;
import villagearia.component.PropertyDeed;
import villagearia.component.PropertyDeedRef;
import villagearia.component.VillageZone;
import villagearia.interaction.GivePropertyDeedFilled;
import villagearia.interaction.PlaceBlockWithVillageZone;
import villagearia.interaction.PlacePropertyDeed;
import villagearia.resource.BlockOfInterestStore;
import villagearia.resource.PropertyDeedStore;
import villagearia.resource.VillageZoneStore;
import villagearia.resource.manager.VillageZoneManager;
import villagearia.system.DoorTracker;
import villagearia.system.PlaceBlockListener;
import villagearia.system.VillageZoneBreakBlockSystem;
import villagearia.system.VillageZoneDebugSystem;

public class Villagearia extends JavaPlugin {
    private static Villagearia instance;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    
    private ComponentType<EntityStore, VillageZone> villageZoneComponentType;
    public ComponentType<EntityStore, VillageZone> getVillageZoneComponentType() {
        return villageZoneComponentType;
    };
    
    
    private ResourceType<EntityStore, villagearia.resource.VillageZoneStore> villageZoneStoreResourceType;
    public ResourceType<EntityStore, villagearia.resource.VillageZoneStore> getVillageZoneStoreResourceType() {
        return villageZoneStoreResourceType;
    }

    
    private ResourceType<EntityStore, BlockOfInterestStore> blockOfInterestStoreResourceType;
    public ResourceType<EntityStore, BlockOfInterestStore> getBlockOfInterestStoreResourceType() {
        return blockOfInterestStoreResourceType;
    }

    
    private ResourceType<EntityStore, PropertyDeedStore> propertyDeedStoreResourceType;
    public ResourceType<EntityStore, PropertyDeedStore> getPropertyDeedStoreResourceType() {
        return propertyDeedStoreResourceType;
    }

    
    private ComponentType<EntityStore, PropertyDeed> propertyDeedComponentType;
    public ComponentType<EntityStore, PropertyDeed> getPropertyDeedComponentType() {
        return propertyDeedComponentType;
    };

    
    private ComponentType<EntityStore, PropertyDeedRef> propertyDeedRefComponentType;
    public ComponentType<EntityStore, PropertyDeedRef> getPropertyDeedRefComponentType() {
        return propertyDeedRefComponentType;
    };

    
    private ComponentType<EntityStore, HousedNpcEntity> aiHousedNpcComponentType;
    public ComponentType<EntityStore, HousedNpcEntity> getAiHousedNpcComponentType() {
        return aiHousedNpcComponentType;
    };

    private ComponentType<EntityStore, WorkComponent> workComponentType;
    public ComponentType<EntityStore, WorkComponent> getWorkComponentType() {
        return workComponentType;
    };

    private ComponentType<EntityStore, villagearia.ai.action.Leisure.LeisureComponent> leisureComponentType;
    public ComponentType<EntityStore, villagearia.ai.action.Leisure.LeisureComponent> getLeisureComponentType() {
        return leisureComponentType;
    }

    private ComponentType<EntityStore, TannerWorkComponent> tannerWorkComponentType;
    public ComponentType<EntityStore, TannerWorkComponent> getTannerWorkComponentType() {
        return tannerWorkComponentType;
    }

    private ComponentType<EntityStore, WanderNearbyZoneComponent> wanderNearbyZoneComponentType;
    public ComponentType<EntityStore, WanderNearbyZoneComponent> getWanderNearbyZoneComponentType() {
        return wanderNearbyZoneComponentType;
    };



    public static Villagearia instance() {
        return instance;
    }

    public Villagearia( JavaPluginInit init) {
        super(init);
        instance = this;

        LOGGER.atInfo().log("Villagearia STARTED initializing!");

        this.villageZoneComponentType     = EntityStore.REGISTRY.registerComponent(VillageZone.class,         "VillageZone",         VillageZone.CODEC);
        this.propertyDeedComponentType    = EntityStore.REGISTRY.registerComponent(PropertyDeed.class,       "PropertyDeed",        PropertyDeed.CODEC);
        this.propertyDeedRefComponentType = EntityStore.REGISTRY.registerComponent(PropertyDeedRef.class, "PropertyDeedRef",     PropertyDeedRef.CODEC);
        this.aiHousedNpcComponentType     = EntityStore.REGISTRY.registerComponent(HousedNpcEntity.class,     "AiHousedNpc",     HousedNpcEntity.CODEC);
        this.workComponentType            = EntityStore.REGISTRY.registerComponent(WorkComponent.class,              "Work",       WorkComponent.CODEC);
        this.tannerWorkComponentType      = EntityStore.REGISTRY.registerComponent(villagearia.ai.action.work.TannerWork.TannerWorkComponent.class,  "TannerWork", villagearia.ai.action.work.TannerWork.TannerWorkComponent.CODEC);
        this.leisureComponentType = EntityStore.REGISTRY.registerComponent(villagearia.ai.action.Leisure.LeisureComponent.class, "Leisure", villagearia.ai.action.Leisure.LeisureComponent.CODEC);
        this.wanderNearbyZoneComponentType = EntityStore.REGISTRY.registerComponent(villagearia.ai.action.leisure.WanderNearbyZone.WanderNearbyZoneComponent.class, "WanderNearbyZone", villagearia.ai.action.leisure.WanderNearbyZone.WanderNearbyZoneComponent.CODEC);

        
        this.villageZoneStoreResourceType = 
            this.getEntityStoreRegistry().registerResource(VillageZoneStore.class, "VillageZoneResource", VillageZoneStore.CODEC);
        this.propertyDeedStoreResourceType = 
            this.getEntityStoreRegistry().registerResource(PropertyDeedStore.class, "PropertyDeedResource", PropertyDeedStore.CODEC);
        this.blockOfInterestStoreResourceType = 
            EntityStore.REGISTRY.registerResource(BlockOfInterestStore.class, "VillageBlocksIndexResource", BlockOfInterestStore.CODEC);

        this.getCodecRegistry(Interaction.CODEC)
            .register("PlacePropertyDeed", PlacePropertyDeed.class, PlacePropertyDeed.CODEC)
            .register("GivePropertyDeedFilled", GivePropertyDeedFilled.class, GivePropertyDeedFilled.CODEC)
            .register("PlaceBlockWithVillageZone", PlaceBlockWithVillageZone.class, PlaceBlockWithVillageZone.CODEC);
        LOGGER.atInfo().log("Villagearia FINISHED initializing!");
    }


    // To prevent crash on hot reload
    public boolean hasBeenSetup = false;

    @Override
    protected void setup() {
        if (hasBeenSetup) return;

        this.entitySystem()
            .register(new villagearia.system.PlaceBlockListener())
            .register(new villagearia.system.BreakBlockListener())
            .register(new VillageZoneDebugSystem())
            .register(new VillageZoneBreakBlockSystem())
            .register(new InjectHousedNpcAI());
            // .register(new villagearia.system.ChestTagEventSystem.Tagged())
            // .register(new villagearia.system.ChestTagEventSystem.Untagged());

        this.getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, event -> {
            for (World world : Universe.get().getWorlds().values()) {
                VillageZoneManager.initGraph(world.getEntityStore().getStore());
            }
        });

        this.getEventRegistry().registerGlobal(RemoveWorldEvent.class, event -> {
            VillageZoneManager.clearGraphForWorld(event.getWorld().getEntityStore().getStore());
            DoorTracker.clear();
        });

        this.getEventRegistry().registerGlobal(LoadAssetEvent.class, event -> {
            PlaceBlockListener.createHousedNpcBlockOfInterestIdMap();
            DoorTracker.initDoorBlockIds();
        });

        this.getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, DoorTracker::onChunkLoaded);
        this.getEventRegistry().registerGlobal(ChunkUnloadEvent.class, DoorTracker::onChunkUnloaded);

        hasBeenSetup = true;
    }


    public interface RegisterSystemChain {
        RegisterSystemChain register( ISystem<EntityStore> system);
    }

    public RegisterSystemChain entitySystem() {
        var registry = this.getEntityStoreRegistry();
        return new RegisterSystemChain() {
            public RegisterSystemChain register( ISystem<EntityStore> system) {
                registry.registerSystem(system);
                return this;
            };
        };
    }
}
