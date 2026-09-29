package com.delmartec.projectorb.quiz;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

/** Perguntas do quiz e nota de aprovação, definidas em assets/quiz/*.json. */
public class QuizData {
    public int passScore = 4;
    public Array<QuizQuestion> questions = new Array<>();

    public static QuizData load(FileHandle file) {
        return new Json().fromJson(QuizData.class, file);
    }
}
