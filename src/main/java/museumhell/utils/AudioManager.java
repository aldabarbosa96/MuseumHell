package museumhell.utils;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;

import java.util.HashMap;
import java.util.Map;

public class AudioManager {
    private final AssetManager assetManager;
    private final Map<String, AudioNode> sounds = new HashMap<>();

    public AudioManager(AssetManager am) {
        this.assetManager = am;
        loadSounds();
    }

    private void loadSounds() {
        //sounds.put("footstep", new AudioNode(assetManager, "Sounds/footstep.ogg", AudioData.DataType.Buffer));
        sounds.put("flashlight_click", new AudioNode(assetManager, "Sounds/click.ogg", AudioData.DataType.Buffer));
    }

    public AudioNode get(String name) {
        return sounds.get(name).clone();
    }
}

