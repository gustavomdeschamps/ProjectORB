package com.delmartec.projectorb.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;

/**
 * Paisagem de fundo em camadas de parallax (480x270 de arte a 4x), desenhada
 * em coordenadas de tela (câmera de HUD). Ordem, de trás para a frente:
 * céu, 4 nuvens, montanhas, estruturas, água do lago, reflexos (montanhas e
 * estruturas, cada um no parallax da própria camada, ondulando por linha),
 * brilhos da água, margem, chão do fundo e névoa baixa.
 *
 * Cada camada se repete em wrap simples com período próprio (a largura da
 * textura). Deslocamentos são arredondados à grade de 4 px: pixel art pura.
 * Movimento reduzido: sem deriva das nuvens/névoa, sem ondulação e brilhos
 * parados.
 */
public final class Backdrop {
    /** Primeira linha (de arte, de cima) onde a água existe; ver tools/fundo_rodada3.py. */
    public static final int WATER_TOP = 225;
    private static final int H = 270;
    private static final int PX = Constants.PIXEL_SCALE;

    // parallax/deriva das camadas 00-06 (céu ... estruturas)
    private static final float[] PARALLAX = { 0.02f, 0.05f, 0.07f, 0.09f, 0.11f, 0.16f, 0.26f };
    private static final float[] DRIFT = { 0f, 3f, 5f, 7f, 9f, 0f, 0f };
    private static final float LAKE = 0.34f, GROUND = 0.42f, MIST = 0.46f, MIST_DRIFT = 4f;
    private static final float MOUNTAINS = 0.16f, STRUCTURES = 0.26f;

    /** Tinta do jogo: o cenário nunca compete com personagens e alvos. */
    public static final Color GAME_TINT = new Color(0.74f, 0.76f, 0.88f, 1f);

    // Brilhos da água: (x no período do lago, linha, comprimento, fase)
    private static final int GLINTS = 22;
    private final int[] glintX = new int[GLINTS];
    private final int[] glintRow = new int[GLINTS];
    private final int[] glintLen = new int[GLINTS];
    private final float[] glintPhase = new float[GLINTS];
    private static final Color GLINT_DIM = new Color(64 / 255f, 42 / 255f, 251 / 255f, 1f);
    private static final Color GLINT_HI = new Color(172 / 255f, 134 / 255f, 249 / 255f, 1f);

    private final Assets assets;
    private final Color tmp = new Color();

    public Backdrop(Assets assets) {
        this.assets = assets;
        // posições fixas (sem aleatório em tempo de jogo): semente simples
        int s = 7;
        int period = assets.lakeWater.getWidth();
        for (int i = 0; i < GLINTS; i++) {
            s = s * 1103515245 + 12345;
            glintX[i] = Math.floorMod(s >> 8, period);
            s = s * 1103515245 + 12345;
            glintRow[i] = WATER_TOP + 5 + Math.floorMod(s >> 8, H - WATER_TOP - 10);
            glintLen[i] = 2 + (i % 3);
            glintPhase[i] = i * 1.37f;
        }
    }

    /**
     * Desenha a paisagem inteira. 'cameraX' é a posição da câmera do mundo;
     * 'time' o relógio da cena; 'tint' a tinta de todas as camadas.
     */
    public void draw(SpriteBatch batch, float cameraX, float time, boolean reducedMotion, Color tint) {
        boolean motion = !reducedMotion;
        Texture[] layers = assets.backgroundLayers;
        for (int i = 0; i < layers.length; i++) {
            layer(batch, layers[i], cameraX * PARALLAX[i] + (motion ? time * DRIFT[i] : 0f), tint);
        }
        layer(batch, assets.lakeWater, cameraX * LAKE, tint);
        reflection(batch, assets.reflectionMountains, cameraX * MOUNTAINS, time, motion, tint, 0f);
        reflection(batch, assets.reflectionStructures, cameraX * STRUCTURES, time, motion, tint, 1.9f);
        glints(batch, cameraX * LAKE, time, motion, tint);
        layer(batch, assets.lakeBank, cameraX * LAKE, tint);
        layer(batch, assets.groundLayer, cameraX * GROUND, tint);
        layer(batch, assets.mistLayer, cameraX * MIST + (motion ? time * MIST_DRIFT : 0f), tint);
        batch.setColor(Color.WHITE);
    }

    /** Uma camada repetida em wrap, deslocada na grade de pixel. */
    private void layer(SpriteBatch batch, Texture texture, float offset, Color tint) {
        batch.setColor(tint);
        float period = texture.getWidth() * PX;
        float height = texture.getHeight() * PX;
        float scroll = Math.round(offset / PX) * PX;
        float first = (float)Math.floor(scroll / period) * period - scroll;
        for (float x = first; x < Constants.VIEW_WIDTH; x += period) {
            batch.draw(texture, x, 0f, period, height);
        }
    }

    /**
     * Reflexo: linha a linha, cada uma deslocada 0 ou 1 pixel de arte por uma
     * onda lenta (mais funda = mais ondulada). A onda anda de cima para baixo.
     */
    private void reflection(SpriteBatch batch, Texture texture, float offset, float time, boolean motion,
                            Color tint, float phase) {
        batch.setColor(tint);
        int w = texture.getWidth();
        float period = w * PX;
        float scroll = Math.round(offset / PX) * PX;
        float first = (float)Math.floor(scroll / period) * period - scroll;
        for (int row = WATER_TOP; row < H; row++) {
            int depth = row - WATER_TOP;
            int shift = 0;
            if (motion && depth > 2) {
                float wave = MathUtils.sin(time * 1.3f - depth * 0.62f + phase)
                    + 0.5f * MathUtils.sin(time * 0.7f + depth * 1.9f + phase);
                float amp = depth > 14 ? 1.2f : 0.8f;
                shift = Math.round(wave * amp * 0.66f);
            }
            float y = (H - 1 - row) * PX;
            for (float x = first - period; x < Constants.VIEW_WIDTH + period; x += period) {
                batch.draw(texture, x + shift * PX, y, period, PX, 0, row, w, 1, false, false);
            }
        }
    }

    /** Brilhos curtos na água: acendem, andam devagar e apagam (parados em movimento reduzido). */
    private void glints(SpriteBatch batch, float offset, float time, boolean motion, Color tint) {
        int period = assets.lakeWater.getWidth();
        float scroll = Math.round(offset / PX);
        for (int i = 0; i < GLINTS; i++) {
            float cycle = motion ? MathUtils.sin(time * (0.55f + (i % 4) * 0.12f) + glintPhase[i]) : (i % 3 == 0 ? 1f : 0f);
            if (cycle < 0.35f) continue;
            float drift = motion ? (time * 0.8f + glintPhase[i] * 3f) % 6f : 0f;
            int ax = Math.round(glintX[i] + drift);
            Color base = cycle > 0.8f ? GLINT_HI : GLINT_DIM;
            batch.setColor(tmp.set(base).mul(tint.r, tint.g, tint.b, 1f));
            float y = (H - 1 - glintRow[i]) * PX;
            float x0 = Math.floorMod((int)(ax - scroll), period) * PX;
            for (float x = x0 - period * PX; x < Constants.VIEW_WIDTH; x += period * PX) {
                if (x + glintLen[i] * PX < 0) continue;
                batch.draw(assets.pixel, x, y, glintLen[i] * PX, PX);
            }
        }
    }
}
