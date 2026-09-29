package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.dialogue.DialogueRunner;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.screens.GameScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Joga o tutorial do Pi de ponta a ponta com input simulado (proxy de
 * Gdx.input): avança as falas, anda, pula, salto duplo, dash e acerta o alvo
 * de treino mirando pela projeção real da câmera. Confere que o portão da
 * seção 0 só abre no fim e que, durante o diálogo, o ORB não leva dano.
 */
public final class TutorialCheck {
    private TutorialCheck() { }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(false);
        Game game = new Game();
        new Lwjgl3Application(game, config);
        System.out.println(game.failures.isEmpty() ? "TUTORIAL CHECK: PASS" : "TUTORIAL CHECK: FAIL");
        game.failures.forEach(f -> System.out.println("  - " + f));
        game.log.forEach(l -> System.out.println("  . " + l));
        System.exit(game.failures.isEmpty() ? 0 : 1);
    }

    private static final class Game extends ProjectOrbGame {
        final List<String> failures = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        private final Set<Integer> just = new HashSet<>();
        private final Set<Integer> held = new HashSet<>();
        private boolean mouseHeld, mouseJust;
        private int mouseX, mouseY;
        private int frame, stepFrames;
        private String lastWait = "";
        private boolean gateOpenedEarly;

        @Override public void create() {
            super.create();
            startGame();
        }

        @Override public void render() {
            final Input real = Gdx.input;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
                (p, m, a) -> switch (m.getName()) {
                    case "isKeyJustPressed" -> just.contains((Integer) a[0]);
                    case "isKeyPressed" -> held.contains((Integer) a[0]) || just.contains((Integer) a[0]);
                    case "isButtonPressed" -> mouseHeld && (Integer) a[0] == Input.Buttons.LEFT;
                    case "isButtonJustPressed" -> mouseJust && (Integer) a[0] == Input.Buttons.LEFT;
                    case "getX" -> a == null ? mouseX : m.invoke(real, a);
                    case "getY" -> a == null ? mouseY : m.invoke(real, a);
                    default -> m.invoke(real, a);
                });
            script();
            super.render();
            just.clear();
            mouseJust = false;
            frame++;
            stepFrames++;
        }

        private void script() {
            GameScreen s = (GameScreen) getScreen();
            DialogueRunner d = get(s, "dialogue");
            boolean[] cleared = get(s, "sectionCleared");
            boolean done = get(s, "tutorialDone");
            Player player = get(s, "player");

            if (done) {
                if (!cleared[0]) failures.add("tutorial terminou mas o portão não abriu");
                if (gateOpenedEarly) failures.add("portão abriu antes do fim do tutorial");
                log.add("tutorial concluído em " + frame + " frames");
                Gdx.app.exit();
                return;
            }
            if (frame > 4000) {
                failures.add("tutorial não terminou (fala " + (d.isActive() ? d.getIndex() : -1) + ")");
                Gdx.app.exit();
                return;
            }
            if (!d.isActive()) return;
            if (cleared[0] && d.getIndex() < 11) gateOpenedEarly = true;

            // durante o diálogo o ORB não pode levar dano: um projétil inimigo de
            // verdade é lançado nele e a vida tem que continuar cheia
            if (frame == 400) {
                List<com.delmartec.projectorb.entities.Projectile> shots = get(s, "enemyProjectiles");
                shots.add(new com.delmartec.projectorb.entities.Projectile(player.getX(), player.getY(), 0f, 0f, 12f, true, 40, false));
            }
            if (frame == 410 && player.getHealth() < com.delmartec.projectorb.utils.Constants.MAX_HEALTH) {
                failures.add("ORB levou dano durante o diálogo");
            }

            String wait = d.line().waitFor == null ? "" : d.line().waitFor;
            if (!wait.equals(lastWait)) { lastWait = wait; stepFrames = 0; if (!wait.isEmpty()) log.add("passo: " + wait); }
            held.clear();
            mouseHeld = false;
            switch (wait) {
                case "" -> { if (stepFrames % 12 == 0) just.add(Input.Keys.SPACE); }
                case "move" -> held.add(Input.Keys.D);
                case "jump" -> { if (player.isGrounded() && stepFrames % 20 == 5) just.add(Input.Keys.SPACE); }
                case "doubleJump" -> {
                    if (player.isGrounded() && stepFrames % 40 == 5) just.add(Input.Keys.SPACE);
                    else if (!player.isGrounded() && stepFrames % 40 == 15) just.add(Input.Keys.SPACE);
                }
                case "dash" -> { if (stepFrames % 50 == 5) just.add(Input.Keys.SHIFT_LEFT); }
                case "hitTarget" -> aimAndClick(s);
                default -> { }
            }
        }

        private void aimAndClick(GameScreen s) {
            try {
                Viewport vp = get(s, "worldViewport");
                Method ty = GameScreen.class.getDeclaredMethod("targetY");
                ty.setAccessible(true);
                Vector2 v = vp.project(new Vector2(860f, (Float) ty.invoke(s)));
                mouseX = Math.round(v.x);
                mouseY = Math.round(Gdx.graphics.getHeight() - v.y);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            if (stepFrames % 20 == 10) { mouseHeld = true; mouseJust = true; }
        }

        @SuppressWarnings("unchecked")
        private static <T> T get(Object target, String name) {
            try {
                Field f = target.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return (T) f.get(target);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
