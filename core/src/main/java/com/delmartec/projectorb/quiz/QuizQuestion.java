package com.delmartec.projectorb.quiz;

import com.badlogic.gdx.utils.Array;

/** Uma pergunta de múltipla escolha (dados carregados do JSON). */
public class QuizQuestion {
    /** Conceito da fase que a pergunta reforça (VÉRTICES, LADOS, ÂNGULOS...). */
    public String concept = "";
    public String prompt = "";
    /** 4 alternativas, na ordem do arquivo. */
    public Array<String> options = new Array<>();
    /** Índice da certa em 'options' (antes de embaralhar). */
    public int answer;
    /** Uma frase: por que a certa é a certa. Mostrada sempre, mesmo no acerto. */
    public String explanation = "";
}
