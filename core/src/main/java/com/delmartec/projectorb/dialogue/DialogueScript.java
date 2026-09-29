package com.delmartec.projectorb.dialogue;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

/**
 * Um roteiro de falas de um falante, definido em JSON (assets/dialogue/).
 *
 * <pre>
 * { "id": "pi_tutorial", "speaker": "PI", "portrait": "pi",
 *   "lines": [ { "text": "Oi!", "anim": "wave" },
 *              { "text": "Ande até mim.", "anim": "point", "waitFor": "move" } ] }
 * </pre>
 */
public class DialogueScript {
    public String id = "";
    /** Nome mostrado na caixa de fala. */
    public String speaker = "";
    /** Chave do retrato (Assets.portrait). */
    public String portrait = "";
    public Array<DialogueLine> lines = new Array<>();

    public static DialogueScript load(FileHandle file) {
        return new Json().fromJson(DialogueScript.class, file);
    }

    /** Índice da primeira fala com waitFor == event, ou -1. */
    public int indexOfStep(String event) {
        for (int i = 0; i < lines.size; i++) {
            if (event.equals(lines.get(i).waitFor)) return i;
        }
        return -1;
    }
}
