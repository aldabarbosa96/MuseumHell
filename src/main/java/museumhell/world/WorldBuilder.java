package museumhell.world;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;

public class WorldBuilder {
    private AssetManager assetManager;
    private Node rootNode;
    private PhysicsSpace physicsSpace;

    public WorldBuilder(AssetManager am, Node rootNode, PhysicsSpace ps) {
        this.assetManager = am;
        this.rootNode = rootNode;
        this.physicsSpace = ps;
    }

    public void buildRoom(float w, float l, float h) {
        // Suelo
        Box floor = new Box(w/2, 0.1f, l/2);
        Geometry floorG = makeGeom("Floor", floor, ColorRGBA.LightGray);
        floorG.setLocalTranslation(0, -0.1f, 0);
        addStatic(floorG);

        // Techo
        Geometry ceiling = makeGeom("Ceiling", floor, ColorRGBA.Blue);
        ceiling.setLocalTranslation(0, h, 0);
        addStatic(ceiling);

        // Paredes (norte, sur, este, oeste)
        Box wallMesh = new Box(w/2, h/2, 0.1f);
        Geometry north = makeGeom("NorthWall", wallMesh, ColorRGBA.Gray);
        north.setLocalTranslation(0, h/2, -l/2);
        addStatic(north);
        Geometry south = north.clone(false);
        south.setLocalTranslation(0, h/2,  l/2);
        addStatic(south);

        Box wallMesh2 = new Box(0.1f, h/2, l/2);
        Geometry west = makeGeom("WestWall", wallMesh2, ColorRGBA.DarkGray);
        west.setLocalTranslation(-w/2, h/2, 0);
        addStatic(west);
        Geometry east = west.clone(false);
        east.setLocalTranslation( w/2, h/2, 0);
        addStatic(east);

        // Una vitrina central
        Box vitrina = new Box(1, 1, 0.5f);
        Geometry vitG = makeGeom("Vitrina", vitrina, ColorRGBA.Red);
        vitG.setLocalTranslation(0, 1, 0);
        addStatic(vitG);
    }

    private Geometry makeGeom(String name, Mesh mesh, ColorRGBA color) {
        Geometry g = new Geometry(name, mesh);
        Material m = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        g.setMaterial(m);
        return g;
    }

    private void addStatic(Geometry geo) {
        RigidBodyControl body = new RigidBodyControl(0);
        geo.addControl(body);
        rootNode.attachChild(geo);
        physicsSpace.add(body);
    }

}
