package com.delmartec.projectorb.entities;

import com.delmartec.projectorb.utils.Constants;

/**
 * As formas de combate e o chefe geométrico.
 *
 * Cada tipo carrega a geometria real do próprio sprite (assets/sprites/),
 * medida por tools/build_orb_assets.py e gravada em sprites/manifest.json.
 * Todos os sprites seguem o gabarito da arte oficial: o centro do núcleo fica
 * no centro do canvas e os marcadores (vértices, ou o meio dos lados no
 * quadrado) ficam num círculo de raio fixo em volta dele. É por isso que os
 * pontos fracos caem EM CIMA dos marcadores desenhados.
 *
 * Medidas em pixels do canvas nativo; os métodos devolvem pixels de mundo
 * (x Constants.PIXEL_SCALE, a escala única do jogo).
 *
 * canvas     — lado do canvas quadrado do sprite (ímpar: o centro é um pixel).
 * baseline   — linhas vazias abaixo do pixel mais baixo em repouso (inclui os
 *              arcos): é essa linha que encosta no chão.
 * sides      — lados do polígono do corpo (0 = círculo).
 * startDeg   — ângulo do primeiro vértice (graus, anti-horário, y para cima).
 * markerR    — raio do círculo dos marcadores / pontos fracos.
 * outerR     — raio circunscrito do contorno do corpo.
 */
public enum EnemyType {
    //        canvas baseline sides startDeg markerR outerR
    TRIANGLE(73, 12, 3, 90f, 29f, 33f, "TRIÂNGULO"),   // hostil (F2 v2): maior
    SQUARE  (73, 9, 4, 45f, 24f, 35.355f, "QUADRADO"),
    DIAMOND (73, 6, 4, 0f, 26f, 30f, "LOSANGO"),
    HEXAGON (73, 6, 6, 90f, 26f, 30f, "HEXÁGONO"),
    BOSS    (113, 4, 6, 90f, 45.5f, 52.5f, "NÚCLEO GEOMÉTRICO");

    private final int canvas;
    private final int scale;
    private final int baseline;
    private final int sides;
    private final float startDeg;
    private final float markerR;
    private final float outerR;
    private final String displayName;
    private final float halfW;
    private final float halfH;

    EnemyType(int canvas, int baseline, int sides, float startDeg,
              float markerR, float outerR, String displayName) {
        this.canvas = canvas;
        this.scale = Constants.PIXEL_SCALE;
        this.baseline = baseline;
        this.sides = sides;
        this.startDeg = startDeg;
        this.markerR = markerR;
        this.outerR = outerR;
        this.displayName = displayName;
        // Meias-medidas do contorno a partir dos vértices reais.
        float hw = 0f, hh = 0f;
        if (sides == 0) {
            hw = hh = outerR;
        } else {
            for (int i = 0; i < sides; i++) {
                double a = Math.toRadians(startDeg + i * 360.0 / sides);
                hw = Math.max(hw, Math.abs((float)Math.cos(a) * outerR));
                hh = Math.max(hh, Math.abs((float)Math.sin(a) * outerR));
            }
        }
        this.halfW = hw;
        this.halfH = hh;
    }

    public boolean isBoss() { return this == BOSS; }

    /** Lado do quad desenhado: canvas x escala inteira. */
    public float drawSize() { return canvas * scale; }

    /** Distância, em pixels de mundo, do centro do corpo até a linha do chão. */
    public float centerAboveFeet() { return (canvas / 2f - baseline) * scale; }

    /** Deslocamento do canto inferior do quad em relação ao centro do corpo. */
    public float drawOriginY() { return canvas / 2f * scale; }

    /** Raio (mundo) do círculo dos marcadores: é onde ficam os pontos fracos. */
    public float markerRadius() { return markerR * scale; }

    public float halfWidth() { return halfW * scale; }

    public float halfHeight() { return halfH * scale; }

    /**
     * Hitbox local coerente com a silhueta: o polígono regular real do corpo.
     * O padding representa o raio do projétil; assim um tiro raspando a borda
     * ainda conta sem transformar todos os inimigos em um grande círculo.
     */
    public boolean containsLocal(float localX, float localY, float padding) {
        float r = outerR * scale;
        if (sides == 0) return localX * localX + localY * localY <= (r + padding) * (r + padding);
        float apothem = r * (float)Math.cos(Math.PI / sides);
        for (int i = 0; i < sides; i++) {
            // normal de cada aresta: a meio caminho entre dois vértices
            double a = Math.toRadians(startDeg + (i + 0.5) * 360.0 / sides);
            if (localX * Math.cos(a) + localY * Math.sin(a) > apothem + padding) return false;
        }
        return true;
    }

    public String displayName() { return displayName; }
}
