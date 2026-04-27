package villagearia.system;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class DoorTracker {

    private static IntOpenHashSet doorBlockIds = null;
    private static final Long2ObjectOpenHashMap<ObjectOpenHashSet<Vector3i>> doorsByChunk = new Long2ObjectOpenHashMap<>();

    public static void initDoorBlockIds() {
        doorBlockIds = new IntOpenHashSet();
        var map = BlockType.getAssetMap();
        for (var entry : map.getAssetMap().entrySet()) {
            if (entry.getKey().toLowerCase().contains("door")) {
                int index = map.getIndex(entry.getKey());
                if (index >= 0) doorBlockIds.add(index);
            }
        }
    }

    public static ObjectOpenHashSet<Vector3i> getDoorsInChunk(long chunkIndex) {
        return doorsByChunk.get(chunkIndex);
    }

    public static boolean isDoor(int blockId) {
        if (doorBlockIds == null) return false;
        return doorBlockIds.contains(blockId);
    }

    public static void addDoor(Vector3i pos) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        doorsByChunk.computeIfAbsent(chunkIndex, k -> new ObjectOpenHashSet<>()).add(pos);
    }

    public static void removeDoor(Vector3i pos) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        var set = doorsByChunk.get(chunkIndex);
        if (set != null) {
            set.remove(pos);
            if (set.isEmpty()) doorsByChunk.remove(chunkIndex);
        }
    }

    public static void clear() {
        doorsByChunk.clear();
        doorBlockIds = null;
    }

    @SuppressWarnings("deprecation")
    public static void onChunkLoaded(ChunkPreLoadProcessEvent event) {
        if (doorBlockIds == null) initDoorBlockIds();

        var chunk = event.getChunk();
        var chunkIndex = ChunkUtil.indexChunk(chunk.getX(), chunk.getZ());

        var chunkDoors = new ObjectOpenHashSet<Vector3i>();
        
        var blockChunk = chunk.getBlockChunk();
        if (blockChunk == null) return;

        for (int cy = 0; cy <= 9; cy++) {
            var section = blockChunk.getSectionAtBlockY(cy << 5);
            if (section == null) continue;

            var hasDoor = false;
            var chunkIt = section.values().iterator();
            while (chunkIt.hasNext()) {
                if (doorBlockIds.contains(chunkIt.nextInt())) {
                    hasDoor = true;
                    break;
                }
            }
            if (!hasDoor) continue;

            int baseX = chunk.getX() << 5;
            int baseY = cy << 5;
            int baseZ = chunk.getZ() << 5;

            for (int x = 0; x < 32; x++) {
                for (int y = 0; y < 32; y++) {
                    for (int z = 0; z < 32; z++) {
                        int blockId = section.get(x, y, z);
                        if (!doorBlockIds.contains(blockId)) continue;
                        
                        var pos = new Vector3i(baseX + x, baseY + y, baseZ + z);
                        var filler = section.getFiller(x, y, z);
                        if (filler != 0) {
                            pos = new Vector3i(
                                pos.x - FillerBlockUtil.unpackX(filler),
                                pos.y - FillerBlockUtil.unpackY(filler),
                                pos.z - FillerBlockUtil.unpackZ(filler)
                            );
                        }
                        chunkDoors.add(pos);
                    }
                }
            }
        }
        
        if (!chunkDoors.isEmpty()) {
            doorsByChunk.put(chunkIndex, chunkDoors);
        }
    }

    public static void onChunkUnloaded(ChunkUnloadEvent event) {
        WorldChunk chunk = event.getChunk();
        if (chunk == null) return;
        long chunkIndex = ChunkUtil.indexChunk(chunk.getX(), chunk.getZ());
        doorsByChunk.remove(chunkIndex);
    }
}