package villagearia.utils;

public final class JomlUtils {
    
    private JomlUtils() {}

    /**
     * Converts a Hytale Vector3d to a JOML Vector3d without re-instantiating if possible,
     * or creating a new JOML Vector3d instance.
     */
    
    public static org.joml.Vector3d toJoml( com.hypixel.hytale.math.vector.Vector3d hytaleVec) {
        return new org.joml.Vector3d(hytaleVec.x, hytaleVec.y, hytaleVec.z);
    }

    /**
     * Converts a Hytale Vector3d to a JOML Vector3d without re-instantiating if possible,
     * or creating a new JOML Vector3d instance.
     */
    
    public static org.joml.Vector3d vector3itoJoml( com.hypixel.hytale.math.vector.Vector3i hytaleVec) {
        return new org.joml.Vector3d(hytaleVec.x, hytaleVec.y, hytaleVec.z);
    }
    
    /**
     * Updates an existing JOML Vector3d with the values from a Hytale Vector3d to avoid allocation.
     */
    
    public static org.joml.Vector3d toJoml( com.hypixel.hytale.math.vector.Vector3d hytaleVec,  org.joml.Vector3d destJomlVec) {
        destJomlVec.set(hytaleVec.x, hytaleVec.y, hytaleVec.z);
        return destJomlVec;
    }

    /**
     * Converts a JOML Vector3d to a Hytale Vector3d.
     */
    
    public static com.hypixel.hytale.math.vector.Vector3d toHytale( org.joml.Vector3d jomlVec) {
        return new com.hypixel.hytale.math.vector.Vector3d(jomlVec.x(), jomlVec.y(), jomlVec.z());
    }

    /**
     * Updates an existing Hytale Vector3d with the values from a JOML Vector3d to avoid allocation.
     */
    
    public static com.hypixel.hytale.math.vector.Vector3d toHytale( org.joml.Vector3d jomlVec,  com.hypixel.hytale.math.vector.Vector3d destHytaleVec) {
        destHytaleVec.setX(jomlVec.x());
        destHytaleVec.setY(jomlVec.y());
        destHytaleVec.setZ(jomlVec.z());
        return destHytaleVec;
    }
}
