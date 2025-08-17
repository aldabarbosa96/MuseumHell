package museumhell.engine.world.levelgen.roomObjects;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import museumhell.engine.world.levelgen.Connection;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.enums.ConnectionType;
import museumhell.engine.world.levelgen.enums.Direction;
import museumhell.utils.media.AssetLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static museumhell.engine.world.levelgen.enums.Direction.*;
import static museumhell.utils.ConstantManager.DOOR_W;
import static museumhell.utils.ConstantManager.HOLE_W;

public class TablePlacer {
    private final Node root;
    private final AssetLoader assets;
    private final Random rng;

    private static final float PROB_PER_ROOM = 0.25f;
    private static final int MAX_PER_ROOM = 1;
    private static final float SURF_EPS = 0.015f;
    private static final float FLOOR_EPS = 0.005f;
    private static final float CORNER_CLEAR = 0.30f;
    private static final float GAP_PAD = 0.40f;
    private static final float SIDE_MARGIN = 0.60f;

    private static final float SCALE = 5f;

    private final Spatial base;

    public TablePlacer(AssetLoader assets, Node root, long seed) {
        this.assets = assets;
        this.root = root;
        this.rng = new Random(seed);
        this.base = assets.get("table1");
        if (base == null) throw new IllegalStateException("Asset 'table1' no encontrado");
    }

    public void placeInRoom(Room r, float yBase, List<Connection> conns) {
        if (rng.nextFloat() > PROB_PER_ROOM) return;
        List<Direction> walls = new ArrayList<>(List.of(NORTH, SOUTH, WEST, EAST));
        Collections.shuffle(walls, rng);
        int placed = 0;
        for (Direction dir : walls) {
            if (tryPlaceOnWall(r, dir, yBase, conns)) {
                if (++placed >= MAX_PER_ROOM) break;
            }
        }
    }

    private boolean tryPlaceOnWall(Room r, Direction dir, float yBase, List<Connection> conns) {
        Spatial probe = base.clone();
        probe.setLocalScale(SCALE);

        Vector3f wallNormal = switch (dir) {
            case NORTH -> new Vector3f(0, 0, 1);
            case SOUTH -> new Vector3f(0, 0, -1);
            case WEST -> new Vector3f(1, 0, 0);
            case EAST -> new Vector3f(-1, 0, 0);
        };
        Quaternion rot = new Quaternion().lookAt(wallNormal, Vector3f.UNIT_Y);
        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            rot.multLocal(new Quaternion().fromAngleAxis(FastMath.PI, Vector3f.UNIT_Y));
        }
        probe.setLocalRotation(rot);
        forceUpdateModelBounds(probe);
        probe.updateGeometricState();

        BoundingBox bb = (BoundingBox) probe.getWorldBound();
        float cx = bb.getCenter().x, cy = bb.getCenter().y, cz = bb.getCenter().z;
        float ex = bb.getXExtent(), ey = bb.getYExtent(), ez = bb.getZExtent();

        boolean ns = (dir == NORTH || dir == SOUTH);
        float halfSpan = ns ? ex : ez;

        float lo = ns ? r.x() : r.z();
        float hi = ns ? (r.x() + r.w()) : (r.z() + r.h());
        lo += Math.max(SIDE_MARGIN, halfSpan + CORNER_CLEAR);
        hi -= Math.max(SIDE_MARGIN, halfSpan + CORNER_CLEAR);
        if (hi <= lo) return false;

        List<float[]> blocks = new ArrayList<>();
        for (Connection c : conns) {
            if (!appliesToWall(c, r, dir)) continue;
            Room o = (c.a() == r) ? c.b() : c.a();
            float ovMin = ns ? Math.max(r.x(), o.x()) : Math.max(r.z(), o.z());
            float ovMax = ns ? Math.min(r.x() + r.w(), o.x() + o.w()) : Math.min(r.z() + r.h(), o.z() + o.h());
            if (ovMax <= ovMin) continue;
            float center = (ovMin + ovMax) * 0.5f;
            float holeHalf = ((c.type() == ConnectionType.DOOR ? DOOR_W : HOLE_W) * 0.5f) + GAP_PAD + halfSpan + CORNER_CLEAR;
            float s = center - holeHalf, e = center + holeHalf;
            if (e > lo && s < hi) blocks.add(new float[]{Math.max(s, lo), Math.min(e, hi)});
        }

        List<float[]> free = subtractMerged(lo, hi, merge(blocks));
        if (free.isEmpty()) return false;

        float coord = pickFromSegments(free);
        coord = Math.max(lo + halfSpan + CORNER_CLEAR, Math.min(hi - halfSpan - CORNER_CLEAR, coord));

        float ty = (yBase + FLOOR_EPS) - (cy - ey);

        float tx, tz;
        if (ns) {
            tx = coord - cx;
            if (dir == Direction.NORTH) {
                tz = (r.z() + SURF_EPS) - (cz - ez);
            } else {
                tz = (r.z() + r.h() - SURF_EPS) - (cz + ez);
            }
        } else {
            tz = coord - cz;
            if (dir == Direction.WEST) {
                tx = (r.x() + SURF_EPS) - (cx - ex);
            } else {
                tx = (r.x() + r.w() - SURF_EPS) - (cx + ex);
            }
        }

        Spatial s = base.clone();
        s.setLocalScale(SCALE);
        s.setLocalRotation(rot);
        s.setLocalTranslation(tx, ty, tz);
        s.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        root.attachChild(s);
        return true;
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

    private static void forceUpdateModelBounds(Spatial s) {
        if (s instanceof Geometry g) {
            g.updateModelBound();
        } else if (s instanceof Node n) {
            for (Spatial c : n.getChildren()) forceUpdateModelBounds(c);
        }
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
