package com.delmartec.projectorb.dialogue;

/**
 * Estado de um diálogo em andamento, sem nenhuma dependência de render ou de
 * input: quem usa informa o tempo (update), o toque de avançar (advance) e os
 * eventos do jogo (signal). Assim a lógica é testável sem janela.
 *
 * Regras:
 * - máquina de escrever: CHARS_PER_SECOND letras por segundo;
 * - advance(): se a fala ainda está sendo escrita, completa; se está completa
 *   e não é interativa, passa para a próxima;
 * - fala interativa (waitFor): advance() só completa o texto; ela avança
 *   sozinha quando o jogo chama signal(waitFor). Um sinal que chega enquanto
 *   o texto ainda é escrito também vale (o jogador pode fazer a ação antes de
 *   terminar de ler).
 */
public final class DialogueRunner {
    public static final float CHARS_PER_SECOND = 42f;

    /** Avisos para o jogo (animação do NPC, eventos, fim). */
    public interface Listener {
        void onLineStart(DialogueScript script, int index, DialogueLine line);

        void onFinished(DialogueScript script);
    }

    private DialogueScript script;
    private int index = -1;
    private float shown;
    private int lastShown;
    private int newChars;
    private boolean pendingSignal;
    private Listener listener;

    public void setListener(Listener listener) { this.listener = listener; }

    public void start(DialogueScript script, int fromIndex) {
        this.script = script;
        begin(Math.max(0, Math.min(fromIndex, script.lines.size - 1)));
    }

    private void begin(int i) {
        index = i;
        shown = 0f;
        lastShown = 0;
        pendingSignal = false;
        if (listener != null) listener.onLineStart(script, index, script.lines.get(index));
    }

    public void update(float delta) {
        newChars = 0;
        if (!isActive()) return;
        int total = line().text.length();
        shown = Math.min(total, shown + CHARS_PER_SECOND * delta);
        int now = (int)shown;
        newChars = now - lastShown;
        lastShown = now;
        if (pendingSignal && isLineComplete()) next();
    }

    /** ESPAÇO/clique. */
    public void advance() {
        if (!isActive()) return;
        if (!isLineComplete()) {
            shown = line().text.length();
            lastShown = (int)shown;
            if (pendingSignal) next();
            return;
        }
        if (!line().isInteractive()) next();
    }

    /** Evento do jogo (o jogador andou, pulou, acertou o alvo...). */
    public void signal(String event) {
        if (!isActive() || !line().isInteractive() || !line().waitFor.equals(event)) return;
        if (isLineComplete()) next();
        else pendingSignal = true;
    }

    private void next() {
        if (index + 1 < script.lines.size) {
            begin(index + 1);
        } else {
            DialogueScript done = script;
            script = null;
            index = -1;
            if (listener != null) listener.onFinished(done);
        }
    }

    public void stop() {
        script = null;
        index = -1;
    }

    public boolean isActive() { return script != null && index >= 0; }
    public DialogueScript getScript() { return script; }
    public int getIndex() { return index; }
    public DialogueLine line() { return script.lines.get(index); }
    public boolean isLineComplete() { return isActive() && shown >= line().text.length(); }
    /** Passo interativo: os controles do jogo valem enquanto ele está na tela. */
    public boolean isInteractive() { return isActive() && line().isInteractive(); }
    /** Quantas letras apareceram no último update (para o som por letra). */
    public int newCharacters() { return newChars; }
    public String visibleText() { return isActive() ? line().text.substring(0, (int)shown) : ""; }
}
