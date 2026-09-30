package com.delmartec.projectorb.utils;

/**
 * Deixa o botão ativado visível no estado "pressionado" por um instante antes
 * da ação acontecer (sem isso a tela trocava no mesmo frame e o estado nunca
 * aparecia). Mais curto em movimento reduzido.
 */
public final class ButtonPress {
    private int pending = -1;
    private float timer;

    public void press(int index, GameSettings settings) {
        if (pending >= 0) return;
        pending = index;
        timer = settings.isReducedMotion() ? 0.06f : 0.12f;
    }

    /** Devolve o índice cuja ação deve acontecer agora, ou -1. */
    public int update(float delta) {
        if (pending < 0) return -1;
        timer -= delta;
        if (timer > 0f) return -1;
        int done = pending;
        pending = -1;
        return done;
    }

    public boolean isPressed(int index) { return pending == index; }

    /** Enquanto um botão está afundado, a tela ignora novos comandos. */
    public boolean isBusy() { return pending >= 0; }
}
