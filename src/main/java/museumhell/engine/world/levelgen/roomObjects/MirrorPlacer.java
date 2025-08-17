package museumhell.engine.world.levelgen.roomObjects;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.enums.ConnectionType;
import museumhell.engine.world.levelgen.enums.Direction;
import museumhell.utils.media.AssetLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static museumhell.utils.ConstantManager.DOOR_W;
import static museumhell.utils.ConstantManager.HOLE_W;

public class MirrorPlacer {
    private final Node root;
    private final AssetLoader assets;
    private final Random rng;

    private static final float PROB = 0.7f;
    private static final float WALL_EPS = 0.03f;
    private static final float SIDE_MARGIN = 0.9f;

    // NUEVO: evitar esquinas y exigir un tramo mínimo
    private static final float CORNER_PAD = 0.45f; // margen en extremos del muro para no “comerse” esquinas
    private static final float GAP_PAD = 0.30f; // margen a cada lado de puertas / openings
    private static final float MIN_SEG = 1.00f; // longitud mínima de tramo sólido para poner espejo
    private static final int MAX_TRIES = 16;

    private static final Quaternion MODEL_FORWARD_FIX = new Quaternion().fromAngles(0, 0, 0);

    public MirrorPlacer(AssetLoader assets, Node root, long seed) {
        this.assets = assets;
        this.root = root;
        this.rng = new Random(seed);
    }

    public void onWall(Room r, Direction dir, float yBase, float wallH, List<Connection> levelConns) {
        if (rng.nextFloat() > PROB) return;

        Spatial mirror = assets.get("mirror");
        if (mirror == null) return;
        mirror.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        mirror.setLocalScale(7f);

        float y = yBase + wallH * 0.33f;

        final boolean ns = (dir == Direction.NORTH || dir == Direction.SOUTH);
        float lo = ns ? r.x() : r.z();
        float hi = ns ? (r.x() + r.w()) : (r.z() + r.h());

        // margen lateral + margen de esquina
        lo += Math.max(SIDE_MARGIN, CORNER_PAD);
        hi -= Math.max(SIDE_MARGIN, CORNER_PAD);
        if (hi - lo < MIN_SEG) return; // no hay tramo útil

        // 1) rangos bloqueados por puertas/openings en ESTE muro
        List<float[]> blocks = new ArrayList<>();
        for (Connection c : levelConns) {
            if (!appliesToWall(c, r, dir)) continue;

            // superposición a lo largo del eje del muro
            Room o = (c.a() == r) ? c.b() : c.a();
            float ovMin = ns ? Math.max(r.x(), o.x()) : Math.max(r.z(), o.z());
            float ovMax = ns ? Math.min(r.x() + r.w(), o.x() + o.w()) : Math.min(r.z() + r.h(), o.z() + o.h());
            if (ovMax <= ovMin) continue;

            // asumimos hueco centrado (igual que buildOpening/door), con padding
            float center = (ovMin + ovMax) * 0.5f;
            float half = ((c.type() == ConnectionType.DOOR ? DOOR_W : HOLE_W) * 0.5f) + GAP_PAD;
            float s = center - half;
            float e = center + half;

            // clamp al tramo del muro
            if (e <= lo || s >= hi) continue;
            blocks.add(new float[]{Math.max(s, lo), Math.min(e, hi)});
        }

        // 2) construir tramos libres = [lo,hi] \ blocks (merge + resta)
        List<float[]> free = subtractMerged(lo, hi, merge(blocks));
        free.removeIf(seg -> (seg[1] - seg[0]) < MIN_SEG);
        if (free.isEmpty()) return;

        // 3) muestrea un tramo proporcional a su longitud y toma coord
        float coord = pickFromSegments(free);

        Vector3f pos = new Vector3f();
        Vector3f normal = new Vector3f();
        switch (dir) {
            case NORTH -> {
                pos.set(coord, y, r.z() + WALL_EPS);
                normal.set(Vector3f.UNIT_Z);
            }
            case SOUTH -> {
                pos.set(coord, y, r.z() + r.h() - WALL_EPS);
                normal.set(Vector3f.UNIT_Z).negateLocal();
            }
            case WEST -> {
                pos.set(r.x() + WALL_EPS, y, coord);
                normal.set(Vector3f.UNIT_X);
            }
            case EAST -> {
                pos.set(r.x() + r.w() - WALL_EPS, y, coord);
                normal.set(Vector3f.UNIT_X).negateLocal();
            }
        }

        Quaternion rot = new Quaternion().lookAt(normal, Vector3f.UNIT_Y).multLocal(MODEL_FORWARD_FIX);
        mirror.setLocalTranslation(pos);
        mirror.setLocalRotation(rot);
        root.attachChild(mirror);
    }

    private static boolean appliesToWall(Connection c, Room r, Direction dir) {
        return (c.a() == r && c.dir() == dir) || (c.b() == r && opposite(c.dir()) == dir);
    }

    private static Direction opposite(Direction d) {
        return switch (d) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }

    // ---- utilidades de intervalos ----

    private static List<float[]> merge(List<float[]> ivs) {
        ivs.sort((a, b) -> Float.compare(a[0], b[0]));
        List<float[]> out = new ArrayList<>();
        for (float[] iv : ivs) {
            if (out.isEmpty() || iv[0] > out.get(out.size() - 1)[1]) out.add(new float[]{iv[0], iv[1]});
            else out.get(out.size() - 1)[1] = Math.max(out.get(out.size() - 1)[1], iv[1]);
        }
        return out;
    }

    private static List<float[]> subtractMerged(float lo, float hi, List<float[]> blocks) {
        List<float[]> res = new ArrayList<>();
        float cur = lo;
        for (float[] b : blocks) {
            if (b[0] > cur) res.add(new float[]{cur, Math.min(b[0], hi)});
            cur = Math.max(cur, b[1]);
            if (cur >= hi) break;
        }
        if (cur < hi) res.add(new float[]{cur, hi});
        return res;
    }

    private float pickFromSegments(List<float[]> segs) {
        float total = 0f;
        for (float[] s : segs) total += (s[1] - s[0]);
        float t = rng.nextFloat() * total;
        for (float[] s : segs) {
            float len = s[1] - s[0];
            if (t <= len) return s[0] + t;
            t -= len;
        }
        // fallback
        float[] last = segs.get(segs.size() - 1);
        return (last[0] + last[1]) * 0.5f;
    }
}
