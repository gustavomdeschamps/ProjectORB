package com.delmartec.projectorb.level;

import com.badlogic.gdx.math.Rectangle;

public class Platform {
    public final Rectangle bounds;
    public final boolean wall;

    public Platform(float x, float y, float w, float h) {
        this(x, y, w, h, false);
    }

    public Platform(float x, float y, float w, float h, boolean wall) {
        this.bounds = new Rectangle(x, y, w, h);
        this.wall = wall;
    }
}
