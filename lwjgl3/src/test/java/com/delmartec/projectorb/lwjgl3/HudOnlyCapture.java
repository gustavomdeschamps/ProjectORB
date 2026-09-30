package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.screens.GameScreen;
import com.delmartec.projectorb.utils.HeartMeter;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Desenha SÓ o HUD (GameScreen.renderHud) sobre preto, com o relógio dos
 * corações zerado: compara o HUD entre versões sem o fundo no meio.
 * Saída em -Dsmoke.out (padrão ../tmp/hud-only).
 */
public final class HudOnlyCapture {
    private HudOnlyCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/hud-only")).getCanonicalPath();
        new File(out).mkdirs();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — HUD Only");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frame;

            @Override public void create() {
                super.create();
                startGame();
                ((GameScreen) getScreen()).skipTutorial();
            }

            @Override public void render() {
                super.render();
                if (++frame < 10) return;
                try {
                    GameScreen s = (GameScreen) getScreen();
                    Field hm = GameScreen.class.getDeclaredField("heartMeter");
                    hm.setAccessible(true);
                    Field t = HeartMeter.class.getDeclaredField("time");
                    t.setAccessible(true);
                    t.setFloat(hm.get(s), 0f);
                    Field b = GameScreen.class.getDeclaredField("bannerTimer");
                    b.setAccessible(true);
                    b.setFloat(s, 0f);
                    Method hud = GameScreen.class.getDeclaredMethod("renderHud");
                    hud.setAccessible(true);
                    FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
                    fbo.begin();
                    s.resize(1920, 1080);
                    Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    hud.invoke(s);
                    Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1920, 1080);
                    fbo.end();
                    Pixmap f = new Pixmap(1920, 1080, p.getFormat());
                    for (int y = 0; y < 1080; y++) f.drawPixmap(p, 0, y, 1920, 1, 0, 1079 - y, 1920, 1);
                    PixmapIO.writePNG(Gdx.files.absolute(out + File.separator + "hud.png"), f);
                    f.dispose(); p.dispose(); fbo.dispose();
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                Gdx.app.exit();
            }
        }, config);
    }
}
