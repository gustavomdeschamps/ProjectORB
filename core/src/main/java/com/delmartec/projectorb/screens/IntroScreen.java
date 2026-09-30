package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.delmartec.projectorb.utils.PixelViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

/**
 * Abertura animada, toda em código (sem vídeo), antes do menu.
 *
 * Roteiro (~20 s): vazio com estrelas e formas à deriva -> linhas ligam os
 * vértices e formam polígonos -> o Rift se abre com flash e tremor -> o ORB
 * sai do Rift -> "PROJECT ORB" se monta a partir de vértices e arestas ->
 * "PRESSIONE QUALQUER TECLA".
 *
 * Tudo é desenhado em pixels de arte (quadrados de PIXEL_SCALE alinhados à
 * grade). Segurar qualquer tecla/clique por 0,5 s pula; no fim, qualquer
 * toque vai para o menu. Com movimento reduzido: versão estática, só com
 * transições em fade (sem tremor, sem flash, sem deriva).
 */
public final class IntroScreen extends ScreenAdapter {
    private static final int PX = Constants.PIXEL_SCALE;
    private static final float T_LINES = 3.5f, T_RIFT = 7.5f, T_ORB = 10f, T_TITLE = 12.5f, T_PROMPT = 17.5f;
    public static final float DURATION = 20f;
    private static final float SKIP_HOLD = 0.5f;

    private static final Color STAR = new Color(0.78f, 0.80f, 1f, 1f);
    private static final Color SHAPE = new Color(0.47f, 0.23f, 0.67f, 1f);
    private static final Color LINE = new Color(0.32f, 0.85f, 0.92f, 1f);
    private static final Color VERTEX = new Color(0.96f, 0.18f, 0.82f, 1f);
    private static final Color RIFT_OUT = new Color(0.47f, 0.38f, 0.98f, 1f);
    private static final Color RIFT_IN = new Color(0.23f, 0.15f, 0.46f, 1f);
    private static final Color SPARK = new Color(0.67f, 0.96f, 1f, 1f);
    private static final Color TITLE = new Color(0.95f, 0.94f, 1f, 1f);
    private static final Color SPLIT_A = new Color(0.32f, 0.85f, 0.92f, 0.85f);
    private static final Color SPLIT_B = new Color(0.96f, 0.18f, 0.82f, 0.85f);
    private static final Color PROMPT = new Color(0.76f, 0.82f, 1f, 1f);

    private final ProjectOrbGame game;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new PixelViewport(camera);
    private final boolean reduced;
    private final boolean shakeAllowed;
    private final RandomXS128 rng = new RandomXS128(2026);
    private final Color tmp = new Color();

    private final float[][] stars = new float[240][3];      // x, y, camada
    private final float[][] shapes = new float[12][5];       // x, y, raio, lados, fase
    /** Pares de formas vizinhas ligadas por linhas (a constelação). */
    private final int[][] links = new int[16][2];
    private final float[][] dust = new float[60][4];         // x, y, vx, vy
    private final float[][] sparks = new float[40][5];       // x, y, vx, vy, vida
    private float time;
    private float holdTime;
    private boolean anyHeld;
    private boolean anyTapped;
    private boolean riftSound;
    private boolean titleSound;
    private boolean finished;

    public IntroScreen(ProjectOrbGame game) {
        this(game, game.settings.isReducedMotion());
    }

    /** reduced: força a versão de movimento reduzido (capturas de QA). */
    public IntroScreen(ProjectOrbGame game, boolean reduced) {
        this.game = game;
        this.reduced = reduced;
        this.shakeAllowed = game.settings.isScreenShakeEnabled();
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
        for (float[] s : stars) { s[0] = rng.nextFloat() * 1920f; s[1] = rng.nextFloat() * 1080f; s[2] = rng.nextInt(3); }
        for (int i = 0; i < shapes.length; i++) {
            float[] s = shapes[i];
            s[0] = 160f + (i % 4) * 520f + rng.nextFloat() * 160f;
            s[1] = 150f + (i / 4) * 330f + rng.nextFloat() * 120f;
            s[2] = 52f + rng.nextFloat() * 64f;
            s[3] = 3 + rng.nextInt(4);
            s[4] = rng.nextFloat() * MathUtils.PI2;
        }
        // cada forma se liga às vizinhas da direita e de baixo
        int k = 0;
        for (int i = 0; i < shapes.length && k < links.length; i++) {
            if (i % 4 < 3) links[k++] = new int[] { i, i + 1 };
            if (i + 4 < shapes.length && k < links.length) links[k++] = new int[] { i, i + 4 };
        }
        while (k < links.length) links[k++] = new int[] { 0, 0 };
        for (float[] d : dust) { d[0] = rng.nextFloat() * 1920f; d[1] = rng.nextFloat() * 1080f; d[2] = -8f - rng.nextFloat() * 18f; d[3] = rng.nextFloat() * 10f - 5f; }
    }

    @Override public void show() {
        // Qualquer tecla/botão: InputProcessor pega também teclas que não são
        // consultadas por Gdx.input.isKeyPressed(ANY_KEY) em alguns backends.
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int keycode) { anyTapped = true; return true; }
            @Override public boolean touchDown(int x, int y, int pointer, int button) { anyTapped = true; return true; }
        });
    }

    @Override public void hide() { Gdx.input.setInputProcessor(null); }

    @Override
    public void render(float delta) {
        delta = Math.min(delta, 1f / 30f);
        time += delta;
        // pular: segurar ESC (rodada 3: uma tecla só, dita na tela)
        anyHeld = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ESCAPE);
        holdTime = anyHeld ? holdTime + delta : 0f;
        if (holdTime >= SKIP_HOLD || (time >= T_PROMPT && anyTapped) || time >= DURATION + 6f) {
            goToMenu();
            return;
        }
        anyTapped = false;

        if (!riftSound && time >= T_RIFT) { riftSound = true; game.audio.stingRift(); }
        if (!titleSound && time >= T_TITLE) { titleSound = true; game.audio.stingTitle(); }
        updateParticles(delta);

        // tremor na abertura do Rift (respeita as opções)
        float shake = 0f;
        if (!reduced && shakeAllowed && time > T_RIFT && time < T_RIFT + 0.9f) shake = 10f * (1f - (time - T_RIFT) / 0.9f);
        camera.position.set(960f + snap(MathUtils.random(-shake, shake)), 540f + snap(MathUtils.random(-shake, shake)), 0f);
        camera.update();

        Gdx.gl.glClearColor(0.008f, 0.005f, 0.025f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        drawStars();
        drawShapes();
        drawRift();
        drawOrb();
        drawTitle();
        drawSparks();
        drawSkipHint();
        drawFlash();
        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    private void goToMenu() {
        if (finished) return;
        finished = true;
        Gdx.app.log("IntroScreen", String.format("abertura encerrada em %.2f s", time));
        game.showMenu();
    }

    public float getTime() { return time; }

    // ------------------------------------------------------------ desenho

    private static float snap(float v) { return Math.round(v / PX) * PX; }

    private void px(float x, float y, Color c, float alpha) {
        game.batch.setColor(c.r, c.g, c.b, c.a * alpha);
        game.batch.draw(game.assets.pixel, snap(x), snap(y), PX, PX);
    }

    /** Linha em pixels de arte (Bresenham na grade de PIXEL_SCALE), parcial até 'amount'. */
    private void line(float x0, float y0, float x1, float y1, float amount, Color c, float alpha) {
        int ax = Math.round(x0 / PX), ay = Math.round(y0 / PX);
        int bx = Math.round((x0 + (x1 - x0) * amount) / PX), by = Math.round((y0 + (y1 - y0) * amount) / PX);
        int dx = Math.abs(bx - ax), dy = -Math.abs(by - ay), sx = ax < bx ? 1 : -1, sy = ay < by ? 1 : -1, err = dx + dy;
        game.batch.setColor(c.r, c.g, c.b, c.a * alpha);
        while (true) {
            game.batch.draw(game.assets.pixel, ax * PX, ay * PX, PX, PX);
            if (ax == bx && ay == by) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; ax += sx; }
            if (e2 <= dx) { err += dx; ay += sy; }
        }
    }

    private float fade(float start, float length) {
        return MathUtils.clamp((time - start) / length, 0f, 1f);
    }

    private void drawStars() {
        float intro = fade(0f, 1.5f);
        for (float[] s : stars) {
            float speed = reduced ? 0f : 6f + s[2] * 10f;           // parallax: 3 camadas
            float x = ((s[0] - time * speed) % 1920f + 1920f) % 1920f;
            float twinkle = reduced ? 1f : 0.6f + 0.4f * MathUtils.sin(time * 2f + s[0]);
            px(x, s[1], STAR, intro * twinkle * (0.35f + s[2] * 0.25f));
            if (s[2] == 2) {                                        // estrelas da frente: 2x2 pixels
                px(x + PX, s[1], STAR, intro * twinkle * 0.8f);
                px(x, s[1] + PX, STAR, intro * twinkle * 0.8f);
                px(x + PX, s[1] + PX, STAR, intro * twinkle * 0.8f);
            }
        }
        for (float[] d : dust) px(d[0], d[1], SHAPE, intro * 0.6f);
    }

    private void drawShapes() {
        float appear = fade(0.5f, 2f);
        float connect = fade(T_LINES, 3f);
        float out = 1f - fade(T_TITLE + 1f, 2f);
        if (appear <= 0f || out <= 0f) return;
        float net = fade(T_LINES + 1f, 2.5f);
        if (net > 0f) {
            for (int[] l : links) {
                if (l[0] == l[1]) continue;
                float[] a = shapes[l[0]], b = shapes[l[1]];
                float da = reduced ? 0f : MathUtils.sin(time * 0.6f + a[4]) * 12f;
                float db = reduced ? 0f : MathUtils.sin(time * 0.6f + b[4]) * 12f;
                line(a[0], a[1] + da, b[0], b[1] + db, reduced ? 1f : net, SHAPE, (reduced ? net : 1f) * out * 0.8f);
            }
        }
        for (float[] s : shapes) {
            int n = (int)s[3];
            float rot = reduced ? s[4] : s[4] + time * 0.25f;
            float drift = reduced ? 0f : MathUtils.sin(time * 0.6f + s[4]) * 12f;
            float cx = s[0], cy = s[1] + drift;
            for (int i = 0; i < n; i++) {
                float a0 = rot + i * MathUtils.PI2 / n, a1 = rot + (i + 1) * MathUtils.PI2 / n;
                float x0 = cx + MathUtils.cos(a0) * s[2], y0 = cy + MathUtils.sin(a0) * s[2];
                float x1 = cx + MathUtils.cos(a1) * s[2], y1 = cy + MathUtils.sin(a1) * s[2];
                // arestas vão se ligando uma a uma (em movimento reduzido: aparecem em fade)
                float edge = reduced ? (connect > 0f ? 1f : 0f) : MathUtils.clamp(connect * n - i, 0f, 1f);
                float alpha = appear * out * (reduced ? connect : 1f);
                if (edge > 0f) line(x0, y0, x1, y1, edge, LINE, alpha * 0.9f);
                px(x0, y0, VERTEX, appear * out);
                px(x0 + PX, y0, VERTEX, appear * out);
                px(x0, y0 + PX, VERTEX, appear * out);
                px(x0 + PX, y0 + PX, VERTEX, appear * out);
            }
        }
    }

    /** Rift: elipse em anéis de pixel que abre de 0 até o tamanho cheio. */
    private float riftOpen() {
        return reduced ? fade(T_RIFT, 1.2f) : MathUtils.clamp((time - T_RIFT) / 0.7f, 0f, 1f);
    }

    private static final Color[] RIFT_RINGS = {
        new Color(0.47f, 0.38f, 0.98f, 1f), new Color(0.66f, 0.50f, 1f, 1f),
        new Color(0.32f, 0.85f, 0.92f, 1f), new Color(0.96f, 0.18f, 0.82f, 1f) };

    private void drawRift() {
        float open = riftOpen();
        float close = 1f - fade(T_TITLE + 2.5f, 1.5f);
        if (open <= 0f || close <= 0f) return;
        float cx = 960f, cy = 560f;
        float pulse = reduced ? 1f : 0.85f + 0.15f * MathUtils.sin(time * 5f);
        float rxMax = 170f * open, ryMax = 280f * open;
        // miolo escuro
        for (float y = -ryMax; y <= ryMax; y += PX) {
            float w = rxMax * (float)Math.sqrt(Math.max(0f, 1f - (y * y) / (ryMax * ryMax)));
            game.batch.setColor(RIFT_IN.r, RIFT_IN.g, RIFT_IN.b, close * (reduced ? open : 1f));
            game.batch.draw(game.assets.pixel, snap(cx - w), snap(cy + y), snap(w * 2f), PX);
        }
        // anéis concêntricos (1 px de arte cada), de fora para dentro
        for (int k = 0; k < RIFT_RINGS.length; k++) {
            float rx = rxMax * (1f - 0.1f * k), ry = ryMax * (1f - 0.1f * k);
            for (float y = -ry; y <= ry; y += PX) {
                float w = rx * (float)Math.sqrt(Math.max(0f, 1f - (y * y) / (ry * ry)));
                px(cx - w, cy + y, RIFT_RINGS[k], close * pulse);
                px(cx + w - PX, cy + y, RIFT_RINGS[k], close * pulse);
            }
        }
        // faíscas girando lá dentro
        for (int i = 0; i < 24; i++) {
            float a = i * 0.9f + (reduced ? 0f : time * (0.6f + (i % 3) * 0.3f));
            float r = 0.25f + (i % 5) * 0.14f;
            px(cx + MathUtils.cos(a) * rxMax * r, cy + MathUtils.sin(a) * ryMax * r, i % 2 == 0 ? SPARK : VERTEX, close * open);
        }
        // ondas de choque saindo da borda quando abre
        if (!reduced && time > T_RIFT && time < T_RIFT + 1.6f) {
            float t = (time - T_RIFT) / 1.6f;
            float rx = 170f + t * 700f, ry = 280f + t * 500f;
            for (float a = 0f; a < MathUtils.PI2; a += 0.02f) {
                px(cx + MathUtils.cos(a) * rx, cy + MathUtils.sin(a) * ry, LINE, (1f - t) * 0.8f);
            }
        }
        if (!reduced && time > T_RIFT && time < T_RIFT + 0.05f && sparks[0][4] <= 0f) burst(cx, cy);
    }

    private void drawOrb() {
        if (time < T_ORB) return;
        float t = fade(T_ORB, 1.4f);
        // sai do Rift e pousa um pouco abaixo (em movimento reduzido: fade no lugar)
        float y = reduced ? 440f : MathUtils.lerp(600f, 400f, 1f - (1f - t) * (1f - t));
        float alpha = reduced ? t : Math.min(1f, t * 3f);
        TextureRegion frame = game.assets.orbIdle.getKeyFrame(time);
        float size = Constants.PLAYER_W;
        game.batch.setColor(1f, 1f, 1f, alpha);
        game.batch.draw(frame, 960f - size / 2f, snap(y), size, size);
        if (!reduced && t > 0f && t < 0.2f && sparks[0][4] <= 0f) burst(960f, y + 90f);
    }

    private void burst(float x, float y) {
        for (float[] s : sparks) {
            float a = rng.nextFloat() * MathUtils.PI2, sp = 80f + rng.nextFloat() * 260f;
            s[0] = x; s[1] = y; s[2] = MathUtils.cos(a) * sp; s[3] = MathUtils.sin(a) * sp; s[4] = 0.6f + rng.nextFloat() * 0.8f;
        }
    }

    private void updateParticles(float delta) {
        if (!reduced) {
            for (float[] d : dust) {
                d[0] = (d[0] + d[2] * delta + 1920f) % 1920f;
                d[1] = (d[1] + d[3] * delta + 1080f) % 1080f;
            }
        }
        for (float[] s : sparks) {
            if (s[4] <= 0f) continue;
            s[0] += s[2] * delta; s[1] += s[3] * delta; s[3] -= 240f * delta; s[4] -= delta;
        }
    }

    private void drawSparks() {
        for (float[] s : sparks) if (s[4] > 0f) px(s[0], s[1], SPARK, Math.min(1f, s[4] * 2f));
    }

    private void drawTitle() {
        if (time < T_TITLE) return;
        float t = fade(T_TITLE, 3f);
        String title = "PROJECT ORB";
        float w = game.ui.textWidth(title, UiRenderer.TITLE_HUGE);
        float left = 960f - w / 2f, base = 880f;
        if (!reduced && t < 1f) {
            // vértices voam para as quinas de cada letra; arestas ligam os pontos
            int letters = title.length();
            for (int i = 0; i < letters; i++) {
                if (title.charAt(i) == ' ') continue;
                float lx = left + i * w / letters, lt = MathUtils.clamp(t * 1.6f - i * 0.05f, 0f, 1f);
                float sx = 960f + MathUtils.cos(i * 1.7f) * 700f, sy = 540f + MathUtils.sin(i * 2.3f) * 400f;
                float x = MathUtils.lerp(sx, lx, lt), y = MathUtils.lerp(sy, base - 40f, lt);
                px(x, y, VERTEX, 1f);
                px(x + w / letters * 0.7f * lt, y, VERTEX, 1f);
                if (lt > 0.5f) line(x, y, x + w / letters * 0.7f * lt, y, 1f, LINE, 1f);
            }
        }
        float alpha = reduced ? t : MathUtils.clamp((t - 0.45f) * 2.5f, 0f, 1f);
        if (alpha <= 0f) return;
        // separação de cor: duas cópias deslocadas que convergem
        float split = reduced ? 0f : snap(24f * (1f - t)) + PX;
        tmp.set(SPLIT_A).a = SPLIT_A.a * alpha;
        game.ui.text(title, 960f - split, base, UiRenderer.TITLE_HUGE, tmp, true);
        tmp.set(SPLIT_B).a = SPLIT_B.a * alpha;
        game.ui.text(title, 960f + split, base, UiRenderer.TITLE_HUGE, tmp, true);
        float glowPulse = reduced ? 1f : 0.9f + 0.1f * MathUtils.sin(time * 3f);
        tmp.set(TITLE).a = alpha * glowPulse;
        game.ui.text(title, 960f, base, UiRenderer.TITLE_HUGE, tmp, true);
        if (time >= T_PROMPT) {
            boolean on = reduced || ((int)(time * 2f)) % 2 == 0;
            tmp.set(PROMPT).a = on ? fade(T_PROMPT, 0.6f) : 0.35f * fade(T_PROMPT, 0.6f);
            game.ui.text("ENTER", 960f, 160f, UiRenderer.TEXT, tmp, true);
        }
    }

    private void drawSkipHint() {
        if (time >= T_PROMPT) return;
        tmp.set(PROMPT).a = 0.55f;
        game.ui.text("Segure ESC para pular", 1888f - game.ui.textWidth("Segure ESC para pular", UiRenderer.TEXT),
            48f, UiRenderer.TEXT, tmp, false);
        if (holdTime > 0f) {
            // barra de pixels do "segurar para pular"
            int cells = (int)(holdTime / SKIP_HOLD * 20f);
            for (int i = 0; i < cells; i++) px(1888f - 20 * 3 * PX + i * 3 * PX, 72f, LINE, 1f);
        }
    }

    private void drawFlash() {
        if (reduced) {
            // transição em fade no lugar do flash
            float black = 1f - fade(0f, 1.2f);
            if (black > 0f) { game.batch.setColor(0f, 0f, 0.02f, black); game.batch.draw(game.assets.pixel, 0f, 0f, 1920f, 1080f); }
            return;
        }
        float f = time > T_RIFT ? Math.max(0f, 1f - (time - T_RIFT) / 0.35f) : 0f;
        f = Math.max(f, time > T_TITLE ? Math.max(0f, 0.5f - (time - T_TITLE) / 0.5f) : 0f);
        if (f <= 0f) return;
        game.batch.setColor(0.9f, 0.88f, 1f, f);
        game.batch.draw(game.assets.pixel, 0f, 0f, 1920f, 1080f);
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, false); }
}
