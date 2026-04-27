package villagearia.interaction;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import villagearia.resource.BlockOfInterest;
import villagearia.resource.BlockOfInterestStore;
import villagearia.resource.manager.VillageZoneManager;
// import villagearia.component.villageZone.VillageZoneTaggedChests;
import villagearia.utils.JomlUtils;

public class TagChestForHousedNpc extends SimpleBlockInteraction {

    
    public static final BuilderCodec<TagChestForHousedNpc> CODEC = BuilderCodec.builder(
            TagChestForHousedNpc.class, TagChestForHousedNpc::new, SimpleBlockInteraction.CODEC)
            .build();

    public TagChestForHousedNpc() {
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    
    public String toString() {
        return "TagChestForHousedNpc{" + super.toString() + "}";
    }

    @Override
    protected void interactWithBlock(
         World world,  CommandBuffer<EntityStore> cb,
         InteractionType type,  InteractionContext ctx, @Nullable ItemStack itemStack,
         Vector3i pos,  CooldownHandler cooldown
    ) {
        var caster = ctx.getEntity();
        var tagAmount = tagChest(world, cb, pos);
        var player = cb.getComponent(caster, Player.getComponentType());
        if (player == null) return;
        if (tagAmount == 0) {
            player.sendMessage(Message.translation("This block is not a chest/container."));
        } else if (tagAmount < 0) {
            var amount = Math.abs(tagAmount);
            player.sendMessage(Message.translation("Chest untagged for " + Math.abs(tagAmount)+ " village zone" + (amount>1?"s.":".")));
        } else {
            var amount = Math.abs(tagAmount);
            player.sendMessage(Message.translation("Chest tagged for " + Math.abs(tagAmount)+ " village zone" + (amount>1?"s.":".")));
        }
    }
    
    private int tagChest(
         World world,  CommandBuffer<EntityStore> cb,  Vector3i pos
    ) {
        var chunkStore = world.getChunkStore();
        var chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef == null) return 0;
        var blockComponentChunk = chunkStore.getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) return 0;
        var blockRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
        if (blockRef == null) return 0;
        var itemContainerBlock = chunkStore.getStore().getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (itemContainerBlock == null) return 0;
        var store = cb.getStore();
        var blockOfInterestIndex = store.getResource(BlockOfInterestStore.getResourceType()).getIndex();

        // To avoid having 2 village Zone having the same chest untagged in one VillageZone and tagged in an other
        // we keep track of tag amount (can be negative for untag)
        // So all VillageZone will have the same state for the chest tag
        // so if the first villageZone is untag all next will be untag
        int[] tagAmount = { 0 };

        VillageZoneManager.getVillageZoneInRange(store, JomlUtils.vector3itoJoml(pos)).forEach(match -> {
            var uuid = match.uuid();
            var blockOfInterests = blockOfInterestIndex.get(uuid);
            if (blockOfInterests == null) return;
            
            var taggedChests = blockOfInterests.computeIfAbsent(BlockOfInterest.TAGGED_CHEST, k -> new ObjectOpenHashSet<>());
            if (!taggedChests.contains(pos) || tagAmount[0] > 0) {
                // Chest is untag we tag it
                taggedChests.add(pos);
                tagAmount[0]++;
            } else {
                // chest is tag we untag it
                taggedChests.remove(pos);
                tagAmount[0]--;
            }
        });
        return tagAmount[0];
    }
    
    
    @Override
    protected void simulateInteractWithBlock(
         InteractionType arg0,  InteractionContext arg1,
        @Nullable ItemStack arg2,  World arg3,  Vector3i arg4
    ) {

    }
}
