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
    private static final Color SHADOW = new Color(0.02f, 0.02f, 0.06f, 1f);

    private final SpriteBatch batch;
    private final BitmapFont font;
    private final BitmapFont titleFont;
    private final Assets assets;
    // Painéis (sprites/ui/panel*.png) em NinePatch na escala única. Os cortes
    // vêm de sprites/manifest.json ("ninepatch_px"): incluem os detalhes de
    // canto, então só as faixas lisas das bordas e o miolo esticam.
    private final NinePatch dialogPatch;
    private final NinePatch hudPatch;
    private final NinePatch dangerPatch;
    private final GlyphLayout layout = new GlyphLayout();

    public UiRenderer(SpriteBatch batch, BitmapFont font, BitmapFont titleFont, Assets assets) {
        this.batch = batch;
        this.font = font;
        this.titleFont = titleFont;
        this.assets = assets;
        dialogPatch = patch(assets.panelCyan);
        hudPatch = patch(assets.panel);
        dangerPatch = patch(assets.panelRed);
    }

    private static NinePatch patch(com.badlogic.gdx.graphics.Texture texture) {
        NinePatch p = new NinePatch(texture, 12, 14, 4, 4);
        p.scale(Constants.PIXEL_SCALE, Constants.PIXEL_SCALE);
        return p;
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
        float size = assets.weakPoint.getWidth() * Constants.PIXEL_SCALE;
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
            float orb = assets.lifeOrb.getWidth() * Constants.PIXEL_SCALE;
            float oy = bounds.y + bounds.height / 2f - orb / 2f;
            batch.draw(assets.lifeOrb, bounds.x + 58f, oy, orb, orb);
            batch.draw(assets.lifeOrb, bounds.x + bounds.width - 58f - orb, oy, orb, orb);
        }
        text(text, bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f + 12f - inset, TEXT,
            enabled ? Color.WHITE : DISABLED_TEXT, true);
    }

    /** Texto corrido e HUD: 1 pixel da fonte = 1 pixel de arte (4 de mundo). */
    public static final int TEXT = Constants.PIXEL_SCALE;
    /** Títulos: 2 e 3 pixels de arte por pixel da fonte (ainda escala inteira). */
    public static final int TITLE = Constants.PIXEL_SCALE * 2;
    public static final int TITLE_BIG = Constants.PIXEL_SCALE * 3;
    /** Título da abertura. */
    public static final int TITLE_HUGE = Constants.PIXEL_SCALE * 4;

    /**
     * Converte a escala antiga (relativa à Inconsolata 18 px) para a escala
     * inteira mais próxima da Silkscreen: texto 4, títulos 8 ou 12.
     */
    public static int pixelScale(float legacyScale) {
        if (legacyScale >= 3.3f) return TITLE_BIG;
        if (legacyScale >= 2.3f) return TITLE;
        return TEXT;
    }

    public void textCentered(String text, float centerX, float baselineY, float scale, Color color) {
        text(text, centerX, baselineY, pixelScale(scale), color, true);
    }

    /**
     * Texto em escala inteira com sombra de 1 pixel de fonte (sem caixa, sem
     * brilho). centered=true centraliza em x.
     */
    public float text(String text, float x, float baselineY, int pixelScale, Color color, boolean centered) {
        BitmapFont face = pixelScale > TEXT ? titleFont : font;
        face.getData().setScale(pixelScale);
        // A cor fica gravada no GlyphLayout no setText: sombra e texto são
        // montados cada um com a sua cor (antes a sombra herdava a cor da
        // chamada anterior).
        face.setColor(SHADOW.r, SHADOW.g, SHADOW.b, color.a);
        layout.setText(face, text);
        float width = layout.width;
        float left = Math.round(centered ? x - width / 2f : x);
        float base = Math.round(baselineY);
        face.draw(batch, layout, left + pixelScale, base - pixelScale);
        face.setColor(color);
        layout.setText(face, text);
        face.draw(batch, layout, left, base);
        return width;
    }

    /**
     * Quebra gulosa trocando espaços por quebras de linha (mesmo comprimento
     * do texto original, o que permite máquina de escrever sem palavras pulando).
     */
    public String wrap(String text, float width, int pixelScale) {
        StringBuilder out = new StringBuilder(text);
        int lineStart = 0, lastSpace = -1;
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (c == '\n') { lineStart = i + 1; lastSpace = -1; continue; }
            if (c == ' ') lastSpace = i;
            if (textWidth(out.substring(lineStart, i + 1), pixelScale) > width && lastSpace > lineStart) {
                out.setCharAt(lastSpace, '\n');
                lineStart = lastSpace + 1;
                lastSpace = -1;
            }
        }
        return out.toString();
    }

    public static final int OPTION_NORMAL = 0, OPTION_HOVER = 1, OPTION_RIGHT = 2, OPTION_WRONG = 3, OPTION_DIM = 4;

    /** Alternativa de quiz: moldura conforme o estado, texto alinhado à esquerda. */
    public void option(Rectangle b, String text, int style) {
        NinePatch patch = style == OPTION_WRONG ? dangerPatch
            : (style == OPTION_HOVER || style == OPTION_RIGHT) ? dialogPatch : hudPatch;
        batch.setColor(1f, 1f, 1f, style == OPTION_DIM ? 0.5f : 1f);
        patch.draw(batch, b.x, b.y, b.width, b.height);
        batch.setColor(Color.WHITE);
        Color c = style == OPTION_DIM ? DISABLED_TEXT : style == OPTION_WRONG ? DANGER
            : style == OPTION_RIGHT ? CYAN : Color.WHITE;
        text(text, b.x + 56f, b.y + b.height / 2f + 12f, TEXT, c, false);
    }

    /** Largura do texto na escala dada (para alinhar à direita). */
    public float textWidth(String text, int pixelScale) {
        BitmapFont face = pixelScale > TEXT ? titleFont : font;
        face.getData().setScale(pixelScale);
        layout.setText(face, text);
        return layout.width;
    }
}
