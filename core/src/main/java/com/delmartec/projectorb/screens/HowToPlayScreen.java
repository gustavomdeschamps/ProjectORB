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
import com.delmartec.projectorb.utils.UiRenderer;

/** Instruções acessíveis pelo menu, sem interromper o tutorial dentro da fase. */
public final class HowToPlayScreen extends ScreenAdapter {
    private final ProjectOrbGame game;
    private final MenuScreen menu;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT, camera);
    private final Vector2 mouse = new Vector2();
    private final Rectangle back = new Rectangle(760f, 205f, 400f, 82f);

    public HowToPlayScreen(ProjectOrbGame game, MenuScreen menu) {
        this.game = game;
        this.menu = menu;
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
    }

    @Override public void render(float delta) {
        viewport.apply();
        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
            || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || (back.contains(mouse) && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT))) {
            game.setScreen(menu);
            return;
        }

        Gdx.gl.glClearColor(0.01f, 0.01f, 0.04f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(0.62f, 0.67f, 0.87f, 1f);
        game.batch.draw(game.assets.menuBackground, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT); // 480x270 a 4x
        game.batch.setColor(Color.WHITE);
        game.ui.panel(420f, 120f, 1080f, 850f, UiRenderer.CYAN, 1f);
        game.ui.textCentered("COMO JOGAR", 960f, 830f, 2.8f, Color.WHITE);
        game.ui.textCentered("MOVIMENTO", 960f, 748f, 1.3f, UiRenderer.CYAN);
        game.ui.textCentered("A / D: mover    ESPAÇO: salto duplo", 960f, 700f, 1.1f, Color.WHITE);
        game.ui.textCentered("SHIFT: dash    MOUSE: mirar e atirar", 960f, 653f, 1.1f, Color.WHITE);
        game.ui.textCentered("COMBATE", 960f, 575f, 1.3f, UiRenderer.CYAN);
        game.ui.textCentered("Atire nos alvos brilhantes do inimigo.",
            960f, 526f, 1.1f, Color.WHITE);
        game.ui.textCentered("Cada acerto apaga um alvo. Desvie dos projéteis.",
            960f, 481f, 1f, UiRenderer.SOFT_TEXT);
        game.ui.textCentered("ESC: pausa    E: atravessa o portal final",
            960f, 426f, 1f, UiRenderer.SOFT_TEXT);
        game.ui.button(back, "VOLTAR", back.contains(mouse), true, UiRenderer.MAGENTA);
        game.batch.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
}
