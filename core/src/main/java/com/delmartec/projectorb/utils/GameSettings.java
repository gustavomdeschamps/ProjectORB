package com.delmartec.projectorb.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.math.MathUtils;

/** Configuracoes pequenas, persistentes e seguras para alterar durante o jogo. */
public final class GameSettings {
    private static final String PREFS_NAME = "project-orb-settings";
    private static final String MASTER_VOLUME = "masterVolume";
    private static final String SCREEN_SHAKE = "screenShake";
    private static final String REDUCED_MOTION = "reducedMotion";
    private static final String DIALOGUE_SOUND = "dialogueSound";
    private static final String INTRO = "intro";

    private final Preferences preferences;
    private float masterVolume;
    private boolean screenShake;
    private boolean reducedMotion;
    private boolean dialogueSound;
    private boolean intro;

    public GameSettings() {
        preferences = Gdx.app.getPreferences(PREFS_NAME);
        masterVolume = MathUtils.clamp(preferences.getFloat(MASTER_VOLUME, 0.80f), 0f, 1f);
        screenShake = preferences.getBoolean(SCREEN_SHAKE, true);
        reducedMotion = preferences.getBoolean(REDUCED_MOTION, false);
        dialogueSound = preferences.getBoolean(DIALOGUE_SOUND, true);
        intro = preferences.getBoolean(INTRO, true);
    }

    public float getMasterVolume() { return masterVolume; }
    public boolean isScreenShakeEnabled() { return screenShake && !reducedMotion; }
    public boolean isReducedMotion() { return reducedMotion; }
    /** Som curto por letra nas falas dos NPCs (desligável nas opções). */
    public boolean isDialogueSoundEnabled() { return dialogueSound; }

    /** Abertura animada antes do menu (desligável nas opções). */
    public boolean isIntroEnabled() { return intro; }

    public void toggleIntro() {
        intro = !intro;
        save();
    }

    public void toggleDialogueSound() {
        dialogueSound = !dialogueSound;
        save();
    }

    public void adjustMasterVolume(float delta) {
        masterVolume = MathUtils.clamp(Math.round((masterVolume + delta) * 10f) / 10f, 0f, 1f);
        save();
    }

    public void toggleScreenShake() {
        screenShake = !screenShake;
        save();
    }

    public void toggleReducedMotion() {
        reducedMotion = !reducedMotion;
        save();
    }

    private void save() {
        preferences.putFloat(MASTER_VOLUME, masterVolume);
        preferences.putBoolean(SCREEN_SHAKE, screenShake);
        preferences.putBoolean(REDUCED_MOTION, reducedMotion);
        preferences.putBoolean(DIALOGUE_SOUND, dialogueSound);
        preferences.putBoolean(INTRO, intro);
        preferences.flush();
    }
}
