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

/**
 * Vitória (rodada 3): título grande à esquerda, números em coluna com
 * rótulo apagado e valor claro (hierarquia: pontos maior), corações que
 * sobraram e os itens JOGAR DE NOVO / MENU só em texto. Sem frase de efeito.
 * Destaque: verde-menta. O resultado do quiz entra na etapa 7.
 */
public final class VictoryScreen extends ScreenAdapter {
    private static final float LEFT = 112f, VALUE_X = 520f;
    private static final Color ACCENT = UiRenderer.MINT;

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
    private final Rectangle next = new Rectangle();
    private final Rectangle menu = new Rectangle();
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
            int before = selected;
            if (next.contains(mouse)) selected = 0;
            if (menu.contains(mouse)) selected = 1;
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.DOWN)
                || Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) selected = 1 - selected;
            if (selected != before) game.audio.uiMove();
            boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                    && (next.contains(mouse) || menu.contains(mouse)));
            if (activate) {
                press.press(selected, game.settings);
                game.audio.uiConfirm();
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { game.showMenu(); return; }

        Gdx.gl.glClearColor(0.006f, 0.015f, 0.035f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(Color.WHITE);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.ui.shade(760f, 0.66f);

        // o ORB na arte, à direita, quicando de leve (parado em movimento reduzido)
        float bob = game.settings.isReducedMotion() ? 0f : Math.round(MathUtils.sin(stateTime * 3.2f) * 2f) * Constants.PIXEL_SCALE;
        game.batch.draw(game.assets.orbIdle.getKeyFrame(stateTime), 1180f, 180f + bob,
            Constants.PLAYER_W, Constants.PLAYER_H); // escala única

        game.ui.text("VITÓRIA", LEFT, 980f, UiRenderer.TITLE_BIG, ACCENT, false);

        int min = (int)(time / 60f), sec = (int)(time % 60f);
        int accuracy = correct + wrong == 0 ? 100 : Math.round(correct * 100f / (correct + wrong));
        game.ui.text("PONTOS", LEFT, 820f, UiRenderer.TEXT, UiRenderer.TEXT_DIM, false);
        game.ui.text(String.format("%06d", score), LEFT, 780f, UiRenderer.TITLE, UiRenderer.TEXT_BRIGHT, false);
        row("TEMPO", String.format("%02d:%02d", min, sec), 660f);
        row("PRECISÃO", accuracy + "%", 610f);
        row("CRISTAIS", Integer.toString(crystals), 560f);
        HeartMeter.drawStatic(game.batch, game.assets, LEFT, 450f, lives, Constants.START_LIVES);

        game.ui.menuItem(next, "JOGAR DE NOVO", LEFT, 330f, UiRenderer.TITLE, selected == 0, press.isPressed(0), ACCENT);
        game.ui.menuItem(menu, "MENU", LEFT, 210f, UiRenderer.TEXT, selected == 1, press.isPressed(1), ACCENT);
        game.batch.end();
    }

    private void row(String label, String value, float top) {
        game.ui.text(label, LEFT, top, UiRenderer.TEXT, UiRenderer.TEXT_DIM, false);
        game.ui.text(value, VALUE_X, top, UiRenderer.TEXT, UiRenderer.TEXT_BRIGHT, false);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
