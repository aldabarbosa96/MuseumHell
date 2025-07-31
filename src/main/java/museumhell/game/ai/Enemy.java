// src/main/java/museumhell/game/ai/Enemy.java
package museumhell.game.ai;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.Door;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.player.PlayerController;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class Enemy extends Node {
    private enum State {WANDER, CHASE}

    private State state = State.WANDER;

    private final CharacterControl control;
    private final PhysicsSpace space;
    private final PlayerController player;
    private final WorldBuilder world;

    private final float detectRange = 15f;
    private final float halfFov = FastMath.DEG_TO_RAD * 22.5f;
    private final float cosHalfFov = FastMath.cos(halfFov);

    private final float wanderSpeed = 0.5f;
    private final float chaseSpeed = 2f;

    private final List<Vector3f> patrolPoints = new ArrayList<>();
    private int patrolIndex;
    private final float pointTolerance = 0.25f;

    private Vector3f lastDir = new Vector3f(1, 0, 0);
    private Vector3f lastPosition;
    private float stuckTimer;
    private static final float STUCK_THRESHOLD = 1f;
    private static final float MOVEMENT_EPSILON = 0.02f;

    private final Random rnd = new Random();

    public Enemy(AssetManager am, PhysicsSpace space, PlayerController player, WorldBuilder world, Room room, float floorBaseY, Node rootNode) {
        super("Enemy");
        this.space = space;
        this.player = player;
        this.world = world;

        Box box = new Box(1f, 1f, 1f);
        Geometry geom = new Geometry("GuardCube", box);
        Material mat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Black);
        geom.setMaterial(mat);
        attachChild(geom);

        CapsuleCollisionShape shape = new CapsuleCollisionShape(1f, 1f);
        control = new CharacterControl(shape, 0.05f);
        control.setGravity(30);
        control.setFallSpeed(20);

        Vector3f start = room.center3f(floorBaseY + 0.5f);
        setLocalTranslation(start);
        control.setPhysicsLocation(start);

        addControl(control);
        space.add(control);
        rootNode.attachChild(this);

        lastPosition = start.clone();
        patrolIndex = 0;
    }

    public void setPatrolPoints(List<Vector3f> points) {
        patrolPoints.clear();
        patrolPoints.addAll(points);
        patrolIndex = 0;
    }

    public void update(float tpf) {
        Vector3f pos = control.getPhysicsLocation();

        Door d = world.nearestDoor(pos, 1.5f);
        if (d != null && !d.isOpen()) {
            d.toggle();
            return;
        }

        if (canSee(pos)) {
            state = State.CHASE;
        }

        if (state == State.WANDER) {
            if (!patrolPoints.isEmpty()) {
                Vector3f target = patrolPoints.get(patrolIndex);
                Vector3f delta = target.subtract(pos).setY(0);

                if (delta.length() < pointTolerance) {
                    patrolIndex = (patrolIndex + 1) % patrolPoints.size();
                    delta = patrolPoints.get(patrolIndex).subtract(pos).setY(0);
                }

                Vector3f dir = delta.normalizeLocal();
                lastDir.set(dir);
                control.setWalkDirection(dir.mult(wanderSpeed));
            } else {
                classicWander(tpf);
            }
        } else {
            Vector3f dir = player.getLocation().subtract(pos).setY(0).normalizeLocal();
            lastDir.set(dir);
            control.setWalkDirection(dir.mult(chaseSpeed));
        }

        detectStuck(tpf);
        setLocalTranslation(control.getPhysicsLocation());
    }

    private void classicWander(float tpf) {
        Vector3f pos = control.getPhysicsLocation();
        Vector3f delta = lastPosition.subtract(pos).setY(0);
        if (delta.lengthSquared() < 0.25f) {
            Vector3f rndDir = new Vector3f(rnd.nextFloat() * 2 - 1, 0, rnd.nextFloat() * 2 - 1).normalizeLocal();
            control.setWalkDirection(rndDir.mult(wanderSpeed));
            lastDir.set(rndDir);
        } else {
            control.setWalkDirection(lastDir.mult(wanderSpeed));
        }
    }

    private void detectStuck(float tpf) {
        Vector3f current = control.getPhysicsLocation();
        if (lastPosition.distance(current) < MOVEMENT_EPSILON) {
            stuckTimer += tpf;
            if (stuckTimer > STUCK_THRESHOLD && !patrolPoints.isEmpty()) {
                patrolIndex = (patrolIndex + 1) % patrolPoints.size();
                stuckTimer = 0f;
            }
        } else {
            stuckTimer = 0f;
        }
        lastPosition.set(current);
    }

    private boolean canSee(Vector3f p) {
        Vector3f tp = player.getLocation(), d = tp.subtract(p);
        if (d.length() > detectRange) return false;
        if (lastDir.dot(d.normalizeLocal()) < cosHalfFov) return false;
        PhysicsCollisionObject hit = space.rayTest(p, tp).stream().min((a, b) -> Float.compare(a.getHitFraction(), b.getHitFraction())).map(PhysicsRayTestResult::getCollisionObject).orElse(null);
        return hit == player.getCharacterControl();
    }
}
