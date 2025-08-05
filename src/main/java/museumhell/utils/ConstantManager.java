package museumhell.utils;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;

public class ConstantManager {

    // HUD
    public static final float H = 60;
    public static final float PAD = 8;
    public static final float ICON_SIZE = 28;
    public static final ColorRGBA BORDER = new ColorRGBA(.1f, .1f, .1f, .9f);
    public static final ColorRGBA BG = new ColorRGBA(.0f, .0f, .0f, .55f);
    public static final ColorRGBA ICON = ColorRGBA.Orange;


    // PLAYER
    public static final float CAPSULE_RADIUS = 0.8f;
    public static final float STAND_HEIGHT = 2f;
    public static final float CROUCH_HEIGHT = 0.5f;
    public static final float BOB_SPEED = 15f;
    public static final float BOB_AMPLITUDE = 0.12f;
    public static final float SPRINT_BOB_SPEED = 25f;
    public static final float SPRINT_BOB_AMPLITUDE = 0.3f;
    public static final float SMOOTH_FACTOR = 0.12f;


    // INPUT
    public static final float WALK_SPEED = 8f;
    public static final float CROUCH_SPEED = 4f;
    public static final float SPRINT_MULT = 2.75f;


    // WORLD && BUILDERS
    public static final float DOOR_W = 3.5f, WALL_T = 2f, MARGIN = 1f;
    public static final float DOOR_H = 10f;
    public static final float HOLE_W = DOOR_W * 2f;
    public static final float DOOR_T = .33f;
    public static final float DOOR_OPEN_DIST = 4f;
    public static final float SLICE_DOOR_T = 3 * DOOR_T;
    public static final float SLICE_DOOR_W = 1.25f * DOOR_W;
    public static final int MIN_OVERLAP_FOR_DOOR = (int) (DOOR_W + 2 * MARGIN);
    public static final float CORRIDOR_WALL_T = WALL_T * 3f;
    public static final float FLOOR_T = 0.1f;
    public static final float CEIL_T = 0.1f;
    public static final float STAIR_WIDTH = 4.5f;
    public static final float STEP_H = 0.3f;
    public static final float STEP_DEPTH = 0.45f;
    public static final float SPEED = 4f;
    public static final float PROTRUDE = 0.1f;
    public static final float PENETRATION = 0.5f;
    public static final int MIN_ROOM = 20;
    public static final int MIN_SPLIT = MIN_ROOM * 2 + 2;
    public static final int MAX_DEPTH = 6;
    public static final int DOOR_MIN_OVERLAP = 3;
    public static final float STAIR_WALL_GAP = -2f;
    public static final float STAIR_FOOT_GAP = 2f;
    public static final int MAX_STAIRS = 3;
    public static final float RAIL_H = 2.5f;
    public static final float RAIL_T = 0.2f;
    public static final float STAIR_CLEAR = 0.04f;


    // ITEMS
    public static final ColorRGBA FLASHLIGHT_COLOR = new ColorRGBA(1f, 0.95f, 0.65f, 1f).multLocal(2.5f);
    public static final float SPOT_RANGE = 50f;
    public static final float INNER_ANGLE = FastMath.DEG_TO_RAD * 8;
    public static final float OUTER_ANGLE = FastMath.DEG_TO_RAD * 28f;

    // ENEMIES
    public static final float EN_STEP_INTERVAL = 1f;
    public static final float EN_STEP_INTERVAL_RUN = 0.5f;
    public static final float EN_STEP_SHARPNESS = 2.0f;
    public static final float EN_STEP_GAIN = 2.0f;
    public static final float AVOID_PROBE_PERIOD = 0.2f;

}
