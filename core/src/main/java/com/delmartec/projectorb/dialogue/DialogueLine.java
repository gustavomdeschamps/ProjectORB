package com.delmartec.projectorb.dialogue;

/**
 * Uma fala. Campos públicos porque é carregada do JSON pelo
 * com.badlogic.gdx.utils.Json (dados, não lógica).
 */
public class DialogueLine {
    /** Texto em português do Brasil. */
    public String text = "";
    /** Animação do NPC durante a fala (talk, point, wave, cheer...). Opcional. */
    public String anim;
    /**
     * Passo interativo: a fala só avança quando o jogo sinaliza este evento
     * (ex.: "move", "jump", "doubleJump", "dash", "shoot", "hitTarget").
     * Enquanto espera, os controles do jogo ficam liberados.
     */
    public String waitFor;
    /** Evento disparado quando a fala começa (ex.: "spawnTarget", "openGate"). Opcional. */
    public String event;

    public boolean isInteractive() { return waitFor != null && !waitFor.isEmpty(); }
}
