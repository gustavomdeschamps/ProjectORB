package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.screens.GameScreen;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Cenário em 4 posições de câmera, 100% determinístico (relógio zerado, tudo
 * renderizado com delta 0 no primeiro frame, mouse fixo), para comparar
 * antes/depois pixel a pixel. Uso: -Dsmoke.out=pasta [-Dbg.xs=1000,4000,...].
 */
public final class BackgroundCompare {
    private BackgroundCompare() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/background-compare")).getCanonicalPath();
        new File(out).mkdirs();
        String[] parts = System.getProperty("bg.xs", "1000,4000,7000,11000").split(",");
        float[] xs = new float[parts.length];
        for (int i = 0; i < parts.length; i++) xs[i] = Float.parseFloat(parts[i].trim());
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Background Compare");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new ProjectOrbGame() {
            @Override public void create() {
                super.create();
                // sem tremor (aleatório) nesta execução; não grava nas preferências
                set(settings, "screenShake", false);
                startGame();
                ((GameScreen) getScreen()).skipTutorial();
            }

            @Override public void render() {
                final Input real = Gdx.input;
                Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
                    (p, m, a) -> switch (m.getName()) {
                        case "getX", "getY" -> a == null ? 0 : m.invoke(real, a);
                        case "isKeyJustPressed", "isKeyPressed", "isButtonPressed", "isButtonJustPressed" -> false;
                        default -> m.invoke(real, a);
                    });
                try {
                    GameScreen screen = (GameScreen) getScreen();
                    // um frame com delta 0 consome os eventos da escada 0 (que
                    // pedem tremor aleatório); o tremor é zerado antes de cada foto
                    screen.render(0f);
                    for (float x : xs) {
                        set(screen, "shakeTimer", 0f);
                        set(screen, "shakeStrength", 0f);
                        // o ORB fica fora do quadro: só o cenário importa aqui
                        ((Player) get(screen, "player")).respawn(x + 3000f, 230f);
                        ((List<?>) get(screen, "enemies")).clear();
                        set(screen, "bannerTimer", 0f);
                        set(screen, "gameTime", 0f);
                        set(screen, "feedbackTimer", 0f);
                        set(screen, "cameraBaseX", Math.max(960f, Math.min(12000f - 960f, x)));
                        ScreenCapture.capture(screen, out + File.separator + String.format("x%05d.png", (int) x), 0f);
                    }
                    // -Dbg.anim=N: N quadros a 10 quadros/s na 1ª posição (água, névoa, brilhos)
                    int anim = Integer.getInteger("bg.anim", 0);
                    for (int i = 0; i < anim; i++) {
                        set(screen, "cameraBaseX", Math.max(960f, Math.min(12000f - 960f, xs[0])));
                        set(screen, "gameTime", i * 0.1f);
                        ScreenCapture.capture(screen, out + File.separator + "anim" + File.separator
                            + String.format("f%03d.png", i), 0f);
                    }
                } finally {
                    Gdx.input = real;
                }
                Gdx.app.exit();
            }
        }, config);
    }

    private static Object get(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
