package villagearia.interaction;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Ref;

import villagearia.graph.VillageZoneGraph;
// import villagearia.component.villageZone.VillageZoneTaggedChests;

@SuppressWarnings("null")
public class TagChestForHousedNpc extends SimpleBlockInteraction {

    @Nonnull
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
    @Nonnull
    public String toString() {
        return "TagChestForHousedNpc{" + super.toString() + "}";
    }

    @Override
    protected void interactWithBlock(
        @Nonnull World world, @Nonnull CommandBuffer<EntityStore> cb,
        @Nonnull InteractionType type, @Nonnull InteractionContext ctx, @Nullable ItemStack itemStack,
        @Nonnull Vector3i pos, @Nonnull CooldownHandler cooldown
    ) {
        Ref<EntityStore> playerRef = ctx.getEntity();
        Player playerComponent = cb.getComponent(playerRef, Player.getComponentType());

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunkRef != null) {
            BlockComponentChunk blockComponentChunk = chunkStore.getStore().getComponent(chunkRef, BlockComponentChunk.getComponentType());
            if (blockComponentChunk != null) {
                Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z));
                if (blockRef != null) {
                    ItemContainerBlock itemContainerBlock = chunkStore.getStore().getComponent(blockRef, ItemContainerBlock.getComponentType());
                    // if (itemContainerBlock != null) {
                    //     villagearia.component.villageZone.VillageZoneChestTag existingTag = chunkStore.getStore().getComponent(blockRef, villagearia.component.villageZone.VillageZoneChestTag.getComponentType());
                        
                    //     if (existingTag == null) {
                    //         chunkStore.getStore().addComponent(blockRef, villagearia.component.villageZone.VillageZoneChestTag.getComponentType(), new villagearia.component.villageZone.VillageZoneChestTag());
                    //         cb.invoke(playerRef, new villagearia.event.ChestTaggedEvent(pos));
                    //         if (playerComponent != null) {
                    //             playerComponent.sendMessage(Message.translation("Chest tagged for village zones."));
                    //         }
                    //     } else {
                    //         chunkStore.getStore().removeComponent(blockRef, villagearia.component.villageZone.VillageZoneChestTag.getComponentType());
                    //         cb.invoke(playerRef, new villagearia.event.ChestUntaggedEvent(pos));
                    //         if (playerComponent != null) {
                    //             playerComponent.sendMessage(Message.translation("Chest untagged for village zones."));
                    //         }
                    //     }
                    //     return;
                    // }
                }
            }
        }
        if (playerComponent != null) {
            playerComponent.sendMessage(Message.translation("This block is not a chest/container."));
        }
    }    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType arg0, @Nonnull InteractionContext arg1,
            @Nullable ItemStack arg2, @Nonnull World arg3, @Nonnull Vector3i arg4) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'simulateInteractWithBlock'");
    }
}
