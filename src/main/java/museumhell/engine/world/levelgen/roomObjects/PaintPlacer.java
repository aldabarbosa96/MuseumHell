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
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static museumhell.engine.world.levelgen.enums.Direction.*;
import static museumhell.utils.ConstantManager.DOOR_W;
import static museumhell.utils.ConstantManager.HOLE_W;

public class PaintPlacer {
    private final Node root;
    private final AssetLoader assets;
    private final Random rng;

    private static final float CORNER_CLEAR = 0.75f;
    private static final float SURF_EPS = 0.015f;
    private static final float GAP_PAD = 0.35f;
    private static final float SIDE_MARGIN = 0.75f;
    private static final float SCALE = 7f;
    private static final float CLAMP_EPS = 0.01f;
    private static final float FRAME_GAP = 0.55f;
    private static final float ROW_Y_FACTOR = 0.40f;

    private final List<Spatial> cuadros = new ArrayList<>(4);
    private float maxHalfSpanNS = 0f;
    private float maxHalfSpanEW = 0f;

    public PaintPlacer(AssetLoader assets, Node root, long seed) {
        this.assets = assets;
        this.root = root;
        this.rng = new Random(seed);

        for (String key : List.of("cuadro1", "cuadro2", "cuadro3", "cuadro4")) {
            Spatial s = assets.get(key);
            if (s != null) cuadros.add(s);
        }
        if (cuadros.isEmpty()) throw new IllegalStateException("Error: assets de cuadros no encontrados");

        maxHalfSpanNS = computeMaxHalfSpan(true);
        maxHalfSpanEW = computeMaxHalfSpan(false);
    }

    public void onWall(Room r, Direction dir, float yBase, float wallH, List<Connection> levelConns) {
        final boolean ns = (dir == NORTH || dir == SOUTH);
        float lo = ns ? r.x() : r.z();
        float hi = ns ? (r.x() + r.w()) : (r.z() + r.h());

        float halfSpanMax = ns ? maxHalfSpanNS : maxHalfSpanEW;
        lo += Math.max(SIDE_MARGIN, halfSpanMax + CORNER_CLEAR);
        hi -= Math.max(SIDE_MARGIN, halfSpanMax + CORNER_CLEAR);
        if (hi <= lo) return;

        // Bloqueos por puertas/huecos (ligeramente conservadores)
        List<float[]> blocks = new ArrayList<>();
        for (Connection c : levelConns) {
            if (!appliesToWall(c, r, dir)) continue;
            Room o = (c.a() == r) ? c.b() : c.a();
            float ovMin = ns ? Math.max(r.x(), o.x()) : Math.max(r.z(), o.z());
            float ovMax = ns ? Math.min(r.x() + r.w(), o.x() + o.w()) : Math.min(r.z() + r.h(), o.z() + o.h());
            if (ovMax <= ovMin) continue;

            float center = (ovMin + ovMax) * 0.5f;
            float holeHalf = ((c.type() == ConnectionType.DOOR ? DOOR_W : HOLE_W) * 0.5f) + GAP_PAD + halfSpanMax + CORNER_CLEAR;

            float s = center - holeHalf;
            float e = center + holeHalf;
            if (e <= lo || s >= hi) continue;
            blocks.add(new float[]{Math.max(s, lo), Math.min(e, hi)});
        }

        List<float[]> freeSegments = subtractMerged(lo, hi, merge(blocks));
        if (freeSegments.isEmpty()) return;

        Vector3f nrm = switch (dir) {
            case NORTH -> new Vector3f(0, 0, 1);
            case SOUTH -> new Vector3f(0, 0, -1);
            case WEST -> new Vector3f(1, 0, 0);
            case EAST -> new Vector3f(-1, 0, 0);
        };

        float y = yBase + wallH * ROW_Y_FACTOR;

        // Precalcular variantes (ancho) para esta dirección y ordenarlas de mayor a menor
        record Variant(Spatial base, Dims dims, float width) {
        }
        List<Variant> variants = new ArrayList<>();
        for (Spatial s : cuadros) {
            Dims d = dimsFor(s, dir);
            variants.add(new Variant(s, d, d.halfSpan * 2f));
        }
        variants.sort(Comparator.comparingDouble(v -> -v.width)); // greedy ancho→estrecho

        for (float[] seg : freeSegments) {
            float segStart = seg[0], segEnd = seg[1];
            float cursor = segStart;

            int guard = 0;
            while (cursor < segEnd && guard++ < 10000) {
                float remaining = segEnd - cursor;

                // Filtrar las que caben en el espacio restante (>= ancho)
                List<Variant> fits = new ArrayList<>();
                for (Variant v : variants) if (v.width + 1e-6f <= remaining) fits.add(v);

                if (fits.isEmpty()) break; // no cabe ningún cuadro más en este segmento

                // Elegir una: sesgo al más ancho que quepa, con un poco de variedad
                Variant pick = fits.get(0);
                if (fits.size() >= 3) {
                    // elige entre los 3 más anchos de forma aleatoria
                    int idx = rng.nextInt(3);
                    pick = fits.get(idx);
                }

                float nextCenter = cursor + pick.dims.halfSpan;

                Vector3f pos = new Vector3f();
                switch (dir) {
                    case NORTH -> pos.set(nextCenter, y, r.z() + (pick.dims.halfDepth + SURF_EPS));
                    case SOUTH -> pos.set(nextCenter, y, r.z() + r.h() - (pick.dims.halfDepth + SURF_EPS));
                    case WEST -> pos.set(r.x() + (pick.dims.halfDepth + SURF_EPS), y, nextCenter);
                    case EAST -> pos.set(r.x() + r.w() - (pick.dims.halfDepth + SURF_EPS), y, nextCenter);
                }

                Spatial cuadro = pick.base.clone();
                cuadro.setLocalScale(SCALE);
                cuadro.setLocalRotation(new Quaternion().lookAt(nrm, Vector3f.UNIT_Y));
                cuadro.setLocalTranslation(pos);
                cuadro.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

                // Clamp a los bordes del segmento
                cuadro.updateGeometricState();
                BoundingBox bb = (BoundingBox) cuadro.getWorldBound();
                float minEdge = ns ? bb.getCenter().x - bb.getXExtent() : bb.getCenter().z - bb.getZExtent();
                float maxEdge = ns ? bb.getCenter().x + bb.getXExtent() : bb.getCenter().z + bb.getZExtent();
                float shift = 0f;
                if (minEdge < segStart + CLAMP_EPS) shift += (segStart + CLAMP_EPS) - minEdge;
                if (maxEdge > segEnd - CLAMP_EPS) shift -= maxEdge - (segEnd - CLAMP_EPS);
                if (Math.abs(shift) > 0f) {
                    if (ns) pos.x += shift;
                    else pos.z += shift;
                    cuadro.setLocalTranslation(pos);
                    cuadro.updateGeometricState();
                }

                root.attachChild(cuadro);

                // Avanza cursor según la posición final real del cuadro
                float centerPlaced = ns ? cuadro.getWorldTranslation().x : cuadro.getWorldTranslation().z;
                cursor = centerPlaced + pick.dims.halfSpan + FRAME_GAP;
            }
        }
    }

    private record Dims(float halfSpan, float halfDepth) {
    }

    private Dims dimsFor(Spatial base, Direction dir) {
        Spatial tmp = base.clone();
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

    private float computeMaxHalfSpan(boolean ns) {
        float mx = 0f;
        for (Spatial s : cuadros) {
            Spatial tmp = s.clone();
            tmp.setLocalScale(SCALE);
            for (Direction d : ns ? List.of(NORTH, SOUTH) : List.of(EAST, WEST)) {
                tmp.setLocalRotation(new Quaternion().lookAt((d == NORTH) ? new Vector3f(0, 0, 1) : (d == SOUTH) ? new Vector3f(0, 0, -1) : (d == EAST) ? new Vector3f(-1, 0, 0) : new Vector3f(1, 0, 0), Vector3f.UNIT_Y));
                tmp.updateGeometricState();
                BoundingBox bb = (BoundingBox) tmp.getWorldBound();
                float halfSpan = ns ? bb.getXExtent() : bb.getZExtent();
                mx = Math.max(mx, halfSpan);
            }
        }
        return mx;
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
}
