package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.GeoEnemy;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.entities.Projectile;
import com.delmartec.projectorb.entities.WeakPoint;
import com.delmartec.projectorb.level.StairGate;
import com.delmartec.projectorb.screens.GameScreen;
import com.delmartec.projectorb.utils.Callout;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Rodada 3, etapa 3: prova do feedback de jogo no lugar de texto. Na câmara
 * do losango: um tiro no ponto fraco (o alvo estilhaça, "+100" sobe, o
 * contador pula) e um tiro no corpo (faísca rosa, "-25"); o cartão de seção
 * entrando/saindo; e a chamada rara do meio da tela. Quadros com passo fixo
 * de 1/30 s (determinístico). Grava em -Dsmoke.out.
 */
public final class FeedbackCapture {
    private FeedbackCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/feedback")).getCanonicalPath();
        new File(out).mkdirs();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Feedback Capture");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new ProjectOrbGame() {
            @Override public void create() {
                super.create();
                set(settings, "screenShake", false);
                startGame();
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
                    run(out);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    Gdx.input = real;
                }
                Gdx.app.exit();
            }

            private void run(String out) throws Exception {
                GameScreen s = (GameScreen) getScreen();
                s.skipTutorial();
                boolean[] cleared = (boolean[]) get(s, "sectionCleared");
                cleared[0] = true;
                ((StairGate[]) get(s, "stairs"))[0].openImmediately();
                set(s, "currentSection", 1);
                Player player = (Player) get(s, "player");
                player.respawn(2420f, 230f);
                Method spawn = GameScreen.class.getDeclaredMethod("spawnSection", int.class, boolean.class);
                spawn.setAccessible(true);
                spawn.invoke(s, 1, false);
                set(s, "bannerTimer", 2.6f);
                set(s, "cameraBaseX", 2700f);
                // cartão da seção entrando (0,1 s, 0,25 s), parado e saindo
                float[] cardTimes = { 0.10f, 0.25f, 1.2f, 2.40f };
                float t = 0f;
                for (float ct : cardTimes) {
                    while (t < ct) { s.render(1f / 30f); t += 1f / 30f; }
                    set(s, "cameraBaseX", 2700f);
                    ScreenCapture.capture(s, out + File.separator + String.format(java.util.Locale.ROOT, "cartao_%.2fs.png", ct), 0f);
                }
                set(s, "bannerTimer", 0f);
                // espera o losango terminar de surgir
                for (int i = 0; i < 60; i++) { s.render(1f / 30f); }
                List<?> enemies = (List<?>) get(s, "enemies");
                GeoEnemy e = (GeoEnemy) enemies.get(0);
                List<Projectile> shots = (List<Projectile>) get(s, "playerProjectiles");
                // tiro no ponto fraco
                WeakPoint wp = e.getWeakPoints().get(0);
                float wx = wp.worldX(e.getX(), e.getRotation()), wy = wp.worldY(e.getY(), e.getRotation());
                shots.add(new Projectile(wx - 20f, wy, 1200f, 0f, Constants.SHOT_RADIUS, false, 1, false));
                for (int f = 0; f < 18; f++) {
                    s.render(1f / 30f);
                    set(s, "cameraBaseX", 2700f);
                    if (f % 3 == 0) ScreenCapture.capture(s, out + File.separator + String.format("acerto_%02d.png", f), 0f);
                }
                // tiro no corpo (longe dos pontos): meio do corpo
                shots.add(new Projectile(e.getX() - 40f, e.getY() + 10f, 1200f, 0f, Constants.SHOT_RADIUS, false, 1, false));
                for (int f = 0; f < 15; f++) {
                    s.render(1f / 30f);
                    set(s, "cameraBaseX", 2700f);
                    if (f % 3 == 0) ScreenCapture.capture(s, out + File.separator + String.format("erro_%02d.png", f), 0f);
                }
                // chamada rara do meio da tela
                Callout callout = (Callout) get(s, "callout");
                callout.show("NÚCLEO PARTIDO", UiRenderer.LILAC);
                for (int f = 0; f < 36; f++) {
                    s.render(1f / 30f);
                    set(s, "cameraBaseX", 2700f);
                    if (f % 4 == 0) ScreenCapture.capture(s, out + File.separator + String.format("chamada_%02d.png", f), 0f);
                }
            }
        }, config);
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
