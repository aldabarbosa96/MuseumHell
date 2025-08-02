package museumhell.game.ai;


import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import museumhell.engine.world.levelgen.Room;
import museumhell.engine.world.levelgen.Door;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.player.PlayerController;
import museumhell.utils.media.AssetLoader;

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
    private final Spatial model;
    private final WorldBuilder world;

    private static final float DETECT_RANGE = 15f;
    private static final float COS_HALF_FOV = FastMath.cos(FastMath.DEG_TO_RAD * 22.5f);
    private static final float WANDER_SPEED = 0.075f;
    private static final float CHASE_SPEED = 0.1f;
    private static final float POINT_TOL = 0.25f;

    private final List<Vector3f> patrolPoints = new ArrayList<>();
    private int patrolIndex = 0;
    private boolean patrolFinished = false;
    private final Vector3f lastDir = new Vector3f(1, 0, 0);
    private final Vector3f lastPos = new Vector3f();
    private float stuckTimer = 0f;
    private static final float STUCK_EPS = 0.1f;
    private static final float STUCK_THR = 1f;
    private final Set<Door> openingDoors = new HashSet<>();
    private final Random rnd = new Random();
    private boolean avoiding = false;
    private final Vector3f avoidOrigin = new Vector3f();
    private static final float AVOID_DISTANCE = 1f;
    private final Quaternion[] rotSamples = new Quaternion[13];
    private final Vector3f candDir = new Vector3f();
    private final Vector3f bestDir = new Vector3f();
    private final Vector3f scratchVec = new Vector3f();
    private final Vector3f scratchEnd = new Vector3f();
    private final Quaternion lookQuat = new Quaternion();
    private final Quaternion currentQuat = new Quaternion();
    private final Quaternion desiredQuat = new Quaternion();
    private final Quaternion offsetQuat = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);

    public Enemy(AssetLoader am, PhysicsSpace space, PlayerController player, WorldBuilder world, Room room, float baseY, Node rootNode) {
        super("Enemy");
        this.space = space;
        this.player = player;
        this.world = world;

        // Precompute ±90° in 15° steps around Y
        for (int i = -6; i <= 6; i++) {
            rotSamples[i + 6] = new Quaternion().fromAngleAxis(i * 15f * FastMath.DEG_TO_RAD, Vector3f.UNIT_Y);
        }

        // Load and orient model
        model = am.get("wander1");
        model.setLocalScale(0.55f);
        model.rotate(0, -FastMath.HALF_PI, 0);
        model.setLocalTranslation(0, -1.75f, 0);
        attachChild(model);

        // Physics control
        control = new CharacterControl(new CapsuleCollisionShape(1f, 1f), .05f);
        control.setGravity(30);
        control.setFallSpeed(20);

        // Spawn position
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
        patrolFinished = false;
    }

    public boolean isPatrolFinished() {
        return patrolFinished;
    }

    public void update(float tpf) {
        Vector3f pos = control.getPhysicsLocation();

        // 1) Open nearby doors
        Door d = world.nearestDoor(pos, 3.5f);
        if (d != null) {
            if (!openingDoors.contains(d)) {
                world.tryUseDoor(pos);
                openingDoors.add(d);
            }
            if (!d.isOpen()) return;
            openingDoors.remove(d);
        }

        // 2) State transition
        boolean seesPlayer = canSee(pos);
        if (seesPlayer) state = State.CHASE;
        else if (state == State.CHASE) state = State.WANDER;

        // 3) Behavior
        if (state == State.CHASE) chase(pos);
        else wander(pos);

        // 4) Obstacle avoidance & stuck detection
        avoidObstacles(pos);
        detectStuck(pos, tpf);

        // 5) Apply physics location
        setLocalTranslation(control.getPhysicsLocation());

        // 6) Smooth model rotation
        if (lastDir.lengthSquared() > 0f) {
            lookQuat.lookAt(lastDir.normalizeLocal(), Vector3f.UNIT_Y);
            desiredQuat.set(lookQuat).multLocal(offsetQuat);
            currentQuat.set(model.getLocalRotation());
            currentQuat.slerp(desiredQuat, tpf * 5f);
            model.setLocalRotation(currentQuat);
        }
    }

    private void chase(Vector3f p) {
        Vector3f dir = player.getLocation().subtract(p).setY(0).normalizeLocal();
        lastDir.set(dir);
        control.setWalkDirection(dir.mult(CHASE_SPEED));
    }

    private void wander(Vector3f p) {
        if (patrolFinished) return;

        if (!patrolPoints.isEmpty()) {
            if (patrolIndex >= patrolPoints.size()) {
                patrolFinished = true;
                return;
            }
            Vector3f tgt = patrolPoints.get(patrolIndex);
            Vector3f d = tgt.subtract(p).setY(0);
            if (d.length() < POINT_TOL) {
                patrolIndex++;
                if (patrolIndex >= patrolPoints.size()) {
                    patrolFinished = true;
                    return;
                }
                d = patrolPoints.get(patrolIndex).subtract(p).setY(0);
            }
            Vector3f dir = d.normalizeLocal();
            lastDir.set(dir);
            control.setWalkDirection(dir.mult(WANDER_SPEED));
        } else {
            classicWander(p);
        }
    }

    private void classicWander(Vector3f p) {
        Vector3f d = lastPos.subtract(p).setY(0);
        if (d.lengthSquared() < 0.25f) {
            Vector3f r = new Vector3f(rnd.nextFloat() * 2 - 1, 0, rnd.nextFloat() * 2 - 1).normalizeLocal();
            lastDir.set(r);
            control.setWalkDirection(r.mult(WANDER_SPEED));
        } else {
            control.setWalkDirection(lastDir.mult(WANDER_SPEED));
        }
    }

    private void avoidObstacles(Vector3f p) {
        if (avoiding) {
            if (avoidOrigin.distance(p) > AVOID_DISTANCE) {
                avoiding = false;
            } else {
                return;
            }
        }

        Vector3f dirNorm = lastDir.normalizeLocal();
        float probeLen = 1.5f;

        if (measureClearance(p, dirNorm, probeLen) >= probeLen) {
            return;
        }

        float bestClear = -1f;
        for (Quaternion rot : rotSamples) {
            rot.mult(dirNorm, candDir);
            float cl = measureClearance(p, candDir, probeLen);
            if (cl > bestClear) {
                bestClear = cl;
                bestDir.set(candDir);
            }
        }

        lastDir.set(bestDir);
        float speed = (state == State.CHASE) ? CHASE_SPEED : WANDER_SPEED;
        control.setWalkDirection(bestDir.mult(speed));
        avoiding = true;
        avoidOrigin.set(p);
        stuckTimer = 0f;
        lastPos.set(p);
    }

    private float measureClearance(Vector3f origin, Vector3f dir, float maxDist) {
        // Compute end point = origin + dir * maxDist
        scratchEnd.set(dir).multLocal(maxDist).addLocal(origin);
        List<PhysicsRayTestResult> results = space.rayTest(origin, scratchEnd);

        float minFrac = 1f;
        for (PhysicsRayTestResult rr : results) {
            if (rr.getHitFraction() < minFrac && !(rr.getCollisionObject() instanceof CharacterControl)) {
                minFrac = rr.getHitFraction();
            }
        }
        return minFrac * maxDist;
    }

    private void detectStuck(Vector3f p, float tpf) {
        if (lastPos.distance(p) < STUCK_EPS) {
            stuckTimer += tpf;
            if (stuckTimer > STUCK_THR && !patrolFinished) {
                patrolIndex++;
                stuckTimer = 0f;
                lastPos.set(p);
                if (patrolIndex < patrolPoints.size()) {
                    Vector3f nextTgt = patrolPoints.get(patrolIndex);
                    Vector3f d = nextTgt.subtract(p).setY(0).normalizeLocal();
                    lastDir.set(d);
                    control.setWalkDirection(d.mult(WANDER_SPEED));
                } else {
                    patrolFinished = true;
                }
            }
        } else {
            stuckTimer = 0f;
        }
        lastPos.set(p);
    }

    private boolean canSee(Vector3f p) {
        Vector3f tp = player.getLocation();
        scratchVec.set(tp).subtractLocal(p);

        if (scratchVec.length() > DETECT_RANGE) return false;
        if (lastDir.dot(scratchVec.normalizeLocal()) < COS_HALF_FOV) return false;

        List<PhysicsRayTestResult> results = space.rayTest(p, tp);
        PhysicsCollisionObject closest = null;
        float minFrac = 1f;
        for (PhysicsRayTestResult rr : results) {
            if (rr.getHitFraction() < minFrac) {
                closest = rr.getCollisionObject();
                minFrac = rr.getHitFraction();
            }
        }
        return closest == player.getCharacterControl();
    }
}
