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
    private final NinePatch buttonNormal;
    private final NinePatch buttonSelected;
    private final NinePatch buttonPressed;
    private final NinePatch keyPatch;
    private final NinePatch keyLitPatch;
    private final GlyphLayout layout = new GlyphLayout();

    public UiRenderer(SpriteBatch batch, BitmapFont font, BitmapFont titleFont, Assets assets) {
        this.batch = batch;
        this.font = font;
        this.titleFont = titleFont;
        this.assets = assets;
        dialogPatch = patch(assets.panelCyan);
        hudPatch = patch(assets.panel);
        dangerPatch = patch(assets.panelRed);
        buttonNormal = buttonPatch(assets.buttonNormal);
        buttonSelected = buttonPatch(assets.buttonSelected);
        buttonPressed = buttonPatch(assets.buttonPressed);
        keyPatch = new NinePatch(assets.key, 3, 3, 3, 4);
        keyPatch.scale(Constants.PIXEL_SCALE, Constants.PIXEL_SCALE);
        keyLitPatch = new NinePatch(assets.keyLit, 3, 3, 3, 4);
        keyLitPatch.scale(Constants.PIXEL_SCALE, Constants.PIXEL_SCALE);
    }

    private static NinePatch patch(com.badlogic.gdx.graphics.Texture texture) {
        NinePatch p = new NinePatch(texture, 12, 14, 4, 4);
        p.scale(Constants.PIXEL_SCALE, Constants.PIXEL_SCALE);
        return p;
    }

    /** Botão: grade 12x12 com cantos de 3 px (sprites/ui/button_*.png). */
    private static NinePatch buttonPatch(com.badlogic.gdx.graphics.Texture texture) {
        NinePatch p = new NinePatch(texture, 3, 3, 3, 3);
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

    public void button(Rectangle bounds, String text, boolean selected, boolean enabled, Color accent) {
        button(bounds, text, selected, enabled, accent, false);
    }

    /**
     * Botão retangular em pixel art, texto centralizado. Três estados:
     * normal, selecionado (o próprio botão clareia e ganha contorno forte) e
     * pressionado (afunda 1 pixel de arte). Nenhum ícone ao lado.
     */
    public void button(Rectangle bounds, String text, boolean selected, boolean enabled, Color accent, boolean pressed) {
        float drop = pressed ? Constants.PIXEL_SCALE : 0f;
        NinePatch patch = pressed ? buttonPressed : (selected && enabled) ? buttonSelected : buttonNormal;
        batch.setColor(1f, 1f, 1f, enabled ? 1f : 0.45f);
        patch.draw(batch, bounds.x, bounds.y - drop, bounds.width, bounds.height);
        batch.setColor(Color.WHITE);
        text(text, bounds.x + bounds.width / 2f, bounds.y + bounds.height / 2f + 12f - drop, TEXT,
            enabled ? Color.WHITE : DISABLED_TEXT, true);
    }

    /** Altura de uma tecla desenhada (12 pixels de arte). */
    public static final float KEY_H = 12 * Constants.PIXEL_SCALE;
    private static final Color KEY_TEXT = new Color(0.86f, 0.80f, 1f, 1f);

    /**
     * Tecla em pixel art com o rótulo dentro; 'right' é a borda direita.
     * Acesa, ela afunda 1 pixel de arte. Devolve a borda esquerda.
     */
    public float keyCap(String label, int minArtWidth, float right, float y, boolean lit) {
        int px = Constants.PIXEL_SCALE;
        int textArt = (int)Math.ceil(textWidth(label, TEXT) / px);
        float w = Math.max(minArtWidth, textArt + 6) * px;
        float left = right - w;
        float drop = lit ? px : 0f;
        (lit ? keyLitPatch : keyPatch).draw(batch, left, y - drop, w, KEY_H);
        text(label, left + w / 2f, y + 40f - drop, TEXT, lit ? Color.WHITE : KEY_TEXT, true);
        return left;
    }

    /** Tecla com um desenho (setas) no lugar do rótulo. Devolve a borda esquerda. */
    public float keyCap(com.badlogic.gdx.graphics.Texture glyph, float right, float y, boolean lit) {
        int px = Constants.PIXEL_SCALE;
        float w = 11 * px;
        float left = right - w;
        float drop = lit ? px : 0f;
        (lit ? keyLitPatch : keyPatch).draw(batch, left, y - drop, w, KEY_H);
        float gw = glyph.getWidth() * px, gh = glyph.getHeight() * px;
        batch.draw(glyph, left + (w - gw) / 2f, y + 3 * px + (9 * px - gh) / 2f - drop, gw, gh);
        return left;
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

    // ------------------------------------------------------------------
    // Rodada 3: menus sem caixa, alinhados à esquerda, uma cor de destaque
    // por tela. O texto fica sobre a arte com uma faixa escura atrás (borda em
    // pontilhado, sem degradê).
    // ------------------------------------------------------------------
    public static final Color TEXT_BRIGHT = new Color(0.96f, 0.94f, 1f, 1f);
    public static final Color TEXT_DIM = new Color(0.62f, 0.58f, 0.82f, 1f);
    public static final Color LILAC = new Color(0.78f, 0.64f, 1f, 1f);
    public static final Color MINT = new Color(0.47f, 0.92f, 0.76f, 1f);
    public static final Color ROSE = new Color(1f, 0.47f, 0.62f, 1f);
    public static final Color TEAL = new Color(0.45f, 0.84f, 0.90f, 1f);
    private static final Color SHADE = new Color(0.02f, 0.014f, 0.06f, 1f);

    /** Faixa escura da esquerda até 'right' (tela inteira na altura), borda pontilhada. */
    public void shade(float right, float alpha) {
        batch.setColor(SHADE.r, SHADE.g, SHADE.b, alpha);
        batch.draw(assets.pixel, 0f, 0f, right, Constants.VIEW_HEIGHT);
        float w = assets.shadeEdge.getWidth() * Constants.PIXEL_SCALE;
        float h = assets.shadeEdge.getHeight() * Constants.PIXEL_SCALE;
        for (float y = 0f; y < Constants.VIEW_HEIGHT; y += h) batch.draw(assets.shadeEdge, right, y, w, h);
        batch.setColor(Color.WHITE);
    }

    /** Altura das maiúsculas na escala dada (o y de text() é o topo das letras). */
    public float capHeight(int pixelScale) {
        BitmapFont face = pixelScale > TEXT ? titleFont : font;
        face.getData().setScale(pixelScale);
        return face.getCapHeight();
    }

    /**
     * Item de menu só com texto: sem caixa e sem ícone. Normal: texto apagado.
     * Selecionado: texto claro, 2 pixels de arte para a direita e um traço da
     * cor de destaque embaixo. Pressionado: afunda 1 pixel e o texto fica na
     * cor de destaque. 'hit' recebe a área clicável.
     */
    public void menuItem(Rectangle hit, String label, float x, float top, int scale,
                         boolean selected, boolean pressed, Color accent) {
        int px = Constants.PIXEL_SCALE;
        float shift = (selected || pressed) ? 2 * px : 0f;
        float drop = pressed ? px : 0f;
        Color c = pressed ? accent : selected ? TEXT_BRIGHT : TEXT_DIM;
        float w = text(label, x + shift, top - drop, scale, c, false);
        float cap = capHeight(scale);
        if (selected || pressed) {
            batch.setColor(accent);
            float thick = scale > TEXT ? 2 * px : px;
            batch.draw(assets.pixel, x + shift, top - drop - cap - 3 * px - thick, w, thick);
            batch.setColor(Color.WHITE);
        }
        if (hit != null) hit.set(x - 4 * px, top - cap - 6 * px, w + 10 * px, cap + 9 * px);
    }

    /** Largura do texto na escala dada (para alinhar à direita). */
    public float textWidth(String text, int pixelScale) {
        BitmapFont face = pixelScale > TEXT ? titleFont : font;
        face.getData().setScale(pixelScale);
        layout.setText(face, text);
        return layout.width;
    }
}
