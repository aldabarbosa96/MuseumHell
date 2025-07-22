package museumhell.engine.world.levelgen;

import com.jme3.math.Vector3f;

public record Room(int x, int z, int w, int h) {

    public Vector3f center3f(float y) {
        return new Vector3f(x + w * 0.5f, y, z + h * 0.5f);
    }
}
