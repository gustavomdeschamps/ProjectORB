package com.delmartec.projectorb.utils;

import com.badlogic.gdx.graphics.Color;

/**
 * Número pequeno que sobe do ponto do acerto (ex.: "+100", "-25") e some.
 * Coordenadas de MUNDO; sobe em passos de 1 pixel de arte (sem deslizar
 * fracionado) e pisca antes de sumir, sem degradê de transparência.
 */
public final class FloatingText {
    public static final float DURATION = 0.75f;
    private static final float RISE = 44f;

    public final float x, y;
    public final String text;
    public final Color color;
    private float time;

    public FloatingText(float x, float y, String text, Color color) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.color = color;
    }

    public void update(float delta) { time += delta; }

    public boolean dead() { return time >= DURATION; }

    /** Altura atual (sobe rápido e desacelera), na grade de pixel. */
    public float currentY(boolean reducedMotion) {
        if (reducedMotion) return y;
        float t = Math.min(1f, time / DURATION);
        float e = 1f - (1f - t) * (1f - t);
        return y + Math.round(RISE * e / Constants.PIXEL_SCALE) * Constants.PIXEL_SCALE;
    }

    /** Visível? Nos últimos 30% pisca (liga/desliga), em vez de esmaecer. */
    public boolean visible() {
        float t = time / DURATION;
        return t < 0.7f || ((int)(time * 20f)) % 2 == 0;
    }
}
