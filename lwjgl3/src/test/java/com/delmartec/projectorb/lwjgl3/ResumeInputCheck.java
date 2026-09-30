package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.screens.GameScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checagem automatizada: retomar da pausa/códex não pode vazar input para a
 * partida. Simula o teclado/mouse substituindo Gdx.input por um proxy a cada
 * frame. Não entra no build de produção.
 *
 * Roteiro: pausa -> ESPAÇO confirma "CONTINUAR" (não pode pular); pausa ->
 * clique em "CONTINUAR" mantido pressionado (não pode atirar); códex -> ESC
 * junto com ESPAÇO (não pode pular). Controles positivos confirmam que pulo e
 * tiro voltam a funcionar depois.
 */
public final class ResumeInputCheck {
    private ResumeInputCheck() { }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Resume Input Check");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(false);
        CheckGame game = new CheckGame();
        new Lwjgl3Application(game, config);
        System.out.println(game.failures.isEmpty() ? "RESUME INPUT CHECK: PASS" : "RESUME INPUT CHECK: FAIL");
        for (String f : game.failures) System.out.println("  - " + f);
        System.exit(game.failures.isEmpty() ? 0 : 1);
    }

    private static final class CheckGame extends ProjectOrbGame {
        final List<String> failures = new ArrayList<>();
        private final Set<Integer> justKeys = new HashSet<>();
        private boolean mouseHeld;
        private boolean mouseJust;
        private int mouseX;
        private int mouseY;
        private int frame;
        private int step;
        private int stepFrame;

        @Override
        public void create() {
            super.create();
            startGame();
            // o tutorial do Pi trava os controles; estes testes são sobre outra coisa
            ((GameScreen) getScreen()).skipTutorial();
        }

        @Override
        public void render() {
            final Input real = Gdx.input;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[] { Input.class }, (proxy, method, a) -> {
                    switch (method.getName()) {
                        case "isKeyJustPressed": return justKeys.contains((Integer) a[0]);
                        case "isKeyPressed": return justKeys.contains((Integer) a[0]);
                        case "isButtonPressed": return mouseHeld && (Integer) a[0] == Input.Buttons.LEFT;
                        case "isButtonJustPressed": return mouseJust && (Integer) a[0] == Input.Buttons.LEFT;
                        case "getX": if (a == null) return mouseX; break;
                        case "getY": if (a == null) return mouseY; break;
                        default: break;
                    }
                    return method.invoke(real, a);
                });
            script();
            super.render();
            justKeys.clear();
            mouseJust = false;
            frame++;
            stepFrame++;
        }

        private void next() { step++; stepFrame = 0; }

        private void script() {
            GameScreen screen = (GameScreen) getScreen();
            Player player = get(screen, "player");
            List<?> shots = get(screen, "playerProjectiles");
            // Centro do botão "CONTINUAR" (HUD 1920x1080) em coordenadas de tela.
            int buttonX = Math.round(960f / 1920f * Gdx.graphics.getWidth());
            int buttonY = Math.round((1f - 546f / 1080f) * Gdx.graphics.getHeight());
            // Mira em um ponto qualquer à direita do jogador.
            int aimX = Math.round(0.8f * Gdx.graphics.getWidth());
            int aimY = Math.round(0.5f * Gdx.graphics.getHeight());

            switch (step) {
                case 0 -> { if (stepFrame > 70) { expect(player.isGrounded(), "jogador deveria estar no chão"); next(); } }
                // --- pausa + ESPAÇO
                case 1 -> { justKeys.add(Input.Keys.ESCAPE); next(); }
                case 2 -> { expect(get(screen, "paused"), "ESC deveria pausar"); justKeys.add(Input.Keys.SPACE); next(); }
                case 3 -> {
                    // o botão afunda por ~0,12 s (ButtonPress) antes de retomar
                    if (stepFrame >= 15) expect(!(Boolean) get(screen, "paused"), "ESPAÇO deveria retomar");
                    expect(player.isGrounded() && player.getVy() <= 0f,
                        "pulou ao retomar com ESPAÇO (frame " + stepFrame + ")");
                    if (stepFrame > 20) next();
                }
                // controle positivo: pulo funciona depois da janela de proteção
                case 4 -> { justKeys.add(Input.Keys.SPACE); next(); }
                case 5 -> { expect(player.getVy() > 0f, "pulo deveria funcionar após a retomada"); next(); }
                case 6 -> { if (stepFrame > 70) next(); }
                // --- pausa + clique mantido em CONTINUAR
                case 7 -> { mouseX = buttonX; mouseY = buttonY; justKeys.add(Input.Keys.ESCAPE); next(); }
                case 8 -> { mouseHeld = true; mouseJust = true; shots.clear(); next(); }
                case 9 -> {
                    if (stepFrame >= 15) expect(!(Boolean) get(screen, "paused"), "clique deveria retomar");
                    expect(shots.isEmpty(), "atirou com o clique que retomou (frame " + stepFrame + ")");
                    if (stepFrame > 30) { mouseHeld = false; next(); }
                }
                // controle positivo: soltar e clicar de novo atira
                case 10 -> { mouseX = aimX; mouseY = aimY; mouseHeld = true; mouseJust = true; next(); }
                case 11 -> { expect(!shots.isEmpty(), "tiro deveria funcionar após soltar o botão"); mouseHeld = false; next(); }
                case 12 -> { if (stepFrame > 60) next(); }
                // --- códex fechado com ESC junto de ESPAÇO e mouse pressionado
                case 13 -> { justKeys.add(Input.Keys.C); next(); }
                case 14 -> {
                    expect(get(screen, "codexOpen"), "C deveria abrir o códex");
                    justKeys.add(Input.Keys.ESCAPE);
                    justKeys.add(Input.Keys.SPACE);
                    mouseHeld = true;
                    shots.clear();
                    next();
                }
                case 15 -> {
                    expect(!(Boolean) get(screen, "codexOpen"), "ESC deveria fechar o códex");
                    expect(player.isGrounded() && player.getVy() <= 0f, "pulou ao fechar o códex");
                    expect(shots.isEmpty(), "atirou ao fechar o códex com o mouse pressionado");
                    if (stepFrame > 20) { mouseHeld = false; next(); }
                }
                default -> Gdx.app.exit();
            }
            if (frame > 2000) {
                failures.add("roteiro não terminou (passo " + step + ")");
                Gdx.app.exit();
            }
        }

        private void expect(Object condition, String message) {
            if (!Boolean.TRUE.equals(condition) && !failures.contains(message)) failures.add(message);
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
