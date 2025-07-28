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
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import museumhell.engine.world.levelgen.Direction;
import museumhell.engine.world.levelgen.Room;
import museumhell.utils.AssetLoader;

import java.util.List;

import static museumhell.engine.world.levelgen.Direction.*;

public class WallBuilder {

    private static final int DOOR_MIN_OVERLAP = 3;
    private static final float WALL_T = 0.33f;

    private final Node root;
    private final PhysicsSpace space;
    private final Material wallMat;
    private final Spatial wallModel, wall2Model;

    private final float wallLength, wallHeight, wallThickness;
    private final float wall2Length, wall2Height, wall2Thickness;

    private final Quaternion rotNS = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);
    private final Quaternion rotEW = new Quaternion();

    public WallBuilder(AssetManager assetManager, Node root, PhysicsSpace space, AssetLoader assetLoader) {
        this.root = root;
        this.space = space;

        this.wallModel = assetLoader.get("wall1");
        this.wall2Model = assetLoader.get("wall2");

        wallModel.updateGeometricState();
        BoundingBox bb1 = (BoundingBox) wallModel.getWorldBound();
        wallLength = bb1.getXExtent() * 2f;
        wallHeight = bb1.getYExtent() * 2f;
        wallThickness = bb1.getZExtent() * 2f;

        wall2Model.updateGeometricState();
        BoundingBox bb2 = (BoundingBox) wall2Model.getWorldBound();
        wall2Length = bb2.getXExtent() * 2f;
        wall2Height = bb2.getYExtent() * 2f;
        wall2Thickness = bb2.getZExtent() * 2f;

        wallMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        wallMat.setBoolean("UseMaterialColors", true);
        wallMat.setColor("Ambient", new ColorRGBA(0.05f, 0.05f, 0.05f, 1f));
        wallMat.setColor("Diffuse", new ColorRGBA(0.8f, 0.8f, 0.8f, 1f));
        wallMat.setColor("Specular", ColorRGBA.Black);
        wallMat.setFloat("Shininess", 1);
        wallMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
    }

    public void buildSolid(Room r, Direction dir, float y0, float h) {
        float length = (dir == NORTH || dir == SOUTH) ? r.w() : r.h();
        float sx = (dir == NORTH || dir == SOUTH) ? length : WALL_T;
        float sz = (dir == NORTH || dir == SOUTH) ? WALL_T : length;

        Spatial wall = wallModel.clone();
        if (dir == NORTH || dir == SOUTH) {
            wall.setLocalRotation(rotNS);
            wall.setLocalScale(sz / wallLength, h / wallHeight, sx / wallThickness);
        } else {
            wall.setLocalRotation(rotEW);
            wall.setLocalScale(sx / wallLength, h / wallHeight, sz / wallThickness);
        }

        wall.setShadowMode(ShadowMode.CastAndReceive);

        float halfThick = (dir == NORTH || dir == SOUTH) ? sz * 0.5f : sx * 0.5f;
        float tx, tz;
        float cx = r.x() + r.w() * 0.5f;
        float cz = r.z() + r.h() * 0.5f;

        tz = switch (dir) {
            case NORTH -> {
                tx = cx;
                yield r.z() - halfThick;
            }
            case SOUTH -> {
                tx = cx;
                yield r.z() + r.h() + halfThick;
            }
            case WEST -> {
                tx = r.x() - halfThick;
                yield cz;
            }
            case EAST -> {
                tx = r.x() + r.w() + halfThick;
                yield cz;
            }
        };
        wall.setLocalTranslation(tx, y0, tz);

        addStaticModel(wall);
    }

    public void buildOpening(Room r, Direction dir, float y0, float h, List<Room> rooms, float holeWidth, float thickness) {
        float[] ov = getOverlapRange(r, rooms, dir);
        float center = (ov[0] + ov[1]) * 0.5f;
        float halfHole = holeWidth * 0.5f;

        if (dir == NORTH || dir == SOUTH) {
            float zEdge = (dir == NORTH) ? r.z() : r.z() + r.h();
            float leftW = center - halfHole - r.x();
            float rightW = (r.x() + r.w()) - (center + halfHole);
            float halfT = thickness * 0.5f;
            float tz = (dir == NORTH) ? zEdge - halfT : zEdge + halfT;

            if (leftW > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rotNS);
                slice.setLocalScale(thickness / wall2Length, h / wall2Height, leftW / wall2Thickness);
                slice.setLocalTranslation(r.x() + leftW * 0.5f, y0, tz);
                addStaticModel(slice);
            }
            if (rightW > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rotNS);
                slice.setLocalScale(thickness / wall2Length, h / wall2Height, rightW / wall2Thickness);
                slice.setLocalTranslation(r.x() + r.w() - rightW * 0.5f, y0, tz);
                addStaticModel(slice);
            }

        } else {
            float xEdge = (dir == WEST) ? r.x() : r.x() + r.w();
            float backD = center - halfHole - r.z();
            float frontD = (r.z() + r.h()) - (center + halfHole);
            float halfT = thickness * 0.5f;
            float tx = (dir == WEST) ? xEdge - halfT : xEdge + halfT;

            if (backD > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rotEW);
                slice.setLocalScale(thickness / wall2Length, h / wall2Height, backD / wall2Thickness);
                slice.setLocalTranslation(tx, y0, r.z() + backD * 0.5f);
                addStaticModel(slice);
            }
            if (frontD > 0) {
                Spatial slice = wall2Model.clone();
                slice.setLocalRotation(rotEW);
                slice.setLocalScale(thickness / wall2Length, h / wall2Height, frontD / wall2Thickness);
                slice.setLocalTranslation(tx, y0, r.z() + r.h() - frontD * 0.5f);
                addStaticModel(slice);
            }
        }
    }

    public float[] getOverlapRange(Room r, List<Room> rooms, Direction dir) {
        if (dir == NORTH || dir == SOUTH) {
            int a1 = r.x(), a2 = r.x() + r.w();
            int zEdge = (dir == NORTH) ? r.z() : r.z() + r.h();
            for (Room o : rooms) {
                boolean match = (dir == NORTH && o.z() + o.h() == zEdge) || (dir == SOUTH && o.z() == zEdge);
                if (match) {
                    int b1 = o.x(), b2 = o.x() + o.w();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= DOOR_MIN_OVERLAP) return new float[]{Math.max(a1, b1), Math.min(a2, b2)};
                }
            }
        } else {
            int a1 = r.z(), a2 = r.z() + r.h();
            int xEdge = (dir == WEST) ? r.x() : r.x() + r.w();
            for (Room o : rooms) {
                boolean match = (dir == WEST && o.x() + o.w() == xEdge) || (dir == EAST && o.x() == xEdge);
                if (match) {
                    int b1 = o.z(), b2 = o.z() + o.h();
                    int overlap = Math.min(a2, b2) - Math.max(a1, b1);
                    if (overlap >= DOOR_MIN_OVERLAP) return new float[]{Math.max(a1, b1), Math.min(a2, b2)};
                }
            }
        }
        throw new IllegalStateException("No vecino válido para dir=" + dir + " en sala " + r);
    }

    private void addStaticModel(Spatial s) {
        root.attachChild(s);
        var body = new RigidBodyControl(CollisionShapeFactory.createMeshShape(s), 0);
        s.addControl(body);
        space.add(body);
    }
}
