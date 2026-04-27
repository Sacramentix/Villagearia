package villagearia.interaction;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.ai.HousedNpcEntity;
import villagearia.component.PropertyDeed;
import villagearia.component.PropertyDeedRef;
import villagearia.resource.manager.PropertyDeedManager;

public class GivePropertyDeedFilled extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    
    public static final BuilderCodec<GivePropertyDeedFilled> CODEC = BuilderCodec.builder(
            GivePropertyDeedFilled.class, GivePropertyDeedFilled::new
        )
        .build();

    public GivePropertyDeedFilled( String id) {
        super(id);
    }

    protected GivePropertyDeedFilled() {
    }

    
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    
    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.UseEntityInteraction();
    }

    @Override
    protected void configurePacket(com.hypixel.hytale.protocol.Interaction packet) {
        super.configurePacket(packet);
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    
    @Override
    public String toString() {
        return "GivePropertyDeedFilled{} " + super.toString();
    }

    @Override
    protected void firstRun(
         InteractionType interactionType,  InteractionContext ctx,
         CooldownHandler cooldownHandler
    ) {
        var clientState = ctx.getClientState();
        if (clientState == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        var entityId = clientState.entityId;
        var cb = ctx.getCommandBuffer();

        if (cb == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        var world = cb.getStore().getExternalData().getWorld();

        var targetRef = cb.getStore().getExternalData().getRefFromNetworkId(entityId);
        if (targetRef == null || !targetRef.isValid()) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        // // Check if the target is an NPC
        // if (!cb.getStore().getArchetype(targetRef).contains(NPCMarkerComponent.getComponentType())) {
        //     ctx.getState().state = InteractionState.Failed;
        //     return;
        // }

        var itemInHand = ctx.getHeldItem();
        if (itemInHand == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        var deedRef = itemInHand.getFromMetadataOrNull("PropertyDeedRef", PropertyDeedRef.CODEC);
        if (deedRef == null || deedRef.getPosition() == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        var deed = getPropertyDeedFromRef(cb.getStore(), deedRef);
        if (deed == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        
        var bedPos = deed.getLinkedBed();
        if (bedPos == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        cb.addComponent(targetRef, HousedNpcEntity.getComponentType(), new HousedNpcEntity(bedPos, deedRef.getVillageZoneUuid()));
        
        teleportNPCToBed(world, targetRef, bedPos);

        // Consume the property deed filled if needed (assuming single use, we consume it)
        if (itemInHand.getQuantity() > 1) {
            ctx.setHeldItem(itemInHand.withQuantity(itemInHand.getQuantity() - 1));
        } else {
            ctx.setHeldItem(null);
        }

        ctx.getState().state = InteractionState.Finished;
    }

    

    @Nullable
    
    private PropertyDeed getPropertyDeedFromRef(Store<EntityStore> store, PropertyDeedRef deedRef) {
        if (deedRef.getPosition() == null || deedRef.getVillageZoneUuid() == null) return null;
        LOGGER.atInfo().log("START getPropertyDeedFromRef");
        LOGGER.atInfo().log("deedRef " + deedRef.getPosition().toString());
        var propertyDeed = PropertyDeedManager.getPropertyDeed(store, deedRef.getVillageZoneUuid());
        LOGGER.atInfo().log("END getPropertyDeedFromRef");
        return propertyDeed;
    }
    
    private void teleportNPCToBed(World world, Ref<EntityStore> targetRef, Vector3i bedPos) {
        var targetPos = new Vector3d(bedPos.x + 0.5, bedPos.y + 1, bedPos.z + 0.5);
        var targetRot = new Vector3f(0f, 0f, 0f);

        var chunkX     = ChunkUtil.chunkCoordinate(targetPos.getX());
        var chunkZ     = ChunkUtil.chunkCoordinate(targetPos.getZ());
        var chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

        world.getChunkStore().getChunkReferenceAsync(chunkIndex).thenAcceptAsync(chunkRef -> {
            if (!targetRef.isValid()) return;
            world.getEntityStore().getStore().addComponent(
                targetRef, 
                Teleport.getComponentType(), 
                new Teleport(world, targetPos, targetRot)
            );
        }, world);
    }

}

