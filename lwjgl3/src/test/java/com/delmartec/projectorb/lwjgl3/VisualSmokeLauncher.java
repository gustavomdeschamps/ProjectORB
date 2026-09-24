package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
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
        config.setWindowedMode(1280, 720);
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
                capture("04-gameplay.png");
                jumpToSection(1, 2420f);
                stage++;
            } else if (stage == 4 && elapsed > 4.3f) {
                capture("05-diamond.png");
                jumpToSection(2, 4020f);
                stage++;
            } else if (stage == 5 && elapsed > 5.1f) {
                capture("06-triangle.png");
                jumpToSection(3, 6260f);
                stage++;
            } else if (stage == 6 && elapsed > 5.9f) {
                capture("07-square.png");
                jumpToSection(4, 8060f);
                stage++;
            } else if (stage == 7 && elapsed > 6.7f) {
                capture("08-hexagon.png");
                jumpToSection(5, 10480f);
                stage++;
            } else if (stage == 8 && elapsed > 7.5f) {
                capture("09-boss.png");
                setScreen(new GameOverScreen(this, 64f, 3, 18, 4, 2480));
                stage++;
            } else if (stage == 9 && elapsed > 8.3f) {
                capture("10-defeat.png");
                setScreen(new VictoryScreen(this, 172f, 2, 34, 5, 8420, 29));
                stage++;
            } else if (stage == 10 && elapsed > 9.1f) {
                capture("11-victory.png");
                stage++;
                Gdx.app.exit();
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
                Method spawn = GameScreen.class.getDeclaredMethod("spawnSection", int.class, boolean.class);
                spawn.setAccessible(true);
                spawn.invoke(screen, section, false);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Falha ao preparar captura de combate", e);
            }
        }

        private void capture(String name) {
            Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            Pixmap flipped = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), pixmap.getFormat());
            for (int y = 0; y < pixmap.getHeight(); y++) {
                flipped.drawPixmap(pixmap, 0, y, pixmap.getWidth(), 1,
                    0, pixmap.getHeight() - 1 - y, pixmap.getWidth(), 1);
            }
            PixmapIO.writePNG(Gdx.files.absolute(output + File.separator + name), flipped);
            flipped.dispose();
            pixmap.dispose();
        }
    }
}
