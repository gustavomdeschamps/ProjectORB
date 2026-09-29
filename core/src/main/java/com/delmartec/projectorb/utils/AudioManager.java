package com.delmartec.projectorb.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

public class AudioManager {
    private final GameSettings settings;
    private final Sound shoot;
    private final Sound hit;
    private final Sound jump;
    private final Sound dash;
    private final Sound enemyAttack;
    private final Sound weakPoint;
    private final Sound hurt;
    private final Sound victory;
    private final Sound blip;
    private final Sound stingRift;
    private final Sound stingTitle;
    private final Music ambient;

    public AudioManager(GameSettings settings) {
        this.settings = settings;
        shoot = Gdx.audio.newSound(Gdx.files.internal("audio/shoot.wav"));
        hit = Gdx.audio.newSound(Gdx.files.internal("audio/hit.wav"));
        jump = Gdx.audio.newSound(Gdx.files.internal("audio/jump.wav"));
        dash = Gdx.audio.newSound(Gdx.files.internal("audio/dash.wav"));
        enemyAttack = Gdx.audio.newSound(Gdx.files.internal("audio/enemy_attack.wav"));
        weakPoint = Gdx.audio.newSound(Gdx.files.internal("audio/weakpoint.wav"));
        hurt = Gdx.audio.newSound(Gdx.files.internal("audio/hurt.wav"));
        victory = Gdx.audio.newSound(Gdx.files.internal("audio/victory.wav"));
        // Sintetizados por tools/build_orb_audio.py
        blip = Gdx.audio.newSound(Gdx.files.internal("audio/blip.wav"));
        stingRift = Gdx.audio.newSound(Gdx.files.internal("audio/sting_rift.wav"));
        stingTitle = Gdx.audio.newSound(Gdx.files.internal("audio/sting_title.wav"));
        ambient = Gdx.audio.newMusic(Gdx.files.internal("audio/ambient.wav"));
        ambient.setLooping(true);
        refreshVolume();
    }

    public void refreshVolume() { ambient.setVolume(0.25f * settings.getMasterVolume()); }
    public void startAmbient() { refreshVolume(); if (!ambient.isPlaying()) ambient.play(); }
    public void stopAmbient() { ambient.stop(); }
    private float volume(float base) { return base * settings.getMasterVolume(); }
    public void shoot() { shoot.play(volume(0.38f)); }
    public void hit() { hit.play(volume(0.40f)); }
    public void jump() { jump.play(volume(0.34f)); }
    public void dash() { dash.play(volume(0.38f)); }
    public void enemyAttack() { enemyAttack.play(volume(0.42f)); }
    public void weakPoint() { weakPoint.play(volume(0.44f)); }
    public void hurt() { hurt.play(volume(0.45f)); }
    public void victory() { victory.play(volume(0.50f)); }
    /** Letra do diálogo: suave e com leve variação de altura para não cansar. */
    public void blip(float pitch) { if (settings.isDialogueSoundEnabled()) blip.play(volume(0.22f), pitch, 0f); }
    public void stingRift() { stingRift.play(volume(0.55f)); }
    public void stingTitle() { stingTitle.play(volume(0.45f)); }

    public void dispose() {
        shoot.dispose(); hit.dispose(); jump.dispose(); dash.dispose();
        enemyAttack.dispose(); weakPoint.dispose(); hurt.dispose(); victory.dispose();
        blip.dispose(); stingRift.dispose(); stingTitle.dispose();
        ambient.dispose();
    }
}
