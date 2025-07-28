package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.levelgen.Room;
import museumhell.utils.AssetLoader;

import java.util.List;

import static museumhell.engine.world.levelgen.Direction.*;

public class WallBuilder {

    private static final int DOOR_MIN_OVERLAP = 3;
    private static final float WALL_T = 0.33f;

    private final AssetManager assetManager;
    private final Node root;
    private final PhysicsSpace space;
    private final Material wallMat;
    private final Spatial wallModel, wall2Model;

    private final float modelLength;
    private final float modelHeight;
    private final float modelThickness;

    public WallBuilder(AssetManager assetManager, Node root, PhysicsSpace space, AssetLoader assetLoader) {
        this.assetManager = assetManager;
        this.root = root;
        this.space = space;
        this.wallModel = assetLoader.get("wall1");
        this.wall2Model = assetLoader.get("wall2");

        wallModel.updateGeometricState();
        BoundingBox bb = (BoundingBox) wallModel.getWorldBound();
        modelLength = bb.getXExtent() * 2f;
        modelHeight = bb.getYExtent() * 2f;
        modelThickness = bb.getZExtent() * 2f;

        wallMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        wallMat.setBoolean("UseMaterialColors", true);

        wallMat.setColor("Ambient", new ColorRGBA(0.05f, 0.05f, 0.05f, 1f));
        wallMat.setColor("Diffuse", new ColorRGBA(0.8f, 0.8f, 0.8f, 1f));
        wallMat.setColor("Specular", ColorRGBA.Black);

        wallMat.setFloat("Shininess", 1);
        wallMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
    }

    public void buildSolid(Room r, Direction dir, float y0, float h) {
        // 1) Dimensiones deseadas en mundo (longitud sobre X o Z, altura, grosor)
        float length = (dir == NORTH || dir == SOUTH) ? r.w() : r.h();
        float sx = (dir == NORTH || dir == SOUTH) ? length : WALL_T;
        float sy = h;
        float sz = (dir == NORTH || dir == SOUTH) ? WALL_T : length;

        // 2) Clona el modelo y saca sus dimensiones originales
        Spatial wall = wallModel.clone();
        wall.updateGeometricState();
        BoundingBox bb = (BoundingBox) wall.getWorldBound();
        float origW = bb.getXExtent() * 2f;
        float origH = bb.getYExtent() * 2f;
        float origT = bb.getZExtent() * 2f;

        // 3) Rotación para Norte/Sur
        if (dir == NORTH || dir == SOUTH) {
            // gira 90° (PI/2) sobre Y
            Quaternion rot = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);
            wall.setLocalRotation(rot);
            // al rotar, el eje X del modelo pasa a Z, y viceversa:
            wall.setLocalScale(sz / origW, sy / origH, sx / origT);
        } else {
            // Este es el caso Este/Oeste, usa escala normal
            wall.setLocalScale(sx / origW, sy / origH, sz / origT);
        }

        wall.setShadowMode(ShadowMode.CastAndReceive);

        // 4) Cálculo de posición igual que antes
        float halfThick = (dir == NORTH || dir == SOUTH) ? sz * 0.5f : sx * 0.5f;
        float tx, tz;
        final float tx1 = r.x() + r.w() * 0.5f;
        final float v = r.z() + r.h() * 0.5f;
        tz = switch (dir) {
            case NORTH -> {
                tx = tx1;
                yield r.z() - halfThick;
            }
            case SOUTH -> {
                tx = tx1;
                yield r.z() + r.h() + halfThick;
            }
            case WEST -> {
                tx = r.x() - halfThick;
                yield v;
            }
            case EAST -> {
                tx = r.x() + r.w() + halfThick;
                yield v;
            }
        };
        wall.setLocalTranslation(tx, y0, tz);

        // 5) Añádelo a la escena y física (igual que antes)
        root.attachChild(wall);
        var shape = CollisionShapeFactory.createMeshShape(wall);
        var body = new RigidBodyControl(shape, 0);
        wall.addControl(body);
        space.add(body);
    }


    public void buildOpening(Room r, Direction dir, float y0, float h, List<Room> rooms, float holeWidth, float thickness) {
        // 1) Calcula el rango de solapamiento y el centro del hueco
        float[] ov = getOverlapRange(r, rooms, dir);
        float center = (ov[0] + ov[1]) * 0.5f;
        float halfHole = holeWidth * 0.5f;

        // 2) Lee dimensiones originales de wall2Model
        wall2Model.updateGeometricState();
        BoundingBox bb2 = (BoundingBox) wall2Model.getWorldBound();
        float origW = bb2.getXExtent() * 2f;
        float origH = bb2.getYExtent() * 2f;
        float origT = bb2.getZExtent() * 2f;

        if (dir == NORTH || dir == SOUTH) {
            // borde en Z
            float zEdge = (dir == NORTH) ? r.z() : r.z() + r.h();
            float leftW = center - halfHole - r.x();
            float rightW = (r.x() + r.w()) - (center + halfHole);
            float halfT = thickness * 0.5f;
            // misma rotación que buildSolid para N/S
            Quaternion rot = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);

            // Slice izquierdo
            float v = (dir == NORTH) ? zEdge - halfT : zEdge + halfT;
            if (leftW > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rot);
                slice.setLocalScale(thickness / origW, h / origH, leftW / origT);
                float tx = r.x() + leftW * 0.5f;
                float tz = v;
                slice.setLocalTranslation(tx, y0, tz);
                addStaticModel(slice);
            }

            // Slice derecho
            if (rightW > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rot);
                slice.setLocalScale(thickness / origW, h / origH, rightW / origT);
                float tx = r.x() + r.w() - rightW * 0.5f;
                float tz = v;
                slice.setLocalTranslation(tx, y0, tz);
                addStaticModel(slice);
            }

        } else {
            // borde en X
            float xEdge = (dir == WEST) ? r.x() : r.x() + r.w();
            float backD = center - halfHole - r.z();
            float frontD = (r.z() + r.h()) - (center + halfHole);
            float halfT = thickness * 0.5f;
            // sin rotación para O/E
            Quaternion rot = new Quaternion();

            // Slice trasero (hacia -Z)
            float v = (dir == WEST) ? xEdge - halfT : xEdge + halfT;
            if (backD > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rot);
                slice.setLocalScale(thickness / origW, h / origH, backD / origT);
                float tx = v;
                float tz = r.z() + backD * 0.5f;
                slice.setLocalTranslation(tx, y0, tz);
                addStaticModel(slice);
            }

            // Slice frontal (hacia +Z)
            if (frontD > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rot);
                slice.setLocalScale(thickness / origW, h / origH, frontD / origT);
                float tx = v;
                float tz = r.z() + r.h() - frontD * 0.5f;
                slice.setLocalTranslation(tx, y0, tz);
                addStaticModel(slice);
            }
        }
    }


    public float[] getOverlapRange(Room r, List<Room> rooms, Direction dir) {
        int a1, a2, b1, b2;

        if (dir == Direction.NORTH || dir == SOUTH) {
            a1 = r.x();
            a2 = r.x() + r.w();
            int zEdge = (dir == Direction.NORTH) ? r.z() : r.z() + r.h();

            for (Room o : rooms) {
                boolean match = (dir == Direction.NORTH && o.z() + o.h() == zEdge) || (dir == SOUTH && o.z() == zEdge);
                if (match) {
                    b1 = o.x();
                    b2 = o.x() + o.w();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= DOOR_MIN_OVERLAP) {
                        return new float[]{Math.max(a1, b1), Math.min(a2, b2)};
                    }
                }
            }

        } else {
            a1 = r.z();
            a2 = r.z() + r.h();
            int xEdge = (dir == Direction.WEST) ? r.x() : r.x() + r.w();

            for (Room o : rooms) {
                boolean match = (dir == Direction.WEST && o.x() + o.w() == xEdge) || (dir == Direction.EAST && o.x() == xEdge);
                if (match) {
                    b1 = o.z();
                    b2 = o.z() + o.h();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= DOOR_MIN_OVERLAP) {
                        return new float[]{Math.max(a1, b1), Math.min(a2, b2)};
                    }
                }
            }
        }

        throw new IllegalStateException("No vecino válido para dir=" + dir + " en sala " + r);
    }

    private void addStaticModel(Spatial s) {
        var shape = CollisionShapeFactory.createMeshShape(s);
        var body = new RigidBodyControl(shape, 0);
        s.addControl(body);
        root.attachChild(s);
        space.add(body);
    }

}
