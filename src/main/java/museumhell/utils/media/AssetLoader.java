package museumhell.utils.media;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;

import java.util.HashMap;
import java.util.Map;

public class AssetLoader {
    private final AssetManager assetManager;
    private final Map<String, Spatial> models = new HashMap<>();

    public AssetLoader(com.jme3.asset.AssetManager am) {
        this.assetManager = am;
        loadModels();
    }

    private void loadModels() {
        models.put("camera1", assetManager.loadModel("Models/CAMARAMIRRORV2.glb"));
        models.put("floor1", assetManager.loadModel("Models/Floor1.glb"));
        models.put("wall1", assetManager.loadModel("Models/Wall1.glb"));
        models.put("wall2", assetManager.loadModel("Models/Wall2.glb"));
        models.put("wall3", assetManager.loadModel("Models/Wall3.glb"));
        models.put("wander2Animated", assetManager.loadModel("Models/Monster_Animated2.glb"));
    }

    public Spatial get(String name) {
        Spatial s = models.get(name);
        return s != null ? s.clone() : null;
    }
}
