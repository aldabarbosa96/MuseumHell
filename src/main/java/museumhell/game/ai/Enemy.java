package museumhell.game.ai;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import museumhell.game.player.PlayerController;
import museumhell.engine.world.levelgen.Room;

import java.util.List;
import java.util.Random;

public class Enemy extends Node {
    private enum State { WANDER, CHASE }
    private State state = State.WANDER;

    private final CharacterControl control;
    private final PhysicsSpace space;
    private final PlayerController player;
    private final Room room;
    private final float floorBaseY;

    private Vector3f wanderTarget;
    private Vector3f lastDir = new Vector3f(1,0,0);
    private final Random rnd = new Random();

    // parámetros
    private final float wanderSpeed = 0.5f;
    private final float chaseSpeed  = 2f;
    private final float detectRange = 15f;
    private final float halfFov     = FastMath.DEG_TO_RAD * 22.5f;
    private final float cosHalfFov  = FastMath.cos(halfFov);

    public Enemy(AssetManager am, PhysicsSpace space, PlayerController player,
                 Room room, float floorBaseY, Node rootNode) {
        super("Enemy");
        this.space      = space;
        this.player     = player;
        this.room       = room;
        this.floorBaseY = floorBaseY;

        // 1) Género un cubo negro como placeholder
        Box b = new Box(1f,1f,1f);
        Geometry geom = new Geometry("GuardCube", b);
        Material mat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Black);
        geom.setMaterial(mat);
        attachChild(geom);

        // 2) Control de personaje para colisiones y movimiento
        CapsuleCollisionShape shape = new CapsuleCollisionShape(1f, 1f);
        control = new CharacterControl(shape, 0.05f);
        control.setGravity(30);
        control.setFallSpeed(20);
        // posición inicial: centro de la sala, a medio alto del cubo
        Vector3f start = room.center3f(floorBaseY + 0.5f);
        setLocalTranslation(start);
        control.setPhysicsLocation(start);
        addControl(control);
        space.add(control);

        // 3) primer objetivo de deambular
        pickNewWanderTarget();

        // 4) añadir al grafo
        rootNode.attachChild(this);
    }

    private void pickNewWanderTarget() {
        float margin = 1f;
        float x = rnd.nextFloat()*(room.w()-2*margin) + room.x()+margin;
        float z = rnd.nextFloat()*(room.h()-2*margin) + room.z()+margin;
        float y = floorBaseY + 0.5f;
        wanderTarget = new Vector3f(x, y, z);
    }

    public void update(float tpf) {
        Vector3f pos = control.getPhysicsLocation();

        // 1) ¿ve al jugador?
        if (canSeePlayer(pos)) {
            state = State.CHASE;
        }

        // 2) comportamiento según estado
        if (state == State.WANDER) {
            Vector3f dir = wanderTarget.subtract(pos).setY(0);
            if (dir.lengthSquared() < 0.25f) {
                pickNewWanderTarget();
            } else {
                dir.normalizeLocal();
                lastDir.set(dir);
                control.setWalkDirection(dir.mult(wanderSpeed));
            }
        } else {
            // CHASE: siempre a por el jugador
            Vector3f target = player.getLocation();
            Vector3f dir = target.subtract(pos).setY(0).normalizeLocal();
            lastDir.set(dir);
            control.setWalkDirection(dir.mult(chaseSpeed));
        }

        // 3) sincronizo visual con físico
        setLocalTranslation(control.getPhysicsLocation());
    }

    private boolean canSeePlayer(Vector3f pos) {
        Vector3f ppos = player.getLocation();
        Vector3f toPlayer = ppos.subtract(pos);
        if (toPlayer.length() > detectRange) return false;
        Vector3f dirNorm = toPlayer.normalizeLocal();
        if (lastDir.dot(dirNorm) < cosHalfFov) return false;

        // raycast para oclusión
        List<PhysicsRayTestResult> results = space.rayTest(pos, ppos);
        float closest = 1f;
        PhysicsCollisionObject hit = null;
        for (var r : results) {
            if (r.getHitFraction() < closest) {
                closest = r.getHitFraction();
                hit = r.getCollisionObject();
            }
        }
        return hit == player.getCharacterControl();
    }
}
