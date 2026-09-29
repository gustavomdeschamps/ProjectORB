package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.screens.GameScreen;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Captura o cenário em várias posições de câmera (padrão: x = 1000, 4000,
 * 7000, 11000) para conferir repetição do fundo. Uso:
 * BackgroundTour [pasta-de-saída] [x1 x2 ...]. Não entra no build.
 */
public final class BackgroundTour {
    private BackgroundTour() { }

    public static void main(String[] args) throws Exception {
        String out = new File(args.length > 0 ? args[0] : "../tmp/background-tour").getCanonicalPath();
        new File(out).mkdirs();
        float[] xs = { 1000f, 4000f, 7000f, 11000f };
        if (args.length > 1) {
            xs = new float[args.length - 1];
            for (int i = 1; i < args.length; i++) xs[i - 1] = Float.parseFloat(args[i]);
        }
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Background Tour");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(false);
        new Lwjgl3Application(new Tour(out, xs), config);
    }

    private static final class Tour extends ProjectOrbGame {
        private final String out;
        private final float[] xs;
        private int index = -1;
        private int frames;

        Tour(String out, float[] xs) { this.out = out; this.xs = xs; }

        @Override
        public void create() {
            super.create();
            startGame();
            ((GameScreen) getScreen()).skipTutorial();
        }

        @Override
        public void render() {
            super.render();
            frames++;
            if (index < 0 && frames > 20) next();
            else if (index >= 0 && frames > 90) {
                capture(String.format("x%05d.png", (int) xs[index]));
                if (index == xs.length - 1) Gdx.app.exit();
                else next();
            }
        }

        private void next() {
            index++;
            frames = 0;
            try {
                GameScreen screen = (GameScreen) getScreen();
                Field pf = GameScreen.class.getDeclaredField("player");
                pf.setAccessible(true);
                ((Player) pf.get(screen)).respawn(xs[index], 230f);
                // Sem inimigos nem banner na frente: só o cenário importa aqui.
                Field ef = GameScreen.class.getDeclaredField("enemies");
                ef.setAccessible(true);
                ((List<?>) ef.get(screen)).clear();
                Field bf = GameScreen.class.getDeclaredField("bannerTimer");
                bf.setAccessible(true);
                bf.setFloat(screen, 0f);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        private void capture(String name) {
            Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            Pixmap flipped = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), pixmap.getFormat());
            for (int y = 0; y < pixmap.getHeight(); y++) {
                flipped.drawPixmap(pixmap, 0, y, pixmap.getWidth(), 1, 0, pixmap.getHeight() - 1 - y, pixmap.getWidth(), 1);
            }
            PixmapIO.writePNG(Gdx.files.absolute(out + File.separator + name), flipped);
            flipped.dispose();
            pixmap.dispose();
        }
    }
}
