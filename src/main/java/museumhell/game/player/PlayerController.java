package museumhell.game.player;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import static museumhell.utils.ConstantManager.*;

public class PlayerController {
    private CharacterControl control;
    private final Node playerNode;
    private final PhysicsSpace space;

    public PlayerController( PhysicsSpace space, Vector3f startPos) {
        this.space = space;

        playerNode = new Node("Player");
        playerNode.setLocalTranslation(startPos);

        // arrancamos en pie
        control = makeControl(STAND_HEIGHT);
        playerNode.addControl(control);
        space.add(control);
    }

    // helper para crear un CharacterControl con la altura deseada
    private CharacterControl makeControl(float capsuleHeight) {
        CapsuleCollisionShape shape = new CapsuleCollisionShape(CAPSULE_RADIUS, capsuleHeight);
        CharacterControl cc = new CharacterControl(shape, 0.05f);
        cc.setGravity(30);
        cc.setJumpSpeed(12);
        cc.setFallSpeed(20);
        return cc;
    }

    public void setCrouch(boolean crouching) {
        // guardamos posición actual
        Vector3f loc = control.getPhysicsLocation();

        // quitamos el viejo control
        playerNode.removeControl(control);
        space.remove(control);

        // creamos uno nuevo con la altura adecuada
        control = makeControl(crouching ? CROUCH_HEIGHT : STAND_HEIGHT);
        control.setPhysicsLocation(loc);

        // lo añadimos de nuevo
        playerNode.addControl(control);
        space.add(control);
    }

    public Node getNode() {
        return playerNode;
    }

    public void update(float tpf) {
    }

    public void move(Vector3f dir) {
        control.setWalkDirection(dir);
    }

    public void jump() {
        control.jump();
    }

    public Vector3f getLocation() {
        return control.getPhysicsLocation();
    }
}
