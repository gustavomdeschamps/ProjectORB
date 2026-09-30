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
import com.delmartec.projectorb.utils.ButtonPress;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/**
 * "Como jogar": só os controles, desenhados em pixel art, cada um com uma
 * palavra. Toda a explicação em texto fica com o NPC Pi dentro da fase. A
 * tecla acende enquanto o jogador a pressiona.
 */
public final class HowToPlayScreen extends ScreenAdapter {
    private static final int PX = Constants.PIXEL_SCALE;
    private static final float ROW = 104f;
    private static final float KEYS_RIGHT = 900f;
    private static final float LABEL_X = 948f;
    private static final Color LABEL = new Color(0.95f, 0.94f, 1f, 1f);

    private final ProjectOrbGame game;
    private final MenuScreen menu;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new PixelViewport(camera);
    private final Vector2 pointer = new Vector2();
    private final Rectangle back = new Rectangle(760f, 64f, 400f, 82f);
    private final ButtonPress press = new ButtonPress();

    public HowToPlayScreen(ProjectOrbGame game, MenuScreen menu) {
        this.game = game;
        this.menu = menu;
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
    }

    @Override public void render(float delta) {
        viewport.apply();
        pointer.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(pointer);
        // VOLTAR é o único botão, então tem o foco do teclado (selecionado);
        // ENTER/ESC/clique afundam e a tela volta quando ele sobe.
        if (press.update(delta) == 0) {
            game.setScreen(menu);
            return;
        }
        if (!press.isBusy() && (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
            || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || (back.contains(pointer) && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)))) {
            press.press(0, game.settings);
        }

        Gdx.gl.glClearColor(0.01f, 0.01f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(0.52f, 0.56f, 0.78f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);
        game.ui.text("COMO JOGAR", 960f, 1000f, UiRenderer.TITLE, Color.WHITE, true);

        float y = 820f;
        // A D  /  ← →   MOVER
        float x = KEYS_RIGHT;
        x = keyArrow(game.assets.arrowRight, x, y, pressed(Input.Keys.RIGHT));
        x = keyArrow(game.assets.arrowLeft, x, y, pressed(Input.Keys.LEFT));
        x = key("D", 11, x - 24f, y, pressed(Input.Keys.D));
        key("A", 11, x, y, pressed(Input.Keys.A));
        label("MOVER", y);

        y -= ROW;
        key("ESPAÇO", 34, KEYS_RIGHT, y, pressed(Input.Keys.SPACE));
        label("PULAR", y);

        y -= ROW;
        key("SHIFT", 24, KEYS_RIGHT, y, pressed(Input.Keys.SHIFT_LEFT) || pressed(Input.Keys.SHIFT_RIGHT));
        label("DASH", y);

        y -= ROW;
        boolean click = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        Texture m = click ? game.assets.mouseLit : game.assets.mouse;
        float mw = m.getWidth() * PX, mh = m.getHeight() * PX;
        game.batch.draw(m, KEYS_RIGHT - mw, y - (mh - UiRenderer.KEY_H) / 2f, mw, mh);
        label("ATIRAR", y);

        y -= ROW;
        key("E", 11, KEYS_RIGHT, y, pressed(Input.Keys.E));
        label("PORTAL", y);

        y -= ROW;
        x = key("TAB", 17, KEYS_RIGHT, y, pressed(Input.Keys.TAB));
        key("C", 11, x - 24f, y, pressed(Input.Keys.C));
        label("CÓDEX", y);

        y -= ROW;
        key("ESC", 17, KEYS_RIGHT, y, pressed(Input.Keys.ESCAPE));
        label("PAUSA", y);

        game.ui.button(back, "VOLTAR", true, true, UiRenderer.MAGENTA, press.isPressed(0));
        game.batch.end();
    }

    private static boolean pressed(int keycode) {
        return Gdx.input.isKeyPressed(keycode);
    }

    /** Tecla com rótulo; 'right' é a borda direita. Devolve a borda esquerda livre. */
    private float key(String text, int minArtWidth, float right, float y, boolean lit) {
        return game.ui.keyCap(text, minArtWidth, right, y, lit) - 12f;
    }

    private float keyArrow(Texture glyph, float right, float y, boolean lit) {
        return game.ui.keyCap(glyph, right, y, lit) - 12f;
    }

    private void label(String text, float y) {
        game.ui.text(text, LABEL_X, y + 40f, UiRenderer.TEXT, LABEL, false);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
