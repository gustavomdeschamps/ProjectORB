package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.screens.GameScreen;

import java.io.File;

/**
 * Captura a JANELA real (backbuffer, não FrameBuffer) em -Dwin.w x -Dwin.h:
 * prova das barras da escala inteira. Tela de jogo e menu.
 */
public final class WindowBarsCapture {
    private WindowBarsCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/bars")).getCanonicalPath();
        new File(out).mkdirs();
        int w = Integer.getInteger("win.w", 1280), h = Integer.getInteger("win.h", 720);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Window Bars");
        config.setWindowedMode(w, h);
        config.useVsync(false);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frame;

            @Override public void create() {
                super.create();
                startGame();
                ((GameScreen) getScreen()).skipTutorial();
            }

            @Override public void render() {
                super.render();
                frame++;
                if (frame == 20) { grab(out + "/janela-" + w + "x" + h + "-jogo.png"); showMenu(); }
                if (frame == 30) { grab(out + "/janela-" + w + "x" + h + "-menu.png"); Gdx.app.exit(); }
            }

            private void grab(String path) {
                int bw = Gdx.graphics.getBackBufferWidth(), bh = Gdx.graphics.getBackBufferHeight();
                Pixmap p = ScreenUtils.getFrameBufferPixmap(0, 0, bw, bh);
                Pixmap f = new Pixmap(bw, bh, p.getFormat());
                for (int y = 0; y < bh; y++) f.drawPixmap(p, 0, y, bw, 1, 0, bh - 1 - y, bw, 1);
                PixmapIO.writePNG(Gdx.files.absolute(path), f);
                f.dispose(); p.dispose();
            }
        }, config);
    }
}
