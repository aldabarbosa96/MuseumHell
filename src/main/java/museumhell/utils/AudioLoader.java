package museumhell.utils;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.scene.Node;

import java.util.HashMap;
import java.util.Map;

public class AudioLoader {
    private final AssetManager assetManager;
    private final Node rootNode;
    private final Map<String, Entry> sounds = new HashMap<>();

    private static class Entry {
        AudioNode node;
        boolean looping;
        boolean played;

        Entry(AudioNode node, boolean looping) {
            this.node    = node;
            this.looping = looping;
            this.played  = false;
        }
    }

    public AudioLoader(AssetManager am, Node rootNode) {
        this.assetManager = am;
        this.rootNode = rootNode;
        loadAllSounds();
    }

    public void loadAllSounds() {
        register("ambient1", "Sounds/ambientSound1.ogg", true, 0.2f);
        register("ambient2", "Sounds/ambientSound2.ogg", true, 0.3f);
        register("door", "Sounds/doorSound.ogg", false, 1f);
        register("flashlight", "Sounds/click.ogg", false, 0.3f);
        register("footstep1", "Sounds/footsteps1.ogg", false, 0.75f);
        register("footstep2", "Sounds/footsteps2.ogg", false, 0.75f);
        register("footstep3", "Sounds/footsteps3.ogg", false, 0.75f);
    }

    private void register(String name, String path, boolean looping, float volume) {
        AudioNode node = new AudioNode(assetManager, path, AudioData.DataType.Buffer);
        node.setPositional(false);
        node.setLooping(looping);
        node.setVolume(volume);
        if (looping) {
            // adjunta al grafo PARA que persista en escena y pueda hacer bucle
            rootNode.attachChild(node);
        }
        sounds.put(name, new Entry(node, looping));
    }

    public void play(String name) {
        Entry e = sounds.get(name);
        if (e == null) return;

        if (e.looping) {
            if (!e.played) {
                e.node.play();
                e.played = true;
            }
        } else {
            e.node.clone().playInstance();
        }
    }
}
