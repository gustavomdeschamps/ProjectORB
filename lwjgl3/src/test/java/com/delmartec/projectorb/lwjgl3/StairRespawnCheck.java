package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.level.Platform;
import com.delmartec.projectorb.level.StairGate;
import com.delmartec.projectorb.screens.GameScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Escadinha no jogo inteiro (etapa B): vencer a seção 0 abre a escada 0; com
 * o ORB na seção 1, morrer e renascer mantém a escada aberta, o ORB renasce em
 * pé (não dentro de sólido) e a escada 1 continua fechada.
 */
public final class StairRespawnCheck {
    private StairRespawnCheck() { }

    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Stair Respawn Check");
        config.setWindowedMode(640, 360);
        config.useVsync(false);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frame;
            int phase;
            int mark;

            @Override public void create() {
                super.create();
                startGame();
            }

            @Override public void render() {
                super.render();
                frame++;
                try {
                    GameScreen s = (GameScreen) getScreen();
                    StairGate[] stairs = (StairGate[]) get(s, "stairs");
                    Player p = (Player) get(s, "player");
                    if (frame == 2) {
                        expect(stairs[0].getState() == StairGate.State.CLOSED, "escada 0 deveria começar fechada");
                        s.skipTutorial();                       // seção 0 vencida
                    } else if (frame == 4) {
                        expect(stairs[0].getState() == StairGate.State.OPENING, "escada 0 deveria estar se rearranjando");
                    } else if (phase == 0 && frame > 4 && stairs[0].getState() == StairGate.State.OPEN) {
                        phase = 1;
                        mark = frame;
                        Field cur = GameScreen.class.getDeclaredField("currentSection");
                        cur.setAccessible(true);
                        cur.setInt(s, 1);
                        Method spawn = GameScreen.class.getDeclaredMethod("spawnSection", int.class, boolean.class);
                        spawn.setAccessible(true);
                        spawn.invoke(s, 1, false);
                        p.respawn(1940f, 400f);
                    } else if (phase == 1 && frame == mark + 60) {
                        p.forceDeath();
                    } else if (phase == 1 && frame == mark + 300) {
                        expect(stairs[0].getState() == StairGate.State.OPEN, "escada 0 fechou depois de morrer");
                        expect(stairs[1].getState() == StairGate.State.CLOSED, "escada 1 abriu sem vencer a seção 1");
                        expect(p.getHealth() > 0, "ORB deveria ter renascido");
                        expect(p.isGrounded(), "ORB deveria estar apoiado depois de renascer");
                        @SuppressWarnings("unchecked")
                        List<Platform> col = (List<Platform>) get(s, "collision");
                        for (Platform pl : col) {
                            expect(!p.getBounds().overlaps(pl.bounds), "ORB renasceu dentro de um sólido");
                        }
                        float top = stairs[0].topAt(p.getX() - 29f, p.getX() + 29f);
                        expect(Math.abs(p.getFeetY() - top) < 2f,
                            "ORB deveria renascer em pé na escada (pés " + p.getFeetY() + ", topo " + top + ")");
                        Gdx.app.exit();
                    } else if (frame > 2000) {
                        expect(false, "escada 0 não abriu em 2000 quadros");
                        Gdx.app.exit();
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }, config);
        System.out.println(failures.isEmpty() ? "STAIR RESPAWN CHECK: PASS" : "STAIR RESPAWN CHECK: FAIL");
        for (String f : failures) System.out.println("  - " + f);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    static void expect(boolean ok, String msg) { if (!ok && !failures.contains(msg)) failures.add(msg); }

    static Object get(Object o, String name) throws ReflectiveOperationException {
        Field f = GameScreen.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }
}
