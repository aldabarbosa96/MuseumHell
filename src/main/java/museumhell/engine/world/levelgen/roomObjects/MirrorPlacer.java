package museumhell.engine.world.levelgen.roomObjects;

import com.jme3.bounding.BoundingBox;
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

import static museumhell.engine.world.levelgen.enums.Direction.*;
import static museumhell.utils.ConstantManager.DOOR_W;
import static museumhell.utils.ConstantManager.HOLE_W;

public class MirrorPlacer {
    private final Node root;
    private final AssetLoader assets;
    private final Random rng;

    private static final float PROB = 0.3f;
    private static final float CORNER_CLEAR = 0.7f;
    private static final float SURF_EPS = 0.015f;
    private static final float GAP_PAD = 0.35f;
    private static final float SIDE_MARGIN = 0.80f;
    private static final float SCALE = 7f;

    private final Spatial mirrorBase;

    public MirrorPlacer(AssetLoader assets, Node root, long seed) {
        this.assets = assets;
        this.root = root;
        this.rng = new Random(seed);
        Spatial m = assets.get("mirror");
        if (m == null) throw new IllegalStateException("No existe asset 'mirror'");
        this.mirrorBase = m;
    }

    public void onWall(Room r, Direction dir, float yBase, float wallH, List<Connection> levelConns) {
        if (rng.nextFloat() > PROB) return;

        Dims dims = dimsFor(dir);
        float halfSpan = dims.halfSpan;
        float halfDepth = dims.halfDepth;

        final boolean ns = (dir == NORTH || dir == SOUTH);
        float lo = ns ? r.x() : r.z();
        float hi = ns ? (r.x() + r.w()) : (r.z() + r.h());

        lo += Math.max(SIDE_MARGIN, halfSpan + CORNER_CLEAR);
        hi -= Math.max(SIDE_MARGIN, halfSpan + CORNER_CLEAR);
        if (hi <= lo) return;

        List<float[]> blocks = new ArrayList<>();
        for (Connection c : levelConns) {
            if (!appliesToWall(c, r, dir)) continue;

            Room o = (c.a() == r) ? c.b() : c.a();
            float ovMin = ns ? Math.max(r.x(), o.x()) : Math.max(r.z(), o.z());
            float ovMax = ns ? Math.min(r.x() + r.w(), o.x() + o.w()) : Math.min(r.z() + r.h(), o.z() + o.h());
            if (ovMax <= ovMin) continue;

            float center = (ovMin + ovMax) * 0.5f;
            float holeHalf = ((c.type() == ConnectionType.DOOR ? DOOR_W : HOLE_W) * 0.5f) + GAP_PAD + halfSpan + CORNER_CLEAR;

            float s = center - holeHalf;
            float e = center + holeHalf;
            if (e <= lo || s >= hi) continue;

            blocks.add(new float[]{Math.max(s, lo), Math.min(e, hi)});
        }

        List<float[]> free = subtractMerged(lo, hi, merge(blocks));
        if (free.isEmpty()) return;
        float coord = pickFromSegments(free);

        float minC = lo + (halfSpan + CORNER_CLEAR);
        float maxC = hi - (halfSpan + CORNER_CLEAR);
        coord = Math.max(minC, Math.min(maxC, coord));

        float y = yBase + wallH * .4f;
        Vector3f pos = new Vector3f();
        Vector3f nrm = new Vector3f();
        switch (dir) {
            case NORTH -> {
                pos.set(coord, y, r.z() + (halfDepth + SURF_EPS));
                nrm.set(0, 0, 1);
            }
            case SOUTH -> {
                pos.set(coord, y, r.z() + r.h() - (halfDepth + SURF_EPS));
                nrm.set(0, 0, -1);
            }
            case WEST -> {
                pos.set(r.x() + (halfDepth + SURF_EPS), y, coord);
                nrm.set(1, 0, 0);
            }
            case EAST -> {
                pos.set(r.x() + r.w() - (halfDepth + SURF_EPS), y, coord);
                nrm.set(-1, 0, 0);
            }
        }

        Spatial mirror = mirrorBase.clone();
        mirror.setLocalScale(SCALE);
        mirror.setLocalRotation(new Quaternion().lookAt(nrm, Vector3f.UNIT_Y));
        mirror.setLocalTranslation(pos);
        mirror.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        root.attachChild(mirror);
    }

    private record Dims(float halfSpan, float halfDepth) {
    }

    private Dims dimsFor(Direction dir) {
        Spatial tmp = mirrorBase.clone();
        tmp.setLocalScale(SCALE);

        Vector3f nrm = switch (dir) {
            case NORTH -> new Vector3f(0, 0, 1);
            case SOUTH -> new Vector3f(0, 0, -1);
            case WEST -> new Vector3f(1, 0, 0);
            case EAST -> new Vector3f(-1, 0, 0);
        };
        tmp.setLocalRotation(new Quaternion().lookAt(nrm, Vector3f.UNIT_Y));
        tmp.updateGeometricState();

        BoundingBox bb = (BoundingBox) tmp.getWorldBound();

        float halfSpan = (dir == NORTH || dir == SOUTH) ? bb.getXExtent() : bb.getZExtent();
        float halfDepth = (dir == NORTH || dir == SOUTH) ? bb.getZExtent() : bb.getXExtent();
        return new Dims(halfSpan, halfDepth);
    }

    private static boolean appliesToWall(Connection c, Room r, Direction dir) {
        return (c.a() == r && c.dir() == dir) || (c.b() == r && opposite(c.dir()) == dir);
    }

    private static Direction opposite(Direction d) {
        return switch (d) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

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
        float[] last = segs.get(segs.size() - 1);
        return (last[0] + last[1]) * 0.5f;
    }
}
