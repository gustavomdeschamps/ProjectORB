package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.entities.Projectile;
import com.delmartec.projectorb.screens.GameScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Checagem automatizada: perder uma vida numa seção JÁ LIMPA não pode refazer
 * a luta (inimigos, portão, portal). Numa seção ainda em combate, a tentativa
 * continua sendo reiniciada. Não entra no build de produção.
 */
public final class SectionRespawnCheck {
    private SectionRespawnCheck() { }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Section Respawn Check");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(false);
        CheckGame game = new CheckGame();
        new Lwjgl3Application(game, config);
        System.out.println(game.failures.isEmpty() ? "SECTION RESPAWN CHECK: PASS" : "SECTION RESPAWN CHECK: FAIL");
        for (String f : game.failures) System.out.println("  - " + f);
        System.exit(game.failures.isEmpty() ? 0 : 1);
    }

    private static final class CheckGame extends ProjectOrbGame {
        final List<String> failures = new ArrayList<>();
        private int step;
        private int stepFrame;
        private int frame;

        @Override
        public void create() {
            super.create();
            startGame();
            // o tutorial do Pi trava os controles; estes testes são sobre outra coisa
            ((GameScreen) getScreen()).skipTutorial();
        }

        @Override
        public void render() {
            super.render();
            script((GameScreen) getScreen());
            frame++;
            stepFrame++;
            if (frame > 3000) {
                failures.add("roteiro não terminou (passo " + step + ")");
                Gdx.app.exit();
            }
        }

        private void next() { step++; stepFrame = 0; }

        private void script(GameScreen s) {
            Player player = get(s, "player");
            List<?> enemies = get(s, "enemies");
            List<Projectile> enemyShots = get(s, "enemyProjectiles");
            boolean[] cleared = get(s, "sectionCleared");

            switch (step) {
                // --- Seção 1 limpa + projétil inimigo no ar + morte
                case 0 -> { if (stepFrame > 30) { enterSection(s, 1, 2420f); next(); } }
                case 1 -> { enemies.clear(); next(); }
                case 2 -> {
                    expect(cleared[1], "seção 1 deveria estar limpa sem inimigos");
                    enemyShots.add(new Projectile(player.getX() + 900f, 900f, 0f, 0f, 12f, true, 10, false));
                    player.forceDeath();
                    next();
                }
                case 3 -> {
                    if (player.getHealth() > 0) {
                        expect(enemies.isEmpty(), "respawn numa seção limpa recriou os inimigos");
                        expect(cleared[1], "respawn numa seção limpa zerou sectionCleared");
                        expect(enemyShots.isEmpty(), "projéteis inimigos deveriam ser limpos no respawn");
                        next();
                    } else if (stepFrame > 200) { failures.add("respawn não aconteceu (seção 1)"); next(); }
                }
                // o portão é recalculado no update seguinte
                case 4 -> {
                    if (stepFrame > 2) {
                        expect(!(Boolean) get(s, "gateActive"), "respawn numa seção limpa reativou o portão");
                        next();
                    }
                }
                // --- Controle: seção 2 em combate + morte -> luta reiniciada
                case 5 -> { if (stepFrame > 20) { enterSection(s, 2, 4020f); next(); } }
                case 6 -> { enemies.remove(0); player.forceDeath(); next(); }
                case 7 -> {
                    if (player.getHealth() > 0) {
                        expect(enemies.size() == 2, "seção em combate deveria respawnar os 2 triângulos");
                        expect(!cleared[2], "seção em combate não deveria ficar limpa");
                        expect(get(s, "gateActive"), "portão deveria continuar ativo em combate");
                        next();
                    } else if (stepFrame > 200) { failures.add("respawn não aconteceu (seção 2)"); next(); }
                }
                // --- Arena limpa: portal final continua ativo após a morte
                case 8 -> {
                    if (stepFrame > 20) {
                        set(player, "lives", 3); // o roteiro já gastou duas vidas
                        enterSection(s, 5, 10480f);
                        next();
                    }
                }
                case 9 -> { enemies.clear(); next(); }
                case 10 -> {
                    expect(get(s, "portalActive"), "portal deveria ativar com a arena limpa");
                    player.forceDeath();
                    next();
                }
                case 11 -> {
                    if (player.getHealth() > 0) {
                        expect(get(s, "portalActive"), "morrer com a arena limpa desativou o portal");
                        expect(enemies.isEmpty(), "morrer com a arena limpa recriou o boss");
                        next();
                    } else if (player.getLives() <= 0 || stepFrame > 200) {
                        failures.add("respawn não aconteceu (arena)");
                        next();
                    }
                }
                default -> Gdx.app.exit();
            }
        }

        private void enterSection(GameScreen s, int section, float playerX) {
            try {
                Field current = GameScreen.class.getDeclaredField("currentSection");
                current.setAccessible(true);
                current.setInt(s, section);
                ((Player) get(s, "player")).respawn(playerX, 230f);
                Method spawn = GameScreen.class.getDeclaredMethod("spawnSection", int.class, boolean.class);
                spawn.setAccessible(true);
                spawn.invoke(s, section, false);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        private void expect(Object condition, String message) {
            if (!Boolean.TRUE.equals(condition) && !failures.contains(message)) failures.add(message);
        }

        private static void set(Object target, String name, int value) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.setInt(target, value);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> T get(Object target, String name) {
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
