package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.screens.GameOverScreen;
import com.delmartec.projectorb.screens.GameScreen;
import com.delmartec.projectorb.screens.HowToPlayScreen;
import com.delmartec.projectorb.screens.MenuScreen;
import com.delmartec.projectorb.screens.OptionsScreen;
import com.delmartec.projectorb.screens.VictoryScreen;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * QA visual automatizado. Nao entra no build de producao; renderiza as telas
 * principais e grava capturas para comparacao de layout.
 */
public final class VisualSmokeLauncher {
    private VisualSmokeLauncher() { }

    public static void main(String[] args) throws Exception {
        String output = new File("../tmp/visual-smoke").getCanonicalPath();
        new File(output).mkdirs();

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Visual Smoke Test");
        // -Dsmoke.width/-Dsmoke.height: 1920x1080 mostra a pixel art 1:1.
        config.setWindowedMode(Integer.getInteger("smoke.width", 1280), Integer.getInteger("smoke.height", 720));
        config.setForegroundFPS(60);
        config.useVsync(false);
        new Lwjgl3Application(new SmokeGame(output), config);
    }

    private static final class SmokeGame extends ProjectOrbGame {
        private final String output;
        private float elapsed;
        private int stage;

        private SmokeGame(String output) { this.output = output; }

        @Override
        public void render() {
            super.render();
            elapsed += Gdx.graphics.getDeltaTime();

            if (stage == 0 && elapsed > 0.8f) {
                capture("01-menu.png");
                setScreen(new HowToPlayScreen(this, (MenuScreen) getScreen()));
                stage++;
            } else if (stage == 1 && elapsed > 1.5f) {
                capture("02-how-to-play.png");
                setScreen(new OptionsScreen(this, getScreen()));
                stage++;
            } else if (stage == 2 && elapsed > 2.2f) {
                capture("03-options.png");
                startGame();
                stage++;
            } else if (stage == 3 && elapsed > 3.5f) {
                capture("04-tutorial-pi.png");
                tutorialAt(9);   // passo do alvo de treino
                stage++;
            } else if (stage == 4 && elapsed > 4.4f) {
                capture("04b-tutorial-alvo.png");
                ((GameScreen) getScreen()).skipTutorial();
                stage++;
            } else if (stage == 5 && elapsed > 5.0f) {
                capture("04c-gameplay.png");
                jumpToSection(1, 2420f);
                stage++;
            } else if (stage == 6 && elapsed > 5.8f) {
                capture("05-diamond.png");
                jumpToSection(2, 4020f);
                stage++;
            } else if (stage == 7 && elapsed > 6.6f) {
                capture("06-triangle.png");
                jumpToSection(3, 6260f);
                stage++;
            } else if (stage == 8 && elapsed > 7.4f) {
                capture("07-square.png");
                jumpToSection(4, 8060f);
                stage++;
            } else if (stage == 9 && elapsed > 8.2f) {
                capture("08-hexagon.png");
                jumpToSection(5, 10480f);
                stage++;
            } else if (stage == 10 && elapsed > 9.0f) {
                capture("09-boss.png");
                setScreen(new GameOverScreen(this, 64f, 3, 18, 4, 2480));
                stage++;
            } else if (stage == 11 && elapsed > 9.8f) {
                capture("10-defeat.png");
                setScreen(new VictoryScreen(this, 172f, 2, 34, 5, 8420, 29));
                stage++;
            } else if (stage == 12 && elapsed > 10.6f) {
                capture("11-victory.png");
                stage++;
                Gdx.app.exit();
            }
        }

        /** Leva o tutorial do Pi direto para um passo (e mostra o alvo). */
        private void tutorialAt(int step) {
            try {
                GameScreen screen = (GameScreen) getScreen();
                Field target = GameScreen.class.getDeclaredField("targetActive");
                target.setAccessible(true);
                target.setBoolean(screen, true);
                Field started = GameScreen.class.getDeclaredField("tutorialStarted");
                started.setAccessible(true);
                started.setBoolean(screen, true);
                Field fs = GameScreen.class.getDeclaredField("piScript");
                fs.setAccessible(true);
                Field fp = GameScreen.class.getDeclaredField("piPortrait");
                fp.setAccessible(true);
                Field fl = GameScreen.class.getDeclaredField("piListener");
                fl.setAccessible(true);
                screen.startDialogue((com.delmartec.projectorb.dialogue.DialogueScript) fs.get(screen), step,
                    (com.badlogic.gdx.graphics.g2d.TextureRegion) fp.get(screen),
                    (com.delmartec.projectorb.dialogue.DialogueRunner.Listener) fl.get(screen));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        private void jumpToSection(int section, float playerX) {
            try {
                GameScreen screen = (GameScreen) getScreen();
                Field current = GameScreen.class.getDeclaredField("currentSection");
                current.setAccessible(true);
                current.setInt(screen, section);
                Field playerField = GameScreen.class.getDeclaredField("player");
                playerField.setAccessible(true);
                ((Player) playerField.get(screen)).respawn(playerX, 230f);
                screen.skipTutorial();
                Method spawn = GameScreen.class.getDeclaredMethod("spawnSection", int.class, boolean.class);
                spawn.setAccessible(true);
                spawn.invoke(screen, section, false);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Falha ao preparar captura de combate", e);
            }
        }

        /**
         * Renderiza a tela atual num FrameBuffer 1920x1080 (a resolução nativa
         * do jogo) e grava: assim a captura é 1:1 com a pixel art em qualquer
         * monitor, sem a reamostragem da janela.
         */
        private void capture(String name) {
            int w = 1920, h = 1080;
            FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            fbo.begin();
            getScreen().resize(w, h);
            getScreen().render(0f);
            Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
            fbo.end();
            getScreen().resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            Pixmap flipped = new Pixmap(w, h, pixmap.getFormat());
            for (int y = 0; y < h; y++) {
                flipped.drawPixmap(pixmap, 0, y, w, 1, 0, h - 1 - y, w, 1);
            }
            PixmapIO.writePNG(Gdx.files.absolute(output + File.separator + name), flipped);
            flipped.dispose();
            pixmap.dispose();
            fbo.dispose();
        }
    }
}
