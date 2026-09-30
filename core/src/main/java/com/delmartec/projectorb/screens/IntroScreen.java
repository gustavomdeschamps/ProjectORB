package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.EnemyType;
import com.delmartec.projectorb.utils.Assets;
import com.delmartec.projectorb.utils.Backdrop;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.PixelViewport;
import com.delmartec.projectorb.utils.UiRenderer;

import java.util.Map;

/**
 * Abertura (rodada 3, etapa 4): cutscene em código, pixel perfeita, 40 s,
 * sincronizada com a música (tools/musica_abertura.py; a cena segue a posição
 * da música quando ela toca).
 *
 * Plano 1 (0-6)   o mundo em paz: ruínas, formas calmas flutuando, letterbox.
 * Plano 2 (6-12)  rachadura no céu, a imagem falha (glitch), o Rift abre com estrondo.
 * Plano 3 (12-20) quatro cortes rápidos: cada forma é atingida e se corrompe.
 * Plano 4 (20-27) o chefe se forma no Rift: silhueta, olhos acendem, tremor e flash.
 * Plano 5 (27-34) um cristal caído pulsa, racha e o ORB nasce; o Pi chega e estende a mão.
 * Plano 6 (34-40) o ORB encara o Rift, a câmera sobe; corta para a imagem-chave e
 *                 o logo se monta peça por peça nos impactos da música.
 * Transições: losango que abre (wipe geométrico) e corte com glitch. Tremor e
 * flash respeitam as opções; em movimento reduzido: sem tremor, sem flash,
 * sem glitch e sem pan (cortes com fade curto). Pular: segurar ESC.
 */
public final class IntroScreen extends ScreenAdapter {
    public static final float DURATION = 40f;
    /** A textura do FrameBuffer já sai na orientação certa com esta câmera. */
    private static final boolean FLIP = false;
    private static final int PX = Constants.PIXEL_SCALE;
    private static final float SKIP_HOLD = 0.6f;
    /** Cortes (início de cada plano/corte). */
    private static final float[] CUTS = { 12f, 14f, 16f, 18f, 20f, 27f, 34f, 36f };
    private static final float WIPE = 0.30f;
    private static final String[] KINDS = { "triangle", "square", "diamond", "hexagon" };
    private static final EnemyType[] TYPES = { EnemyType.TRIANGLE, EnemyType.SQUARE, EnemyType.DIAMOND, EnemyType.HEXAGON };

    private static final Color SKY_TOP = new Color(0f, 14 / 255f, 100 / 255f, 1f);
    private static final Color CLOSE_BG = new Color(14 / 255f, 8 / 255f, 34 / 255f, 1f);
    private static final Color STREAK = new Color(150 / 255f, 88 / 255f, 240 / 255f, 1f);
    private static final Color STREAK2 = new Color(226 / 255f, 72 / 255f, 214 / 255f, 1f);
    private static final Color BOLT = new Color(236 / 255f, 220 / 255f, 255 / 255f, 1f);
    private static final Color RIFT_LIGHT = new Color(120 / 255f, 60 / 255f, 200 / 255f, 1f);
    private static final Color PROMPT = new Color(0.76f, 0.82f, 1f, 1f);

    private final ProjectOrbGame game;
    private final Assets.IntroArt art;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new PixelViewport(camera);
    private final OrthographicCamera fboCamera = new OrthographicCamera();
    private final Backdrop backdrop;
    private final boolean reduced;
    private final boolean shakeAllowed;
    private final Map<String, Animation<TextureRegion>> pi;
    private final Color tmp = new Color();

    private FrameBuffer scene;
    private FrameBuffer previous;
    /** Quando a cena é gravada num FrameBuffer de fora (vídeo), ele volta a ser o alvo. */
    private FrameBuffer outer;
    /** true: o tempo anda só pelo delta (gravação quadro a quadro, sem tocar a música). */
    private boolean fixedStep;
    private boolean musicStarted;

    private float time;
    private float holdTime;
    private boolean finished;

    public IntroScreen(ProjectOrbGame game) {
        this(game, game.settings.isReducedMotion());
    }

    /** reduced: força a versão de movimento reduzido (capturas de QA). */
    public IntroScreen(ProjectOrbGame game, boolean reduced) {
        this.game = game;
        this.art = game.assets.intro;
        this.reduced = reduced;
        this.shakeAllowed = game.settings.isScreenShakeEnabled() && !reduced;
        this.backdrop = new Backdrop(game.assets);
        this.pi = game.assets.npcAnimations("pi", 0.12f,
            new String[] { "idle", "walk", "talk", "point" }, new String[] { "wave", "cheer", "appear" });
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
        fboCamera.setToOrtho(false, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
    }

    /** Gravação quadro a quadro: tempo pelo delta, sem música, desenho no FrameBuffer dado. */
    public void setRecording(FrameBuffer target) {
        this.fixedStep = true;
        this.outer = target;
    }

    public float getTime() { return time; }

    @Override public void show() {
        game.audio.stopAmbient();
    }

    @Override public void hide() {
        game.audio.intro.stop();
        if (scene != null) { scene.dispose(); scene = null; }
        if (previous != null) { previous.dispose(); previous = null; }
    }

    @Override
    public void render(float delta) {
        if (!fixedStep) delta = Math.min(delta, 1f / 20f);
        if (!fixedStep && !musicStarted) {
            game.audio.refreshVolume();
            game.audio.intro.play();
            musicStarted = true;
        }
        // o tempo segue a música (sincronia); sem música, o delta
        if (!fixedStep && game.audio.intro.isPlaying()) time = Math.max(time, game.audio.intro.getPosition());
        else time += delta;

        if (!fixedStep) {
            boolean esc = Gdx.input.isKeyPressed(Input.Keys.ESCAPE);
            holdTime = esc ? holdTime + delta : 0f;
            if (holdTime >= SKIP_HOLD) { goToMenu(); return; }
        }
        if (time >= DURATION && !fixedStep) { goToMenu(); return; }

        ensureBuffers();
        // 1) plano atual no FrameBuffer da cena
        drawInto(scene, time);
        // 2) transição: plano anterior congelado no instante do corte
        float cut = currentCut();
        boolean wiping = cut >= 0f && time - cut < WIPE;
        if (wiping) drawInto(previous, cut - 0.001f);
        if (outer != null) outer.bind();

        // 3) composição na tela (com tremor)
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float sx = 0f, sy = 0f;
        float shake = shakeAmount();
        if (shake > 0f) {
            // tremor determinístico (mesmo em gravação quadro a quadro)
            sx = snap(MathUtils.sin(time * 91f) * shake);
            sy = snap(MathUtils.cos(time * 77f) * shake);
        }
        camera.position.set(Constants.VIEW_WIDTH / 2f - sx, Constants.VIEW_HEIGHT / 2f - sy, 0f);
        camera.update();
        viewport.apply();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        game.batch.setColor(Color.WHITE);
        drawSceneTexture(scene.getColorBufferTexture(), glitchAmount());
        if (wiping) drawWipe(previous.getColorBufferTexture(), (time - cut) / WIPE, cut);
        game.batch.end();

        // 4) sobreposições fixas na tela (sem tremor)
        camera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        drawLetterbox();
        drawFlashAndFades();
        drawSkipHint();
        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    private void goToMenu() {
        if (finished) return;
        finished = true;
        game.audio.intro.stop();
        Gdx.app.log("IntroScreen", String.format("abertura encerrada em %.2f s", time));
        game.showMenu();
    }

    private void ensureBuffers() {
        if (scene == null) scene = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
        if (previous == null) previous = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
        scene.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        previous.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    private void drawInto(FrameBuffer fbo, float t) {
        fbo.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        game.batch.setProjectionMatrix(fboCamera.combined);
        game.batch.begin();
        game.batch.setColor(Color.WHITE);
        drawShot(t);
        game.batch.setColor(Color.WHITE);
        game.batch.end();
        fbo.end();
    }

    /** O corte mais recente já passado (para a transição), ou -1. */
    private float currentCut() {
        float c = -1f;
        for (float k : CUTS) if (time >= k) c = k;
        return c;
    }

    // ------------------------------------------------------------ planos

    private void drawShot(float t) {
        if (t < 12f) shotWorld(t);
        else if (t < 20f) shotCorruption(t);
        else if (t < 27f) shotBoss(t);
        else if (t < 34f) shotBirth(t);
        else if (t < 36f) shotHero(t);
        else shotTitle(t);
    }

    /** Planos 1 e 2: a paisagem, as formas calmas, a rachadura e o Rift. */
    private void shotWorld(float t) {
        float camX = reduced ? 2700f : 2560f + t * 34f;
        backdrop.draw(game.batch, camX, t, reduced, Backdrop.GAME_TINT);
        // formas calmas flutuando (2 quadros, balanço lento)
        float[][] pos = { { 470f, 560f }, { 780f, 680f }, { 1150f, 600f }, { 1450f, 690f } };
        for (int i = 0; i < 4; i++) {
            Texture[] fr = art.calm.get(KINDS[i]);
            Texture tex = fr[((int)(t * 2f + i)) % fr.length];
            float bob = reduced ? 0f : snap(MathUtils.sin(t * 1.4f + i * 1.3f) * 10f);
            // depois da rachadura, tremem de medo (1 px de arte)
            float jitter = (!reduced && t > 7.4f && ((int)(t * 20f + i)) % 3 == 0) ? PX : 0f;
            draw(tex, pos[i][0] + jitter, pos[i][1] + bob);
        }
        // rachadura no céu (6.0-7.5 cresce; fica até o Rift)
        if (t >= 6f && t < 10.2f) {
            int f = Math.min(art.crack.length - 1, (int)((t - 6f) / 1.5f * art.crack.length));
            Texture cr = art.crack[f];
            draw(cr, 960f - cr.getWidth() * PX / 2f, 700f);
        }
        // o Rift abre (10.0-10.8) e pulsa
        if (t >= 10f) {
            int f = Math.min(art.rift.length - 1, (int)((t - 10f) / 0.8f * art.rift.length));
            if (t > 10.8f && !reduced && ((int)(t * 6f)) % 2 == 0) f = art.rift.length - 2;
            Texture r = art.rift[f];
            riftLight(t, 0.10f + 0.05f * f / art.rift.length);
            draw(r, 960f - r.getWidth() * PX / 2f, 460f);
        }
    }

    /** Plano 3: quatro cortes, cada forma atingida pela energia e corrompida. */
    private void shotCorruption(float t) {
        int i = MathUtils.clamp((int)((t - 12f) / 2f), 0, 3);
        float lt = t - 12f - i * 2f;                // 0..2 dentro do corte
        fill(CLOSE_BG, 0f, 0f, 1920f, 1080f);
        // fundo: faixas de energia descendo na diagonal (luz do Rift)
        for (int k = 0; k < 46; k++) {
            float speed = 900f + (k % 5) * 180f;
            float x = (k * 197f + (reduced ? 0f : t * speed * 0.35f)) % 2200f - 140f;
            float y = 1080f - ((k * 131f + (reduced ? 0f : t * speed)) % 1300f);
            fill(k % 3 == 0 ? STREAK2 : STREAK, snap(x), snap(y), 3 * PX, 14 * PX);
        }
        float cx = 960f, base = 330f;
        EnemyType type = TYPES[i];
        Texture calm = art.calm.get(KINDS[i])[0];
        if (lt < 0.5f) {
            draw(calm, cx - calm.getWidth() * PX / 2f, base + 90f);
        } else if (lt < 1.2f) {
            // raio da energia caindo do alto e a forma falhando entre calma/corrompida
            if (lt < 0.8f) bolt(cx, base + 260f, lt);
            boolean hostile = !reduced && ((int)(lt * 18f)) % 2 == 0;
            if (hostile || (reduced && lt > 0.85f)) drawEnemy(type, game.assets.enemyGlitch(type), lt, cx, base);
            else draw(calm, cx - calm.getWidth() * PX / 2f, base + 90f);
        } else {
            // corrompida: olhos acendem (carga) e depois os dentes (ataque)
            Animation<TextureRegion> anim = lt < 1.6f ? game.assets.enemyCharge(type) : game.assets.enemyAttack(type);
            if (anim == null) anim = game.assets.enemyIdle(type);
            drawEnemy(type, anim, lt - 1.2f, cx, base);
        }
        // chão do close: laje escura
        fill(new Color(0.03f, 0.02f, 0.07f, 1f), 0f, 0f, 1920f, base - 8f);
    }

    /** Plano 4: o chefe se forma no Rift. */
    private void shotBoss(float t) {
        float lt = t - 20f;
        backdrop.draw(game.batch, 2700f, t, reduced, Backdrop.GAME_TINT);
        Texture r = art.rift[art.rift.length - 1 - ((!reduced && ((int)(t * 6f)) % 2 == 0) ? 1 : 0)];
        riftLight(t, 0.16f);
        float rx = 960f - r.getWidth() * PX / 2f, ry = 460f;
        draw(r, rx, ry);
        // silhueta: aparece em linhas do meio para fora (sem degradê)
        Texture sil = art.bossSil[lt < 3.5f ? 0 : lt < 3.7f ? 1 : 2];
        float sw = sil.getWidth() * PX, sh = sil.getHeight() * PX;
        float sxp = 960f - sw / 2f, syp = 640f;
        int rows = sil.getHeight();
        float reveal = reduced ? 1f : MathUtils.clamp(lt / 2.6f, 0f, 1f);
        int shown = Math.round(rows * reveal);
        int from = (rows - shown) / 2;
        if (shown > 0) {
            game.batch.draw(sil, sxp, syp + (rows - from - shown) * PX, sw, shown * PX, 0, from, sil.getWidth(), shown, false, false);
        }
        // partículas sugadas para dentro do Rift
        if (!reduced) {
            for (int k = 0; k < 40; k++) {
                float a = k * 2.399f;
                float d = 700f - ((lt * 260f + k * 37f) % 700f);
                fill(k % 2 == 0 ? STREAK : STREAK2, snap(960f + MathUtils.cos(a) * d), snap(760f + MathUtils.sin(a) * d * 0.55f), PX * 2, PX * 2);
            }
        }
    }

    /** Plano 5: o cristal caído, o ORB nasce, o Pi chega e estende a mão. */
    private void shotBirth(float t) {
        float lt = t - 27f;
        backdrop.draw(game.batch, 700f, t, reduced, Backdrop.GAME_TINT);
        drawFloor(700f);
        Texture[] egg = art.egg;
        float ex = 900f, ey = Constants.FLOOR_Y - 4 * PX;
        int ef;
        if (lt < 2.5f) ef = ((int)(lt * 2.4f)) % 3;                   // pulsa
        else if (lt < 3.2f) ef = 3 + (int)((lt - 2.5f) / 0.7f * 3f);  // racha
        else if (lt < 3.6f) ef = 6 + (int)((lt - 3.2f) / 0.4f * 3f);  // parte
        else ef = -1;
        if (ef >= 0) draw(egg[Math.min(egg.length - 1, ef)], ex - egg[0].getWidth() * PX / 2f, ey);
        if (lt >= 3.3f) {
            // ORB: pisca e olha em volta (vira para um lado e para o outro)
            float o = lt - 3.3f;
            boolean left = (o > 0.7f && o < 1.3f) || o > 2.4f;
            TextureRegion f = game.assets.orbIdle.getKeyFrame(o);
            drawSprite(f, ex, Constants.FLOOR_Y, !left, Constants.PLAYER_W);
        }
        if (lt >= 5f) {
            float o = lt - 5f;
            Animation<TextureRegion> a = o < pi.get("appear").getAnimationDuration() ? pi.get("appear")
                : o < 1.4f ? pi.get("wave") : pi.get("point");
            TextureRegion f = a.getKeyFrame(o < pi.get("appear").getAnimationDuration() ? o : o - pi.get("appear").getAnimationDuration());
            // o Pi olha para o ORB (à esquerda)
            drawSprite(f, 1180f, Constants.FLOOR_Y, false, f.getRegionWidth() * PX);
        }
    }

    /** Plano 6 (34-36): o ORB encara o Rift e a câmera sobe. */
    private void shotHero(float t) {
        float lt = t - 34f;
        float rise = reduced ? 0f : snap(MathUtils.clamp((lt - 0.4f) / 1.5f, 0f, 1f) * 360f);
        // céu por cima (a câmera sobe: o céu desce menos que o chão)
        fill(SKY_TOP, 0f, 0f, 1920f, 1080f);
        game.batch.setTransformMatrix(game.batch.getTransformMatrix().idt().translate(0f, -rise * 0.3f, 0f));
        backdrop.draw(game.batch, 700f, t, reduced, Backdrop.GAME_TINT);
        Texture r = art.rift[art.rift.length - 1];
        draw(r, 960f - r.getWidth() * PX / 2f, 460f + rise * 0.5f);
        game.batch.setTransformMatrix(game.batch.getTransformMatrix().idt().translate(0f, -rise, 0f));
        drawFloor(700f);
        // pose heroica: o quadro do tiro (braço estendido), virado para o Rift
        TextureRegion f = game.assets.orbAttack.getKeyFrame(Math.min(lt * 0.5f, game.assets.orbAttack.getAnimationDuration() * 0.6f));
        drawSprite(f, 820f, Constants.FLOOR_Y, true, Constants.PLAYER_W);
        game.batch.setTransformMatrix(game.batch.getTransformMatrix().idt());
    }

    /** Plano 6 (36-40): imagem-chave; o logo se monta nos impactos da música. */
    private void shotTitle(float t) {
        game.batch.draw(game.assets.keyArt, 0f, 0f, 1920f, 1080f);
        game.ui.shade(704f, 0.62f);
        Texture logo = game.assets.logo;
        float lx = 112f - 16f, ly = 1080f - 88f - logo.getHeight() * PX;
        String[] order = { "project", "o", "r", "b" };
        float[] at = { 36.0f, 36.5f, 37.0f, 37.5f };
        for (int k = 0; k < order.length; k++) {
            Texture part = art.logoPart.get(order[k]);
            int[] off = art.logoOffset.get(order[k]);
            if (part == null || t < at[k] - 0.18f) continue;
            float px0 = lx + off[0] * PX, py0 = ly + (logo.getHeight() - off[1] - part.getHeight()) * PX;
            float e = reduced ? 1f : MathUtils.clamp((t - (at[k] - 0.18f)) / 0.18f, 0f, 1f);
            // entra de fora (esquerda, cima, direita, direita) e bate no lugar
            float dx = 0f, dy = 0f;
            float k2 = (1f - e) * (1f - e);
            switch (k) {
                case 0 -> dx = -600f * k2;
                case 1 -> dy = 700f * k2;
                default -> dx = 900f * k2;
            }
            // quique de 1 px de arte logo depois do impacto
            if (!reduced && t > at[k] && t < at[k] + 0.12f) dy -= PX;
            draw(part, px0 + snap(dx), py0 + snap(dy));
        }
    }

    // ------------------------------------------------------------ ajudas

    private static float snap(float v) { return Math.round(v / PX) * PX; }

    private void draw(Texture tex, float x, float y) {
        game.batch.draw(tex, snap(x), snap(y), tex.getWidth() * PX, tex.getHeight() * PX);
    }

    private void fill(Color c, float x, float y, float w, float h) {
        game.batch.setColor(c);
        game.batch.draw(game.assets.pixel, x, y, w, h);
        game.batch.setColor(Color.WHITE);
    }

    /** Sprite de personagem (canvas quadrado) com a base no chão; espelhado se !faceRight. */
    private void drawSprite(TextureRegion f, float cx, float floorY, boolean faceRight, float size) {
        float drawY = floorY - Constants.CHAR_BASELINE * size;
        game.batch.draw(f, snap(cx - size / 2f), snap(drawY), size / 2f, size / 2f, size, size, faceRight ? 1f : -1f, 1f, 0f);
    }

    private void drawEnemy(EnemyType type, Animation<TextureRegion> anim, float t, float cx, float floorY) {
        if (anim == null) anim = game.assets.enemyIdle(type);
        TextureRegion f = anim.getKeyFrame(Math.max(0f, t));
        float size = type.drawSize();
        game.batch.draw(f, snap(cx - size / 2f), snap(floorY - type.drawOriginY() + 12f), size, size);
    }

    private void drawFloor(float camX) {
        Texture[] tiles = game.assets.grounds;
        float tw = tiles[0].getWidth() * PX, th = tiles[0].getHeight() * PX;
        float start = -((camX % tw) + tw) % tw;
        int i0 = (int)Math.floor(camX / tw);
        int k = 0;
        for (float x = start; x < 1920f; x += tw, k++) {
            Texture tex = tiles[GameScreen.groundVariant(i0 + k, tiles.length)];
            game.batch.draw(tex, x, Constants.FLOOR_Y - th, tw, th);
        }
    }

    /** Raio de energia em zigue-zague (pixels de arte), do alto até a forma. */
    private void bolt(float cx, float toY, float lt) {
        float y = 1080f;
        float x = cx + 120f;
        int seg = 0;
        while (y > toY) {
            float nx = cx + ((seg * 73) % 7 - 3) * 12f, ny = y - 60f;
            float steps = Math.max(Math.abs(nx - x), Math.abs(ny - y)) / PX;
            for (int s = 0; s <= steps; s++) {
                float bx = x + (nx - x) * s / steps, by = y + (ny - y) * s / steps;
                fill(BOLT, snap(bx), snap(by), PX * 2, PX);
            }
            x = nx;
            y = ny;
            seg++;
        }
    }

    /** Luz do Rift na cena: véu violeta (translúcido, pulsando devagar). */
    private void riftLight(float t, float strength) {
        float p = reduced ? 1f : 0.85f + 0.15f * MathUtils.sin(t * 3f);
        game.batch.setColor(RIFT_LIGHT.r, RIFT_LIGHT.g, RIFT_LIGHT.b, strength * p);
        game.batch.draw(game.assets.pixel, 0f, 0f, 1920f, 1080f);
        game.batch.setColor(Color.WHITE);
    }

    /** A cena na tela; com glitch, faixas horizontais deslocadas e canais separados. */
    private void drawSceneTexture(Texture tex, float glitch) {
        if (glitch <= 0f) {
            game.batch.draw(tex, 0f, 0f, 1920f, 1080f, 0, 0, 1920, 1080, false, FLIP);
            return;
        }
        int band = 24;
        for (int y = 0; y < 1080; y += band) {
            int k = (y / band) * 31 + (int)(time * 30f) * 17;
            float off = ((k * 7919) % 5 == 0) ? snap((((k * 104729) % 11) - 5) * 12f * glitch) : 0f;
            game.batch.draw(tex, off, y, 1920f, band, 0, 1080 - y - band, 1920, band, false, FLIP);
        }
        // separação de canais: cópia magenta deslocada
        game.batch.setColor(1f, 0.2f, 0.9f, 0.35f * glitch);
        game.batch.draw(tex, snap(8f * glitch), 0f, 1920f, 1080f, 0, 0, 1920, 1080, false, FLIP);
        game.batch.setColor(Color.WHITE);
    }

    /** Wipe geométrico: um losango que abre do centro revela o plano novo. */
    private void drawWipe(Texture old, float p, float cut) {
        if (reduced) {
            game.batch.setColor(1f, 1f, 1f, 1f - p);
            game.batch.draw(old, 0f, 0f, 1920f, 1080f, 0, 0, 1920, 1080, false, FLIP);
            game.batch.setColor(Color.WHITE);
            return;
        }
        float radius = p * 1600f;                       // metade da diagonal do losango
        for (int y = 0; y < 1080; y += PX) {
            float dy = Math.abs(y + PX / 2f - 540f);
            float half = Math.max(0f, radius - dy * 1.2f);
            float x0 = snap(960f - half), x1 = snap(960f + half);
            if (x0 > 0f) game.batch.draw(old, 0f, y, x0, PX, 0, 1080 - y - PX, (int)x0, PX, false, FLIP);
            if (x1 < 1920f) game.batch.draw(old, x1, y, 1920f - x1, PX, (int)x1, 1080 - y - PX, (int)(1920f - x1), PX, false, FLIP);
        }
        // borda do losango em lilás (1 px de arte)
        game.batch.setColor(STREAK);
        for (int y = 0; y < 1080; y += PX) {
            float dy = Math.abs(y + PX / 2f - 540f);
            float half = radius - dy * 1.2f;
            if (half <= 0f) continue;
            game.batch.draw(game.assets.pixel, snap(960f - half) - PX, y, PX, PX);
            game.batch.draw(game.assets.pixel, snap(960f + half), y, PX, PX);
        }
        game.batch.setColor(Color.WHITE);
    }

    private float shakeAmount() {
        if (!shakeAllowed) return 0f;
        float s = 0f;
        s = Math.max(s, pulse(time, 10f, 0.9f, 14f));       // Rift abre
        for (float c : new float[] { 12f, 14f, 16f, 18f }) s = Math.max(s, pulse(time, c, 0.25f, 6f));
        s = Math.max(s, pulse(time, 23.5f, 0.8f, 12f));     // olhos do chefe
        s = Math.max(s, pulse(time, 30.2f, 0.3f, 5f));      // cristal parte
        for (float c : new float[] { 36f, 36.5f, 37f, 37.5f }) s = Math.max(s, pulse(time, c, 0.18f, 6f));
        return s;
    }

    private static float pulse(float t, float at, float dur, float amp) {
        if (t < at || t > at + dur) return 0f;
        return amp * (1f - (t - at) / dur);
    }

    private float glitchAmount() {
        if (reduced) return 0f;
        // falhas intermitentes enquanto a rachadura cresce e no corte para o chefe
        if (time > 7.5f && time < 9.9f && ((int)(time * 8f)) % 3 == 0) return 1f;
        if (time > 33.85f && time < 34.15f) return 1f;
        return 0f;
    }

    private void drawLetterbox() {
        // barras de cinema nos primeiros planos; saem deslizando no plano 5
        float h = 108f;
        if (time > 27f) h = reduced ? 0f : snap(108f * (1f - MathUtils.clamp((time - 27f) / 0.6f, 0f, 1f)));
        if (h <= 0f) return;
        fill(Color.BLACK, 0f, 0f, 1920f, h);
        fill(Color.BLACK, 0f, 1080f - h, 1920f, h);
    }

    private void drawFlashAndFades() {
        // entrada do preto
        float black = 1f - MathUtils.clamp(time / 1.2f, 0f, 1f);
        if (black > 0f) { game.batch.setColor(0f, 0f, 0.02f, black); game.batch.draw(game.assets.pixel, 0f, 0f, 1920f, 1080f); }
        if (reduced) return;
        float f = 0f;
        f = Math.max(f, flash(time, 10f, 0.35f, 0.85f));
        f = Math.max(f, flash(time, 23.5f, 0.30f, 0.7f));
        f = Math.max(f, flash(time, 30.2f, 0.20f, 0.6f));
        f = Math.max(f, flash(time, 38f, 0.25f, 0.5f));
        if (f > 0f) {
            game.batch.setColor(0.92f, 0.88f, 1f, f);
            game.batch.draw(game.assets.pixel, 0f, 0f, 1920f, 1080f);
        }
        game.batch.setColor(Color.WHITE);
    }

    private float flash(float t, float at, float dur, float peak) {
        if (!shakeAllowed && !game.settings.isScreenShakeEnabled()) return 0f;
        if (t < at || t > at + dur) return 0f;
        return peak * (1f - (t - at) / dur);
    }

    private void drawSkipHint() {
        if (fixedStep || time > 35f) return;
        tmp.set(PROMPT).a = 0.55f;
        String s = "Segure ESC para pular";
        game.ui.text(s, 1888f - game.ui.textWidth(s, UiRenderer.TEXT), 48f, UiRenderer.TEXT, tmp, false);
        if (holdTime > 0f) {
            int cells = (int)(holdTime / SKIP_HOLD * 20f);
            game.batch.setColor(STREAK);
            for (int i = 0; i < cells; i++) game.batch.draw(game.assets.pixel, 1888f - 20 * 3 * PX + i * 3 * PX, 72f, 2 * PX, PX);
            game.batch.setColor(Color.WHITE);
        }
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, false); }

    @Override public void dispose() { hide(); }
}
