package villagearia.utils;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;

public class MyBlockUtils {
     public static BlockType getSafeBlockType(World world, int x, int y, int z) {
        var chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return null;
        int blockId = chunk.getBlock(x, y, z);
        return BlockType.getAssetMap().getAsset(blockId);
    }
    
    public static BlockType getSafeBlockType(World world, Vector3i pos) {
        return getSafeBlockType(world, pos.x, pos.y, pos.z);
    }


    public static int getSafeBlockFiller(World world, int x, int y, int z) {
        if (y < 0 || y >= 320) return 0;
        var chunkStore = world.getChunkStore();
        if (chunkStore == null) return 0;
        var sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null) return 0;
        var blockSection = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        return blockSection != null ? blockSection.getFiller(x, y, z) : 0;
    }

    public static int getSafeBlockFiller(World world, Vector3i pos) {
        return getSafeBlockFiller(world, pos.x, pos.y, pos.z);
    }

}