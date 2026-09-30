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
    // Rodada 3: feedback de jogo no lugar de texto na tela
    private final Sound shatter;
    private final Sound miss;
    private final Sound stairRumble;
    private final Sound stairOpen;
    private final Sound uiMove;
    private final Sound uiConfirm;
    private final Sound callout;
    private final Music ambient;
    /** Música da abertura (40 s, tools/musica_abertura.py); a cena segue o tempo dela. */
    public final Music intro;

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
        shatter = Gdx.audio.newSound(Gdx.files.internal("audio/shatter.wav"));
        miss = Gdx.audio.newSound(Gdx.files.internal("audio/miss.wav"));
        stairRumble = Gdx.audio.newSound(Gdx.files.internal("audio/stair_rumble.wav"));
        stairOpen = Gdx.audio.newSound(Gdx.files.internal("audio/stair_open.wav"));
        uiMove = Gdx.audio.newSound(Gdx.files.internal("audio/ui_move.wav"));
        uiConfirm = Gdx.audio.newSound(Gdx.files.internal("audio/ui_confirm.wav"));
        callout = Gdx.audio.newSound(Gdx.files.internal("audio/callout.wav"));
        ambient = Gdx.audio.newMusic(Gdx.files.internal("audio/ambient.wav"));
        ambient.setLooping(true);
        intro = Gdx.audio.newMusic(Gdx.files.internal("audio/intro_music.wav"));
        intro.setLooping(false);
        refreshVolume();
    }

    public void refreshVolume() {
        ambient.setVolume(0.25f * settings.getMasterVolume());
        intro.setVolume(0.6f * settings.getMasterVolume());
    }
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
    /** O alvo de ponto fraco estilhaçou (altura sobe a cada alvo da rodada). */
    public void shatter(float pitch) { shatter.play(volume(0.40f), pitch, 0f); }
    /** Tiro acertou o corpo, não o alvo. */
    public void miss() { miss.play(volume(0.46f)); }
    public void uiMove() { uiMove.play(volume(0.25f)); }
    public void uiConfirm() { uiConfirm.play(volume(0.32f)); }
    /** Momento raro e importante (acompanha o texto curto no meio da tela). */
    public void callout() { callout.play(volume(0.45f)); }

    /** A escadinha começa a se rearranjar / termina. */
    public void stairRumble() { stairRumble.play(volume(0.50f)); }

    public void stairOpened() { stairOpen.play(volume(0.45f)); }

    public void dispose() {
        shoot.dispose(); hit.dispose(); jump.dispose(); dash.dispose();
        enemyAttack.dispose(); weakPoint.dispose(); hurt.dispose(); victory.dispose();
        blip.dispose(); stingRift.dispose(); stingTitle.dispose();
        shatter.dispose(); miss.dispose(); stairRumble.dispose(); stairOpen.dispose();
        uiMove.dispose(); uiConfirm.dispose(); callout.dispose();
        ambient.dispose();
        intro.dispose();
    }
}
