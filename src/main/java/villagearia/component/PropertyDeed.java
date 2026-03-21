package villagearia.component;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.meta.state.RespawnBlock;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import villagearia.Villagearia;

/**
 * Component representing the zone of a village.
 */
public class PropertyDeed implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PropertyDeed> CODEC = BuilderCodec.builder(
            PropertyDeed.class, PropertyDeed::new
        )
        .append(new KeyedCodec<>("Radius",  Codec.INTEGER), (deed, reach) -> deed.radius = reach, deed -> deed.radius).add()
        .append(new KeyedCodec<>("Npc_owner",  Codec.UUID_BINARY), (deed, npc_owner) -> deed.npc_owner = npc_owner, deed -> deed.npc_owner).add()
        .append(new KeyedCodec<>("Linked_bed",  Vector3i.CODEC), (deed, linked_bed) -> deed.linked_bed = linked_bed, deed -> deed.linked_bed).add()
        .append(new KeyedCodec<>("Position",  Vector3i.CODEC), (deed, position) -> deed.position = position, deed -> deed.position).add()
        .append(new KeyedCodec<>("Has_Light",  Codec.BOOLEAN), (deed, has_light) -> deed.has_light = has_light, deed -> deed.has_light).add()
        .build();

    private int radius;
    
    @Nullable
    private UUID npc_owner;
    
    @Nullable
    private Vector3i linked_bed;

    private Vector3i position; 

    private boolean has_light = false;

    /**
     * Default constructor for serialization.
     */
    public PropertyDeed() {
    }

    public PropertyDeed clone() {
        return new PropertyDeed(radius);
    }

    /**
     * Constructor to initialize the village zone.
     * 
     * @param radius  The radius of the village zone.
     */
    public PropertyDeed(int radius) {
        this.radius = radius;
    }

    // Getters and setters

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public Vector3i getPosition() {
        return position;
    }

    public void setPosition(Vector3i position) {
        this.position = position;
    }

    @Nullable
    public Vector3i getLinkedBed() {
        return linked_bed;
    }

    public void setLinkedBed(@Nullable Vector3i linked_bed) {
        this.linked_bed = linked_bed;
    }

    public void scan(World world) {
        var bedPosRef = new AtomicReference<Vector3i>();
        var foundLight = new AtomicBoolean(false);

        // Uses a stream to significantly reduce code, with early exit checks 
        streamBlockPositions(this.position, this.radius).forEach(p -> {
            if (foundLight.get() && bedPosRef.get() != null) return; // both conditions met, short-circuit

            var blockType = world.getBlockType(p.x, p.y, p.z);
            if (blockType == null) return;
            
            if (!foundLight.get() && blockType.getLight() != null && blockType.getLight().radius > 0) {
                foundLight.set(true);
            }
            
            if (bedPosRef.get() == null && isUnclaimedBed(world, p, blockType)) {
                bedPosRef.compareAndSet(null, p);
            }
        });
        
        this.has_light = foundLight.get();
        var bedPos = bedPosRef.get();
        
        if (bedPos != null) {
            // Unclaimed bed found
            this.linked_bed = bedPos;
        }
    }

    private Stream<Vector3i> streamBlockPositions(Vector3i pos, int r) {
        var minX = pos.x - r;
        var maxX = pos.x + r;
        var minY = pos.y - r;
        var maxY = pos.y + r;
        var minZ = pos.z - r;
        var maxZ = pos.z + r;

        return IntStream.rangeClosed(minX, maxX).boxed()
            .flatMap(x -> IntStream.rangeClosed(minY, maxY).boxed()
                .flatMap(y -> IntStream.rangeClosed(minZ, maxZ).mapToObj(z -> new Vector3i(x, y, z))));
    }

    private boolean isUnclaimedBed(World world, Vector3i pos, BlockType blockType) {
        if (blockType.getBeds() == null) return false;

        var chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        var chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
        if (chunkRef == null) return true;
        if (!chunkRef.isValid()) return true;

        var chunkStore = chunkRef.getStore();
        
        var blockComponentChunk = chunkStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) return true;

        var blockIndex = ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z);
        var blockRef = blockComponentChunk.getEntityReference(blockIndex);
        if (blockRef == null) return true;
        if (!blockRef.isValid()) return true;

        var respawnBlock = chunkStore.getComponent(blockRef, RespawnBlock.getComponentType());
        if (respawnBlock == null) return true;

        return respawnBlock.getOwnerUUID() == null;
    }
    public static ComponentType<EntityStore, PropertyDeed> getComponentType() {
        return Villagearia.get().getPropertyDeedComponentType();
    }

}