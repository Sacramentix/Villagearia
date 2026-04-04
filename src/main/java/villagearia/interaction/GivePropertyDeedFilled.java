package villagearia.interaction;

import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.NPCMarkerComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.component.PropertyDeed;
import villagearia.component.PropertyDeedRef;
import villagearia.ai.HousedNpcEntity;

public class GivePropertyDeedFilled extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    public static final BuilderCodec<GivePropertyDeedFilled> CODEC = BuilderCodec.builder(
            GivePropertyDeedFilled.class, GivePropertyDeedFilled::new
        )
        .build();

    public GivePropertyDeedFilled(@Nonnull String id) {
        super(id);
    }

    protected GivePropertyDeedFilled() {
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Nonnull
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

    @Nonnull
    @Override
    public String toString() {
        return "GivePropertyDeedFilled{} " + super.toString();
    }

    @SuppressWarnings({"removal", "null"})
    @Override
    protected void firstRun(
        @Nonnull InteractionType interactionType, @Nonnull InteractionContext ctx,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        var clientState = ctx.getClientState();
        if (clientState == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        var entityId = clientState.entityId;
        var commandBuffer = ctx.getCommandBuffer();

        if (commandBuffer == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        var world = commandBuffer.getStore().getExternalData().getWorld();

        var targetRef = commandBuffer.getStore().getExternalData().getRefFromNetworkId(entityId);
        if (targetRef == null || !targetRef.isValid()) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        // Check if the target is an NPC
        var npcMarkerType = NPCMarkerComponent.getComponentType();
        if (npcMarkerType == null || !commandBuffer.getStore().getArchetype(targetRef).contains(npcMarkerType)) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

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

        var deed = getPropertyDeedFromRef(world, deedRef);
        if (deed == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }
        
        var bedPos = deed.getLinkedBed();
        if (bedPos == null) {
            ctx.getState().state = InteractionState.Failed;
            return;
        }

        var aiType = villagearia.Villagearia.instance().getAiHousedNpcComponentType();
        var safeAiType = aiType;
        commandBuffer.addComponent(targetRef, safeAiType, new HousedNpcEntity(bedPos, deedRef.getVillageZoneUuid()));
        
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
    @SuppressWarnings("null")
    private PropertyDeed getPropertyDeedFromRef(World world, PropertyDeedRef deedRef) {
        if (deedRef.getPosition() == null || deedRef.getVillageZoneUuid() == null) return null;
        LOGGER.atInfo().log("START getPropertyDeedFromRef");
        LOGGER.atInfo().log("deedRef " + deedRef.getPosition().toString());
        var propertyDeed = villagearia.PropertyDeedManager.getPropertyDeed(world, deedRef.getVillageZoneUuid());
        LOGGER.atInfo().log("END getPropertyDeedFromRef");
        return propertyDeed;
    }    private void teleportNPCToBed(World world, Ref<EntityStore> targetRef, Vector3i bedPos) {
        var targetPos = new Vector3d(bedPos.x + 0.5, bedPos.y + 1, bedPos.z + 0.5);
        var targetRot = new Vector3f(0f, 0f, 0f);

        var chunkX     = ChunkUtil.chunkCoordinate(targetPos.getX());
        var chunkZ     = ChunkUtil.chunkCoordinate(targetPos.getZ());
        var chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

        var teleportType = Teleport.getComponentType();

        world.getChunkStore().getChunkReferenceAsync(chunkIndex).thenAcceptAsync(chunkRef -> {
            if (!targetRef.isValid()) return;
            world.getEntityStore().getStore().addComponent(
                targetRef, 
                teleportType, 
                new Teleport(world, targetPos, targetRot)
            );
        }, world);
    }

}

