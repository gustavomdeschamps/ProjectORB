package com.delmartec.projectorb.quiz;

import com.badlogic.gdx.utils.IntArray;

import java.util.Random;

/**
 * Regras do quiz, sem render nem input (testável sem janela).
 *
 * - 1ª rodada: todas as perguntas, uma vez.
 * - Aprovação: passScore acertos no total. Quem não passa revê só as que
 *   errou (nunca as que acertou), em rodadas de revisão, até passar; na
 *   revisão, a aprovação vale assim que a nota é atingida. Não existe
 *   reprovação definitiva (sem game over).
 * - A ordem das alternativas é embaralhada cada vez que a pergunta aparece;
 *   o índice da certa acompanha o embaralhamento.
 */
public final class QuizSession {
    public enum Phase { ASKING, FEEDBACK, PASSED }

    private final QuizData data;
    private final Random random;
    private final boolean[] solved;
    private final IntArray round = new IntArray();
    private final int[] order = new int[4];
    private int pos;
    private int reviewRound;
    private int firstTryCorrect;
    private int chosenSlot = -1;
    private boolean lastCorrect;
    private Phase phase = Phase.ASKING;

    public QuizSession(QuizData data, long seed) {
        this.data = data;
        this.random = new Random(seed);
        this.solved = new boolean[data.questions.size];
        for (int i = 0; i < data.questions.size; i++) round.add(i);
        shuffle();
    }

    private void shuffle() {
        int n = question().options.size;
        for (int i = 0; i < n; i++) order[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = order[i]; order[i] = order[j]; order[j] = t;
        }
    }

    public QuizQuestion question() { return data.questions.get(round.get(pos)); }
    public int optionCount() { return question().options.size; }
    /** Texto da alternativa mostrada na posição 'slot' (0..3). */
    public String option(int slot) { return question().options.get(order[slot]); }
    /** Posição (0..3) em que a alternativa certa está sendo mostrada. */
    public int correctSlot() {
        for (int s = 0; s < optionCount(); s++) if (order[s] == question().answer) return s;
        return -1;
    }

    /** Responde; só vale enquanto a pergunta está sendo feita. */
    public boolean answer(int slot) {
        if (phase != Phase.ASKING || slot < 0 || slot >= optionCount()) return false;
        chosenSlot = slot;
        lastCorrect = order[slot] == question().answer;
        if (lastCorrect) {
            solved[round.get(pos)] = true;
            if (reviewRound == 0) firstTryCorrect++;
        }
        phase = Phase.FEEDBACK;
        return lastCorrect;
    }

    /** Depois da explicação: próxima pergunta, nova rodada de revisão ou aprovação. */
    public void next() {
        if (phase != Phase.FEEDBACK) return;
        chosenSlot = -1;
        if (reviewRound > 0 && score() >= data.passScore) { phase = Phase.PASSED; return; }
        pos++;
        if (pos >= round.size) {
            if (score() >= data.passScore) { phase = Phase.PASSED; return; }
            round.clear();
            for (int i = 0; i < solved.length; i++) if (!solved[i]) round.add(i);
            pos = 0;
            reviewRound++;
        }
        phase = Phase.ASKING;
        shuffle();
    }

    public Phase getPhase() { return phase; }
    public boolean isPassed() { return phase == Phase.PASSED; }
    public boolean wasLastCorrect() { return lastCorrect; }
    public int getChosenSlot() { return chosenSlot; }
    /** Acertos no total (1ª rodada + revisões). */
    public int score() {
        int s = 0;
        for (boolean b : solved) if (b) s++;
        return s;
    }
    public int getFirstTryCorrect() { return firstTryCorrect; }
    public int getTotalQuestions() { return solved.length; }
    public int getPassScore() { return data.passScore; }
    /** 0 = primeira rodada; 1+ = revisão das que errou. */
    public int getReviewRound() { return reviewRound; }
    public int getPosition() { return pos; }
    public int getRoundSize() { return round.size; }
}
