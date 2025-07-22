package museumhell.engine.world;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.engine.world.levelgen.*;

import java.util.*;

/**
 * Construye el contenido físico y gráfico del museo a partir de un {@link MuseumLayout}.
 */
public class WorldBuilder {

    /* ─────────────────── CONSTANTES ─────────────────── */

    private static final float DOOR_W = 2f, WALL_T = .33f, MARGIN = 1f;
    private static final int SMALL_SIDE = 8, GRID_STEP = 12, MAX_LAMPS = 4;
    private static final int MIN_OVERLAP_FOR_DOOR = (int) (DOOR_W + 2 * MARGIN);
    private static final float STAIR_WALL_GAP = 0.20f;
    private static final float STAIR_FOOT_GAP =  1.5f;

    /* ─────────────────── CAMPOS ─────────────────── */

    private final AssetManager am;
    private final Node root;
    private final PhysicsSpace space;
    private final List<Door> doors = new ArrayList<>();

    /**
     * Huecos (rectángulos) provocados por escaleras ― clave = índice de planta.
     */
    private final Map<Integer, List<Rect>> stairVoids = new HashMap<>();

    /* Rectángulo en planta ‑ ayuda para los huecos */
    private record Rect(float x1, float x2, float z1, float z2) {
    }

    public WorldBuilder(AssetManager am, Node root, PhysicsSpace space) {
        this.am = am;
        this.root = root;
        this.space = space;
    }

    /* ─────────────────── API PÚBLICA ─────────────────── */

    public void build(MuseumLayout museum) {
        // 1) calcula primero los huecos causados por las escaleras
        calculateStairVoids(museum);

        // 2) genera las plantas usando esos huecos
        float h = museum.floorHeight();
        for (int i = 0; i < museum.floors().size(); i++) {
            List<Rect> holes = stairVoids.getOrDefault(i, List.of());
            buildSingleFloor(museum.floors().get(i), museum.yOf(i), h, holes, i == 0);
        }

        // 3) coloca las escaleras (ya no “carva” losas, porque llegan recortadas)
        connectFloors(museum);
    }

    public void update(float tpf) {
        doors.forEach(d -> d.update(tpf));
    }

    public void tryUseDoor(Vector3f playerPos) {
        for (Door d : doors)
            if (d.getAccessPoint().distance(playerPos) < 2f) {
                d.toggle();
                break;
            }
    }

    public Door nearestDoor(Vector3f p, float maxDist) {
        Door best = null;
        float best2 = maxDist * maxDist;
        for (Door d : doors) {
            float d2 = d.getAccessPoint().distanceSquared(p);
            if (d2 < best2) {
                best2 = d2;
                best = d;
            }
        }
        return best;
    }

    public void addLootToRoom(Room r, int n) {
        for (int i = 0; i < n; i++) {
            float lx = r.x() + 1 + (float) Math.random() * (r.w() - 2);
            float lz = r.z() + 1 + (float) Math.random() * (r.h() - 2);
            Geometry g = makeGeometry("Loot", new Box(.5f, .5f, .5f), ColorRGBA.Orange);
            g.setLocalTranslation(lx, .5f, lz);
            addStatic(g);
        }
    }

    /* ─────────────────── GENERACIÓN DE PLANTAS ─────────────────── */

    private void buildSingleFloor(LevelLayout layout, float y0, float h, List<Rect> holes, boolean skipFloorHoles) {

        /* Lista de huecos que SÍ se aplicará al suelo de esta planta */
        List<Rect> floorHoles = skipFloorHoles ? List.of() : holes;

        for (Room r : layout.rooms()) {

            int w = r.w(), d = r.h();
            float cx = r.x() + w * .5f, cz = r.z() + d * .5f;

            /* luces ------------------------------------------------ */
            if (w <= SMALL_SIDE && d <= SMALL_SIDE) {
                pointLight(cx, y0 + h - .3f, cz, Math.max(w, d) * .95f);
            } else {
                int nx = Math.max(1, Math.round(w / (float) GRID_STEP));
                int nz = Math.max(1, Math.round(d / (float) GRID_STEP));
                int lamps = Math.min(nx * nz, MAX_LAMPS);
                float sx = w / (float) (nx + 1), sz = d / (float) (nz + 1);
                int placed = 0;
                for (int ix = 1; ix <= nx && placed < lamps; ix++)
                    for (int iz = 1; iz <= nz && placed < lamps; iz++, placed++)
                        pointLight(r.x() + ix * sx, y0 + h - .3f, r.z() + iz * sz, Math.max(sx, sz) * 1.8f);
            }

            /* suelo (piso actual) --------------------------------- */
            addFloorPatches(r.x(), r.z(), w, d, floorHoles, y0 - .1f, .1f, "Floor", ColorRGBA.Brown);

            /* techo (siempre recortable) --------------------------- */
            addFloorPatches(r.x(), r.z(), w, d, holes, y0 + h, .1f, "Ceil", ColorRGBA.Blue);

            /* muros + puertas ------------------------------------- */
            boolean n = doorNorth(r, layout), wN = doorWest(r, layout);
            boolean s = doorSouth(r, layout), e = doorEast(r, layout);

            buildWallX(r.x(), r.z(), w, y0, h, n, "N");
            buildWallZ(r.x(), r.z(), d, y0, h, wN, "W");
            if (!s) buildWallX(r.x(), r.z() + d, w, y0, h, false, "S");
            if (!e) buildWallZ(r.x() + w, r.z(), d, y0, h, false, "E");
        }
    }

    /**
     * Recorta la losa (suelo/techo) en parches para evitar el hueco de la escalera.
     */
    private void addFloorPatches(float rx, float rz, float rw, float rd, List<Rect> holes, float y, float t, String tag, ColorRGBA col) {

        /* Busca si la sala se solapa con algún hueco */
        for (Rect v : holes) {
            float hx1 = Math.max(v.x1, rx);
            float hx2 = Math.min(v.x2, rx + rw);
            float hz1 = Math.max(v.z1, rz);
            float hz2 = Math.min(v.z2, rz + rd);

            if (hx1 < hx2 && hz1 < hz2) {      // hay solapamiento → 4 parches máx.

                // 1) izquierda
                if (hx1 > rx) makePatch(rx, rz, hx1 - rx, rd, y, t, tag + "_L", col);

                // 2) derecha
                if (hx2 < rx + rw) makePatch(hx2, rz, rx + rw - hx2, rd, y, t, tag + "_R", col);

                // 3) delante
                if (hz1 > rz) makePatch(hx1, rz, hx2 - hx1, hz1 - rz, y, t, tag + "_F", col);

                // 4) detrás
                if (hz2 < rz + rd) makePatch(hx1, hz2, hx2 - hx1, rz + rd - hz2, y, t, tag + "_B", col);

                return; // solo un hueco por sala en esta implementación
            }
        }

        /* Sin hueco → losa completa */
        makePatch(rx, rz, rw, rd, y, t, tag, col);
    }

    private void makePatch(float x, float z, float w, float d, float y, float t, String name, ColorRGBA col) {
        Geometry g = makeGeometry(name, new Box(w * .5f, t * .5f, d * .5f), col);
        g.setLocalTranslation(x + w * .5f, y, z + d * .5f);
        addStatic(g);
    }

    /* ─────────────────── ESCALERAS ─────────────────── */

    private void calculateStairVoids(MuseumLayout museum) {

        if (museum.floors().size() < 2) return;

        Room baseRoom = museum.floors().get(0).rooms().get(0);

        /* ─── Posición X pegada a la pared Oeste (‑X) ─── */
        float sx = baseRoom.x() + STAIR_WALL_GAP + Stairs.WIDTH * .5f;
        if (sx + Stairs.WIDTH * .5f > baseRoom.x() + baseRoom.w()) {
            /* Si no cabe, se pega a la pared Este (+X) */
            sx = baseRoom.x() + baseRoom.w() - STAIR_WALL_GAP - Stairs.WIDTH * .5f;
        }

        /* ─── Profundidad y posición Z ─── */
        int steps = (int) Math.ceil(museum.floorHeight() / Stairs.STEP_H);
        float runDepth = steps * Stairs.STEP_DEPTH;

        float sz = baseRoom.z() + Stairs.STEP_DEPTH * .5f + STAIR_FOOT_GAP;
        if (sz + runDepth - Stairs.STEP_DEPTH * .5f > baseRoom.z() + baseRoom.h() - STAIR_FOOT_GAP) {
            sz = baseRoom.z() + baseRoom.h() - runDepth + Stairs.STEP_DEPTH * .5f - STAIR_FOOT_GAP;
        }

        /* ─── Rectángulo que cubre TODO el recorrido ─── */
        final float PAD = 0.05f;
        float hx = Stairs.WIDTH * .5f + PAD;

        float minX = sx - hx, maxX = sx + hx;
        float minZ = sz - Stairs.STEP_DEPTH * .5f;
        float maxZ = sz + runDepth - Stairs.STEP_DEPTH * .5f;

        Rect rect = new Rect(minX, maxX, minZ, maxZ);

        /* Registrar hueco en ambas plantas: i (techo) y i+1 (suelo) */
        for (int i = 0; i < museum.floors().size() - 1; i++) {
            stairVoids.computeIfAbsent(i, k -> new ArrayList<>()).add(rect); // techo planta i
            stairVoids.computeIfAbsent(i + 1, k -> new ArrayList<>()).add(rect); // suelo planta i+1
        }
    }

    private void connectFloors(MuseumLayout museum) {

        if (museum.floors().size() < 2) return;

        Room baseRoom = museum.floors().get(0).rooms().get(0);

        /* ─── Posición X pegada a pared Oeste (o Este si no cabe) ─── */
        float sx = baseRoom.x() + STAIR_WALL_GAP + Stairs.WIDTH * .5f;
        if (sx + Stairs.WIDTH * .5f > baseRoom.x() + baseRoom.w()) {
            sx = baseRoom.x() + baseRoom.w() - STAIR_WALL_GAP - Stairs.WIDTH * .5f;
        }

        /* ─── Posición Z y profundidad ─── */
        int steps = (int) Math.ceil(museum.floorHeight() / Stairs.STEP_H);
        float runDepth = steps * Stairs.STEP_DEPTH;

        float sz = baseRoom.z() + Stairs.STEP_DEPTH * .5f + STAIR_FOOT_GAP;
        if (sz + runDepth - Stairs.STEP_DEPTH * .5f > baseRoom.z() + baseRoom.h() - STAIR_FOOT_GAP) {
            sz = baseRoom.z() + baseRoom.h() - runDepth + Stairs.STEP_DEPTH * .5f - STAIR_FOOT_GAP;
        }

        /* ─── Instancia las escaleras en cada planta ─── */
        for (int i = 0; i < museum.floors().size() - 1; i++) {
            float y0 = museum.yOf(i);
            Stairs.add(root, space, am, new Vector3f(sx, y0, sz), museum.floorHeight());
        }
    }

    /* ─────────────────── MUROS Y PUERTAS ─────────────────── */

    private void buildWallX(float x0, float z, int w, float y0, float h, boolean neighbor, String tag) {

        if (!neighbor) {
            Geometry g = makeGeometry("Wall" + tag, new Box(w * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
            g.setLocalTranslation(x0 + w * .5f, y0 + h * .5f, z);
            addStatic(g);
            return;
        }

        float free = w - 2 * MARGIN;
        if (free < DOOR_W) {
            buildWallX(x0, z, w, y0, h, false, tag);
            return;
        }

        float side = (free - DOOR_W) * .5f;

        Geometry gL = makeGeometry("Wall" + tag + "_L", new Box((MARGIN + side) * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
        gL.setLocalTranslation(x0 + (MARGIN + side) * .5f, y0 + h * .5f, z);
        addStatic(gL);

        Geometry gR = makeGeometry("Wall" + tag + "_R", new Box((MARGIN + side) * .5f, h * .5f, WALL_T), ColorRGBA.Gray);
        gR.setLocalTranslation(x0 + w - (MARGIN + side) * .5f, y0 + h * .5f, z);
        addStatic(gR);

        float doorX = x0 + MARGIN + side + DOOR_W * .5f;
        Door d = new Door(am, space, new Vector3f(doorX, y0 + h * .5f, z), DOOR_W, h, WALL_T, new Vector3f(DOOR_W + .05f, 0, 0));
        root.attachChild(d.getSpatial());
        doors.add(d);
    }

    private void buildWallZ(float x, float z0, int d, float y0, float h, boolean neighbor, String tag) {

        if (!neighbor) {
            Geometry g = makeGeometry("Wall" + tag, new Box(WALL_T, h * .5f, d * .5f), ColorRGBA.DarkGray);
            g.setLocalTranslation(x, y0 + h * .5f, z0 + d * .5f);
            addStatic(g);
            return;
        }

        float free = d - 2 * MARGIN;
        if (free < DOOR_W) {
            buildWallZ(x, z0, d, y0, h, false, tag);
            return;
        }

        float side = (free - DOOR_W) * .5f;

        Geometry gT = makeGeometry("Wall" + tag + "_T", new Box(WALL_T, h * .5f, (MARGIN + side) * .5f), ColorRGBA.DarkGray);
        gT.setLocalTranslation(x, y0 + h * .5f, z0 + (MARGIN + side) * .5f);
        addStatic(gT);

        Geometry gB = makeGeometry("Wall" + tag + "_B", new Box(WALL_T, h * .5f, (MARGIN + side) * .5f), ColorRGBA.DarkGray);
        gB.setLocalTranslation(x, y0 + h * .5f, z0 + d - (MARGIN + side) * .5f);
        addStatic(gB);

        float doorZ = z0 + MARGIN + side + DOOR_W * .5f;
        Door dObj = new Door(am, space, new Vector3f(x, y0 + h * .5f, doorZ), WALL_T, h, DOOR_W, new Vector3f(0, 0, DOOR_W + .05f));
        root.attachChild(dObj.getSpatial());
        doors.add(dObj);
    }

    /* ─────────────────── PUERTAS ─────────────────── */

    private int overlap(int a1, int a2, int b1, int b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }

    private boolean doorNorth(Room a, LevelLayout L) {
        int ax1 = a.x(), ax2 = a.x() + a.w(), az = a.z();
        for (Room b : L.rooms())
            if (b != a && b.z() + b.h() == az && overlap(ax1, ax2, b.x(), b.x() + b.w()) >= MIN_OVERLAP_FOR_DOOR)
                return true;
        return false;
    }

    private boolean doorSouth(Room a, LevelLayout L) {
        int ax1 = a.x(), ax2 = a.x() + a.w(), az = a.z() + a.h();
        for (Room b : L.rooms())
            if (b != a && b.z() == az && overlap(ax1, ax2, b.x(), b.x() + b.w()) >= MIN_OVERLAP_FOR_DOOR) return true;
        return false;
    }

    private boolean doorWest(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax = a.x();
        for (Room b : L.rooms())
            if (b != a && b.x() + b.w() == ax && overlap(az1, az2, b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR)
                return true;
        return false;
    }

    private boolean doorEast(Room a, LevelLayout L) {
        int az1 = a.z(), az2 = a.z() + a.h(), ax = a.x() + a.w();
        for (Room b : L.rooms())
            if (b != a && b.x() == ax && overlap(az1, az2, b.z(), b.z() + b.h()) >= MIN_OVERLAP_FOR_DOOR) return true;
        return false;
    }

    /* ─────────────────── UTILIDADES LOW‑LEVEL ─────────────────── */

    private void pointLight(float x, float y, float z, float radius) {
        PointLight pl = new PointLight();
        pl.setRadius(radius);
        pl.setColor(new ColorRGBA(1f, .75f, .45f, 1f).mult(10f));
        pl.setPosition(new Vector3f(x, y, z));
        root.addLight(pl);
    }

    private Geometry makeGeometry(String name, Mesh mesh, ColorRGBA base) {
        Geometry g = new Geometry(name, mesh);
        ColorRGBA dark = base.mult(.05f + (float) Math.random() * .07f);
        Material m = new Material(am, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Ambient", dark);
        m.setColor("Diffuse", dark);
        m.setColor("Specular", ColorRGBA.Black);
        m.setFloat("Shininess", 1);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        g.setMaterial(m);
        return g;
    }

    private void addStatic(Geometry g) {
        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
