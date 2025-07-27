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
    private final Spatial wallModel;

    private final float modelLength;
    private final float modelHeight;
    private final float modelThickness;

    public WallBuilder(AssetManager assetManager, Node root, PhysicsSpace space, AssetLoader assetLoader) {
        this.assetManager = assetManager;
        this.root = root;
        this.space = space;
        this.wallModel = assetLoader.get("wall1");

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
        float sz = (dir == NORTH || dir == SOUTH) ? WALL_T  : length;

        // 2) Clona el modelo y saca sus dimensiones originales
        Spatial wall = wallModel.clone();
        wall.updateGeometricState();
        BoundingBox bb = (BoundingBox) wall.getWorldBound();
        float origW = bb.getXExtent()*2f;
        float origH = bb.getYExtent()*2f;
        float origT = bb.getZExtent()*2f;

        // 3) Rotación para Norte/Sur
        if (dir == NORTH || dir == SOUTH) {
            // gira 90° (PI/2) sobre Y
            Quaternion rot = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);
            wall.setLocalRotation(rot);
            // al rotar, el eje X del modelo pasa a Z, y viceversa:
            wall.setLocalScale(sz/origW, sy/origH, sx/origT);
        } else {
            // Este es el caso Este/Oeste, usa escala normal
            wall.setLocalScale(sx/origW, sy/origH, sz/origT);
        }

        wall.setShadowMode(ShadowMode.CastAndReceive);

        // 4) Cálculo de posición igual que antes
        float halfThick = (dir == NORTH || dir == SOUTH) ? sz*0.5f : sx*0.5f;
        float tx=0, tz=0;
        switch(dir){
            case NORTH:
                tx = r.x()+r.w()*0.5f;
                tz = r.z() - halfThick;
                break;
            case SOUTH:
                tx = r.x()+r.w()*0.5f;
                tz = r.z()+r.h() + halfThick;
                break;
            case WEST:
                tx = r.x() - halfThick;
                tz = r.z()+r.h()*0.5f;
                break;
            case EAST:
                tx = r.x()+r.w() + halfThick;
                tz = r.z()+r.h()*0.5f;
                break;
        }
        wall.setLocalTranslation(tx, y0, tz);

        // 5) Añádelo a la escena y física (igual que antes)
        root.attachChild(wall);
        var shape = CollisionShapeFactory.createMeshShape(wall);
        var body  = new RigidBodyControl(shape, 0);
        wall.addControl(body);
        space.add(body);
    }


    public void buildOpening(Room r, Direction dir, float y0, float h, List<Room> rooms, float holeWidth, float thickness) {

        float[] ov = getOverlapRange(r, rooms, dir);
        float center = (ov[0] + ov[1]) * .5f;
        float halfHole = holeWidth * .5f;

        if (dir == Direction.NORTH || dir == SOUTH) {
            float z = (dir == Direction.NORTH) ? r.z() : r.z() + r.h();
            float leftW = center - halfHole - r.x();
            float rightW = (r.x() + r.w()) - (center + halfHole);

            if (leftW > 0) {
                Box mesh = new Box(leftW * .5f, h * .5f, thickness);
                Geometry g = makeWallGeometry("WallSlice_L", mesh);
                g.setLocalTranslation(r.x() + leftW * .5f, y0 + h * .5f, z);
                addStatic(g);
            }
            if (rightW > 0) {
                Box mesh = new Box(rightW * .5f, h * .5f, thickness);
                Geometry g = makeWallGeometry("WallSlice_R", mesh);
                g.setLocalTranslation(r.x() + r.w() - rightW * .5f, y0 + h * .5f, z);
                addStatic(g);
            }

        } else {
            float x = (dir == Direction.WEST) ? r.x() : r.x() + r.w();
            float backD = center - halfHole - r.z();
            float frontD = (r.z() + r.h()) - (center + halfHole);

            if (backD > 0) {
                Box mesh = new Box(thickness, h * .5f, backD * .5f);
                Geometry g = makeWallGeometry("WallSlice_B", mesh);
                g.setLocalTranslation(x, y0 + h * .5f, r.z() + backD * .5f);
                addStatic(g);
            }
            if (frontD > 0) {
                Box mesh = new Box(thickness, h * .5f, frontD * .5f);
                Geometry g = makeWallGeometry("WallSlice_F", mesh);
                g.setLocalTranslation(x, y0 + h * .5f, r.z() + r.h() - frontD * .5f);
                addStatic(g);
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


    private Geometry makeWallGeometry(String name, Mesh mesh) {
        Geometry g = new Geometry(name, mesh);
        g.setMaterial(wallMat);
        g.setShadowMode(ShadowMode.CastAndReceive);
        return g;
    }


    private void addStatic(Geometry g) {
        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
