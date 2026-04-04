package villagearia.interaction;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.BlockRotation;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.PlaceBlockInteraction;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.PropertyDeedManager;
import villagearia.component.PropertyDeed;
import villagearia.component.PropertyDeedRef;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

public class PlacePropertyDeed extends SimpleInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    public static final BuilderCodec<PlacePropertyDeed> CODEC = BuilderCodec.builder(
            PlacePropertyDeed.class, PlacePropertyDeed::new, SimpleInteraction.CODEC)
            .documentation("Places the current or given block, and registers village zones.")
            .<String>append(
                    new KeyedCodec<>("BlockTypeToPlace", Codec.STRING),
                    (interaction, blockTypeKey) -> interaction.placeBlockInteraction.setBlockTypeKey(blockTypeKey),
                    interaction -> interaction.placeBlockInteraction.getBlockTypeKey())
            .addValidatorLate(() -> BlockType.VALIDATOR_CACHE.getValidator().late())
            .documentation("Overrides the placed block type of the held item with the provided block type.")
            .add()
            .<Boolean>append(
                    new KeyedCodec<>("RemoveItemInHand", Codec.BOOLEAN),
                    (interaction, aBoolean) -> interaction.placeBlockInteraction.setRemoveItemInHand(aBoolean),
                    interaction -> interaction.placeBlockInteraction.isRemoveItemInHand())
            .documentation("Determines whether to remove the item that is in the instigating entities hand.")
            .add()
            .<Boolean>appendInherited(
                    new KeyedCodec<>("AllowDragPlacement", Codec.BOOLEAN),
                    (interaction, aBoolean) -> interaction.placeBlockInteraction.setAllowDragPlacement(aBoolean),
                    interaction -> interaction.placeBlockInteraction.isAllowDragPlacement(),
                    (interaction, parent) -> interaction.placeBlockInteraction
                            .setAllowDragPlacement(parent.placeBlockInteraction.isAllowDragPlacement()))
            .documentation("If drag placement should be used when click is held for this interaction.")
            .add()
            .build();

    // We subclass PlaceBlockInteraction so we can access/set its protected fields
    private static class InnerPlaceBlockInteraction extends PlaceBlockInteraction {
        public void setBlockTypeKey(String k) {
            this.blockTypeKey = k;
        }

        public String getBlockTypeKey() {
            return this.blockTypeKey;
        }

        public void setRemoveItemInHand(boolean b) {
            this.removeItemInHand = b;
        }

        public boolean isRemoveItemInHand() {
            return this.removeItemInHand;
        }

        public void setAllowDragPlacement(boolean b) {
            this.allowDragPlacement = b;
        }

        public boolean isAllowDragPlacement() {
            return this.allowDragPlacement;
        }

        public void callTick0(boolean firstRun, float time, @Nonnull InteractionType type,
                @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
            super.tick0(firstRun, time, type, context, cooldownHandler);
        }

        public void callConfigurePacket(com.hypixel.hytale.protocol.Interaction packet) {
            super.configurePacket(packet);
        }

        public com.hypixel.hytale.protocol.Interaction callGeneratePacket() {
            return super.generatePacket();
        }
    }

    private final InnerPlaceBlockInteraction placeBlockInteraction = new InnerPlaceBlockInteraction();

    public PlacePropertyDeed() {
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return this.placeBlockInteraction.getWaitForDataFrom();
    }

    @Override
    protected void tick0(
            boolean firstRun, float time, @Nonnull InteractionType type, @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        var clientState = context.getClientState();

        assert clientState != null;

        if (!firstRun) {
            context.getState().state = clientState.state;
            return;
        }
        var ref = context.getEntity();
        var commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null)
            return;
        var rawEntity = EntityUtils.getEntity(ref, commandBuffer);
        if (!(rawEntity instanceof LivingEntity))
            return;

        this.placeBlockInteraction.callTick0(firstRun, time, type, context, cooldownHandler);

        var state = context.getState();

        var pos = state.blockPosition;

        var uuid = placePropertyDeedEntity(
            state.blockPosition, state.blockRotation, state.placedBlockId, commandBuffer
        );
        if (uuid == null) {

        }
        var deedRef = new PropertyDeedRef(new Vector3i(pos.x, pos.y, pos.z), uuid);
        var filledDeed = new ItemStack("Property_Deed_Filled", 1)
            .withMetadata("PropertyDeedRef", PropertyDeedRef.CODEC, deedRef);

        var owningEntity = context.getOwningEntity();
        if (owningEntity != null && owningEntity.isValid()) {
            var playerCompType = Player.getComponentType();
            var player = owningEntity.getStore().getComponent(owningEntity, playerCompType);
            if (player != null) {
                player.getInventory().getCombinedHotbarFirst().addItemStack(filledDeed);
            }
        }


        context.getState().state = InteractionState.Finished;
    }

    public static UUID placePropertyDeedEntity(
        BlockPosition blockPosition, BlockRotation blockRotation, int placedBlockId, CommandBuffer<EntityStore> commandBuffer
    ) {
        var world = commandBuffer.getExternalData().getWorld();
        var deed = new PropertyDeed(15);
        LOGGER.atInfo().log("Deed blockPosition " + blockPosition.toString());
        deed.setPosition(new Vector3i(blockPosition.x, blockPosition.y, blockPosition.z));
        LOGGER.atInfo().log("Deed Vector3i " + deed.getPosition().toString());
        deed.scan(world);


        var uuid = PlaceBlockWithVillageZone.placeVillageZoneInsideBlock(
            new BlockPosition(blockPosition.x, blockPosition.y, blockPosition.z),
            new BlockRotation(),
            placedBlockId,
            commandBuffer
        );
        if (uuid == null) return null;

        PropertyDeedManager.addPropertyDeed(world, uuid, deed);
        return uuid;
    }

    @Nonnull
    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return this.placeBlockInteraction.callGeneratePacket();
    }

    @Override
    protected void configurePacket(com.hypixel.hytale.protocol.Interaction packet) {
        this.placeBlockInteraction.callConfigurePacket(packet);
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    public String toString() {
        return "PlacePropertyDeed{" + this.placeBlockInteraction.toString() + "}";
    }
}
