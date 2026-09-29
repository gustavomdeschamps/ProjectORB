package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.files.FileHandle;
import com.delmartec.projectorb.quiz.QuizData;
import com.delmartec.projectorb.quiz.QuizSession;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checagem (sem janela) das regras do quiz, com as perguntas reais do JSON. */
public final class QuizCheck {
    private static final List<String> failures = new ArrayList<>();

    private QuizCheck() { }

    public static void main(String[] args) {
        QuizData data = QuizData.load(new FileHandle(new File("quiz/octogono.json")));
        expect(data.questions.size == 6, "6 perguntas no JSON");
        expect(data.passScore == 4, "aprovação com 4");
        for (int i = 0; i < data.questions.size; i++) {
            expect(data.questions.get(i).options.size == 4, "pergunta " + i + " com 4 alternativas");
            expect(!data.questions.get(i).explanation.isEmpty(), "pergunta " + i + " com explicação");
        }

        // tudo certo: aprova na 1ª rodada, 6 de primeira
        QuizSession s = new QuizSession(data, 1);
        for (int i = 0; i < 6; i++) { expect(s.answer(s.correctSlot()), "certa reconhecida"); s.next(); }
        expect(s.isPassed() && s.getFirstTryCorrect() == 6, "6/6 aprova direto");

        // 3 certas e 3 erradas: revisão só das erradas; aprova no 4º acerto
        s = new QuizSession(data, 2);
        Set<String> wrongPrompts = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            if (i % 2 == 0) s.answer(s.correctSlot());
            else { wrongPrompts.add(s.question().prompt); s.answer((s.correctSlot() + 1) % 4); }
            s.next();
        }
        expect(!s.isPassed() && s.getReviewRound() == 1 && s.getRoundSize() == 3, "revisão com as 3 erradas");
        expect(wrongPrompts.contains(s.question().prompt), "revisão não repete as que acertou");
        s.answer((s.correctSlot() + 1) % 4);              // erra de novo
        s.next();
        expect(!s.isPassed() && wrongPrompts.contains(s.question().prompt), "continua revisando");
        s.answer(s.correctSlot());                       // 4º acerto
        s.next();
        expect(s.isPassed() && s.score() == 4 && s.getFirstTryCorrect() == 3, "aprova ao chegar em 4 (3 de primeira)");

        // errar tudo nunca reprova de vez: sempre volta para revisão
        s = new QuizSession(data, 3);
        for (int k = 0; k < 30; k++) { s.answer((s.correctSlot() + 1) % 4); s.next(); }
        expect(!s.isPassed() && s.getPhase() == QuizSession.Phase.ASKING, "sem game over no quiz");

        // embaralha: a certa não fica sempre na mesma posição
        Set<Integer> slots = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) slots.add(new QuizSession(data, seed).correctSlot());
        expect(slots.size() > 1, "alternativas embaralhadas");

        System.out.println(failures.isEmpty() ? "QUIZ CHECK: PASS" : "QUIZ CHECK: FAIL");
        failures.forEach(f -> System.out.println("  - " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void expect(boolean ok, String msg) { if (!ok && !failures.contains(msg)) failures.add(msg); }
}
