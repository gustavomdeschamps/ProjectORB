package com.delmartec.projectorb.quiz;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/**
 * Tela do quiz do Octógono (sobre o jogo, em coordenadas de HUD 1920x1080).
 * Responder: 1-4, setas + ENTER/ESPAÇO, ou mouse. Depois de cada resposta a
 * explicação aparece (também no acerto); ENTER/ESPAÇO/clique continua.
 */
public final class QuizOverlay {
    /** Avisos para o jogo (reação do Octógono, aprovação). */
    public interface Listener {
        void onAnswer(boolean correct);

        void onNext();

        void onPassed(QuizSession session);
    }

    private static final int PX = Constants.PIXEL_SCALE;
    private static final float X = 200f, Y = 96f, W = 1520f, H = 872f;
    private static final float TEXT_X = 440f, TEXT_W = 1240f;
    private static final int[] KEYS = { Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3, Input.Keys.NUM_4 };
    private static final int[] PAD = { Input.Keys.NUMPAD_1, Input.Keys.NUMPAD_2, Input.Keys.NUMPAD_3, Input.Keys.NUMPAD_4 };
    private static final Color NAME = new Color(0.96f, 0.18f, 0.82f, 1f);
    private static final Color CONCEPT = new Color(0.32f, 0.85f, 0.92f, 1f);
    private static final Color TEXT = new Color(0.95f, 0.94f, 1f, 1f);
    private static final Color RIGHT = new Color(0.40f, 1f, 0.76f, 1f);
    private static final Color WRONG = new Color(1f, 0.45f, 0.55f, 1f);
    private static final Color SOFT = new Color(0.76f, 0.82f, 1f, 1f);

    private final ProjectOrbGame game;
    private final QuizSession session;
    private final TextureRegion portrait;
    private final Listener listener;
    private final Rectangle[] slots = new Rectangle[4];
    private int hover;

    public QuizOverlay(ProjectOrbGame game, QuizSession session, TextureRegion portrait, Listener listener) {
        this.game = game;
        this.session = session;
        this.portrait = portrait;
        this.listener = listener;
        for (int i = 0; i < 4; i++) slots[i] = new Rectangle(TEXT_X, 588f - i * 88f, TEXT_W, 76f);
    }

    public QuizSession getSession() { return session; }

    /** @param mouseHud mouse em coordenadas de HUD; acceptInput=false logo após pausa */
    public void update(Vector2 mouseHud, boolean acceptInput) {
        if (session.isPassed()) return;
        for (int i = 0; i < session.optionCount(); i++) if (slots[i].contains(mouseHud)) hover = i;
        if (!acceptInput) return;
        boolean click = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE);

        if (session.getPhase() == QuizSession.Phase.ASKING) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) hover = (hover + 3) % 4;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) hover = (hover + 1) % 4;
            int chosen = -1;
            for (int i = 0; i < 4; i++) {
                if (Gdx.input.isKeyJustPressed(KEYS[i]) || Gdx.input.isKeyJustPressed(PAD[i])) chosen = i;
            }
            if (chosen < 0 && confirm) chosen = hover;
            if (chosen < 0 && click) {
                for (int i = 0; i < 4; i++) if (slots[i].contains(mouseHud)) chosen = i;
            }
            if (chosen >= 0) listener.onAnswer(session.answer(chosen));
        } else if (session.getPhase() == QuizSession.Phase.FEEDBACK && (confirm || click)) {
            session.next();
            hover = 0;
            if (session.isPassed()) listener.onPassed(session);
            else listener.onNext();
        }
    }

    public void draw(SpriteBatch batch) {
        if (session.isPassed()) return;
        batch.setColor(0.004f, 0.004f, 0.025f, 0.78f);
        batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        batch.setColor(Color.WHITE);
        game.ui.panel(X, Y, W, H, UiRenderer.CYAN, 1f);

        float pw = portrait.getRegionWidth() * PX, ph = portrait.getRegionHeight() * PX;
        batch.draw(portrait, 244f, Y + H - 40f - ph, pw, ph);

        QuizQuestion q = session.question();
        game.ui.text("OCTÓGONO", TEXT_X, Y + H - 44f, UiRenderer.TEXT, NAME, false);
        String progress = session.getReviewRound() == 0
            ? "PERGUNTA " + (session.getPosition() + 1) + " DE " + session.getTotalQuestions()
            : "REVISÃO  " + (session.getPosition() + 1) + " DE " + session.getRoundSize()
                + "   ACERTOS " + session.score() + "/" + session.getPassScore();
        game.ui.text(progress, X + W - 48f - game.ui.textWidth(progress, UiRenderer.TEXT), Y + H - 44f,
            UiRenderer.TEXT, SOFT, false);
        game.ui.text(q.concept, TEXT_X, Y + H - 88f, UiRenderer.TEXT, CONCEPT, false);
        game.ui.text(game.ui.wrap(q.prompt, TEXT_W, UiRenderer.TEXT), TEXT_X, Y + H - 148f, UiRenderer.TEXT, TEXT, false);

        boolean feedback = session.getPhase() == QuizSession.Phase.FEEDBACK;
        int right = session.correctSlot();
        for (int i = 0; i < session.optionCount(); i++) {
            int style;
            if (!feedback) style = i == hover ? UiRenderer.OPTION_HOVER : UiRenderer.OPTION_NORMAL;
            else if (i == right) style = UiRenderer.OPTION_RIGHT;
            else if (i == session.getChosenSlot()) style = UiRenderer.OPTION_WRONG;
            else style = UiRenderer.OPTION_DIM;
            game.ui.option(slots[i], (i + 1) + "   " + session.option(i), style);
        }

        if (feedback) {
            boolean ok = session.wasLastCorrect();
            String head = ok ? "CERTO!" : "QUASE! A CERTA É A " + (right + 1) + ".";
            game.ui.text(head, 260f, 286f, UiRenderer.TEXT, ok ? RIGHT : WRONG, false);
            game.ui.text(game.ui.wrap(q.explanation, 1400f, UiRenderer.TEXT), 260f, 244f, UiRenderer.TEXT, TEXT, false);
            String next = "ENTER / CLIQUE   CONTINUAR";
            game.ui.text(next, X + W - 48f - game.ui.textWidth(next, UiRenderer.TEXT), 140f, UiRenderer.TEXT, SOFT, false);
        } else {
            game.ui.text("1-4, SETAS + ENTER OU MOUSE", 260f, 140f, UiRenderer.TEXT, SOFT, false);
        }
    }
}
