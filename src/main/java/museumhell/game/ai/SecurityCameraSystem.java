package museumhell.game.ai;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import museumhell.engine.world.levelgen.MuseumLayout;
import museumhell.engine.world.levelgen.Room;
import museumhell.game.player.PlayerController;

import java.util.*;

import static museumhell.utils.ConstantManager.CAM_ALARM_SECONDS;

public class SecurityCameraSystem extends BaseAppState {
    private final List<SecurityCamera> cameras;
    private final PlayerController player;
    private final MuseumLayout museum;
    private final Node root;

    private static final class Pulse {
        PointLight light;
        float ttl;

        Pulse(PointLight l, float life) {
            light = l;
            ttl = life;
        }
    }

    private final Map<Room, Pulse> active = new HashMap<>();

    public SecurityCameraSystem(Node root, MuseumLayout museum, List<SecurityCamera> cameras, PlayerController player) {
        this.root = root;
        this.museum = museum;
        this.cameras = cameras;
        this.player = player;
    }

    @Override
    protected void initialize(Application app) {
    }

    @Override
    public void update(float tpf) {

        Vector3f playerPos = player.getLocation();

        /* 1)Comprobar visión de cada cámara */
        for (SecurityCamera cam : cameras) {
            if (cam.canSee(playerPos)) {
                Room room = roomOf(playerPos);
                if (room != null && !active.containsKey(room)) {
                    triggerAlarm(room);
                }
            }
        }

        /* 2)Actualizar / eliminar focos que expiran */
        active.entrySet().removeIf(e -> {
            Pulse p = e.getValue();
            p.ttl -= tpf;
            if (p.ttl <= 0) {
                root.removeLight(p.light);
                return true;
            }
            return false;
        });
    }

    private void triggerAlarm(Room r) {

        // Centro + radio aproximado de la sala
        Vector3f pos = r.center3f(centerYOf(r));
        float rad = (float) Math.hypot(r.w(), r.h()) * 0.75f;

        PointLight red = new PointLight();
        red.setColor(ColorRGBA.Red.mult(4f));
        red.setRadius(rad);
        red.setPosition(pos);
        root.addLight(red);

        active.put(r, new Pulse(red, CAM_ALARM_SECONDS));
    }

    /* Devuelve la sala en la que está el punto (o null) */
    private Room roomOf(Vector3f p) {
        int floor = (int) Math.floor(p.y / museum.floorHeight());
        if (floor < 0 || floor >= museum.floors().size()) return null;

        for (Room r : museum.floors().get(floor).rooms()) {
            if (p.x > r.x() && p.x < r.x() + r.w() && p.z > r.z() && p.z < r.z() + r.h()) return r;
        }
        return null;
    }

    private float centerYOf(Room r) {
        /* Cálculo de la planta a la que pertenece la sala */
        int floor = (int) Math.floor(
                r.center3f(0).y / museum.floorHeight());          // y=0 en datos
        return museum.yOf(floor) + museum.floorHeight() * 0.5f;
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }
}
