package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.screens.GameOverScreen;
import com.delmartec.projectorb.screens.GameScreen;
import com.delmartec.projectorb.screens.VictoryScreen;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Portão visual da F1: HUD com corações cheios, a quebra de um coração
 * quadro a quadro, pausa, vitória e derrota. Grava em -Dsmoke.out
 * (padrão ../tmp/hearts). Não entra no build de produção.
 */
public final class HeartHudCapture {
    private HeartHudCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/hearts")).getCanonicalPath();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Heart HUD Capture");
        config.setWindowedMode(1280, 720);
        config.useVsync(false);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frame;

            @Override public void create() {
                super.create();
                startGame();
                ((GameScreen) getScreen()).skipTutorial();
            }

            @Override public void render() {
                frame++;
                try {
                    if (frame == 20) {
                        ScreenCapture.capture(getScreen(), out + "/01-hud-cheio.png", 0f);
                        // perde uma vida sem morrer: o HUD reage à troca de 3 para 2
                        Field f = Player.class.getDeclaredField("lives");
                        f.setAccessible(true);
                        f.setInt(player(), 2);
                    } else if (frame > 20 && frame <= 24) {
                        // um render de 0,09 s por captura = um frame da quebra
                        ScreenCapture.capture(getScreen(), out + "/02-quebra-" + (frame - 20) + ".png", 0.09f);
                    } else if (frame == 40) {
                        ScreenCapture.capture(getScreen(), out + "/03-hud-2-vidas.png", 0f);
                        Field p = GameScreen.class.getDeclaredField("paused");
                        p.setAccessible(true);
                        p.setBoolean(getScreen(), true);
                    } else if (frame == 44) {
                        ScreenCapture.capture(getScreen(), out + "/04-pausa.png", 0f);
                        setScreen(new VictoryScreen(this, 172f, 2, 34, 5, 8420, 29));
                    } else if (frame == 50) {
                        freezeTime(getScreen()); ScreenCapture.capture(getScreen(), out + "/05-vitoria.png", 0f);
                        setScreen(new GameOverScreen(this, 64f, 3, 18, 4, 2480));
                    } else if (frame == 56) {
                        freezeTime(getScreen()); ScreenCapture.capture(getScreen(), out + "/06-derrota.png", 0f);
                        showMenu();
                    } else if (frame == 62) {
                        freezeTime(getScreen()); ScreenCapture.capture(getScreen(), out + "/07-menu.png", 0f);
                        com.badlogic.gdx.Gdx.app.exit();
                        return;
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                super.render();
            }

            private Player player() throws ReflectiveOperationException {
                Field f = GameScreen.class.getDeclaredField("player");
                f.setAccessible(true);
                return (Player) f.get(getScreen());
            }
        }, config);
    }

    /** Zera o relógio das telas de fim (ORB balançando, chefe animado): captura determinística. */
    static void freezeTime(com.badlogic.gdx.Screen screen) {
        try {
            java.lang.reflect.Field f = screen.getClass().getDeclaredField("stateTime");
            f.setAccessible(true);
            f.setFloat(screen, 0f);
        } catch (ReflectiveOperationException ignored) {
            // tela sem relógio próprio
        }
    }
}
