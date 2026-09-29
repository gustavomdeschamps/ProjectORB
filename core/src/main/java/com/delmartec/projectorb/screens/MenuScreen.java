package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/** Tela inicial inspirada na composicao aprovada: mundo vivo, placa central e ORB em cena. */
public final class MenuScreen extends ScreenAdapter {
    private final ProjectOrbGame game;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT, camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle[] buttons = {
        new Rectangle(710f, 448f, 500f, 82f),
        new Rectangle(710f, 351f, 500f, 82f),
        new Rectangle(710f, 254f, 500f, 82f),
        new Rectangle(710f, 157f, 500f, 82f)
    };
    private int selected;
    private int pendingAction = -1;
    private float pressTimer;
    private float stateTime;

    public MenuScreen(ProjectOrbGame game) {
        this.game = game;
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
    }

    @Override public void show() { game.audio.startAmbient(); }

    @Override
    public void render(float delta) {
        stateTime += delta;
        viewport.apply();
        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);

        if (pendingAction >= 0) {
            pressTimer -= delta;
            if (pressTimer <= 0f) {
                if (pendingAction == 0) game.startGame();
                else if (pendingAction == 1) game.setScreen(new HowToPlayScreen(game, this));
                else if (pendingAction == 2) game.setScreen(new OptionsScreen(game, this));
                else Gdx.app.exit();
                pendingAction = -1;
                return;
            }
        } else {
            for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(mouse)) selected = i;
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) selected = (selected + buttons.length - 1) % buttons.length;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) selected = (selected + 1) % buttons.length;
            boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || (buttons[selected].contains(mouse) && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT));
            if (activate) {
                pendingAction = selected;
                pressTimer = game.settings.isReducedMotion() ? 0.06f : 0.14f;
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { Gdx.app.exit(); return; }

        Gdx.gl.glClearColor(0.008f, 0.005f, 0.025f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(0.88f, 0.90f, 1f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.batch.setColor(0.008f, 0.006f, 0.03f, 0.12f);
        game.batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, 430f);
        game.batch.setColor(Color.WHITE);

        float floor = 150f;
        game.batch.setColor(1f, 1f, 1f, 0.72f);
        game.batch.draw(game.assets.orbShadow, 215f, floor - 22f, 190f, 31f);
        game.batch.setColor(Color.WHITE);
        float bob = game.settings.isReducedMotion() ? 0f : MathUtils.sin(stateTime * 2.2f) * 2f;
        drawGrounded(game.assets.orbIdle.getKeyFrame(stateTime), 310f, floor + bob,
            Constants.PLAYER_CANVAS * 6f, 1f); // 6x

        game.ui.panel(560f, 105f, 800f, 845f, UiRenderer.MAGENTA, 1f);
        game.ui.textCentered("ORB", 960f, 845f, 4.5f, new Color(0.92f, 0.94f, 1f, 1f));
        game.ui.textCentered("FLOATING RUINS", 960f, 685f, 1.5f, UiRenderer.CYAN);
        game.ui.textCentered("GEOMETRIA EM AÇÃO", 960f, 630f, 1.08f,
            new Color(0.78f, 0.74f, 1f, 1f));

        game.ui.button(buttons[0], "JOGAR", selected == 0, true, UiRenderer.MAGENTA, pendingAction == 0);
        game.ui.button(buttons[1], "COMO JOGAR", selected == 1, true, UiRenderer.CYAN, pendingAction == 1);
        game.ui.button(buttons[2], "OPÇÕES", selected == 2, true, UiRenderer.CYAN, pendingAction == 2);
        game.ui.button(buttons[3], "SAIR", selected == 3, true, UiRenderer.CYAN, pendingAction == 3);
        game.ui.textCentered("Explore  -  Descubra  -  Continue", 960f, 64f, 0.95f,
            new Color(0.78f, 0.84f, 1f, 1f));
        game.batch.end();
    }

    private void drawGrounded(TextureRegion frame, float centerX, float floorY, float size, float alpha) {
        game.batch.setColor(0.82f, 0.76f, 0.93f, alpha);
        game.batch.draw(frame, centerX - size / 2f,
            floorY - Constants.CHAR_BASELINE * size, size, size);
        game.batch.setColor(Color.WHITE);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
