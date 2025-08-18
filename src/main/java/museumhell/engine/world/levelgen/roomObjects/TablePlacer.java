package museumhell.engine.world.levelgen.roomObjects;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
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

import java.util.*;

import static museumhell.engine.world.levelgen.enums.Direction.*;
import static museumhell.utils.ConstantManager.*;

public class TablePlacer {
    private final Node root;
    private final AssetLoader assets;
    private final PhysicsSpace space;
    private final Random rng;
    private static final float PROB_PER_ROOM = 0.25f;
    private static final int MAX_PER_ROOM = 1;
    private static final float FLOOR_EPS = 0.005f;
    private static final float WALL_CLEAR = 0.09f;
    private static final float CORNER_CLEAR = 0.30f;
    private static final float GAP_PAD = 0.40f;
    private static final float SIDE_MARGIN = 0.60f;
    private static final float SCALE = 5f;
    private static final float DECO_SCALE = 4f;
    private static final float DECO_SURF_EPS = 0.008f;
    private static final float DECO_FWD_OFFSET = 0.12f;

    private final Spatial tableBase;
    private final List<Spatial> decoBases = new ArrayList<>(4);
    private final IdentityHashMap<Room, Integer> count = new IdentityHashMap<>();

    private record Dims(float halfSpan, float halfDepth, float halfHeight) {
    }

    private Dims dimsFor(Direction dir) {
        Spatial tmp = tableBase.clone();
        tmp.setLocalScale(SCALE);
        Vector3f nrm = switch (dir) {
            case NORTH -> new Vector3f(0, 0, 1);
            case SOUTH -> new Vector3f(0, 0, -1);
            case WEST -> new Vector3f(1, 0, 0);
            case EAST -> new Vector3f(-1, 0, 0);
        };
        tmp.setLocalRotation(new Quaternion().lookAt(nrm, Vector3f.UNIT_Y));
        forceUpdateModelBounds(tmp);
        tmp.updateGeometricState();

        BoundingBox bb = (BoundingBox) tmp.getWorldBound();
        float halfSpan = (dir == NORTH || dir == SOUTH) ? bb.getXExtent() : bb.getZExtent();
        float halfDepth = (dir == NORTH || dir == SOUTH) ? bb.getZExtent() : bb.getXExtent();
        float halfHeight = bb.getYExtent();
        return new Dims(halfSpan, halfDepth, halfHeight);
    }

    public TablePlacer(AssetLoader assets, Node root, PhysicsSpace space, long seed) {
        this.assets = assets;
        this.root = root;
        this.space = space;
        this.rng = new Random(seed);

        this.tableBase = assets.get("table1");
        if (tableBase == null) throw new IllegalStateException("Asset 'table1' no encontrado");

        for (String key : List.of("deco1", "deco2", "deco3", "deco4")) {
            Spatial s = assets.get(key);
            if (s != null) decoBases.add(s);
        }
        if (decoBases.isEmpty()) {
            throw new IllegalStateException("No se pudo cargar ningún deco (deco1..deco4)");
        }
    }

    public void onWall(Room r, Direction dir, float yBase, List<Connection> conns) {
        if (rng.nextFloat() > PROB_PER_ROOM) return;
        if (count.getOrDefault(r, 0) >= MAX_PER_ROOM) return;
        if (tryPlaceOnWall(r, dir, yBase, conns)) {
            count.put(r, count.getOrDefault(r, 0) + 1);
        }
    }

    private boolean tryPlaceOnWall(Room r, Direction dir, float yBase, List<Connection> conns) {
        Dims d = dimsFor(dir);
        boolean ns = (dir == NORTH || dir == SOUTH);

        float lo = ns ? r.x() : r.z();
        float hi = ns ? (r.x() + r.w()) : (r.z() + r.h());

        // Clearance lateral: media mesa + esquinas + media pared + margen
        float edgeClear = Math.max(SIDE_MARGIN, d.halfSpan + CORNER_CLEAR + (WALL_T * 0.5f) + WALL_CLEAR);
        lo += edgeClear;
        hi -= edgeClear;
        if (hi <= lo) return false;

        // Bloquear alrededor de puertas/aberturas
        List<float[]> blocks = new ArrayList<>();
        for (Connection c : conns) {
            if (!appliesToWall(c, r, dir)) continue;
            Room o = (c.a() == r) ? c.b() : c.a();
            float ovMin = ns ? Math.max(r.x(), o.x()) : Math.max(r.z(), o.z());
            float ovMax = ns ? Math.min(r.x() + r.w(), o.x() + o.w()) : Math.min(r.z() + r.h(), o.z() + o.h());
            if (ovMax <= ovMin) continue;

            float center = (ovMin + ovMax) * 0.5f;
            float holeHalf = ((c.type() == ConnectionType.DOOR ? DOOR_W : HOLE_W) * 0.5f) + GAP_PAD + d.halfSpan + (WALL_T * 0.5f) + WALL_CLEAR;

            float s = center - holeHalf, e = center + holeHalf;
            if (e > lo && s < hi) blocks.add(new float[]{Math.max(s, lo), Math.min(e, hi)});
        }

        List<float[]> free = subtractMerged(lo, hi, merge(blocks));
        if (free.isEmpty()) return false;

        float coord = pickFromSegments(free);
        coord = Math.max(lo + d.halfSpan, Math.min(hi - d.halfSpan, coord));

        Vector3f nrm = switch (dir) {
            case NORTH -> new Vector3f(0, 0, 1);
            case SOUTH -> new Vector3f(0, 0, -1);
            case WEST -> new Vector3f(1, 0, 0);
            case EAST -> new Vector3f(-1, 0, 0);
        };

        Vector3f pos = new Vector3f();
        if (ns) {
            pos.x = coord;
            pos.z = (dir == NORTH) ? r.z() + (d.halfDepth + WALL_CLEAR) : r.z() + r.h() - (d.halfDepth + WALL_CLEAR);
        } else {
            pos.z = coord;
            pos.x = (dir == WEST) ? r.x() + (d.halfDepth + WALL_CLEAR) : r.x() + r.w() - (d.halfDepth + WALL_CLEAR);
        }

        Spatial table = tableBase.clone();
        table.setLocalScale(SCALE);
        table.setLocalRotation(new Quaternion().lookAt(nrm, Vector3f.UNIT_Y));
        table.setLocalTranslation(pos);

        // Alinear con el suelo
        forceUpdateModelBounds(table);
        table.updateGeometricState();
        BoundingBox b = (BoundingBox) table.getWorldBound();
        float bottom = b.getCenter().y - b.getYExtent();
        pos.y += (yBase + FLOOR_EPS) - bottom;
        table.setLocalTranslation(pos);

        // Clamp post-colocación: asegura separación mínima al muro
        forceUpdateModelBounds(table);
        table.updateGeometricState();
        Vector3f nudge = nudgeOffWall(dir, r, (BoundingBox) table.getWorldBound(), WALL_CLEAR);
        if (nudge.x != 0f || nudge.z != 0f) {
            pos.addLocal(nudge);
            table.setLocalTranslation(pos);
            forceUpdateModelBounds(table);
            table.updateGeometricState();
        }

        attachWithPhysics(table);

        // ---- colocar deco aleatorio encima de la mesa ----
        placeDecorOn(table, nrm);

        return true;
    }

    private void placeDecorOn(Spatial table, Vector3f nrm) {
        if (decoBases.isEmpty()) return;

        // Centro superior de la mesa
        BoundingBox tb = (BoundingBox) table.getWorldBound();
        Vector3f topCenter = tb.getCenter().clone();
        topCenter.y += tb.getYExtent();

        // Empuje hacia el interior de la sala y leve EPS para evitar z-fighting
        Vector3f fwd = nrm.normalize().mult(DECO_FWD_OFFSET);

        // Elegir un deco al azar
        Spatial deco = decoBases.get(rng.nextInt(decoBases.size())).clone();
        deco.setLocalScale(DECO_SCALE);
        deco.setLocalRotation(new Quaternion().lookAt(nrm, Vector3f.UNIT_Y));
        deco.setLocalTranslation(topCenter.add(fwd).add(0, DECO_SURF_EPS, 0));

        deco.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        root.attachChild(deco);
    }

    private void attachWithPhysics(Spatial s) {
        s.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        CollisionShape shape = CollisionShapeFactory.createMeshShape(s);
        shape.setMargin(0.005f); // margen pequeño para no “engordar” la colisión
        RigidBodyControl body = new RigidBodyControl(shape, 0f);
        s.addControl(body);
        root.attachChild(s);
        space.add(body);
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
        if (s instanceof Geometry g) g.updateModelBound();
        else if (s instanceof Node n) for (Spatial c : n.getChildren()) forceUpdateModelBounds(c);
    }

    private static List<float[]> merge(List<float[]> ivs) {
        ivs.sort(Comparator.comparingDouble(a -> a[0]));
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

    private static Vector3f nudgeOffWall(Direction dir, Room r, BoundingBox bb, float clear) {
        float minX = bb.getCenter().x - bb.getXExtent();
        float maxX = bb.getCenter().x + bb.getXExtent();
        float minZ = bb.getCenter().z - bb.getZExtent();
        float maxZ = bb.getCenter().z + bb.getZExtent();

        return switch (dir) {
            case NORTH -> {
                float need = (r.z() + clear) - minZ;
                yield (need > 0f) ? new Vector3f(0, 0, need) : Vector3f.ZERO;
            }
            case SOUTH -> {
                float need = maxZ - (r.z() + r.h() - clear);
                yield (need > 0f) ? new Vector3f(0, 0, -need) : Vector3f.ZERO;
            }
            case WEST -> {
                float need = (r.x() + clear) - minX;
                yield (need > 0f) ? new Vector3f(need, 0, 0) : Vector3f.ZERO;
            }
            case EAST -> {
                float need = maxX - (r.x() + r.w() - clear);
                yield (need > 0f) ? new Vector3f(-need, 0, 0) : Vector3f.ZERO;
            }
        };
    }
}
