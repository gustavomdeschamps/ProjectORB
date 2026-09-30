package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.level.LevelDemo;
import com.delmartec.projectorb.level.Platform;
import com.delmartec.projectorb.level.StairGate;
import com.delmartec.projectorb.utils.Constants;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checagem da escadinha (etapa B), sem janela nem GL, com a física real do
 * Player e o nível real (LevelDemo). Para cada um dos 5 portões:
 *  1. FECHADA: muitas tentativas (posições no chão e em cima das plataformas
 *     vizinhas x tempos de pulo, pulo duplo e dash) e nenhuma passa da massa;
 *  2. ABERTA: um controlador simples (anda, pula quando bate, pulo duplo)
 *     atravessa de ida subindo até o topo e descendo, e também volta;
 *  3. SEM SOFT-LOCK: renascendo no ponto de renascimento da seção seguinte (em
 *     cima da escada aberta, como o jogo faz), o ORB não fica preso dentro de
 *     nada e chega até a massa do próximo portão.
 * O caso "morrer com a escada aberta e renascer" com o jogo inteiro está em
 * StairRespawnCheck.
 */
public final class StairGateCheck {
    private static final float DT = 1f / 60f;
    private static final Set<Integer> just = new HashSet<>();
    private static final Set<Integer> held = new HashSet<>();
    private static final List<String> failures = new ArrayList<>();
    private static int attempts;

    private StairGateCheck() { }

    public static void main(String[] args) {
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
            new Class<?>[] { Input.class }, (proxy, method, a) -> switch (method.getName()) {
                case "isKeyJustPressed" -> just.contains((Integer) a[0]);
                case "isKeyPressed" -> held.contains((Integer) a[0]) || just.contains((Integer) a[0]);
                default -> method.getReturnType() == boolean.class ? false : 0;
            });
        LevelDemo level = new LevelDemo();
        System.out.printf("  pulo simples %.0f px, pulo duplo %.0f px, degrau %.0f px (%.0f%% do pulo)%n",
            StairGate.JUMP_HEIGHT, StairGate.MAX_REACH, StairGate.BLOCK_H, 100f * StairGate.BLOCK_H / StairGate.JUMP_HEIGHT);
        if (StairGate.BLOCK_H > 0.4f * StairGate.JUMP_HEIGHT) failures.add("degrau acima de 40% do pulo simples");
        if (StairGate.TOP_COLS * StairGate.BLOCK_W < 2 * Constants.PLAYER_HIT_W) failures.add("topo menor que 2 larguras do ORB");

        for (int g = 0; g < 5; g++) {
            StairGate s = StairGate.forSection(level.getSection(g), level.getPlatforms());
            Rectangle m = s.getMass();
            System.out.printf("  portão %d: massa x=%.0f..%.0f altura %.0f | escada x=%.0f..%.0f topo y=%.0f%n",
                g, m.x, m.x + m.width, m.height, s.getStairLeft(), s.getStairRight(), s.getPlateauTop());
            float oldGateLeft = level.getSection(g).endX - 88f, oldGateRight = level.getSection(g).endX;
            if (m.x > oldGateLeft + 8f || m.x + m.width < oldGateRight - 8f) failures.add("portão " + g + ": massa fora do X do portão antigo");
            if (s.getStairLeft() > oldGateRight + 150f || s.getStairRight() < oldGateLeft) failures.add("portão " + g + ": escada longe do portão antigo");
            for (Platform p : level.getPlatforms()) {
                if (p.bounds.width >= Constants.WORLD_WIDTH) continue;
                for (Platform c : s.getColumns()) {
                    if (c.bounds.overlaps(p.bounds)) failures.add("portão " + g + ": degrau encosta numa plataforma");
                }
            }
            closedBlocks(level, s, g);
            openPasses(level, s, g, true);
            openPasses(level, s, g, false);
            noSoftLock(level, g);
        }
        System.out.println("  tentativas com a massa fechada: " + attempts);
        System.out.println(failures.isEmpty() ? "STAIR GATE CHECK: PASS" : "STAIR GATE CHECK: FAIL");
        for (String f : failures) System.out.println("  - " + f);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static List<Platform> world(LevelDemo level, StairGate s) {
        List<Platform> list = new ArrayList<>(level.getPlatforms());
        s.addCollision(list);
        return list;
    }

    private static void step(Player p, List<Platform> w) {
        p.update(DT, w, null, true);
        just.clear();
    }

    private static Player settle(float x, float y, List<Platform> w) {
        just.clear();
        held.clear();
        Player p = new Player(x, y);
        for (int i = 0; i < 90; i++) step(p, w);
        return p;
    }

    /** 1: com a massa fechada, nenhuma combinação de pulo duplo + dash passa. */
    private static void closedBlocks(LevelDemo level, StairGate s, int g) {
        List<Platform> w = world(level, s);
        Rectangle m = s.getMass();
        List<float[]> starts = new ArrayList<>();
        for (float d : new float[] { 60f, 150f, 300f, 450f }) starts.add(new float[] { m.x - d, 230f });
        for (Platform p : level.getPlatforms()) {
            Rectangle b = p.bounds;
            if (b.width >= Constants.WORLD_WIDTH || b.x + b.width < m.x - 900f || b.x > m.x) continue;
            starts.add(new float[] { b.x + b.width - 40f, b.y + b.height + 60f });   // na ponta da plataforma
        }
        for (float[] st : starts) {
            for (int jumpAt : new int[] { 0, 6, 12, 24 }) {
                for (int second : new int[] { 4, 8, 14, 20, 26, 32, 40 }) {
                    for (int dashAfter : new int[] { -1, 0, 6, 12 }) {
                        attempts++;
                        Player p = settle(st[0], st[1], w);
                        held.add(Input.Keys.D);
                        for (int f = 0; f < 200; f++) {
                            if (f == jumpAt) just.add(Input.Keys.SPACE);
                            if (f == jumpAt + second) just.add(Input.Keys.SPACE);
                            if (dashAfter >= 0 && f == jumpAt + second + dashAfter) just.add(Input.Keys.SHIFT_LEFT);
                            step(p, w);
                            if (p.getX() - Constants.PLAYER_HIT_W / 2f > m.x + m.width) {
                                failures.add(String.format("portão %d FECHADO atravessado (início x=%.0f y=%.0f, pulo %d, 2º +%d, dash +%d)",
                                    g, st[0], st[1], jumpAt, second, dashAfter));
                                return;
                            }
                        }
                        held.clear();
                    }
                }
            }
        }
    }

    /** 2: aberta, atravessa (ida: da esquerda para a direita; volta: o contrário). */
    private static void openPasses(LevelDemo level, StairGate closed, int g, boolean forward) {
        StairGate s = StairGate.forSection(level.getSection(g), level.getPlatforms());
        s.openImmediately();
        List<Platform> w = world(level, s);
        float startX = forward ? s.getStairLeft() - 120f : s.getStairRight() + 120f;
        float goal = forward ? s.getStairRight() + 60f : s.getStairLeft() - 60f;
        Player p = settle(startX, 230f, w);
        held.add(forward ? Input.Keys.D : Input.Keys.A);
        float maxFeet = 0f;
        boolean done = false;
        float lastX = p.getX();
        for (int f = 0; f < 900 && !done; f++) {
            boolean blocked = Math.abs(p.getX() - lastX) < 0.5f;
            lastX = p.getX();
            if (blocked && p.isGrounded()) just.add(Input.Keys.SPACE);
            else if (blocked && !p.isGrounded() && p.getVy() < 0f) just.add(Input.Keys.SPACE);
            step(p, w);
            maxFeet = Math.max(maxFeet, p.getFeetY());
            done = forward ? p.getX() > goal : p.getX() < goal;
        }
        held.clear();
        String dir = forward ? "ida" : "volta";
        if (!done) failures.add("portão " + g + " ABERTO: não atravessou (" + dir + "), parou em x=" + Math.round(p.getX()));
        else if (maxFeet < s.getPlateauTop() - 1f) failures.add("portão " + g + " ABERTO: atravessou sem subir ao topo (" + dir + ")");
    }

    /** 3: renasce no ponto da seção seguinte, com a escada aberta (como o jogo). */
    private static void noSoftLock(LevelDemo level, int g) {
        StairGate s = StairGate.forSection(level.getSection(g), level.getPlatforms());
        s.openImmediately();
        StairGate next = g + 1 < 5 ? StairGate.forSection(level.getSection(g + 1), level.getPlatforms()) : null;
        List<Platform> w = new ArrayList<>(level.getPlatforms());
        s.addCollision(w);
        if (next != null) next.addCollision(w);
        float x = level.getSection(g + 1).respawnX;
        float half = Constants.PLAYER_HIT_W / 2f;
        float y = Math.max(230f, s.topAt(x - half, x + half) + Constants.PLAYER_HIT_H / 2f + 1f);
        Player p = new Player(x, y);
        for (Platform pl : w) {
            if (p.getBounds().overlaps(pl.bounds)) {
                failures.add("seção " + (g + 1) + ": renasce DENTRO de um sólido em x=" + Math.round(x));
                return;
            }
        }
        just.clear();
        held.clear();
        for (int i = 0; i < 60; i++) step(p, w);
        if (!p.isGrounded()) failures.add("seção " + (g + 1) + ": não assenta depois de renascer");
        if (next == null) return;
        held.add(Input.Keys.D);
        float lastX = p.getX();
        boolean reached = false;
        for (int f = 0; f < 1500 && !reached; f++) {
            boolean blocked = Math.abs(p.getX() - lastX) < 0.5f;
            lastX = p.getX();
            if (blocked && p.isGrounded()) just.add(Input.Keys.SPACE);
            step(p, w);
            reached = p.getX() + half >= next.getMass().x - 2f;
        }
        held.clear();
        if (!reached) failures.add("seção " + (g + 1) + ": depois de renascer não chega ao próximo portão (x=" + Math.round(p.getX()) + ")");
    }
}
