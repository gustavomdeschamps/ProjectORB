package com.delmartec.projectorb.dialogue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/**
 * Caixa de fala no estilo pixel do HUD: moldura de pixel art, retrato do
 * falante, nome e texto com máquina de escrever. Desenha em coordenadas de
 * HUD (1920x1080); quem chama já abriu o batch.
 */
public final class DialogueBox {
    private static final int PX = Constants.PIXEL_SCALE;
    // Na faixa de cima, abaixo do HUD: embaixo ela cobria o ORB e o NPC.
    private static final float X = 240f, Y = 676f, W = 1440f, H = 232f;
    private static final float TEXT_X = X + 196f;
    private static final float TEXT_W = W - 196f - 64f;
    /** Cor do nome por personagem (Pi: azul-petróleo claro; Octógono: azul-royal claro). */
    private static final Color NAME_PI = new Color(0.55f, 0.86f, 0.88f, 1f);
    private static final Color NAME_OCTO = new Color(0.60f, 0.70f, 1f, 1f);
    private static final Color NAME_OTHER = new Color(0.82f, 0.70f, 1f, 1f);
    private static final Color TEXT = new Color(0.95f, 0.94f, 1f, 1f);

    private final ProjectOrbGame game;
    private final StringBuilder wrapped = new StringBuilder();
    private String wrappedSource;
    private float time;

    public DialogueBox(ProjectOrbGame game) {
        this.game = game;
    }

    public void update(float delta, DialogueRunner runner) {
        time += delta;
        int n = runner.newCharacters();
        if (n > 0) {
            // um blip por grupo de letras visíveis, com altura variando pouco
            String visible = runner.visibleText();
            char c = visible.isEmpty() ? ' ' : visible.charAt(visible.length() - 1);
            if (c != ' ' && visible.length() % 2 == 0) game.audio.blip(0.92f + (c % 5) * 0.04f);
        }
    }

    /** Desenha a caixa. portrait pode ser nulo. */
    public void draw(SpriteBatch batch, DialogueRunner runner, TextureRegion portrait) {
        if (!runner.isActive()) return;
        game.ui.hudPanel(X, Y, W, H);

        if (portrait != null) {
            float pw = portrait.getRegionWidth() * PX, ph = portrait.getRegionHeight() * PX;
            batch.draw(portrait, X + 36f, Y + (H - ph) / 2f, pw, ph);
        }
        String speaker = runner.getScript().speaker;
        Color name = "PI".equals(speaker) ? NAME_PI : speaker != null && speaker.startsWith("OCT") ? NAME_OCTO : NAME_OTHER;
        game.ui.text(speaker, TEXT_X, Y + H - 28f, UiRenderer.TEXT, name, false);

        String full = runner.line().text;
        String body = wrap(full);
        String visible = body.substring(0, runner.visibleText().length());
        game.ui.text(visible, TEXT_X, Y + H - 80f, UiRenderer.TEXT, TEXT, false);

        boolean blink = game.settings.isReducedMotion() || ((int)(time * 2.5f)) % 2 == 0;
        if (runner.isLineComplete() && !runner.isInteractive() && blink) {
            // "próximo": seta para baixo no canto
            Texture arrow = game.assets.guideArrow;
            float aw = arrow.getWidth() * PX, ah = arrow.getHeight() * PX;
            batch.draw(arrow, X + W - 64f - aw, Y + 28f + ah, aw, -ah);
        } else if (runner.isInteractive() && runner.isLineComplete()) {
            // No passo interativo: a tecla (desenhada) que o passo pede, no
            // canto, piscando devagar. Sem palavra de ordem.
            drawKeyHint(runner.line().waitFor, blink);
        }
    }

    private void drawKeyHint(String waitFor, boolean on) {
        float right = X + W - 48f, y = Y + 24f;
        boolean lit = !on;
        switch (waitFor == null ? "" : waitFor) {
            case "move" -> {
                float l = game.ui.keyCap("D", 11, right, y, lit) - 12f;
                game.ui.keyCap("A", 11, l, y, lit);
            }
            case "jump", "doubleJump" -> game.ui.keyCap("ESPAÇO", 34, right, y, lit);
            case "dash" -> game.ui.keyCap("SHIFT", 24, right, y, lit);
            case "hitTarget", "shoot" -> {
                Texture m = lit ? game.assets.mouseLit : game.assets.mouse;
                float mw = m.getWidth() * PX, mh = m.getHeight() * PX;
                game.batch.draw(m, right - mw, y - 8f, mw, mh);
            }
            default -> { }
        }
    }

    /**
     * Quebra gulosa pelo texto COMPLETO trocando espaços por '\n': as
     * palavras não pulam de linha durante a máquina de escrever e o número
     * de letras continua igual ao do texto original.
     */
    private String wrap(String text) {
        if (text.equals(wrappedSource)) return wrapped.toString();
        wrappedSource = text;
        wrapped.setLength(0);
        wrapped.append(text);
        int lineStart = 0;
        int lastSpace = -1;
        for (int i = 0; i < wrapped.length(); i++) {
            char c = wrapped.charAt(i);
            if (c == '\n') { lineStart = i + 1; lastSpace = -1; continue; }
            if (c == ' ') lastSpace = i;
            float w = game.ui.textWidth(wrapped.substring(lineStart, i + 1), UiRenderer.TEXT);
            if (w > TEXT_W && lastSpace > lineStart) {
                wrapped.setCharAt(lastSpace, '\n');
                lineStart = lastSpace + 1;
                lastSpace = -1;
            }
        }
        return wrapped.toString();
    }
}
