package museumhell.game.ai;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.Door;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.player.PlayerController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Enemy extends Node {
    private enum State {WANDER, CHASE}
    private State state = State.WANDER;

    private final CharacterControl control;
    private final PhysicsSpace space;
    private final PlayerController player;
    private final WorldBuilder world;

    private final float detectRange = 15f;
    private final float cosHalfFov = FastMath.cos(FastMath.DEG_TO_RAD * 22.5f);
    private final float wanderSpeed = 0.1f;
    private final float chaseSpeed = 0.2f;
    private final float pointTol = 0.25f;

    private final List<Vector3f> patrolPoints = new ArrayList<>();
    private int patrolIndex = 0;

    private Vector3f lastDir = new Vector3f(1, 0, 0);
    private Vector3f lastPos = new Vector3f();
    private float stuckTimer = 0f;
    private static final float STUCK_EPS = 0.02f;
    private static final float STUCK_THR = 1f;

    private final Set<Door> openingDoors = new HashSet<>();
    private final Random rnd = new Random();

    public Enemy(AssetManager am, PhysicsSpace space, PlayerController player, WorldBuilder world, Room room, float baseY, Node rootNode) {
        super("Enemy");
        this.space = space;
        this.player = player;
        this.world = world;

        Geometry g = new Geometry("GuardCube", new Box(1, 6, 1));
        Material m = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", ColorRGBA.Black);
        g.setMaterial(m);
        attachChild(g);

        control = new CharacterControl(new CapsuleCollisionShape(1f, 1f), .05f);
        control.setGravity(30);
        control.setFallSpeed(20);

        Vector3f spawn = room.center3f(baseY + 0.5f);
        setLocalTranslation(spawn);
        control.setPhysicsLocation(spawn);
        addControl(control);
        space.add(control);
        rootNode.attachChild(this);

        lastPos.set(spawn);
    }

    public void setPatrolPoints(List<Vector3f> pts) {
        patrolPoints.clear();
        patrolPoints.addAll(pts);
        patrolIndex = 0;
    }

    public void update(float tpf) {
        Vector3f pos = control.getPhysicsLocation();

        Door d = world.nearestDoor(pos, 3.5f);
        if (d != null) {
            // 2) Si aún no la hemos marcado para abrirla, solicitamos apertura
            if (!openingDoors.contains(d)) {
                world.tryUseDoor(pos);
                openingDoors.add(d);
            }
            // 3) Mientras no esté abierta, quedamos esperando
            if (!d.isOpen()) {
                return;
            }
            // 4) Una vez abierta podemos borrarla del set y continuar
            openingDoors.remove(d);
        }

        if (canSee(pos)) state = State.CHASE;

        if (state == State.CHASE) {
            chase(pos);
        } else {
            wander(pos);
        }

        avoidObstacles(pos);
        detectStuck(pos, tpf);
        setLocalTranslation(control.getPhysicsLocation());
    }

    private void chase(Vector3f p) {
        Vector3f dir = player.getLocation().subtract(p).setY(0).normalizeLocal();
        lastDir.set(dir);
        control.setWalkDirection(dir.mult(chaseSpeed));
    }

    private void wander(Vector3f p) {
        if (!patrolPoints.isEmpty()) {
            Vector3f tgt = patrolPoints.get(patrolIndex);
            Vector3f d = tgt.subtract(p).setY(0);
            if (d.length() < pointTol) {
                patrolIndex = (patrolIndex + 1) % patrolPoints.size();
                d = patrolPoints.get(patrolIndex).subtract(p).setY(0);
            }
            Vector3f dir = d.normalizeLocal();
            lastDir.set(dir);
            control.setWalkDirection(dir.mult(wanderSpeed));
        } else {
            classicWander(p);
        }
    }

    private void classicWander(Vector3f p) {
        Vector3f d = lastPos.subtract(p).setY(0);
        if (d.lengthSquared() < 0.25f) {
            Vector3f r = new Vector3f(rnd.nextFloat() * 2 - 1, 0, rnd.nextFloat() * 2 - 1).normalizeLocal();
            control.setWalkDirection(r.mult(wanderSpeed));
            lastDir.set(r);
        } else {
            control.setWalkDirection(lastDir.mult(wanderSpeed));
        }
    }

    private void avoidObstacles(Vector3f p) {
        Vector3f ahead = p.add(lastDir.normalizeLocal().mult(1f));
        for (PhysicsRayTestResult rr : space.rayTest(p, ahead)) {
            if (rr.getHitFraction() < 1f && !(rr.getCollisionObject() instanceof CharacterControl)) {
                Vector3f perp = new Vector3f(-lastDir.z, 0, lastDir.x).normalizeLocal();
                Vector3f dir = rnd.nextBoolean() ? perp : perp.negate();
                lastDir.set(dir);
                float speed = (state == State.CHASE) ? chaseSpeed : wanderSpeed;
                control.setWalkDirection(dir.mult(speed));
                return;
            }
        }
    }

    private void detectStuck(Vector3f p, float tpf) {
        if (lastPos.distance(p) < STUCK_EPS) {
            stuckTimer += tpf;
            if (stuckTimer > STUCK_THR && !patrolPoints.isEmpty()) {
                patrolIndex = (patrolIndex + 1) % patrolPoints.size();
                stuckTimer = 0;
            }
        } else {
            stuckTimer = 0;
        }
        lastPos.set(p);
    }

    private boolean canSee(Vector3f p) {
        Vector3f tp = player.getLocation();
        Vector3f d = tp.subtract(p);
        if (d.length() > detectRange) return false;
        if (lastDir.dot(d.normalizeLocal()) < cosHalfFov) return false;
        PhysicsCollisionObject hit = space.rayTest(p, tp).stream().min((a, b) -> Float.compare(a.getHitFraction(), b.getHitFraction())).map(PhysicsRayTestResult::getCollisionObject).orElse(null);
        return hit == player.getCharacterControl();
    }
}
