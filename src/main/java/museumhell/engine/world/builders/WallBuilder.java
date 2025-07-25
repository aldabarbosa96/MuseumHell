package museumhell.engine.world.builders;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.levelgen.Room;

import java.util.List;

public class WallBuilder {
    private static final int DOOR_MIN_OVERLAP = 3;
    private static final float WALL_T = 0.33f;
    private final AssetManager assetManager;
    private final Node root;
    private final PhysicsSpace space;

    public WallBuilder(AssetManager assetManager, Node root, PhysicsSpace space) {
        this.assetManager = assetManager;
        this.root = root;
        this.space = space;
    }


    public void buildSolid(Room r, Direction dir, float y0, float h) {
        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            float z = dir == Direction.NORTH ? r.z() : r.z() + r.h();
            Geometry g = makeGeometry("Wall" + dir, new Box(r.w() * .5f, h * .5f, WALL_T), ColorRGBA.White);
            g.setLocalTranslation(r.x() + r.w() * .5f, y0 + h * .5f, z);
            addStatic(g);
        } else {
            float x = dir == Direction.WEST ? r.x() : r.x() + r.w();
            Geometry g = makeGeometry("Wall" + dir, new Box(WALL_T, h * .5f, r.h() * .5f), ColorRGBA.White);
            g.setLocalTranslation(x, y0 + h * .5f, r.z() + r.h() * .5f);
            addStatic(g);
        }
    }

    public void buildOpening(Room r, Direction dir, float y0, float h, List<Room> rooms, float holeWidth, float thickness) {
        float[] ov = getOverlapRange(r, rooms, dir);
        float center = (ov[0] + ov[1]) * .5f;
        float halfHole = holeWidth * .5f;

        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            float z = dir == Direction.NORTH ? r.z() : r.z() + r.h();
            float leftW = center - halfHole - r.x();
            float rightW = (r.x() + r.w()) - (center + halfHole);
            if (leftW > 0) addWallSlice(r.x() + leftW * .5f, y0 + h * .5f, z, leftW * .5f, h * .5f, thickness);
            if (rightW > 0)
                addWallSlice(r.x() + r.w() - rightW * .5f, y0 + h * .5f, z, rightW * .5f, h * .5f, thickness);
        } else {
            float x = dir == Direction.WEST ? r.x() : r.x() + r.w();
            float backD = center - halfHole - r.z();
            float frontD = (r.z() + r.h()) - (center + halfHole);
            if (backD > 0) addWallSlice(x, y0 + h * .5f, r.z() + backD * .5f, thickness, h * .5f, backD * .5f);
            if (frontD > 0)
                addWallSlice(x, y0 + h * .5f, r.z() + r.h() - frontD * .5f, thickness, h * .5f, frontD * .5f);
        }
    }

    public float[] getOverlapRange(Room r, List<Room> rooms, Direction dir) {
        int a1, a2, b1, b2;
        if (dir == Direction.NORTH || dir == Direction.SOUTH) {
            a1 = r.x();
            a2 = r.x() + r.w();
            int zEdge = dir == Direction.NORTH ? r.z() : r.z() + r.h();
            for (Room o : rooms) {
                if ((dir == Direction.NORTH && o.z() + o.h() == zEdge) || (dir == Direction.SOUTH && o.z() == zEdge)) {
                    b1 = o.x();
                    b2 = o.x() + o.w();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= DOOR_MIN_OVERLAP) {
                        float start = Math.max(a1, b1);
                        float end = Math.min(a2, b2);
                        return new float[]{start, end};
                    }
                }
            }
        } else {
            a1 = r.z();
            a2 = r.z() + r.h();
            int xEdge = dir == Direction.WEST ? r.x() : r.x() + r.w();
            for (Room o : rooms) {
                if ((dir == Direction.WEST && o.x() + o.w() == xEdge) || (dir == Direction.EAST && o.x() == xEdge)) {
                    b1 = o.z();
                    b2 = o.z() + o.h();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= DOOR_MIN_OVERLAP) {
                        float start = Math.max(a1, b1);
                        float end = Math.min(a2, b2);
                        return new float[]{start, end};
                    }
                }
            }
        }
        throw new IllegalStateException("No hay vecino válido para dir=" + dir + " en sala " + r);
    }


    private void addWallSlice(float x, float y, float z, float sx, float sy, float sz) {
        Box mesh = new Box(sx, sy, sz);
        mesh.scaleTextureCoordinates(new Vector2f(4, 4));
        Geometry g = makeGeometry("WallSlice", mesh, ColorRGBA.Yellow);
        g.setLocalTranslation(x, y, z);
        addStatic(g);
    }


    public Geometry makeGeometry(String name, Mesh mesh, ColorRGBA base) {
        Geometry g = new Geometry(name, mesh);
        Material m = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        m.setBoolean("UseMaterialColors", true);
        m.setColor("Ambient", ColorRGBA.LightGray);
        m.setColor("Diffuse", ColorRGBA.LightGray);
        m.setColor("Specular", ColorRGBA.LightGray);
        m.setFloat("Shininess", 1);
        m.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        g.setMaterial(m);
        return g;
    }

    public void addStatic(Geometry g) {
        g.addControl(new RigidBodyControl(0));
        root.attachChild(g);
        space.add(g);
    }
}
