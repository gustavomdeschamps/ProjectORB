package com.delmartec.projectorb.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Texto no meio da tela SÓ para momentos raros e importantes (1 a 3
 * palavras), animado: as letras caem uma a uma no lugar com um quique, ficam
 * e depois saem caindo na mesma ordem. Em movimento reduzido: aparece e some
 * sem movimento. Coordenadas de HUD (1920x1080).
 */
public final class Callout {
    private static final float IN = 0.45f, HOLD = 1.1f, OUT = 0.35f;
    private static final float Y = 760f;

    private String text;
    private Color color;
    private float time = Float.MAX_VALUE;

    public void show(String text, Color color) {
        this.text = text;
        this.color = color;
        this.time = 0f;
    }

    public boolean active() { return text != null && time < IN + HOLD + OUT; }

    public void update(float delta) { if (text != null) time += delta; }

    public void draw(UiRenderer ui, boolean reducedMotion) {
        if (!active()) return;
        int scale = UiRenderer.TITLE_BIG;
        float total = ui.textWidth(text, scale);
        float x = Math.round((Constants.VIEW_WIDTH - total) / 2f);
        int n = text.length();
        for (int i = 0; i < n; i++) {
            String ch = text.substring(i, i + 1);
            float cw = ui.textWidth(text.substring(0, i + 1), scale) - ui.textWidth(text.substring(0, i), scale);
            if (ch.equals(" ")) { x += cw; continue; }
            float dy = 0f;
            boolean show = true;
            if (!reducedMotion) {
                float start = i * (IN * 0.6f / n);
                float t = MathUtils.clamp((time - start) / (IN * 0.4f), 0f, 1f);
                if (time < start) show = false;
                // cai de 40 px, passa 8 px do lugar e volta (quique)
                dy = t < 0.7f ? 40f * (1f - t / 0.7f) : -8f * MathUtils.sin((t - 0.7f) / 0.3f * MathUtils.PI);
                float outStart = IN + HOLD + i * (OUT * 0.6f / n);
                if (time > outStart) {
                    float o = MathUtils.clamp((time - outStart) / (OUT * 0.4f), 0f, 1f);
                    dy = -60f * o * o;
                    if (o >= 1f) show = false;
                }
            }
            if (show) {
                float snapped = Math.round(dy / Constants.PIXEL_SCALE) * Constants.PIXEL_SCALE;
                ui.text(ch, x, Y + snapped, scale, color, false);
            }
            x += cw;
        }
    }
}
