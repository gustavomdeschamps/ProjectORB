package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.level.StairGate;
import com.delmartec.projectorb.screens.GameScreen;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * As 5 escadinhas, fechada e aberta, com a câmera centrada em cada uma
 * (render com delta 0, sem tremor). Grava também escadas.txt com a caixa da
 * massa, da escada e a posição da câmera (o script de sobreposição usa).
 */
public final class StairLayoutCapture {
    private StairLayoutCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/escadas")).getCanonicalPath();
        new File(out).mkdirs();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Stair Layout");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new ProjectOrbGame() {
            @Override public void create() {
                super.create();
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
                try (PrintWriter log = new PrintWriter(out + File.separator + "escadas.txt", "UTF-8")) {
                    GameScreen s = (GameScreen) getScreen();
                    StairGate[] stairs = (StairGate[]) get(s, "stairs");
                    boolean[] cleared = (boolean[]) get(s, "sectionCleared");
                    Player player = (Player) get(s, "player");
                    s.render(0f);
                    for (int g = 0; g < 5; g++) {
                        for (int k = 0; k < g; k++) { cleared[k] = true; stairs[k].openImmediately(); }
                        set(s, "currentSection", g);
                        ((List<?>) get(s, "enemies")).clear();
                        set(s, "bannerTimer", 0f);
                        set(s, "feedbackTimer", 0f);
                        StairGate st = stairs[g];
                        float cx = st.getMass().x + st.getMass().width / 2f;
                        set(s, "cameraBaseX", cx);
                        player.respawn(cx + 4000f, 230f);
                        set(s, "shakeTimer", 0f);
                        ScreenCapture.capture(s, out + File.separator + String.format("escada%d_fechada.png", g), 0f);
                        cleared[g] = true;
                        st.openImmediately();
                        float top = st.getPlateauTop();
                        player.respawn(cx, top + 60f);
                        for (int i = 0; i < 30; i++) { player.update(1f / 60f, collision(s, stairs), null, false); }
                        set(s, "cameraBaseX", cx);
                        set(s, "shakeTimer", 0f);
                        ScreenCapture.capture(s, out + File.separator + String.format("escada%d_aberta.png", g), 0f);
                        log.printf(java.util.Locale.ROOT, "%d camera=%.0f massa=%.0f,%.0f,%.0f,%.0f escada=%.0f,%.0f,%.0f%n", g, cx,
                            st.getMass().x, st.getMass().y, st.getMass().width, st.getMass().height,
                            st.getStairLeft(), st.getStairRight(), top);
                        cleared[g] = false;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    Gdx.input = real;
                }
                Gdx.app.exit();
            }
        }, config);
    }

    private static List<com.delmartec.projectorb.level.Platform> collision(GameScreen s, StairGate[] stairs) {
        List<com.delmartec.projectorb.level.Platform> list = new java.util.ArrayList<>(
            ((com.delmartec.projectorb.level.LevelDemo) get(s, "level")).getPlatforms());
        for (StairGate st : stairs) st.addCollision(list);
        return list;
    }

    static Object get(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    static void set(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
