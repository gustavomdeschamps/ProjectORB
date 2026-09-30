package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.level.StairGate;
import com.delmartec.projectorb.screens.GameScreen;

import java.io.File;
import java.lang.reflect.Field;

/** Capturas da escadinha 0: fechada, 25%, 50%, 75%, aberta e com o ORB no topo. */
public final class StairCapture {
    private StairCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/escada")).getCanonicalPath();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Stair Capture");
        config.setWindowedMode(640, 360);
        config.useVsync(false);
        config.setForegroundFPS(60);
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
                    StairGate st = ((StairGate[]) StairRespawnCheck.get(s, "stairs"))[0];
                    Player p = (Player) StairRespawnCheck.get(s, "player");
                    boolean[] cleared = (boolean[]) StairRespawnCheck.get(s, "sectionCleared");
                    if (frame == 2) {
                        // o tutorial sai de cena (só a passagem importa aqui), mas a seção
                        // continua "não vencida" para a massa aparecer fechada
                        s.skipTutorial();
                        cleared[0] = false;
                        p.respawn(1560f, 230f);
                    } else if (frame == 80) {
                        set(Player.class, p, "invulnerable", 0f);
                        shot(s, "01-fechada");
                        cleared[0] = true;
                    } else if (frame > 80 && frame < 200 && st.getState() == StairGate.State.OPENING) {
                        float[] marks = { 0.25f, 0.5f, 0.75f };
                        String[] names = { "02-25", "03-50", "04-75" };
                        for (int i = 0; i < marks.length; i++) {
                            set(StairGate.class, st, "time", marks[i] * StairGate.OPEN_TIME);
                            shot(s, names[i]);
                        }
                        set(StairGate.class, st, "time", StairGate.OPEN_TIME - 0.001f);
                        frame = 200;
                    } else if (frame == 230) {
                        shot(s, "05-aberta");
                        p.respawn(1890f, 400f);
                    } else if (frame == 300) {
                        set(Player.class, p, "invulnerable", 0f);
                        shot(s, "06-orb-no-topo");
                        Gdx.app.exit();
                        return;
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                super.render();
            }

            private void shot(GameScreen s, String name) { ScreenCapture.capture(s, out + "/" + name + ".png", 0f); }
        }, config);
    }

    static void set(Class<?> c, Object o, String name, Object v) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }
}
