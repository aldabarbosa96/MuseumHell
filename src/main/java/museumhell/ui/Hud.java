package museumhell.ui;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class Hud extends BaseAppState {

    private static final float H = 60;
    private static final float PAD = 8;
    private static final float ICON_SIZE = 28;

    private static final ColorRGBA BORDER = new ColorRGBA(.1f, .1f, .1f, .9f);
    private static final ColorRGBA BG = new ColorRGBA(.0f, .0f, .0f, .55f);
    private static final ColorRGBA ICON = ColorRGBA.Orange;
    private Node root;
    private BitmapText txt;
    private Geometry gBorder, gBg, gIcon;
    private float pop = 0;


    public void set(int collected, int total) {
        if (txt == null) return;
        txt.setText(collected + " / " + total);
        layout();
        pop = 1f;
    }

    @Override
    protected void initialize(Application app) {

        SimpleApplication sApp = (SimpleApplication) app;
        root = new Node("HUD");
        sApp.getGuiNode().attachChild(root);

        gBorder = makeQuad(1, H, BORDER);
        root.attachChild(gBorder);

        gBg = makeQuad(1, H - 4, BG);
        gBg.setLocalTranslation(2, 2, 0.1f);
        root.attachChild(gBg);

        gIcon = makeQuad(ICON_SIZE, ICON_SIZE, ICON);
        root.attachChild(gIcon);

        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        txt = new BitmapText(font);
        txt.setColor(ColorRGBA.White);
        txt.setSize(22);
        root.attachChild(txt);

        root.setLocalTranslation(10, sApp.getCamera().getHeight() - H - 10, 0);

        set(0, 0);
    }

    @Override
    public void update(float tpf) {
        if (pop > 0) {
            pop = FastMath.clamp(pop - tpf * 4f, 0, 1);
            root.setLocalScale(1 + pop * .25f);
        }
    }

    @Override
    protected void cleanup(Application app) {
        root.removeFromParent();
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    private void layout() {
        float textW = txt.getLineWidth();
        float totalW = PAD + ICON_SIZE + PAD + textW + PAD;

        gBorder.setMesh(new Quad(totalW, H));
        gBg.setMesh(new Quad(totalW - 4, H - 4));

        gIcon.setLocalTranslation(PAD, (H - ICON_SIZE) * .5f, 0.2f);

        txt.setLocalTranslation(PAD + ICON_SIZE + PAD, baseline(txt, H), 0.3f);
    }

    private Geometry makeQuad(float w, float h, ColorRGBA color) {
        Geometry g = new Geometry("Quad", new Quad(w, h));
        Material m = new Material(getApplication().getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", color);
        m.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        g.setMaterial(m);
        return g;
    }

    private float baseline(BitmapText t, float boxH) {
        return (boxH - t.getLineHeight()) * .5f + t.getLineHeight();
    }
}
