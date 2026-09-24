package com.delmartec.projectorb.entities;

import com.delmartec.projectorb.utils.Constants;

/**
 * As quatro formas do pacote visual final mais o chefe geométrico.
 *
 * Cada tipo carrega a geometria real do próprio sprite, medida no frame de
 * idle processado (corpo sem pernas/sombra). É isso que permite posicionar os
 * pontos fracos EM CIMA da forma em vez de flutuando ao redor dela.
 *
 * bodyCenterY  — altura do centro do corpo, em fração do canvas, medida a
 *                partir da BASE do canvas.
 * halfW / halfH — meia largura e meia altura do corpo, também em fração do
 *                canvas.
 */
public enum EnemyType {
    TRIANGLE(0.3638f, 0.2299f, 0.1897f, "TRIÂNGULO"),
    SQUARE  (0.3393f, 0.2031f, 0.1875f, "QUADRADO"),
    DIAMOND (0.3750f, 0.1808f, 0.1830f, "LOSANGO"),
    HEXAGON (0.3549f, 0.2321f, 0.1942f, "HEXÁGONO"),
    BOSS    (0.3125f, 0.2370f, 0.1484f, "NÚCLEO GEOMÉTRICO");

    private final float bodyCenterY;
    private final float halfWFraction;
    private final float halfHFraction;
    private final String displayName;

    EnemyType(float bodyCenterY, float halfWFraction, float halfHFraction, String displayName) {
        this.bodyCenterY = bodyCenterY;
        this.halfWFraction = halfWFraction;
        this.halfHFraction = halfHFraction;
        this.displayName = displayName;
    }

    public boolean isBoss() { return this == BOSS; }

    public float drawSize() { return isBoss() ? Constants.BOSS_SIZE : Constants.ENEMY_SIZE; }

    public float baseline() { return isBoss() ? Constants.BOSS_BASELINE : Constants.CHAR_BASELINE; }

    /** Distância, em pixels de mundo, do centro do corpo até os pés do sprite. */
    public float centerAboveFeet() { return (bodyCenterY - baseline()) * drawSize(); }

    /** Deslocamento do canto inferior do quad em relação ao centro do corpo. */
    public float drawOriginY() { return bodyCenterY * drawSize(); }

    public float halfWidth() { return halfWFraction * drawSize(); }

    public float halfHeight() { return halfHFraction * drawSize(); }

    /** Raio usado no teste de acerto no corpo (tiro "errado"). */
    public float bodyRadius() { return (halfWidth() + halfHeight()) * 0.5f; }

    /**
     * Hitbox local coerente com a silhueta de cada forma. O padding representa
     * o raio do projetil; assim um tiro raspando a borda ainda conta sem
     * transformar todos os inimigos em um grande circulo invisivel.
     */
    public boolean containsLocal(float localX, float localY, float padding) {
        float hw = halfWidth() + padding;
        float hh = halfHeight() + padding;
        float ax = Math.abs(localX);
        float ay = Math.abs(localY);

        return switch (this) {
            case SQUARE -> ax <= hw && ay <= hh;
            case DIAMOND -> ax / hw + ay / hh <= 1f;
            case TRIANGLE -> {
                if (localY < -hh || localY > hh) yield false;
                float maxXAtY = hw * (hh - localY) / (2f * hh);
                yield ax <= maxXAtY + padding * 0.35f;
            }
            case HEXAGON -> ay <= hh && ax <= hw * (1f - 0.46f * ay / hh);
            case BOSS -> ax / hw + ay / hh <= 1.12f;
        };
    }

    public String displayName() { return displayName; }
}
