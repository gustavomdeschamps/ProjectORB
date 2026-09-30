package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.delmartec.projectorb.level.LevelDemo;
import com.delmartec.projectorb.utils.PixelViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.ButtonPress;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.HeartMeter;
import com.delmartec.projectorb.utils.UiRenderer;

/**
 * Derrota (rodada 3): mesmo esqueleto da vitória, em rosa. Diz onde o ORB
 * caiu (nome da seção) e os números da tentativa; sem frase de efeito.
 */
public final class GameOverScreen extends ScreenAdapter {
    private static final float LEFT = 112f, VALUE_X = 520f;
    private static final Color ACCENT = UiRenderer.ROSE;

    private final ProjectOrbGame game;
    private final float time;
    private final int section;
    private final int correct;
    private final int wrong;
    private final int score;
    private final String sectionName;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new PixelViewport(camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle retry = new Rectangle();
    private final Rectangle menu = new Rectangle();
    private int selected;
    private final ButtonPress press = new ButtonPress();
    private float stateTime;

    public GameOverScreen(ProjectOrbGame game, float time, int section, int correct, int wrong, int score) {
        this.game = game;
        this.time = time;
        this.section = section;
        this.correct = correct;
        this.wrong = wrong;
        this.score = score;
        LevelDemo level = new LevelDemo();
        this.sectionName = level.getSection(Math.max(0, Math.min(5, section))).title;
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
            int before = selected;
            if (retry.contains(mouse)) selected = 0;
            if (menu.contains(mouse)) selected = 1;
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
                || Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) selected = 1 - selected;
            if (selected != before) game.audio.uiMove();
            boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                    && (retry.contains(mouse) || menu.contains(mouse)));
            if (activate) {
                press.press(selected, game.settings);
                game.audio.uiConfirm();
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { game.showMenu(); return; }

        Gdx.gl.glClearColor(0.025f, 0.002f, 0.018f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(0.70f, 0.52f, 0.66f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.batch.setColor(Color.WHITE);
        game.ui.shade(760f, 0.70f);

        game.batch.setColor(1f, 1f, 1f, 0.9f);
        game.batch.draw(game.assets.orbDeath.getKeyFrame(game.assets.orbDeath.getAnimationDuration() * 0.5f), 1180f, 150f,
            Constants.PLAYER_W, Constants.PLAYER_H); // escala única
        game.batch.setColor(Color.WHITE);

        game.ui.text("DERROTA", LEFT, 980f, UiRenderer.TITLE_BIG, ACCENT, false);
        game.ui.text("CAIU EM", LEFT, 820f, UiRenderer.TEXT, UiRenderer.TEXT_DIM, false);
        game.ui.text(sectionName, LEFT, 780f, UiRenderer.TITLE, UiRenderer.TEXT_BRIGHT, false);

        int min = (int)(time / 60f), sec = (int)(time % 60f);
        row("PONTOS", String.format("%06d", score), 660f);
        row("TEMPO", String.format("%02d:%02d", min, sec), 610f);
        row("ACERTOS", Integer.toString(correct), 560f);
        row("ERROS", Integer.toString(wrong), 510f);
        HeartMeter.drawStatic(game.batch, game.assets, LEFT, 420f, 0, Constants.START_LIVES);

        game.ui.menuItem(retry, "TENTAR DE NOVO", LEFT, 330f, UiRenderer.TITLE, selected == 0, press.isPressed(0), ACCENT);
        game.ui.menuItem(menu, "MENU", LEFT, 210f, UiRenderer.TEXT, selected == 1, press.isPressed(1), ACCENT);
        game.batch.end();
    }

    private void row(String label, String value, float top) {
        game.ui.text(label, LEFT, top, UiRenderer.TEXT, UiRenderer.TEXT_DIM, false);
        game.ui.text(value, VALUE_X, top, UiRenderer.TEXT, UiRenderer.TEXT_BRIGHT, false);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
