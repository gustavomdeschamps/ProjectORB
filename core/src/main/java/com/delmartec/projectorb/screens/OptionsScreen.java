package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.delmartec.projectorb.utils.PixelViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.ButtonPress;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/**
 * Opções persistentes, acessíveis pelo menu e durante a partida. Rodada 3:
 * mesmo estilo do menu (arte ao fundo, faixa escura à esquerda, itens só em
 * texto alinhados à esquerda). O valor fica numa coluna própria; o volume é
 * uma barra de 10 segmentos. Destaque: azul-petróleo.
 */
public final class OptionsScreen extends ScreenAdapter {
    private static final int PX = Constants.PIXEL_SCALE;
    private static final float LEFT = 112f, VALUE_X = 690f;
    private static final Color ACCENT = UiRenderer.TEAL;

    private final ProjectOrbGame game;
    private final Screen returnScreen;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new PixelViewport(camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle[] rows = {
        new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle()
    };
    private int selected;
    private final ButtonPress press = new ButtonPress();
    private float stateTime;

    public OptionsScreen(ProjectOrbGame game, Screen returnScreen) {
        this.game = game;
        this.returnScreen = returnScreen;
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
    }

    @Override
    public void render(float delta) {
        stateTime += delta;
        viewport.apply();
        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);

        // Volume: setas ajustam na hora; ENTER/ESPAÇO/clique afundam o item e
        // a ação acontece quando ele volta (ButtonPress).
        int done = press.update(delta);
        if (done == 0) {
            game.settings.adjustMasterVolume(game.settings.getMasterVolume() >= 1f ? -1f : 0.1f);
            game.audio.refreshVolume();
        } else if (done == 1) {
            game.settings.toggleScreenShake();
        } else if (done == 2) {
            game.settings.toggleReducedMotion();
        } else if (done == 3) {
            game.settings.toggleDialogueSound();
        } else if (done == 4) {
            game.settings.toggleIntro();
        } else if (done == 5) {
            game.setScreen(returnScreen);
            return;
        }
        if (!press.isBusy()) {
            int before = selected;
            for (int i = 0; i < rows.length; i++) if (rows[i].contains(mouse)) selected = i;
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) selected = (selected + rows.length - 1) % rows.length;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) selected = (selected + 1) % rows.length;
            if (selected != before) game.audio.uiMove();
            boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || (rows[selected].contains(mouse) && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT));
            boolean left = Gdx.input.isKeyJustPressed(Input.Keys.LEFT);
            boolean right = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT);
            if (selected == 0 && (left || right)) {
                game.settings.adjustMasterVolume(left ? -0.1f : 0.1f);
                game.audio.refreshVolume();
                game.audio.uiMove();
            } else if (activate) {
                press.press(selected, game.settings);
                game.audio.uiConfirm();
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                game.setScreen(returnScreen);
                return;
            }
        }

        Gdx.gl.glClearColor(0.008f, 0.005f, 0.025f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(0.78f, 0.80f, 0.92f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.batch.setColor(Color.WHITE);
        game.ui.shade(900f, 0.72f);

        game.ui.text("OPÇÕES", LEFT, 960f, UiRenderer.TITLE, UiRenderer.TEXT_BRIGHT, false);
        String[] labels = { "VOLUME", "TREMOR DE TELA", "MOVIMENTO REDUZIDO", "SOM DAS FALAS", "ABERTURA", "VOLTAR" };
        float top = 800f;
        for (int i = 0; i < labels.length; i++) {
            if (i == 5) top -= 40f;
            game.ui.menuItem(rows[i], labels[i], LEFT, top, UiRenderer.TEXT, selected == i, press.isPressed(i), ACCENT);
            if (i == 0) drawVolume(top, selected == 0);
            else if (i < 5) {
                boolean on = switch (i) {
                    case 1 -> game.settings.isScreenShakeEnabled();
                    case 2 -> game.settings.isReducedMotion();
                    case 3 -> game.settings.isDialogueSoundEnabled();
                    default -> game.settings.isIntroEnabled();
                };
                game.ui.text(on ? "SIM" : "NÃO", VALUE_X, top, UiRenderer.TEXT,
                    selected == i ? UiRenderer.TEXT_BRIGHT : UiRenderer.TEXT_DIM, false);
            }
            // a linha inteira é clicável (rótulo + valor)
            rows[i].width = Math.max(rows[i].width, VALUE_X + 160f - rows[i].x);
            top -= 84f;
        }
        game.batch.end();
    }

    /** Volume em 10 segmentos (3x4 pixels de arte, 1 de vão). */
    private void drawVolume(float top, boolean sel) {
        int lit = Math.round(game.settings.getMasterVolume() * 10f);
        float y = top - game.ui.capHeight(UiRenderer.TEXT);
        for (int k = 0; k < 10; k++) {
            game.batch.setColor(k < lit ? (sel ? ACCENT : UiRenderer.TEXT_DIM) : new Color(0.16f, 0.12f, 0.28f, 1f));
            game.batch.draw(game.assets.pixel, VALUE_X + k * 4 * PX, y, 3 * PX, 5 * PX);
        }
        game.batch.setColor(Color.WHITE);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
