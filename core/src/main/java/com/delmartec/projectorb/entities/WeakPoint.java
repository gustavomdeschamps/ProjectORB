package com.delmartec.projectorb.entities;

public class WeakPoint {
    public final float localX;
    public final float localY;
    public boolean hit;

    public WeakPoint(float localX, float localY) {
        this.localX = localX;
        this.localY = localY;
    }

    public float worldX(float enemyX, float rotation) {
        float c = (float)Math.cos(rotation), s = (float)Math.sin(rotation);
        return enemyX + localX * c - localY * s;
    }

    public float worldY(float enemyY, float rotation) {
        float c = (float)Math.cos(rotation), s = (float)Math.sin(rotation);
        return enemyY + localX * s + localY * c;
    }
}
