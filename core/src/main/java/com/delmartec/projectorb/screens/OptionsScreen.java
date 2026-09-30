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
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.ButtonPress;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/** Opcoes persistentes, acessiveis tanto pelo menu quanto durante uma partida. */
public final class OptionsScreen extends ScreenAdapter {
    private final ProjectOrbGame game;
    private final Screen returnScreen;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT, camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle[] rows = {
        new Rectangle(635f, 620f, 650f, 76f),
        new Rectangle(635f, 532f, 650f, 76f),
        new Rectangle(635f, 444f, 650f, 76f),
        new Rectangle(635f, 356f, 650f, 76f),
        new Rectangle(635f, 268f, 650f, 76f),
        new Rectangle(750f, 170f, 420f, 76f)
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

        // Volume: setas ajustam na hora; ENTER/ESPAÇO/clique afundam o botão e
        // a ação acontece quando ele volta (ButtonPress).
        int done = press.update(delta);
        if (done == 0) {
            game.settings.adjustMasterVolume(0.1f);
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
            for (int i = 0; i < rows.length; i++) if (rows[i].contains(mouse)) selected = i;
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) selected = (selected + rows.length - 1) % rows.length;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) selected = (selected + 1) % rows.length;
            boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || (rows[selected].contains(mouse) && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT));
            boolean left = Gdx.input.isKeyJustPressed(Input.Keys.LEFT);
            boolean right = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT);
            if (selected == 0 && (left || right)) {
                game.settings.adjustMasterVolume(left ? -0.1f : 0.1f);
                game.audio.refreshVolume();
            } else if (activate) {
                press.press(selected, game.settings);
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
        game.batch.setColor(0.72f, 0.74f, 0.88f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.batch.setColor(0.005f, 0.004f, 0.025f, 0.68f);
        game.batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);

        game.ui.panel(545f, 145f, 830f, 780f, UiRenderer.CYAN, 1f);
        game.ui.textCentered("OPÇÕES", 960f, 850f, 2.7f, Color.WHITE);

        String volume = "VOLUME GERAL   " + Math.round(game.settings.getMasterVolume() * 100f) + "%";
        game.ui.button(rows[0], volume, selected == 0, true, UiRenderer.MAGENTA, press.isPressed(0));
        game.ui.button(rows[1], "TREMOR DE TELA   " + onOff(game.settings.isScreenShakeEnabled()),
            selected == 1, true, UiRenderer.MAGENTA, press.isPressed(1));
        game.ui.button(rows[2], "MOVIMENTO REDUZIDO   " + onOff(game.settings.isReducedMotion()),
            selected == 2, true, UiRenderer.MAGENTA, press.isPressed(2));
        game.ui.button(rows[3], "SOM DAS FALAS   " + onOff(game.settings.isDialogueSoundEnabled()),
            selected == 3, true, UiRenderer.MAGENTA, press.isPressed(3));
        game.ui.button(rows[4], "ABERTURA   " + onOff(game.settings.isIntroEnabled()),
            selected == 4, true, UiRenderer.MAGENTA, press.isPressed(4));
        game.ui.button(rows[5], "VOLTAR", selected == 5, true, UiRenderer.CYAN, press.isPressed(5));

        game.batch.end();
    }

    private String onOff(boolean enabled) { return enabled ? "SIM" : "NÃO"; }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
