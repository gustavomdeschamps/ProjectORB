package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.screens.GameOverScreen;
import com.delmartec.projectorb.screens.GameScreen;
import com.delmartec.projectorb.screens.HowToPlayScreen;
import com.delmartec.projectorb.screens.MenuScreen;
import com.delmartec.projectorb.screens.OptionsScreen;
import com.delmartec.projectorb.screens.VictoryScreen;
import com.delmartec.projectorb.utils.ButtonPress;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Portão visual dos botões e das telas de menu: estados normal, selecionado
 * e pressionado; "Como jogar" com e sem teclas acesas; pausa, códex, opções,
 * vitória e derrota. Grava em -Dsmoke.out (padrão ../tmp/ui).
 */
public final class UiCapture {
    private UiCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/ui")).getCanonicalPath();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — UI Capture");
        config.setWindowedMode(1280, 720);
        config.useVsync(false);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frame;
            boolean holdKeys;

            @Override public void create() {
                super.create();
                showMenu();
            }

            @Override public void render() {
                frame++;
                Input real = Gdx.input;
                if (holdKeys) {
                    // "Como jogar" com teclas pressionadas de verdade (A, ESPAÇO, SHIFT, E, clique)
                    Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
                        (p, m, a) -> switch (m.getName()) {
                            case "isKeyPressed" -> {
                                int k = (Integer) a[0];
                                yield k == Input.Keys.A || k == Input.Keys.SPACE || k == Input.Keys.SHIFT_LEFT || k == Input.Keys.E;
                            }
                            case "isButtonPressed" -> (Integer) a[0] == Input.Buttons.LEFT;
                            case "isKeyJustPressed", "isButtonJustPressed" -> false;
                            default -> m.invoke(real, a);
                        });
                }
                try {
                    switch (frame) {
                        case 10 -> shot(out + "/01-menu-selecionado.png");
                        case 11 -> {
                            set(MenuScreen.class, getScreen(), "selected", 1);
                            shot(out + "/02-menu-outro-selecionado.png");
                            set(MenuScreen.class, getScreen(), "pendingAction", 1);
                            set(MenuScreen.class, getScreen(), "pressTimer", 99f);
                            shot(out + "/03-menu-pressionado.png");
                            set(MenuScreen.class, getScreen(), "pendingAction", -1);
                            setScreen(new HowToPlayScreen(this, (MenuScreen) getScreen()));
                        }
                        case 16 -> {
                            shot(out + "/04-como-jogar.png");
                            holdKeys = true;
                        }
                        case 20 -> {
                            shot(out + "/05-como-jogar-teclas-acesas.png");
                            holdKeys = false;
                            setScreen(new OptionsScreen(this, getScreen()));
                        }
                        case 24 -> {
                            shot(out + "/06-opcoes.png");
                            startGame();
                            ((GameScreen) getScreen()).skipTutorial();
                        }
                        case 30 -> {
                            set(GameScreen.class, getScreen(), "paused", true);
                            set(GameScreen.class, getScreen(), "pauseSelected", 1);
                        }
                        case 33 -> {
                            shot(out + "/07-pausa.png");
                            ButtonPress bp = (ButtonPress) get(GameScreen.class, getScreen(), "pausePress");
                            set(ButtonPress.class, bp, "pending", 1);
                            set(ButtonPress.class, bp, "timer", 99f);
                            shot(out + "/08-pausa-pressionado.png");
                            set(ButtonPress.class, bp, "pending", -1);
                            set(GameScreen.class, getScreen(), "paused", false);
                            set(GameScreen.class, getScreen(), "codexOpen", true);
                        }
                        case 37 -> {
                            shot(out + "/09-codex.png");
                            setScreen(new VictoryScreen(this, 172f, 2, 34, 5, 8420, 29));
                        }
                        case 41 -> {
                            shot(out + "/10-vitoria.png");
                            setScreen(new GameOverScreen(this, 64f, 3, 18, 4, 2480));
                        }
                        case 45 -> {
                            shot(out + "/11-derrota.png");
                            Gdx.app.exit();
                        }
                        default -> { }
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                super.render();
                Gdx.input = real;
            }

            private void shot(String path) { freezeTime(getScreen()); ScreenCapture.capture(getScreen(), path, 0f); }
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
