package com.delmartec.projectorb.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;

/** Linguagem visual compartilhada pelas telas: pedra escura, cristal e borda luminosa. */
public final class UiRenderer {
    public static final Color MAGENTA = new Color(0.96f, 0.18f, 0.82f, 1f);
    public static final Color CYAN = new Color(0.24f, 0.84f, 1f, 1f);
    public static final Color DANGER = new Color(1f, 0.18f, 0.42f, 1f);
    public static final Color SUCCESS = new Color(0.40f, 1f, 0.76f, 1f);
    public static final Color SOFT_TEXT = new Color(0.76f, 0.82f, 1f, 1f);
    private static final Color DISABLED_TEXT = new Color(0.55f, 0.57f, 0.68f, 1f);

    private final SpriteBatch batch;
    private final BitmapFont font;
    private final BitmapFont titleFont;
    private final Assets assets;
    // Painéis oficiais (sprites/ui/) em NinePatch 1x. Os cortes vêm de
    // sprites/manifest.json ("ninepatch"): incluem os detalhes de canto, então
    // só as faixas lisas das bordas e o miolo esticam — sem distorcer pixel.
    private final NinePatch dialogPatch;
    private final NinePatch hudPatch;
    private final NinePatch dangerPatch;
    private final GlyphLayout layout = new GlyphLayout();

    public UiRenderer(SpriteBatch batch, BitmapFont font, BitmapFont titleFont, Assets assets) {
        this.batch = batch;
        this.font = font;
        this.titleFont = titleFont;
        this.assets = assets;
        dialogPatch = new NinePatch(assets.dialogPanel, 64, 73, 26, 22);
        hudPatch = new NinePatch(assets.hudPanel, 48, 56, 16, 16);
        dangerPatch = new NinePatch(assets.enemyPanel, 40, 44, 13, 11);
    }

    public void panel(float x, float y, float width, float height, Color accent, float alpha) {
        batch.setColor(1f, 1f, 1f, alpha);
        dialogPatch.draw(batch, x, y, width, height);
        batch.setColor(Color.WHITE);
    }

    /** Painel do HUD (moldura lilás). */
    public void hudPanel(float x, float y, float width, float height) {
        hudPatch.draw(batch, x, y, width, height);
    }

    /** Painel de inimigo/perigo (moldura vermelha). */
    public void dangerPanel(float x, float y, float width, float height) {
        dangerPatch.draw(batch, x, y, width, height);
    }

    public void crystalCorners(float x, float y, float width, float height, float requested, float alpha) {
        // Escala inteira do marcador nativo mais próxima do tamanho pedido.
        float size = assets.weakPoint.getWidth() * Math.max(1, Math.round(requested / assets.weakPoint.getWidth()));
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(assets.weakPoint, x - size * 0.5f, y + height - size * 0.5f, size, size);
        batch.draw(assets.weakPoint, x + width - size * 0.5f, y + height - size * 0.5f, size, size);
        batch.draw(assets.weakPoint, x - size * 0.5f, y - size * 0.5f, size, size);
        batch.draw(assets.weakPoint, x + width - size * 0.5f, y - size * 0.5f, size, size);
        batch.setColor(Color.WHITE);
    }

    public void button(Rectangle bounds, String text, boolean selected, boolean enabled, Color accent) {
        button(bounds, text, selected, enabled, accent, false);
    }

    public void button(Rectangle bounds, String text, boolean selected, boolean enabled, Color accent, boolean pressed) {
        float alpha = enabled ? 1f : 0.45f;
        float inset = pressed ? 6f : 0f;
        batch.setColor(1f, 1f, 1f, alpha);
        (selected && enabled ? dialogPatch : hudPatch).draw(batch, bounds.x + inset, bounds.y - inset,
            bounds.width - inset * 2f, bounds.height - inset * 2f);
        batch.setColor(Color.WHITE);
        if (selected && enabled && !pressed) {
            float orb = assets.lifeOrbSmall.getWidth() * 2f;
            float oy = bounds.y + bounds.height / 2f - orb / 2f;
            batch.draw(assets.lifeOrbSmall, bounds.x + 58f, oy, orb, orb);
            batch.draw(assets.lifeOrbSmall, bounds.x + bounds.width - 58f - orb, oy, orb, orb);
        }
        float fontScale = (pressed ? 1.47f : 1.55f) * 1.8f;
        font.getData().setScale(fontScale);
        layout.setText(font, text);
        float available = bounds.width - 190f;
        if (layout.width > available) {
            font.getData().setScale(fontScale * available / layout.width);
            layout.setText(font, text);
        }
        font.setColor(enabled ? Color.WHITE : DISABLED_TEXT);
        font.draw(batch, layout, bounds.x + bounds.width / 2f - layout.width / 2f,
            bounds.y + bounds.height / 2f + 12f - inset);
    }

    public void textCentered(String text, float centerX, float baselineY, float scale, Color color) {
        BitmapFont face = scale >= 2.3f ? titleFont : font;
        face.getData().setScale(scale * 1.8f * (face == titleFont ? 18f / 96f : 1f));
        face.setColor(color);
        layout.setText(face, text);
        face.draw(batch, layout, centerX - layout.width / 2f, baselineY);
    }
}
