package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.delmartec.projectorb.entities.Npc;

import java.util.HashMap;
import java.util.Map;

/** Checagem (sem janela) do Npc: virar para o jogador e animações de uma vez. */
public final class NpcCheck {
    private NpcCheck() { }

    public static void main(String[] args) {
        Map<String, Animation<TextureRegion>> anims = new HashMap<>();
        TextureRegion f = new TextureRegion();
        Animation<TextureRegion> idle = new Animation<>(0.1f, f, f);
        idle.setPlayMode(Animation.PlayMode.LOOP);
        Animation<TextureRegion> wave = new Animation<>(0.1f, f, f, f);
        wave.setPlayMode(Animation.PlayMode.NORMAL);
        anims.put("idle", idle);
        anims.put("wave", wave);
        Npc npc = new Npc("pi", anims, 48, 4, "idle", 500f, 126f);

        boolean ok = true;
        npc.update(0.01f, 900f);
        ok &= npc.isFacingRight();
        npc.update(0.01f, 100f);
        ok &= !npc.isFacingRight();
        npc.setState("wave");
        ok &= npc.getState().equals("wave");
        npc.update(0.2f, 100f);
        ok &= npc.getState().equals("wave");
        npc.update(0.2f, 100f);
        ok &= npc.getState().equals("idle");      // tocou uma vez e voltou ao repouso
        npc.setState("inexistente");
        ok &= npc.getState().equals("idle");
        System.out.println(ok ? "NPC CHECK: PASS" : "NPC CHECK: FAIL");
        System.exit(ok ? 0 : 1);
    }
}
