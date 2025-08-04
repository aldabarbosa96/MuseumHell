package museumhell.game.ai;


import com.jme3.anim.AnimComposer;
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
import museumhell.utils.media.AudioLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

public class Enemy extends Node {
    private enum State {WANDER, CHASE}

    private State state = State.WANDER;

    private final CharacterControl control;
    private final PhysicsSpace space;
    private final PlayerController player;
    private final Spatial model;
    private final WorldBuilder world;
    private AnimComposer composer;
    private String lastAnim = "";

    private final Supplier<List<Vector3f>> requestNewPath;
    private Room currentRoomRef;

    public Room currentRoom() {
        return currentRoomRef;
    }

    private static final float DETECT_RANGE = 15f;
    private static final float COS_HALF_FOV = FastMath.cos(FastMath.DEG_TO_RAD * 22.5f);
    private static final float WANDER_SPEED = 0.05f;
    private static final float CHASE_SPEED = 0.125f;
    private static final float POINT_TOL = 0.25f;

    private final List<Vector3f> patrolPoints = new ArrayList<>();
    private int patrolIndex = 0;
    private boolean patrolFinished = false;
    private final Vector3f lastDir = new Vector3f(1, 0, 0);
    private final Vector3f lastPos = new Vector3f();
    private float stuckTimer = 0f;
    private static final float STUCK_EPS = 0.1f;
    private final Set<Door> openingDoors = new HashSet<>();
    private final Random rnd = new Random();
    private boolean avoiding = false;
    private int avoidDirSign = 0;
    private final Vector3f avoidOrigin = new Vector3f();
    private static final float AVOID_DISTANCE = 1f;
    private Quaternion[] rotSamples;
    private final Vector3f candDir = new Vector3f();
    private final Vector3f scratchVec = new Vector3f();
    private final Vector3f scratchEnd = new Vector3f();

    private final AudioLoader audio;
    private float stepTime = 0f;
    private int lastStepCount = 0;
    private State prevState = null;
    private static final float STEP_INTERVAL = 0.92f;
    private static final float CHASE_STEP_INTERVAL = 0.33f;

    private final Quaternion lookQuat = new Quaternion();
    private final Quaternion currentQuat = new Quaternion();
    private final Quaternion desiredQuat = new Quaternion();
    private final Quaternion offsetQuat = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);

    public Enemy(AssetLoader am, PhysicsSpace space, PlayerController player, WorldBuilder world, Room room, float baseY, Node rootNode, AudioLoader audio, Supplier<List<Vector3f>> pathSupplier) {
        super("Enemy");
        this.space = space;
        this.player = player;
        this.world = world;
        this.audio = audio;
        this.requestNewPath = pathSupplier;

        int samples = 16;
        rotSamples = new Quaternion[samples];
        for (int i = 0; i < samples; i++) {
            float angle = FastMath.TWO_PI * i / samples;
            rotSamples[i] = new Quaternion().fromAngleAxis(angle, Vector3f.UNIT_Y);
        }

        model = am.get("wander1Animated");
        model.setLocalScale(0.525f);
        model.rotate(0, -FastMath.HALF_PI, 0);
        model.setLocalTranslation(0, -1.64f, 0);
        model.depthFirstTraversal(spat -> {
            if (composer == null) {
                composer = spat.getControl(AnimComposer.class);
            }
        });
        if (composer == null) {
            throw new IllegalStateException("El modelo no tiene AnimComposer");
        }

        attachChild(model);

        control = new CharacterControl(new CapsuleCollisionShape(1f, 1f), .05f);
        control.setGravity(30);
        control.setFallSpeed(20);
        addControl(control);
        space.add(control);
        rootNode.attachChild(this);

        Vector3f spawn = room.center3f(baseY + 0.5f);
        setLocalTranslation(spawn);
        control.setPhysicsLocation(spawn);
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
        currentRoomRef = world.whichRoom(control.getPhysicsLocation());
        Vector3f pos = control.getPhysicsLocation();

        // 1) Gestión de puertas
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
        State newState = seesPlayer ? State.CHASE : (state == State.CHASE ? State.WANDER : state);

        if (newState != state) {
            stepTime = 0f;
            lastStepCount = 0;
        }
        state = newState;

        if (state != prevState) {
            if (state == State.CHASE) {
                composer.setGlobalSpeed(3f);
            } else {
                composer.setGlobalSpeed(1f);
            }
            prevState = state;
        }

        // 3) Comportamiento
        if (state == State.CHASE) chase(pos);
        else wander(pos);

        // 4) Animación de caminar + audio de pasos:
        playAnimationIfChanged("ArmatureAction");

        if ("ArmatureAction".equals(lastAnim)) {
            stepTime += tpf;
            float interval = (state == State.CHASE ? CHASE_STEP_INTERVAL : STEP_INTERVAL);
            int stepCount = (int) (stepTime / interval);
            if (stepCount > lastStepCount) {
                lastStepCount = stepCount;
                float volume = getVolume();
                float dist3d = pos.distance(player.getLocation());
                String soundName = dist3d <= 20f ? "monsterSteps1" : "monsterSteps2";
                audio.playWithVolume(soundName, volume);
            }
        } else {
            stepTime = 0f;
            lastStepCount = 0;
        }

        // 5) Avoidance & stuck detection
        avoidObstacles(pos);
        detectStuck(pos, tpf);

        // 6) Posicionamiento y rotación
        setLocalTranslation(control.getPhysicsLocation());

        if (lastDir.lengthSquared() > 0f) {
            lookQuat.lookAt(lastDir.normalizeLocal(), Vector3f.UNIT_Y);
            desiredQuat.set(lookQuat).multLocal(offsetQuat);
            currentQuat.set(model.getLocalRotation());
            currentQuat.slerp(desiredQuat, tpf * 5f);
            model.setLocalRotation(currentQuat);
        }
    }

    private float getVolume() {
        Vector3f e = this.getWorldTranslation();
        Vector3f j = player.getLocation();

        float dx = e.x - j.x;
        float dz = e.z - j.z;
        float horizontalDist = FastMath.sqrt(dx * dx + dz * dz);

        float dy = Math.abs(e.y - j.y);
        float verticalWeight = 2f; // penalizador de altura para mayor realismo
        float weightedDist = FastMath.sqrt(horizontalDist * horizontalDist + (verticalWeight * dy) * (verticalWeight * dy));

        float fullVolUntil = 13f;
        float audibleFrom = 66f;

        float volume;
        if (weightedDist <= fullVolUntil) {
            volume = 1f;
        } else if (weightedDist >= audibleFrom) {
            volume = 0f;
        } else {
            volume = 1f - (weightedDist - fullVolUntil) / (audibleFrom - fullVolUntil);
        }
        return volume;
    }


    private void chase(Vector3f p) {
        Vector3f dir = player.getLocation().subtract(p).setY(0).normalizeLocal();
        lastDir.set(dir);
        control.setWalkDirection(dir.mult(CHASE_SPEED));
    }

    private void wander(Vector3f p) {
        if (patrolPoints.isEmpty()) {
            setPatrolPoints(requestNewPath.get());
            return;
        }

        if (patrolIndex >= patrolPoints.size()) {
            // ruta agotada ⟶ conseguir otra
            setPatrolPoints(requestNewPath.get());
            return;
        }

        Vector3f tgt = patrolPoints.get(patrolIndex);
        Vector3f d = tgt.subtract(p).setY(0);

        if (d.length() < POINT_TOL) {
            patrolIndex++;
            return;
        }

        Vector3f dir = d.normalizeLocal();
        lastDir.set(dir);
        control.setWalkDirection(dir.mult(WANDER_SPEED));
    }

    private void avoidObstacles(Vector3f p) {
        if (avoiding) {
            if (avoidOrigin.distance(p) > AVOID_DISTANCE) {
                avoiding = false;
                avoidDirSign = 0;
            } else {
                return;
            }
        }
        Vector3f dirNorm = lastDir.normalizeLocal();
        float probeLen = 1.5f;

        if (measureClearance(p, dirNorm, probeLen) >= probeLen) {
            avoidDirSign = 0;
            return;
        }

        // 1) busco la mejor muestra en 360°
        float bestClear = -1f;
        Vector3f bestDir = new Vector3f();
        for (Quaternion rot : rotSamples) {
            rot.mult(dirNorm, candDir);
            float clear = measureClearance(p, candDir, probeLen);
            if (clear > bestClear) {
                bestClear = clear;
                bestDir.set(candDir);
            }
        }

        // 2) si ninguna muestra queda tan libre como medio probeLen, hago reverse 180°
        if (bestClear < probeLen * 0.5f) {
            lastDir.set(dirNorm.negate());
        } else {
            lastDir.set(bestDir);
        }

        // 3) aplico la dirección elegida
        float speed = (state == State.CHASE) ? CHASE_SPEED : WANDER_SPEED;
        control.setWalkDirection(lastDir.mult(speed));
        avoiding = true;
        avoidOrigin.set(p);
        stuckTimer = 0f;
    }

    private float measureClearance(Vector3f origin, Vector3f dir, float maxDist) {
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

    private void detectStuck(Vector3f pos, float tpf) {

        // ¿se ha movido lo suficiente desde el último frame?
        if (lastPos.distanceSquared(pos) < STUCK_EPS * STUCK_EPS) {
            stuckTimer += tpf;
        } else {
            stuckTimer = 0f;
            lastPos.set(pos);
            return;
        }

        // Si lleva más de 0.8 s prácticamente quieto → nueva ruta
        if (stuckTimer > 0.8f) {
            setPatrolPoints(requestNewPath.get());
            stuckTimer = 0f;
            lastPos.set(pos);
        }
    }


    private boolean canSee(Vector3f enemyPos) {
        // 1) Vector desde el enemigo hasta el jugador
        Vector3f playerPos = player.getLocation();
        scratchVec.set(playerPos).subtractLocal(enemyPos);

        // 2) Comprobación de rango usando distancia al cuadrado (sin sqrt)
        float dist2 = scratchVec.lengthSquared();
        if (dist2 > DETECT_RANGE * DETECT_RANGE) {
            return false;
        }

        // 3) Campo de visión: comparamos el coseno directamente
        scratchVec.normalizeLocal();
        float cosAngle = lastDir.dot(scratchVec);
        if (cosAngle < COS_HALF_FOV) {
            return false;
        }

        // 4) Ray-cast hasta la posición exacta del jugador
        List<PhysicsRayTestResult> results = space.rayTest(enemyPos, playerPos);

        // 5) Buscamos la intersección más cercana que NO sea el propio CharacterControl del enemigo
        PhysicsCollisionObject closest = getCollisionObject(results);

        // 6) Vemos al jugador si lo primero que choca es SU CharacterControl
        return closest == player.getCharacterControl();
    }

    private PhysicsCollisionObject getCollisionObject(List<PhysicsRayTestResult> results) {
        PhysicsCollisionObject closest = null;
        float minFrac = Float.MAX_VALUE;
        for (PhysicsRayTestResult rr : results) {
            float frac = rr.getHitFraction();
            PhysicsCollisionObject obj = rr.getCollisionObject();
            if (obj == this.control) {
                continue;
            }
            if (frac < minFrac) {
                minFrac = frac;
                closest = obj;
            }
        }
        return closest;
    }


    private void playAnimationIfChanged(String animName) {
        if (!animName.equals(lastAnim)) {
            composer.setCurrentAction(animName);
            lastAnim = animName;
        }
    }
}
