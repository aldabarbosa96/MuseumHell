package museumhell.utils;

import com.jme3.scene.Spatial;

import java.util.HashMap;
import java.util.Map;

public class AssetManager {
    private final com.jme3.asset.AssetManager assetManager;
    private final Map<String, Spatial> models = new HashMap<>();

    public AssetManager(com.jme3.asset.AssetManager am) {
        this.assetManager = am;
        loadModels();
    }

    private void loadModels() {
        models.put("camera", assetManager.loadModel("Models/camara.glb"));
        models.get("camera").scale(0.65f);
    }

    public Spatial get(String name) {
        Spatial s = models.get(name);
        return s != null ? s.clone() : null;
    }
}
