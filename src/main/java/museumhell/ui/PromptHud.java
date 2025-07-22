package museumhell.ui;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;

public class PromptHud extends BaseAppState {

    private BitmapText txt;

    @Override
    protected void initialize(Application app) {
        SimpleApplication sa = (SimpleApplication) app;
        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        txt = new BitmapText(font);
        txt.setSize(22);
        txt.setColor(ColorRGBA.White);
        txt.setText("");
        txt.setCullHint(BitmapText.CullHint.Always);

        float x = (sa.getCamera().getWidth() - txt.getLineWidth()) * .5f;
        float y = 80;
        txt.setLocalTranslation(x, y, 0);
        sa.getGuiNode().attachChild(txt);
    }

    public void show(String msg) {
        txt.setText(msg);
        txt.setCullHint(BitmapText.CullHint.Never);

        float x = (getApplication().getCamera().getWidth() - txt.getLineWidth()) * .5f;
        txt.setLocalTranslation(x, txt.getLocalTranslation().y, 0);
    }

    public void hide() {
        txt.setCullHint(BitmapText.CullHint.Always);
    }

    @Override protected void cleanup(Application app) { txt.removeFromParent(); }
    @Override protected void onEnable()  { }
    @Override protected void onDisable() { }
}
