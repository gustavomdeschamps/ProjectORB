package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.level.Platform;
import com.delmartec.projectorb.utils.Constants;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checagem automatizada do movimento do Player, sem janela nem GL: Gdx.input
 * é um proxy controlado pelo roteiro. Não entra no build de produção.
 */
public final class PlayerMovementCheck {
    private static final float DT = 1f / 60f;
    private static final Set<Integer> just = new HashSet<>();
    private static final Set<Integer> held = new HashSet<>();
    private static final List<String> failures = new ArrayList<>();

    private PlayerMovementCheck() { }

    public static void main(String[] args) {
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
            new Class<?>[] { Input.class }, (proxy, method, a) -> switch (method.getName()) {
                case "isKeyJustPressed" -> just.contains((Integer) a[0]);
                case "isKeyPressed" -> held.contains((Integer) a[0]) || just.contains((Integer) a[0]);
                default -> method.getReturnType() == boolean.class ? false : 0;
            });

        dashOnGroundKeepsGrounded();
        dashOffLedgeCountsAsLeavingLedge();
        jumpsFromGround();
        jumpsAfterWalkingOffLedge(0);
        jumpsAfterWalkingOffLedge(20); // bem depois da antiga janela de coyote (0,11 s)

        System.out.println(failures.isEmpty() ? "PLAYER MOVEMENT CHECK: PASS" : "PLAYER MOVEMENT CHECK: FAIL");
        for (String f : failures) System.out.println("  - " + f);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static List<Platform> floor() {
        List<Platform> platforms = new ArrayList<>();
        platforms.add(new Platform(0, 0, Constants.WORLD_WIDTH, Constants.FLOOR_Y));
        return platforms;
    }

    private static void step(Player p, List<Platform> platforms, int frames) {
        for (int i = 0; i < frames; i++) {
            p.update(DT, platforms, null, true);
            just.clear();
        }
    }

    private static Player settled(float x, List<Platform> platforms) {
        just.clear();
        held.clear();
        Player p = new Player(x, 230f);
        step(p, platforms, 90);
        expect(p.isGrounded(), "jogador deveria assentar no piso");
        return p;
    }

    private static void dashOnGroundKeepsGrounded() {
        List<Platform> platforms = floor();
        Player p = settled(600f, platforms);
        just.add(Input.Keys.SHIFT_LEFT);
        step(p, platforms, 1);
        int frames = 0;
        while (p.isDashing() && frames < 60) {
            expect(p.isGrounded(), "dash no chão: grounded=false no frame " + frames);
            step(p, platforms, 1);
            frames++;
        }
        expect(frames > 0, "dash deveria ter acontecido");
        // Salto logo após o dash no chão: tem que haver pulo de chão + pulo no ar.
        expect(countJumps(p, platforms) == 2, "após dash no chão deveria ter 2 saltos");
    }

    private static void dashOffLedgeCountsAsLeavingLedge() {
        List<Platform> platforms = floor();
        // Plataforma elevada estreita; o dash sai pela borda direita.
        platforms.add(new Platform(500, 400, 200, 64));
        just.clear();
        held.clear();
        Player p = new Player(670f, 520f);
        step(p, platforms, 90);
        expect(p.isGrounded() && p.getY() > 400f, "jogador deveria estar sobre a plataforma elevada");
        just.add(Input.Keys.SHIFT_LEFT);
        step(p, platforms, 1);
        while (p.isDashing()) step(p, platforms, 1);
        expect(!p.isGrounded(), "dash para fora da borda deveria deixar o jogador no ar");
        expect(countJumps(p, platforms) == 1, "saída de borda por dash deveria dar só 1 salto no ar");
    }

    private static void jumpsFromGround() {
        List<Platform> platforms = floor();
        Player p = settled(600f, platforms);
        expect(countJumps(p, platforms) == 2, "no chão: 1 salto no chão + 1 no ar");
    }

    private static void jumpsAfterWalkingOffLedge(int fallFrames) {
        List<Platform> platforms = floor();
        platforms.add(new Platform(500, 400, 200, 64));
        just.clear();
        held.clear();
        Player p = new Player(670f, 520f);
        step(p, platforms, 90);
        held.add(Input.Keys.D);
        int guard = 0;
        while (p.isGrounded() && guard++ < 120) step(p, platforms, 1);
        held.clear();
        expect(!p.isGrounded(), "deveria sair da borda andando");
        step(p, platforms, fallFrames);
        expect(countJumps(p, platforms) == 1, "saída de borda andando (+" + fallFrames + " frames): 1 salto no ar");
    }

    /** Aperta ESPAÇO várias vezes, ainda no ar, e conta quantos saltos saíram. */
    private static int countJumps(Player p, List<Platform> platforms) {
        int jumps = 0;
        for (int i = 0; i < 4; i++) {
            float before = p.getVy();
            just.add(Input.Keys.SPACE);
            step(p, platforms, 1);
            if (p.getVy() > before + 100f && p.getVy() > Constants.JUMP_SPEED * 0.8f) jumps++;
            step(p, platforms, 6);
        }
        // Deixa aterrissar para não contaminar o próximo cenário.
        step(p, platforms, 180);
        return jumps;
    }

    private static void expect(boolean condition, String message) {
        if (!condition && !failures.contains(message)) failures.add(message);
    }
}
