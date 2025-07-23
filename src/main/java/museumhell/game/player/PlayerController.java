package museumhell.game.player;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class PlayerController {

    private final CharacterControl control;
    private final Node playerNode;


    public PlayerController(AssetManager am, PhysicsSpace space, Vector3f startPos){
        playerNode = new Node("Player");
        playerNode.setLocalTranslation(startPos);

        CapsuleCollisionShape shape = new CapsuleCollisionShape(0.8f, 2f);
        control = new CharacterControl(shape, 0.05f);
        control.setGravity(30);
        control.setJumpSpeed(12);
        control.setFallSpeed(20);

        playerNode.addControl(control);
        space.add(control);
    }

    public Node getNode() {
        return playerNode;
    }

    public void update(float tpf) {
        // TODO --> Animaciones, salud, estados…
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
