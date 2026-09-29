package com.delmartec.projectorb.entities;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.delmartec.projectorb.utils.Constants;

import java.util.Map;

/**
 * Personagem não jogável (Pi, Octógono): posição, sprite animado, estado e
 * virar-se para o jogador. Sem combate.
 *
 * As animações vêm do Assets (mesmo padrão do ORB e dos inimigos). Animações
 * em modo NORMAL (appear, wave, cheer...) tocam uma vez e voltam ao estado
 * de repouso; as em LOOP continuam até alguém trocar o estado.
 */
public final class Npc {
    private final String name;
    private final Map<String, Animation<TextureRegion>> anims;
    private final int canvas;
    private final int baselinePx;
    private final String restState;

    private float x;
    private float feetY;
    private String state;
    private float stateTime;
    private boolean facingRight = true;
    private boolean visible = true;
    /** Se definido, o NPC olha para este x em vez de olhar para o jogador. */
    private Float lookAtX;

    /**
     * @param canvas     lado do canvas do sprite, em pixels de arte
     * @param baselinePx linhas vazias abaixo dos pés no canvas
     */
    public Npc(String name, Map<String, Animation<TextureRegion>> anims, int canvas, int baselinePx,
               String restState, float x, float feetY) {
        this.name = name;
        this.anims = anims;
        this.canvas = canvas;
        this.baselinePx = baselinePx;
        this.restState = restState;
        this.state = restState;
        this.x = x;
        this.feetY = feetY;
    }

    public void update(float delta, float playerX) {
        stateTime += delta;
        facingRight = (lookAtX != null ? lookAtX : playerX) >= x;
        Animation<TextureRegion> a = anims.get(state);
        if (a != null && a.getPlayMode() == Animation.PlayMode.NORMAL && a.isAnimationFinished(stateTime)) {
            setState(restState);
        }
    }

    /** Troca a animação; pedir o estado atual não reinicia (evita tremer). */
    public void setState(String next) {
        if (next == null || !anims.containsKey(next) || next.equals(state)) return;
        state = next;
        stateTime = 0f;
    }

    /** Reinicia a animação mesmo que seja a atual (ex.: comemorar de novo). */
    public void play(String next) {
        if (next == null || !anims.containsKey(next)) return;
        state = next;
        stateTime = 0f;
    }

    public void draw(SpriteBatch batch) {
        if (!visible) return;
        TextureRegion frame = anims.get(state).getKeyFrame(stateTime);
        float size = canvas * Constants.PIXEL_SCALE;
        float drawY = feetY - baselinePx * Constants.PIXEL_SCALE;
        batch.draw(frame, x - size / 2f, drawY, size / 2f, size / 2f, size, size,
            facingRight ? 1f : -1f, 1f, 0f);
    }

    /** Olhar para um ponto (ex.: apontar para o alvo); null volta a olhar o jogador. */
    public void lookAt(Float worldX) { this.lookAtX = worldX; }

    public String getName() { return name; }
    public String getState() { return state; }
    public float getX() { return x; }
    public float getFeetY() { return feetY; }
    public boolean isFacingRight() { return facingRight; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setPosition(float x, float feetY) { this.x = x; this.feetY = feetY; }
    public boolean isAnimationFinished() {
        Animation<TextureRegion> a = anims.get(state);
        return a != null && a.isAnimationFinished(stateTime);
    }
}
