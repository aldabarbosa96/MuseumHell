package museumhell.game.input;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.bullet.BulletAppState;
import com.jme3.input.FlyByCamera;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import museumhell.game.items.HandItemManager;
import museumhell.game.player.PlayerController;
import museumhell.engine.world.world.WorldBuilder;
import museumhell.game.loot.LootSystem;
import museumhell.utils.media.AudioLoader;

import static museumhell.utils.ConstantManager.*;

public class InputSystem extends BaseAppState implements ActionListener, AnalogListener {
    private WorldBuilder world;
    private HandItemManager handMgr;
    private AudioLoader audio;
    private BulletAppState physics;
    private final InputManager inMgr;
    private final FlyByCamera flyCam;
    private Camera cam;
    private PlayerController player;
    private LootSystem lootMgr;
    private boolean up, down, left, right, sprint, crouch, debug, jump;
    private int activeSlot = 0;


    public InputSystem(InputManager inMgr, FlyByCamera flyCam, BulletAppState physics) {
        this.inMgr = inMgr;
        this.flyCam = flyCam;
        this.physics = physics;
        setupMappings();
    }

    private void setupMappings() {
        inMgr.addMapping("Debug", new KeyTrigger(KeyInput.KEY_TAB));
        inMgr.addMapping("Left", new KeyTrigger(KeyInput.KEY_A));
        inMgr.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inMgr.addMapping("Up", new KeyTrigger(KeyInput.KEY_W));
        inMgr.addMapping("Down", new KeyTrigger(KeyInput.KEY_S));
        inMgr.addMapping("Jump", new KeyTrigger(KeyInput.KEY_SPACE));
        inMgr.addMapping("Sprint", new KeyTrigger(KeyInput.KEY_LSHIFT));
        inMgr.addMapping("Crouch", new KeyTrigger(KeyInput.KEY_LCONTROL));
        inMgr.addMapping("Use", new KeyTrigger(KeyInput.KEY_E));
        inMgr.addMapping("Lantern", new MouseButtonTrigger(1));
        inMgr.addListener(this, "Debug", "Left", "Right", "Up", "Down", "Jump", "Sprint", "Use", "Lantern", "Crouch");

        flyCam.setDragToRotate(false);
        flyCam.setRotationSpeed(1.5f);
    }

    public void setupCameraFollow(Camera cam) {
        this.cam = cam;
    }

    public void registerPlayerControl(PlayerController pc) {
        this.player = pc;
    }

    public void setAudioManager(AudioLoader audio) {
        this.audio = audio;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        // --- Slots: solo en PRESSED para no duplicar en release ---
        if (handMgr != null && isPressed) {
            switch (name) {
                case "Slot1":
                    handMgr.selectSlot(0);
                    activeSlot = 0;
                    break;
                case "Slot2":
                    handMgr.selectSlot(1);
                    activeSlot = 1;
                    if (world != null) world.getLightPlacer().setFlashlightEnabled(false);
                    break;
                case "Slot3":
                    handMgr.selectSlot(2);
                    activeSlot = 2;
                    if (world != null) world.getLightPlacer().setFlashlightEnabled(false);
                    break;
                case "Slot4":
                    handMgr.selectSlot(3);
                    activeSlot = 3;
                    if (world != null) world.getLightPlacer().setFlashlightEnabled(false);
                    break;
                case "Slot5":
                    handMgr.selectSlot(4);
                    activeSlot = 4;
                    if (world != null) world.getLightPlacer().setFlashlightEnabled(false);
                    break;
            }
        }

        switch (name) {
            case "Debug" -> {
                debug = isPressed; // opcional; si lo usas en otro sitio
                if (isPressed) {
                    physics.setDebugEnabled(!physics.isDebugEnabled());
                }
            }
            case "Left"  -> left  = isPressed;
            case "Right" -> right = isPressed;
            case "Up"    -> up    = isPressed;
            case "Down"  -> down  = isPressed;
            case "Sprint"-> sprint= isPressed;

            case "Jump" -> {
                jump = isPressed;
                if (isPressed && player != null) player.jump();
            }

            case "Use" -> {
                if (isPressed && world != null && player != null) {
                    world.tryUseDoor(player.getLocation());
                    if (world.isDoorOpen() && audio != null) {
                        audio.play("door");
                    }
                    if (lootMgr != null) lootMgr.tryPickUp(player.getLocation());
                }
            }

            case "Lantern" -> {
                if (isPressed && world != null) {
                    // Solo permite toggle si la linterna está equipada (slot 0)
                    if (activeSlot == 0) {
                        world.getLightPlacer().toggleFlashlight();
                        if (audio != null) audio.play("flashlight");
                    } else {
                        world.getLightPlacer().setFlashlightEnabled(false);
                    }
                }
            }

            case "Crouch" -> {
                crouch = isPressed;
                if (player != null) player.setCrouch(crouch);
            }
        }
    }


    public void update(float tpf) {
        if (player == null || cam == null) return;

        Vector3f dir = new Vector3f();
        if (left) dir.addLocal(cam.getLeft());
        if (right) dir.addLocal(cam.getLeft().negate());
        if (up) dir.addLocal(cam.getDirection());
        if (down) dir.addLocal(cam.getDirection().negate());
        dir.setY(0);

        if (dir.lengthSquared() > 0f) {
            dir.normalizeLocal();

            // Velocidad base en unidades/segundo
            float baseSpeed = crouch ? CROUCH_SPEED : (sprint ? WALK_SPEED * SPRINT_MULT : WALK_SPEED);

            // Convierte a desplazamiento por tick de física (accuracy ≈ 1/60 s)
            float dt = physics.getPhysicsSpace().getAccuracy();
            player.move(dir.multLocal(baseSpeed * dt));
        } else {
            // Sin input: detén el character
            player.move(Vector3f.ZERO);
        }
    }

    public boolean isMoving() {
        return up || down || left || right;
    }

    public boolean isMovingForwardBack() {
        return up || down;
    }

    public boolean isSprinting() {
        return sprint;
    }

    public void setWorld(WorldBuilder w) {
        this.world = w;
    }

    public void setLootManager(LootSystem lm) {
        this.lootMgr = lm;
    }

    public boolean isCrouching() {
        return crouch;
    }

    public boolean isJump() {
        return jump;
    }

    public void setHandManager(HandItemManager m) {
        this.handMgr = m;
    }

    @Override
    protected void initialize(Application application) {
        inMgr.addMapping("Slot1", new KeyTrigger(KeyInput.KEY_1));
        inMgr.addMapping("Slot2", new KeyTrigger(KeyInput.KEY_2));
        inMgr.addMapping("Slot3", new KeyTrigger(KeyInput.KEY_3));
        inMgr.addMapping("Slot4", new KeyTrigger(KeyInput.KEY_4));
        inMgr.addMapping("Slot5", new KeyTrigger(KeyInput.KEY_5));
        inMgr.addListener(this, "Slot1", "Slot2", "Slot3", "Slot4", "Slot5");

// Rueda mouse
        inMgr.addMapping("NextSlot", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inMgr.addMapping("PrevSlot", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inMgr.addListener((AnalogListener) this, "NextSlot", "PrevSlot");
    }

    @Override
    protected void cleanup(Application application) {
        inMgr.clearMappings();
        inMgr.removeListener(this);
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (handMgr == null) return;
        if ("NextSlot".equals(name)) handMgr.next();
        else if ("PrevSlot".equals(name)) handMgr.prev();
    }
}
