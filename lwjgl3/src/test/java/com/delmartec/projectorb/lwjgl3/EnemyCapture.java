package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.GeoEnemy;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.screens.GameScreen;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Portão visual de um inimigo em jogo: surgindo, parado, carga (telegrafia),
 * ataque, dano e morte, com o ORB perto para comparar tamanho.
 * -Denemy.section=2 (seção do inimigo), -Denemy.x=4350 (posição da câmera/ORB),
 * saída em -Dsmoke.out (padrão ../tmp/enemy).
 */
public final class EnemyCapture {
    private EnemyCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/enemy")).getCanonicalPath();
        int section = Integer.getInteger("enemy.section", 2);
        float orbX = Float.parseFloat(System.getProperty("enemy.orbx", "4060"));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Enemy Capture");
        config.setWindowedMode(1280, 720);
        config.useVsync(false);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frame;

            @Override public void create() {
                super.create();
                startGame();
            }

            @Override public void render() {
                frame++;
                try {
                    GameScreen s = (GameScreen) getScreen();
                    if (frame == 3) {
                        s.skipTutorial();
                        set(GameScreen.class, s, "currentSection", section);
                        ((Player) get(GameScreen.class, s, "player")).respawn(orbX, 230f);
                        Method spawn = GameScreen.class.getDeclaredMethod("spawnSection", int.class, boolean.class);
                        spawn.setAccessible(true);
                        spawn.invoke(s, section, false);
                    } else if (frame == 60) {
                        freeze(s);
                        // sem a piscada de invulnerabilidade do renascimento
                        set(Player.class, get(GameScreen.class, s, "player"), "invulnerable", 0f);
                        set(GeoEnemy.class, first(s), "stateTime", 0.12f);
                        shot(s, "01-surgindo", 0f);
                        set(GeoEnemy.class, first(s), "stateTime", 1.0f);
                        shot(s, "02-parado", 0f);
                        set(GeoEnemy.class, first(s), "charge", 0.85f);
                        shot(s, "03-carga", 0f);
                        set(GeoEnemy.class, first(s), "charge", 0f);
                        set(GeoEnemy.class, first(s), "attackTimer", 0.10f);
                        shot(s, "04-ataque-antecipacao", 0f);
                        set(GeoEnemy.class, first(s), "attackTimer", 0.27f);
                        shot(s, "05-ataque-disparo", 0f);
                        set(GeoEnemy.class, first(s), "attackTimer", -1f);
                        set(GeoEnemy.class, first(s), "hurtVisual", 0.3f);
                        shot(s, "06-dano", 0f);
                        set(GeoEnemy.class, first(s), "hurtVisual", 0f);
                        set(GeoEnemy.class, first(s), "defeated", true);
                        set(GeoEnemy.class, first(s), "deathVisual", 0.25f);
                        shot(s, "07-morte", 0f);
                        set(GeoEnemy.class, first(s), "deathVisual", 0.42f);
                        shot(s, "08-morte-cacos", 0f);
                        Gdx.app.exit();
                        return;
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                super.render();
            }

            /** O inimigo para no lugar (sem andar) para as capturas lado a lado. */
            private void freeze(GameScreen s) throws ReflectiveOperationException {
                set(GeoEnemy.class, first(s), "moving", false);
            }

            private GeoEnemy first(GameScreen s) throws ReflectiveOperationException {
                return (GeoEnemy) ((List<?>) get(GameScreen.class, s, "enemies")).get(0);
            }

            private void shot(GameScreen s, String name, float delta) {
                ScreenCapture.capture(s, out + "/" + name + ".png", delta);
            }
        }, config);
    }

    static void set(Class<?> c, Object o, String name, Object value) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    static Object get(Class<?> c, Object o, String name) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }
}
