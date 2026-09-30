package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.delmartec.projectorb.utils.PixelViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.ButtonPress;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.HeartMeter;
import com.delmartec.projectorb.utils.UiRenderer;

public final class VictoryScreen extends ScreenAdapter {
    private final ProjectOrbGame game;
    private final float time;
    private final int lives;
    private final int correct;
    private final int wrong;
    private final int score;
    private final int crystals;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new PixelViewport(camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle next = new Rectangle(450f, 175f, 500f, 92f);
    private final Rectangle menu = new Rectangle(970f, 175f, 500f, 92f);
    private int selected;
    private final ButtonPress press = new ButtonPress();
    private float stateTime;

    public VictoryScreen(ProjectOrbGame game, float time, int lives, int correct, int wrong,
                         int score, int crystals) {
        this.game = game;
        this.time = time;
        this.lives = lives;
        this.correct = correct;
        this.wrong = wrong;
        this.score = score;
        this.crystals = crystals;
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
    }

    @Override
    public void render(float delta) {
        stateTime += delta;
        viewport.apply();
        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        int done = press.update(delta);
        if (done == 0) { game.startGame(); return; }
        if (done == 1) { game.showMenu(); return; }
        if (!press.isBusy()) {
            if (next.contains(mouse)) selected = 0;
            if (menu.contains(mouse)) selected = 1;
            if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) selected = 1 - selected;
            boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                    && (next.contains(mouse) || menu.contains(mouse)));
            if (activate) press.press(selected, game.settings);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { game.showMenu(); return; }

        Gdx.gl.glClearColor(0.006f, 0.015f, 0.035f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(1f, 1f, 1f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.batch.setColor(0.015f, 0.018f, 0.055f, 0.34f);
        game.batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);

        float orbBob = game.settings.isReducedMotion() ? 0f : MathUtils.sin(stateTime * 3.2f) * 10f;
        game.batch.draw(game.assets.orbIdle.getKeyFrame(stateTime), 115f, 210f + orbBob,
            Constants.PLAYER_W, Constants.PLAYER_H); // escala única
        game.batch.draw(game.assets.portal, 1435f, 250f,
            game.assets.portal.getWidth() * Constants.PIXEL_SCALE,
            game.assets.portal.getHeight() * Constants.PIXEL_SCALE); // escala única

        game.ui.panel(390f, 300f, 1140f, 620f, UiRenderer.SUCCESS, 1f);
        game.ui.textCentered("VITÓRIA", 960f, 770f, 3.5f, new Color(0.90f, 0.95f, 1f, 1f));
        game.ui.textCentered("RIFT ESTABILIZADO", 960f, 665f, 1.25f, UiRenderer.SUCCESS);
        game.ui.textCentered("Você leu as formas no meio da ação.",
            960f, 615f, 0.92f, Color.WHITE);

        int min = (int)(time / 60f), sec = (int)(time % 60f);
        float accuracy = correct + wrong == 0 ? 100f : correct * 100f / (correct + wrong);
        game.ui.textCentered(String.format("SCORE %06d   -   CRISTAIS %03d", score, crystals),
            960f, 565f, 1.05f, new Color(0.92f, 0.86f, 1f, 1f));
        game.ui.textCentered(String.format("TEMPO %02d:%02d   -   PRECISÃO %.0f%%", min, sec, accuracy),
            960f, 510f, 0.90f, new Color(0.76f, 0.84f, 1f, 1f));
        // Vidas que sobraram, em corações.
        float hw = HeartMeter.rowWidth(game.assets, Constants.START_LIVES);
        HeartMeter.drawStatic(game.batch, game.assets, 960f - hw / 2f, 420f, lives, Constants.START_LIVES);
        game.ui.textCentered("Vértices - Lados - Ângulos - Simetria",
            960f, 395f, 0.80f, UiRenderer.CYAN);

        game.ui.button(next, "JOGAR NOVAMENTE", selected == 0, true, UiRenderer.SUCCESS, press.isPressed(0));
        game.ui.button(menu, "MENU", selected == 1, true, UiRenderer.CYAN, press.isPressed(1));
        game.batch.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
