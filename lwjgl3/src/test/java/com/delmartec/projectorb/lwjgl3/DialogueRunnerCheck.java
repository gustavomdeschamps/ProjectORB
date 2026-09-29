package com.delmartec.projectorb.lwjgl3;

import com.delmartec.projectorb.dialogue.DialogueLine;
import com.delmartec.projectorb.dialogue.DialogueRunner;
import com.delmartec.projectorb.dialogue.DialogueScript;

import java.util.ArrayList;
import java.util.List;

/** Checagem automatizada (sem janela) das regras do DialogueRunner. */
public final class DialogueRunnerCheck {
    private static final List<String> failures = new ArrayList<>();

    private DialogueRunnerCheck() { }

    public static void main(String[] args) {
        DialogueScript s = new DialogueScript();
        s.speaker = "PI";
        s.lines.add(line("Oi, ORB!", null, "anim:wave"));
        s.lines.add(line("Pule.", "jump", null));
        s.lines.add(line("Muito bem.", null, "openGate"));

        List<String> events = new ArrayList<>();
        boolean[] finished = { false };
        DialogueRunner r = new DialogueRunner();
        r.setListener(new DialogueRunner.Listener() {
            @Override public void onLineStart(DialogueScript sc, int i, DialogueLine l) { if (l.event != null) events.add(l.event); }
            @Override public void onFinished(DialogueScript sc) { finished[0] = true; }
        });
        r.start(s, 0);

        r.update(0.05f);
        expect(!r.isLineComplete(), "fala 0 deveria estar sendo escrita");
        expect(r.newCharacters() > 0, "máquina de escrever deveria revelar letras");
        r.advance();
        expect(r.getIndex() == 0 && r.isLineComplete(), "1º avançar completa a fala, não pula");
        r.advance();
        expect(r.getIndex() == 1, "2º avançar passa para a próxima");

        // interativa: avançar não pula; sinal errado não pula; sinal certo pula
        r.advance();
        expect(r.getIndex() == 1 && r.isLineComplete(), "avançar em fala interativa só completa o texto");
        r.advance();
        expect(r.getIndex() == 1, "fala interativa não avança com ESPAÇO");
        r.signal("dash");
        expect(r.getIndex() == 1, "sinal errado não avança");
        r.signal("jump");
        expect(r.getIndex() == 2, "sinal certo avança");

        // sinal antes de terminar de escrever também vale
        r.start(s, 1);
        r.signal("jump");
        expect(r.getIndex() == 1, "sinal com texto incompleto espera o texto");
        for (int i = 0; i < 30; i++) r.update(0.05f);
        expect(r.getIndex() == 2, "sinal guardado avança quando o texto termina");

        r.update(1f);
        r.advance();
        expect(finished[0] && !r.isActive(), "fim do roteiro avisa e desativa");
        expect(events.contains("openGate") && events.contains("anim:wave"), "eventos de início de fala disparam");
        expect(s.indexOfStep("jump") == 1, "indexOfStep encontra o passo interativo");

        System.out.println(failures.isEmpty() ? "DIALOGUE RUNNER CHECK: PASS" : "DIALOGUE RUNNER CHECK: FAIL");
        failures.forEach(f -> System.out.println("  - " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static DialogueLine line(String text, String waitFor, String event) {
        DialogueLine l = new DialogueLine();
        l.text = text;
        l.waitFor = waitFor;
        l.event = event;
        return l;
    }

    private static void expect(boolean ok, String msg) {
        if (!ok) failures.add(msg);
    }
}
