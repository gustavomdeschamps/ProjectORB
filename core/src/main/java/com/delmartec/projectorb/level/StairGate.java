package com.delmartec.projectorb.level;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.delmartec.projectorb.utils.Constants;

import java.util.List;

/**
 * Passagem entre seções (etapa B): no lugar da antiga barra vertical, uma
 * massa alta de blocos de cristal que, quando a seção é vencida, se
 * rearranja bloco a bloco numa escadinha: sobe STEPS degraus, topo plano de
 * TOP_COLS colunas e desce STEPS degraus.
 *
 * Medidas (pixels de mundo; 1 pixel de arte = 4):
 * - bloco 24x40 (6x10 de arte);
 * - degrau 40 de altura: <= 40% do pulo simples (JUMP_SPEED^2 / 2|GRAVITY| = 180);
 * - topo 5 x 24 = 120: >= 2 larguras do ORB (hitbox 58);
 * - escada inteira 11 x 24 = 264, centrada na massa (rodada 3);
 * - massa fechada centrada no portão antigo (endX - 88 .. endX), 4 blocos de
 *   largura, e alta o bastante para passar do alcance máximo do ORB (pulo
 *   duplo a partir da plataforma mais alta por perto) com 20% de margem;
 * - distância (rodada 3): entre a escada (fechada ou aberta) e qualquer
 *   plataforma, pelo menos CLEARANCE = 3 larguras do ORB na horizontal;
 *   nenhuma plataforma por cima nem por baixo; o topo da escada a pelo menos
 *   1 altura do ORB de qualquer plataforma. O nível (LevelDemo) respeita isso;
 *   StairGateCheck confere.
 */
public final class StairGate {
    public enum State { CLOSED, OPENING, OPEN }

    public static final float BLOCK_W = 24f;
    public static final float BLOCK_H = 40f;
    public static final int STEPS = 3;
    public static final int TOP_COLS = 5;
    public static final int MASS_COLS = 4;
    public static final float OPEN_TIME = 1.25f;
    /** Folga horizontal mínima entre a escada e qualquer plataforma: 3 larguras do ORB. */
    public static final float CLEARANCE = 3f * Constants.PLAYER_HIT_W;
    /** Pulo simples, das constantes reais do Player. */
    public static final float JUMP_HEIGHT = Constants.JUMP_SPEED * Constants.JUMP_SPEED / (2f * -Constants.GRAVITY);
    /** Alcance máximo acima do apoio: pulo duplo (o dash é só horizontal). */
    public static final float MAX_REACH = 2f * JUMP_HEIGHT;
    private static final float PX = Constants.PIXEL_SCALE;

    /** Altura (em blocos) de cada coluna da escada, da esquerda para a direita. */
    private static final int[] COLS;
    static {
        COLS = new int[STEPS * 2 + TOP_COLS];
        for (int i = 0; i < STEPS; i++) {
            COLS[i] = i + 1;
            COLS[COLS.length - 1 - i] = i + 1;
        }
        for (int i = 0; i < TOP_COLS; i++) COLS[STEPS + i] = STEPS + 1;
    }

    private final Rectangle mass = new Rectangle();
    private final Platform massPlatform;
    private final Platform[] columns = new Platform[COLS.length];
    private final float stairLeft;
    private final int massRows;
    /** Blocos da escada: coluna, linha e o bloco da massa de onde cada um sai. */
    private final int[][] blocks;

    private State state = State.CLOSED;
    private float time;
    private boolean justStarted;
    private boolean justFinished;

    private StairGate(float stairLeft, float massLeft, float massHeight) {
        this.stairLeft = stairLeft;
        this.massRows = MathUtils.ceil(massHeight / BLOCK_H);
        mass.set(massLeft, Constants.FLOOR_Y, MASS_COLS * BLOCK_W, massRows * BLOCK_H);
        massPlatform = new Platform(mass.x, mass.y, mass.width, mass.height);
        int count = 0;
        for (int c = 0; c < COLS.length; c++) {
            columns[c] = new Platform(stairLeft + c * BLOCK_W, Constants.FLOOR_Y, BLOCK_W, COLS[c] * BLOCK_H);
            count += COLS[c];
        }
        // Ordem do rearranjo: degrau por degrau, da esquerda para a direita,
        // de baixo para cima; cada bloco sai da massa, também de baixo para cima.
        blocks = new int[count][3];
        int k = 0;
        for (int c = 0; c < COLS.length; c++) {
            for (int r = 0; r < COLS[c]; r++) {
                blocks[k][0] = c;
                blocks[k][1] = r;
                blocks[k][2] = k;
                k++;
            }
        }
    }

    /**
     * Escada da seção: massa centrada no portão antigo e escada aberta
     * centrada na massa. As plataformas ficam longe (ver CLEARANCE); se alguma
     * ainda encostasse numa coluna, a escada anda para a direita até caber.
     */
    public static StairGate forSection(Section section, List<Platform> platforms) {
        float gateLeft = section.endX - 88f, gateRight = section.endX;
        float width = COLS.length * BLOCK_W;
        float highest = Constants.FLOOR_Y;
        for (Platform p : platforms) {
            Rectangle b = p.bounds;
            if (b.width >= Constants.WORLD_WIDTH) continue;             // o piso
            if (b.x + b.width > gateLeft - 700f && b.x < gateRight + 700f) highest = Math.max(highest, b.y + b.height);
        }
        float massLeft = Math.round((section.endX - 44f - MASS_COLS * BLOCK_W / 2f) / PX) * PX;
        float left = Math.round((massLeft + MASS_COLS * BLOCK_W / 2f - width / 2f) / PX) * PX;
        while (!fits(left, platforms)) left += PX;
        // alcance máximo a partir do apoio mais alto por perto, +20%, medido do piso
        float needed = ((highest - Constants.FLOOR_Y) + MAX_REACH) * 1.2f;
        return new StairGate(left, massLeft, needed);
    }

    private static boolean fits(float left, List<Platform> platforms) {
        for (int c = 0; c < COLS.length; c++) {
            float x0 = left + c * BLOCK_W, x1 = x0 + BLOCK_W;
            float top = Constants.FLOOR_Y + COLS[c] * BLOCK_H;
            for (Platform p : platforms) {
                Rectangle b = p.bounds;
                if (b.width >= Constants.WORLD_WIDTH) continue;
                if (b.x < x1 && b.x + b.width > x0 && top + Constants.PLAYER_HIT_H + 2f > b.y) return false;
            }
        }
        return true;
    }

    /** A seção foi vencida: começa o rearranjo (uma vez só). */
    public void open() {
        if (state != State.CLOSED) return;
        state = State.OPENING;
        time = 0f;
        justStarted = true;
    }

    /** Já aberta (ex.: seção limpa antes de a tela existir): sem animação. */
    public void openImmediately() {
        state = State.OPEN;
        time = OPEN_TIME;
    }

    public void update(float delta) {
        if (state != State.OPENING) return;
        time += delta;
        if (time >= OPEN_TIME) {
            state = State.OPEN;
            time = OPEN_TIME;
            justFinished = true;
        }
    }

    /** Sólidos atuais: a massa (fechada ou se rearranjando) ou as colunas da escada. */
    public void addCollision(List<Platform> out) {
        if (state == State.OPEN) {
            for (Platform c : columns) out.add(c);
        } else {
            out.add(massPlatform);
        }
    }

    /** Como o antigo portão: enquanto a massa existe, bloqueia projéteis. */
    public boolean blocks(Rectangle r) {
        return state != State.OPEN && r.overlaps(mass);
    }

    /** Topo da escada (y de mundo) no trecho [x0, x1]; piso se não houver escada ali. */
    public float topAt(float x0, float x1) {
        if (state != State.OPEN) return Constants.FLOOR_Y;
        float top = Constants.FLOOR_Y;
        for (Platform c : columns) {
            Rectangle b = c.bounds;
            if (b.x < x1 && b.x + b.width > x0) top = Math.max(top, b.y + b.height);
        }
        return top;
    }

    public boolean consumeStarted() { boolean v = justStarted; justStarted = false; return v; }
    public boolean consumeFinished() { boolean v = justFinished; justFinished = false; return v; }
    public State getState() { return state; }
    public float progress() { return state == State.CLOSED ? 0f : Math.min(1f, time / OPEN_TIME); }
    public Rectangle getMass() { return mass; }
    public float getStairLeft() { return stairLeft; }
    public float getStairRight() { return stairLeft + COLS.length * BLOCK_W; }
    public float getPlateauTop() { return Constants.FLOOR_Y + (STEPS + 1) * BLOCK_H; }
    public Platform[] getColumns() { return columns; }

    // ---------------------------------------------------------------- desenho

    /** Texturas: bloco fechado, bloco, topo, fresta ciano, fresta magenta, lasca, pixel. */
    public void draw(SpriteBatch batch, Texture closed, Texture block, Texture top, Texture crackC,
                     Texture crackM, Texture chip, Texture pixel, float gameTime, boolean still) {
        float bw = BLOCK_W, bh = BLOCK_H;
        if (state == State.CLOSED) {
            drawMass(batch, closed, crackC, crackM, pixel, gameTime, still, massRows * MASS_COLS);
            return;
        }
        float p = progress();
        int n = blocks.length;
        int massTotal = massRows * MASS_COLS;
        // cada bloco da escada se move numa janela própria (em sequência)
        float span = 0.62f, dur = 0.38f;
        for (int i = 0; i < n; i++) {
            float start = span * i / n;
            float t = MathUtils.clamp((p - start) / dur, 0f, 1f);
            int src = massTotal - 1 - blocks[i][2];     // sai do topo da massa
            float sx = mass.x + (src % MASS_COLS) * bw, sy = mass.y + (src / MASS_COLS) * bh;
            float tx = stairLeft + blocks[i][0] * bw, ty = Constants.FLOOR_Y + blocks[i][1] * bh;
            float e = t * t * (3f - 2f * t);
            // arco: sobe um pouco no meio do caminho
            float x = MathUtils.lerp(sx, tx, e), y = MathUtils.lerp(sy, ty, e) + MathUtils.sin(e * MathUtils.PI) * 40f;
            x = Math.round(x / PX) * PX;
            y = Math.round(y / PX) * PX;
            boolean isTop = blocks[i][1] == COLS[blocks[i][0]] - 1;
            batch.draw(t >= 1f ? (isTop ? top : block) : closed, x, y, bw, bh);
            if (t > 0f && t < 1f && !still) {
                // lascas saltando do bloco em movimento
                for (int k = 0; k < 2; k++) {
                    float a = (i * 97 + k * 131) % 360;
                    float r = 10f + t * 40f;
                    batch.draw(chip, Math.round((x + bw / 2 + MathUtils.cosDeg(a) * r) / PX) * PX,
                        Math.round((y + bh / 2 + MathUtils.sinDeg(a) * r - t * t * 30f) / PX) * PX, 2 * PX, 2 * PX);
                }
            }
        }
        // o resto da massa (a parte de baixo): esfarela de cima para baixo
        int leftover = massTotal - n;
        int gone = (int)(leftover * MathUtils.clamp(p / 0.8f, 0f, 1f));
        for (int k = 0; k < leftover - gone; k++) {
            batch.draw(closed, mass.x + (k % MASS_COLS) * bw, mass.y + (k / MASS_COLS) * bh, bw, bh);
        }
        if (!still && gone < leftover && state == State.OPENING) {
            int k = leftover - gone - 1;
            float x = mass.x + (k % MASS_COLS) * bw, y = mass.y + (k / MASS_COLS) * bh;
            for (int c = 0; c < 6; c++) {
                float a = (k * 53 + c * 60) % 360;
                batch.draw(chip, Math.round((x + bw / 2 + MathUtils.cosDeg(a) * 18f) / PX) * PX,
                    Math.round((y + bh / 2 + MathUtils.sinDeg(a) * 18f) / PX) * PX, 2 * PX, 2 * PX);
            }
        }
        // poeira na base enquanto rearranja
        if (!still && state == State.OPENING) {
            batch.setColor(0.35f, 0.25f, 0.55f, 0.55f * (1f - p));
            for (int d = 0; d < 10; d++) {
                float dx = stairLeft - 20f + ((d * 37) % 11) * 28f;
                float dy = Constants.FLOOR_Y + ((d * 13) % 5) * PX + p * 20f;
                batch.draw(pixel, Math.round(dx / PX) * PX, Math.round(dy / PX) * PX, 3 * PX, 2 * PX);
            }
            batch.setColor(Color.WHITE);
        }
        // brilho nas frestas durante o rearranjo e flash curto ao terminar
        if (state == State.OPEN && time >= OPEN_TIME && !still && flashLeft > 0f) {
            batch.setColor(1f, 1f, 1f, flashLeft / FLASH);
            for (Platform c : columns) batch.draw(pixel, c.bounds.x, c.bounds.y, c.bounds.width, c.bounds.height);
            batch.setColor(Color.WHITE);
        }
    }

    private static final float FLASH = 0.15f;
    private float flashLeft;

    /** Chamado pelo jogo a cada frame: controla o flash curto do fim. */
    public void tickFlash(float delta) {
        if (justFinishedFlash) {
            flashLeft = FLASH;
            justFinishedFlash = false;
        }
        flashLeft = Math.max(0f, flashLeft - delta);
    }

    private boolean justFinishedFlash;

    /** O jogo pede o flash (só se não for movimento reduzido). */
    public void requestFlash() { justFinishedFlash = true; }

    private void drawMass(SpriteBatch batch, Texture closed, Texture crackC, Texture crackM, Texture pixel,
                          float gameTime, boolean still, int count) {
        float bw = BLOCK_W, bh = BLOCK_H;
        for (int k = 0; k < count; k++) {
            batch.draw(closed, mass.x + (k % MASS_COLS) * bw, mass.y + (k / MASS_COLS) * bh, bw, bh);
        }
        // contorno firme só em volta da massa inteira (os degraus ficam insinuados)
        batch.setColor(18f / 255f, 10f / 255f, 32f / 255f, 1f);
        batch.draw(pixel, mass.x - PX, mass.y, PX, mass.height);
        batch.draw(pixel, mass.x + mass.width, mass.y, PX, mass.height);
        batch.draw(pixel, mass.x - PX, mass.y + mass.height, mass.width + 2 * PX, PX);
        // frestas de energia pulsando devagar
        for (int k = 0; k < count; k++) {
            if ((k * 7 + (k / MASS_COLS) * 3) % 5 != 0) continue;
            float pulse = still ? 0.75f : 0.45f + 0.45f * (0.5f + 0.5f * MathUtils.sin(gameTime * 1.6f + k * 0.9f));
            batch.setColor(1f, 1f, 1f, pulse);
            batch.draw(k % 2 == 0 ? crackC : crackM, mass.x + (k % MASS_COLS) * bw, mass.y + (k / MASS_COLS) * bh, bw, bh);
        }
        batch.setColor(Color.WHITE);
    }
}
