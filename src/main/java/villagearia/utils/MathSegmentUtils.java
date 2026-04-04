package villagearia.utils;

import com.hypixel.hytale.math.vector.Vector3d;

public class MathSegmentUtils {
    
    public static double distanceToSegmentSquared(Vector3d p, Vector3d v, Vector3d w) {
        var l2 = v.distanceSquaredTo(w);
        if (l2 == 0) return p.distanceSquaredTo(v);

        var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y) + (p.z - v.z) * (w.z - v.z)) / l2;
        t = Math.max(0, Math.min(1, t));

        var projX = v.x + t * (w.x - v.x);
        var projY = v.y + t * (w.y - v.y);
        var projZ = v.z + t * (w.z - v.z);

        var dx = p.x - projX;
        var dy = p.y - projY;
        var dz = p.z - projZ;

        return dx * dx + dy * dy + dz * dz;
    }
}
