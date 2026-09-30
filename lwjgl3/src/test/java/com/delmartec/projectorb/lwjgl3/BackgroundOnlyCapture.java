package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.screens.GameScreen;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Desenha SÓ o fundo (GameScreen.renderBackground) em 1920x1080 nas posições
 * de câmera x = 1000, 4000, 7000, 11000, com tempo de jogo fixo (0), para
 * comparar o fundo entre versões pixel a pixel. Autocontido (sem ScreenCapture)
 * para poder rodar também numa cópia do baseline. Saída em -Dsmoke.out.
 */
public final class BackgroundOnlyCapture {
    private BackgroundOnlyCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/bg-only")).getCanonicalPath();
        new File(out).mkdirs();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Background Only");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new ProjectOrbGame() {
            @Override public void create() {
                super.create();
                try {
                    GameScreen s = new GameScreen(this);
                    Field cam = GameScreen.class.getDeclaredField("worldCamera");
                    cam.setAccessible(true);
                    Field time = GameScreen.class.getDeclaredField("gameTime");
                    time.setAccessible(true);
                    Method bg = GameScreen.class.getDeclaredMethod("renderBackground");
                    bg.setAccessible(true);
                    for (int x : new int[] { 1000, 4000, 7000, 11000 }) {
                        ((OrthographicCamera) cam.get(s)).position.x = x;
                        time.setFloat(s, 0f);
                        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
                        fbo.begin();
                        s.resize(1920, 1080);
                        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                        bg.invoke(s);
                        Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1920, 1080);
                        fbo.end();
                        Pixmap f = new Pixmap(1920, 1080, p.getFormat());
                        for (int y = 0; y < 1080; y++) f.drawPixmap(p, 0, y, 1920, 1, 0, 1079 - y, 1920, 1);
                        PixmapIO.writePNG(Gdx.files.absolute(out + File.separator + String.format("x%05d.png", x)), f);
                        f.dispose(); p.dispose(); fbo.dispose();
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                Gdx.app.exit();
            }
        }, config);
    }
}
