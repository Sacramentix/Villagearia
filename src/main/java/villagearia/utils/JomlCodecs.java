package villagearia.utils;

import org.joml.Vector3d;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class JomlCodecs {
    
    /**
     * A zero-allocation, primitive-perfect Codec for org.joml.Vector3d.
     * This avoids the overhead of Double[] object boxing.
     */
    public static final Codec<Vector3d> VEC3D_CODEC = BuilderCodec.builder(
            Vector3d.class, Vector3d::new
        )
        .append(new KeyedCodec<>("X", Codec.DOUBLE), (v, x) -> v.x = x, v -> v.x).add()
        .append(new KeyedCodec<>("Y", Codec.DOUBLE), (v, y) -> v.y = y, v -> v.y).add()
        .append(new KeyedCodec<>("Z", Codec.DOUBLE), (v, z) -> v.z = z, v -> v.z).add()
        .build();

}