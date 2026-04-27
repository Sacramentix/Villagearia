package villagearia.interaction;


import java.util.UUID;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.BlockRotation;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.PlaceBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import villagearia.component.VillageZone;
import villagearia.resource.manager.VillageZoneManager;

public class PlaceBlockWithVillageZone extends SimpleInteraction {

   
   public static final BuilderCodec<PlaceBlockWithVillageZone> CODEC = BuilderCodec.builder(
         PlaceBlockWithVillageZone.class, PlaceBlockWithVillageZone::new, SimpleInteraction.CODEC
      )
      .documentation("Places the current or given block, and registers village zones.")
      .<String>append(
         new KeyedCodec<>("BlockTypeToPlace", Codec.STRING),
         (interaction, blockTypeKey) -> interaction.placeBlockInteraction.setBlockTypeKey(blockTypeKey),
         interaction -> interaction.placeBlockInteraction.getBlockTypeKey()
      )
      .addValidatorLate(() -> BlockType.VALIDATOR_CACHE.getValidator().late())
      .documentation("Overrides the placed block type of the held item with the provided block type.")
      .add()
      .<Boolean>append(
         new KeyedCodec<>("RemoveItemInHand", Codec.BOOLEAN),
         (interaction, aBoolean) -> interaction.placeBlockInteraction.setRemoveItemInHand(aBoolean),
         interaction -> interaction.placeBlockInteraction.isRemoveItemInHand()
      )
      .documentation("Determines whether to remove the item that is in the instigating entities hand.")
      .add()
      .<Boolean>appendInherited(
         new KeyedCodec<>("AllowDragPlacement", Codec.BOOLEAN),
         (interaction, aBoolean) -> interaction.placeBlockInteraction.setAllowDragPlacement(aBoolean),
         interaction -> interaction.placeBlockInteraction.isAllowDragPlacement(),
         (interaction, parent) -> interaction.placeBlockInteraction.setAllowDragPlacement(parent.placeBlockInteraction.isAllowDragPlacement())
      )
      .documentation("If drag placement should be used when click is held for this interaction.")
      .add()
      .build();

   // We subclass PlaceBlockInteraction so we can access/set its protected fields
   private static class InnerPlaceBlockInteraction extends PlaceBlockInteraction {
      public void setBlockTypeKey(String k) { this.blockTypeKey = k; }
      public String getBlockTypeKey() { return this.blockTypeKey; }

      public void setRemoveItemInHand(boolean b) { this.removeItemInHand = b; }
      public boolean isRemoveItemInHand() { return this.removeItemInHand; }

      public void setAllowDragPlacement(boolean b) { this.allowDragPlacement = b; }
      public boolean isAllowDragPlacement() { return this.allowDragPlacement; }

      public void callTick0(boolean firstRun, float time,  InteractionType type,  InteractionContext context,  CooldownHandler cooldownHandler) {
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

   public PlaceBlockWithVillageZone() {
   }

   
   @Override
   public WaitForDataFrom getWaitForDataFrom() {
      return this.placeBlockInteraction.getWaitForDataFrom();
   }

   @Override
   protected void tick0(
      boolean firstRun, float time,  InteractionType type,  InteractionContext ctx,  CooldownHandler cooldownHandler
   )  {
      var clientState = ctx.getClientState();

      assert clientState != null;

      if (!firstRun) {
         ctx.getState().state = clientState.state;
         return;
      }
      var cb = ctx.getCommandBuffer();

      this.placeBlockInteraction.callTick0(firstRun, time, type, ctx, cooldownHandler);
      var state = ctx.getState();
      placeVillageZoneInsideBlock(state.blockPosition, state.blockRotation, state.placedBlockId, cb);
   }

   public static UUID placeVillageZoneInsideBlock(
      BlockPosition blockPosition, BlockRotation blockRotation, int placedBlockId, CommandBuffer<EntityStore> cb
   ) {
      if (blockPosition == null) return null;
      if (blockRotation == null) return null;
      if (placedBlockId == Integer.MIN_VALUE) return null; 
      var blockType = BlockType.getAssetMap().getAsset(placedBlockId);
      if (blockType == null) return null;
      var outCenter = new Vector3d();
      
      var yaw   = Rotation.VALUES[blockRotation.rotationYaw.ordinal()];
      var pitch = Rotation.VALUES[blockRotation.rotationPitch.ordinal()];
      var roll  = Rotation.VALUES[blockRotation.rotationRoll.ordinal()];
      
      var rotationIndex = RotationTuple.index(yaw, pitch, roll);
      blockType.getBlockCenter(rotationIndex, outCenter);
      
      outCenter.setX(outCenter.getX() + blockPosition.x);
      outCenter.setY(outCenter.getY() + blockPosition.y);
      outCenter.setZ(outCenter.getZ() + blockPosition.z);
      
      var villageZone = new VillageZone(new org.joml.Vector3d(outCenter.x, outCenter.y, outCenter.z), 30*30);
      var uuid = UUID.randomUUID();
      VillageZoneManager.addVillageZone(cb.getStore(), uuid, villageZone);

      return uuid;
   }

   
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
      return "PlaceBlockWithVillageZone{" + this.placeBlockInteraction.toString() + "}";
   }
}
