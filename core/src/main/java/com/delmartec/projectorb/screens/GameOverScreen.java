package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.HeartMeter;
import com.delmartec.projectorb.utils.UiRenderer;

public final class GameOverScreen extends ScreenAdapter {
    private final ProjectOrbGame game;
    private final float time;
    private final int section;
    private final int correct;
    private final int wrong;
    private final int score;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT, camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle retry = new Rectangle(450f, 190f, 500f, 92f);
    private final Rectangle menu = new Rectangle(970f, 190f, 500f, 92f);
    private int selected;
    private float stateTime;

    public GameOverScreen(ProjectOrbGame game, float time, int section, int correct, int wrong, int score) {
        this.game = game;
        this.time = time;
        this.section = section;
        this.correct = correct;
        this.wrong = wrong;
        this.score = score;
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
    }

    @Override
    public void render(float delta) {
        stateTime += delta;
        viewport.apply();
        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        if (retry.contains(mouse)) selected = 0;
        if (menu.contains(mouse)) selected = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) selected = 1 - selected;
        boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)
                && (retry.contains(mouse) || menu.contains(mouse)));
        if (activate) {
            if (selected == 0) game.startGame(); else game.showMenu();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { game.showMenu(); return; }

        Gdx.gl.glClearColor(0.025f, 0.002f, 0.018f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(0.82f, 0.35f, 0.48f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.batch.setColor(0.12f, 0.0f, 0.06f, 0.58f);
        game.batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);

        game.batch.draw(game.assets.bossEnraged.getKeyFrame(stateTime), 1370f, 480f,
            113f * Constants.PIXEL_SCALE, 113f * Constants.PIXEL_SCALE); // escala única
        game.batch.setColor(1f, 1f, 1f, 0.86f);
        game.batch.draw(game.assets.orbDeath.getKeyFrame(game.assets.orbDeath.getAnimationDuration() * 0.5f), 120f, 105f,
            Constants.PLAYER_W, Constants.PLAYER_H); // escala única
        game.batch.setColor(Color.WHITE);

        game.ui.panel(390f, 315f, 1140f, 590f, UiRenderer.DANGER, 1f);
        game.ui.crystalCorners(390f, 315f, 1140f, 590f, 70f, 1f);
        game.ui.textCentered("DERROTA", 960f, 740f, 3.5f, UiRenderer.DANGER);
        game.ui.textCentered("O Void desestabilizou ORB", 960f, 660f, 1.15f, Color.WHITE);

        int min = (int)(time / 60f), sec = (int)(time % 60f);
        game.ui.textCentered(String.format("SCORE %06d   -   TEMPO %02d:%02d", score, min, sec),
            960f, 590f, 1.05f, new Color(0.90f, 0.86f, 1f, 1f));
        game.ui.textCentered("ÁREA " + (section + 1) + "/6   -   ACERTOS " + correct + "   -   ERROS " + wrong,
            960f, 535f, 0.90f, new Color(0.78f, 0.82f, 0.98f, 1f));
        // Todas as vidas perdidas: corações só em contorno.
        float hw = HeartMeter.rowWidth(game.assets, Constants.START_LIVES);
        HeartMeter.drawStatic(game.batch, game.assets, 960f - hw / 2f, 450f, 0, Constants.START_LIVES);
        game.ui.textCentered("Leia a forma e acerte os pontos marcados.",
            960f, 430f, 0.84f, UiRenderer.CYAN);

        game.ui.button(retry, "REINICIAR", selected == 0, true, UiRenderer.DANGER);
        game.ui.button(menu, "MENU", selected == 1, true, UiRenderer.CYAN);
        game.batch.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
