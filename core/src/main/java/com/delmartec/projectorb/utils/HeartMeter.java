package com.delmartec.projectorb.utils;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Vidas em corações (sprites/ui/heart/). Cheios pulsam devagar, o que se
 * perde quebra em 4 frames tremendo, o que se ganha dá um "pop", e as vidas
 * que faltam aparecem só em contorno.
 *
 * Em movimento reduzido não há pulsação nem tremor: a quebra e o pop ainda
 * tocam (são a informação "perdeu/ganhou"), mas parados.
 */
public final class HeartMeter {
    private static final int PX = Constants.PIXEL_SCALE;
    /** Espaço entre corações, em pixels de mundo (1 pixel de arte). */
    public static final float GAP = PX;
    /** Defasagem da pulsação entre corações vizinhos (não batem juntos). */
    private static final float PULSE_STAGGER = 0.18f;
    private static final float SHAKE_STEP = 0.03f;

    private final Assets assets;
    private final GameSettings settings;
    private int shown = -1;
    private float time;
    private int changing = -1;
    private boolean losing;
    private float changeTime;

    public HeartMeter(Assets assets, GameSettings settings) {
        this.assets = assets;
        this.settings = settings;
    }

    /** Largura de um coração na tela. */
    public static float heartWidth(Assets assets) { return assets.heartEmpty.getWidth() * PX; }

    public static float heartHeight(Assets assets) { return assets.heartEmpty.getHeight() * PX; }

    /** Largura de uma fileira de 'slots' corações. */
    public static float rowWidth(Assets assets, int slots) {
        return slots * heartWidth(assets) + (slots - 1) * GAP;
    }

    public void update(float delta, int lives) {
        time += delta;
        if (changing >= 0) {
            changeTime += delta;
            Animation<TextureRegion> a = losing ? assets.heartBreak : assets.heartGain;
            if (a.isAnimationFinished(changeTime)) changing = -1;
        }
        if (shown < 0) shown = lives;
        if (lives < shown) start(lives, true);        // perdeu: quebra o último cheio
        else if (lives > shown) start(lives - 1, false); // ganhou: pop no novo
        shown = lives;
    }

    private void start(int index, boolean lose) {
        changing = index;
        losing = lose;
        changeTime = 0f;
    }

    /** Fileira animada: (x, y) é o canto inferior esquerdo do primeiro coração. */
    public void draw(SpriteBatch batch, float x, float y, int slots) {
        float w = heartWidth(assets), h = heartHeight(assets);
        boolean still = settings.isReducedMotion();
        for (int i = 0; i < slots; i++) {
            float hx = x + i * (w + GAP);
            if (i == changing) {
                Animation<TextureRegion> a = losing ? assets.heartBreak : assets.heartGain;
                float shake = (int)(changeTime / SHAKE_STEP) % 2 == 0 ? PX : -PX;
                batch.draw(a.getKeyFrame(changeTime), hx + (losing && !still ? shake : 0f), y, w, h);
            } else if (i < shown) {
                TextureRegion f = still ? assets.heartFull.getKeyFrame(0f)
                    : assets.heartFull.getKeyFrame(time + i * PULSE_STAGGER);
                batch.draw(f, hx, y, w, h);
            } else {
                batch.draw(assets.heartEmpty, hx, y, w, h);
            }
        }
    }

    /** Fileira parada (telas de pausa, vitória, derrota): 'full' cheios de 'slots'. */
    public static void drawStatic(SpriteBatch batch, Assets assets, float x, float y, int full, int slots) {
        float w = heartWidth(assets), h = heartHeight(assets);
        TextureRegion heart = assets.heartFull.getKeyFrame(0f);
        for (int i = 0; i < slots; i++) {
            float hx = x + i * (w + GAP);
            if (i < full) batch.draw(heart, hx, y, w, h);
            else batch.draw(assets.heartEmpty, hx, y, w, h);
        }
    }

    /** Marcador pequeno (botão selecionado). */
    public static void drawMini(SpriteBatch batch, Assets assets, float x, float y) {
        Texture t = assets.heartMini;
        batch.draw(t, x, y, t.getWidth() * PX, t.getHeight() * PX);
    }
}
