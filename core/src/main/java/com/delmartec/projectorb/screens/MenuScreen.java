package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.delmartec.projectorb.utils.PixelViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/**
 * Tela de início (rodada 3): a arte ocupa a tela; à esquerda, sobre uma faixa
 * escura de borda pontilhada, o logo desenhado à mão em cima e os botões só
 * em texto, alinhados à esquerda. JOGAR é maior que o resto (hierarquia).
 * Uma cor de destaque: lilás.
 */
public final class MenuScreen extends ScreenAdapter {
    private static final int PX = Constants.PIXEL_SCALE;
    private static final float LEFT = 112f;
    private static final Color ACCENT = UiRenderer.LILAC;
    private static final String[] LABELS = { "JOGAR", "COMO JOGAR", "OPÇÕES", "SAIR" };

    private final ProjectOrbGame game;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new PixelViewport(camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle[] buttons = { new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle() };
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
            int before = selected;
            for (int i = 0; i < buttons.length; i++) if (buttons[i].contains(mouse)) selected = i;
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) selected = (selected + buttons.length - 1) % buttons.length;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) selected = (selected + 1) % buttons.length;
            if (selected != before) game.audio.uiMove();
            boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || (buttons[selected].contains(mouse) && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT));
            if (activate) {
                pendingAction = selected;
                pressTimer = game.settings.isReducedMotion() ? 0.06f : 0.14f;
                game.audio.uiConfirm();
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) { Gdx.app.exit(); return; }

        Gdx.gl.glClearColor(0.008f, 0.005f, 0.025f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(Color.WHITE);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.ui.shade(704f, 0.62f);

        // logo desenhado à mão, na escala única
        Texture logo = game.assets.logo;
        float lw = logo.getWidth() * PX, lh = logo.getHeight() * PX;
        game.batch.draw(logo, LEFT - 16f, 1080f - 88f - lh, lw, lh);

        float top = 540f;
        for (int i = 0; i < LABELS.length; i++) {
            int scale = i == 0 ? UiRenderer.TITLE : UiRenderer.TEXT;
            game.ui.menuItem(buttons[i], LABELS[i], LEFT, top, scale, selected == i, pendingAction == i, ACCENT);
            top -= i == 0 ? 120f : 76f;
        }
        game.batch.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
