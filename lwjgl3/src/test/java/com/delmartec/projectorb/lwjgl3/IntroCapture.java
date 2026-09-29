package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.screens.IntroScreen;

import java.io.File;

/**
 * Captura os momentos-chave da abertura (1920x1080 via FrameBuffer) e mede o
 * tempo até o menu sem nenhum input. Argumento "reduced" usa a versão de
 * movimento reduzido. Não entra no build.
 */
public final class IntroCapture {
    private IntroCapture() { }

    public static void main(String[] args) throws Exception {
        boolean reduced = args.length > 0 && args[0].equals("reduced");
        String out = new File("../tmp/intro" + (reduced ? "-reduzida" : "")).getCanonicalPath();
        new File(out).mkdirs();
        float[] moments = { 1.5f, 5.5f, 7.7f, 8.2f, 11.2f, 13.6f, 16.5f, 19.0f };
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(false);
        new Lwjgl3Application(new ProjectOrbGame() {
            IntroScreen intro;
            int next;
            float wall;

            @Override public void create() {
                super.create();
                intro = new IntroScreen(this, reduced);
                setScreen(intro);
            }

            @Override public void render() {
                wall += Gdx.graphics.getDeltaTime();
                super.render();
                if (getScreen() != intro) {
                    System.out.printf("INTRO: foi para o menu sozinha em %.2f s (conteúdo: %.0f s)%n", intro.getTime(), IntroScreen.DURATION);
                    Gdx.app.exit();
                    return;
                }
                if (next < moments.length && intro.getTime() >= moments[next]) {
                    capture(out, String.format("%02d-%04.1fs.png", next + 1, intro.getTime()).replace(',', '.'));
                    next++;
                }
            }

            private void capture(String dir, String name) {
                FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
                fbo.begin();
                intro.resize(1920, 1080);
                intro.render(0f);
                Pixmap px = Pixmap.createFromFrameBuffer(0, 0, 1920, 1080);
                fbo.end();
                intro.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
                Pixmap flipped = new Pixmap(1920, 1080, px.getFormat());
                for (int y = 0; y < 1080; y++) flipped.drawPixmap(px, 0, y, 1920, 1, 0, 1079 - y, 1920, 1);
                PixmapIO.writePNG(Gdx.files.absolute(dir + File.separator + name), flipped);
                flipped.dispose();
                px.dispose();
                fbo.dispose();
            }
        }, config);
    }
}
