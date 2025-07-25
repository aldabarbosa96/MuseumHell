package museumhell.game.input;

import com.jme3.input.FlyByCamera;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import museumhell.game.player.PlayerController;
import museumhell.engine.world.WorldBuilder;
import museumhell.game.loot.LootSystem;

public class InputSystem implements ActionListener {
    private WorldBuilder world;
    private final InputManager inMgr;
    private final FlyByCamera flyCam;
    private Camera cam;
    private PlayerController player;
    private LootSystem lootMgr;

    private boolean up, down, left, right, sprint, crouch, debug, jump;

    private static final float WALK_SPEED = 8f;
    private static final float CROUCH_SPEED = 4f;
    private static final float SPRINT_MULT = 2.5f;

    public InputSystem(InputManager inMgr, FlyByCamera flyCam) {
        this.inMgr = inMgr;
        this.flyCam = flyCam;
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
        inMgr.addMapping("Lantern", new KeyTrigger(KeyInput.KEY_F));
        inMgr.addListener(this, "Debug","Left", "Right", "Up", "Down", "Jump", "Sprint", "Use", "Lantern", "Crouch");

        flyCam.setDragToRotate(false);
        flyCam.setRotationSpeed(1.5f);
    }

    public void setupCameraFollow(Camera cam) {
        this.cam = cam;
    }

    public void registerPlayerControl(PlayerController pc) {
        this.player = pc;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "Debug" -> {
                debug = isPressed; // todo --> mover lógica de debug aquí en un futuro
            }
            case "Left" -> left = isPressed;
            case "Right" -> right = isPressed;
            case "Up" -> up = isPressed;
            case "Down" -> down = isPressed;
            case "Sprint" -> sprint = isPressed;
            case "Jump" -> {
                jump = isPressed;
                if (isPressed && player != null) player.jump();
            }
            case "Use" -> {
                if (isPressed && world != null && player != null) {
                    world.tryUseDoor(player.getLocation());
                    if (lootMgr != null) lootMgr.tryPickUp(player.getLocation());
                }
            }
            case "Lantern" -> {
                if (isPressed && world != null && world.getLightPlacer() != null) {
                    world.getLightPlacer().toggleFlashlight();
                }
            }
            case "Crouch" -> {
                crouch = isPressed;
                if (player != null) {
                    player.setCrouch(crouch);
                }
            }
        }
    }

    public void update(float tpf) {
        if (player == null || cam == null) return;

        // movimiento
        Vector3f dir = new Vector3f();
        if (left) dir.addLocal(cam.getLeft());
        if (right) dir.addLocal(cam.getLeft().negate());
        if (up) dir.addLocal(cam.getDirection());
        if (down) dir.addLocal(cam.getDirection().negate());

        // velocidad según estado
        float baseSpeed = crouch ? CROUCH_SPEED : (sprint ? WALK_SPEED * SPRINT_MULT : WALK_SPEED);
        player.move(dir.multLocal(baseSpeed * tpf));
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
}
