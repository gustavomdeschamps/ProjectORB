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

    private final Preferences preferences;
    private float masterVolume;
    private boolean screenShake;
    private boolean reducedMotion;

    public GameSettings() {
        preferences = Gdx.app.getPreferences(PREFS_NAME);
        masterVolume = MathUtils.clamp(preferences.getFloat(MASTER_VOLUME, 0.80f), 0f, 1f);
        screenShake = preferences.getBoolean(SCREEN_SHAKE, true);
        reducedMotion = preferences.getBoolean(REDUCED_MOTION, false);
    }

    public float getMasterVolume() { return masterVolume; }
    public boolean isScreenShakeEnabled() { return screenShake && !reducedMotion; }
    public boolean isReducedMotion() { return reducedMotion; }

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
        preferences.flush();
    }
}
