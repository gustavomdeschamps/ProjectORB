package com.delmartec.projectorb.entities;

public class EffectBurst {
    public enum Kind { HIT, VOID, DASH }
    public float x, y;
    public final Kind kind;
    public final float duration;
    public float time;
    public float size;

    public EffectBurst(float x, float y, Kind kind, float size, float duration) {
        this.x = x; this.y = y; this.kind = kind; this.size = size; this.duration = duration;
    }

    public void update(float delta) { time += delta; }
    public boolean dead() { return time >= duration; }
    public float alpha() { return Math.max(0f, 1f - time / duration); }
}
