package museumhell.game.player;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import museumhell.engine.world.builders._6LightPlacer;
import museumhell.game.input.InputSystem;
import museumhell.ui.Hud;
import museumhell.utils.media.AudioLoader;

import java.util.Random;

import static museumhell.utils.ConstantManager.*;

public class MoveEffectState extends BaseAppState {
    private final PlayerController player;
    private final InputSystem input;
    private final AudioLoader audio;
    private final Hud hud;
    private final Camera cam;
    private final _6LightPlacer lightPlacer;
    private float bobTime = 0f, stepTime = 0f;
    private int lastStepCount = 0;
    private Vector3f smoothEyePos, smoothDirection;
    private final Random random = new Random();

    public MoveEffectState(PlayerController player, InputSystem input, AudioLoader audio, Hud hud, Camera cam, _6LightPlacer lightPlacer) {
        this.player = player;
        this.input = input;
        this.audio = audio;
        this.hud = hud;
        this.cam = cam;
        this.lightPlacer = lightPlacer;
    }

    @Override
    protected void initialize(Application app) {
        // Inicializamos smoothEyePos y smoothDirection
        Vector3f initEye = player.getLocation().add(0, 1f, 0).addLocal(cam.getDirection().mult(-0.25f));
        smoothEyePos = initEye.clone();
        smoothDirection = cam.getDirection().clone();
    }

    @Override
    public void update(float tpf) {
        // 1) Brújula
        Vector3f look = cam.getDirection().clone().setY(0).normalizeLocal();
        String compass = Math.abs(look.z) > Math.abs(look.x) ? (look.z < 0 ? "NORTE" : "SUR") : (look.x > 0 ? "ESTE" : "OESTE");
        hud.setDirection(compass);

        // 2) Bobbing
        float bobAmp = input.isSprinting() ? SPRINT_BOB_AMPLITUDE : BOB_AMPLITUDE;
        float bobSpeed = input.isSprinting() ? SPRINT_BOB_SPEED : BOB_SPEED;
        if (input.isMovingForwardBack() && !input.isJump()) {
            bobTime += tpf * bobSpeed;
        } else {
            bobTime = 0f;
        }

        // 3) Pasos (footsteps)
        if (input.isMoving() && !input.isJump()) {
            stepTime += tpf * bobSpeed;
            int currentStep = (int) (stepTime / 8f);
            if (currentStep > lastStepCount) {
                lastStepCount = currentStep;
                int idx = random.nextInt(2) + 1;
                audio.play("footstep" + idx);
            }
        } else {
            stepTime = 0f;
            lastStepCount = 0;
        }

        // 4) Ajuste de cámara
        float bobOffsetY = FastMath.sin(bobTime) * bobAmp;
        float eyeBaseH = input.isCrouching() ? 0.8f : 1.75f;
        Vector3f targetEye = player.getLocation().add(0, eyeBaseH + bobOffsetY, 0).addLocal(cam.getDirection().mult(-0.25f));
        smoothEyePos.interpolateLocal(targetEye, SMOOTH_FACTOR);
        cam.setLocation(smoothEyePos);

        smoothDirection.interpolateLocal(cam.getDirection(), SMOOTH_FACTOR).normalizeLocal();

        // 5) Actualizar linterna
        Vector3f side = cam.getLeft().normalize().mult(FLASH_OFFSET_SIDE);
        Vector3f down = new Vector3f(0, -FLASH_OFFSET_DOWN, 0);
        Vector3f forward = cam.getDirection().normalize().mult(FLASH_OFFSET_FORWARD);
        Vector3f torchPos = smoothEyePos.add(side).addLocal(down).addLocal(forward);

        lightPlacer.updateFlashlight(torchPos, smoothDirection);
    }

    @Override
    protected void cleanup(Application application) {

    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }
}

