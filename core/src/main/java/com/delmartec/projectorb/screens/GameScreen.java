package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.EffectBurst;
import com.delmartec.projectorb.entities.EnemyType;
import com.delmartec.projectorb.entities.GeoEnemy;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.entities.Projectile;
import com.delmartec.projectorb.entities.WeakPoint;
import com.delmartec.projectorb.level.LevelDemo;
import com.delmartec.projectorb.level.Platform;
import com.delmartec.projectorb.level.Section;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.UiRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameScreen extends ScreenAdapter {
    private static final Color CHARGE_LOW = new Color(0.70f, 0.28f, 0.96f, 1f);
    private static final Color CHARGE_HIGH = new Color(1f, 0.16f, 0.36f, 1f);
    private static final Color AIM_LOCK = new Color(1f, 0.83f, 0.38f, 1f);
    /** Meia janela de cull em torno da câmera. */
    private static final float CULL_MARGIN = 1250f;
    /**
     * Zona do portal final. Antes era um círculo de raio 175 centrado em
     * (11700, 250): como o jogador anda com o centro em y=165, sobrava só uma
     * faixa estreita de x para interagir e dava para andar até a borda do
     * mundo e ficar fora dela.
     */
    private static final Rectangle PORTAL_BOUNDS = new Rectangle(11580f, Constants.FLOOR_Y, 340f, 300f);

    private final ProjectOrbGame game;
    private final LevelDemo level = new LevelDemo();
    private final Player player = new Player(210f, 230f);

    private final OrthographicCamera worldCamera = new OrthographicCamera();
    private final OrthographicCamera hudCamera = new OrthographicCamera();
    private final Viewport worldViewport = new FitViewport(Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT, worldCamera);
    private final Viewport hudViewport = new FitViewport(Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT, hudCamera);
    private final Vector2 mouseWorld = new Vector2();
    private final Vector2 mouseHud = new Vector2();
    private final Rectangle[] pauseButtons = {
        new Rectangle(710f, 510f, 500f, 72f),
        new Rectangle(710f, 420f, 500f, 72f),
        new Rectangle(710f, 330f, 500f, 72f),
        new Rectangle(710f, 240f, 500f, 72f)
    };

    private final List<GeoEnemy> enemies = new ArrayList<>();
    private final List<Projectile> playerProjectiles = new ArrayList<>();
    private final List<Projectile> enemyProjectiles = new ArrayList<>();
    private final List<EffectBurst> effects = new ArrayList<>();

    private final boolean[] sectionSpawned = new boolean[6];
    private final boolean[] sectionCleared = new boolean[6];

    // O portão era recriado a cada consulta — e ele é consultado no update do
    // jogador, no teste de cada projétil e no render.
    private final Rectangle gateRect = new Rectangle();
    private boolean gateActive;

    // Fundo em espelho: dá parallax infinito sem emenda visível e sem precisar
    // de uma arte nova e tileável.
    /** Montado uma vez: era um array novo por plataforma por frame. */
    private final Texture[] platformPieces;
    private final TextureRegion backgroundRegion;
    private final TextureRegion backgroundMirrored;
    /** A arte da seta aponta para cima; espelhada ela aponta PARA o alvo. */
    private final TextureRegion guideArrowDown;

    private int currentSection = 0;
    private float shotCooldown = 0f;
    private float gameTime = 0f;
    private float bannerTimer = 3f;
    private float feedbackTimer = 0f;
    private String feedback = "";
    private float shakeTimer = 0f;
    private float shakeStrength = 0f;
    private float cameraBaseX = Constants.VIEW_WIDTH / 2f;
    private float cameraBaseY = Constants.VIEW_HEIGHT / 2f;
    private float dashFxTimer = 0f;
    private int totalCorrectHits = 0;
    private int totalWrongHits = 0;
    private int score = 0;
    private int crystals = 0;
    private int pauseSelected = 0;
    private boolean paused = false;
    private boolean codexOpen = false;
    private boolean portalActive = false;
    private boolean learnedMove;
    private boolean learnedJump;
    private boolean learnedDash;
    private boolean learnedShoot;

    public GameScreen(ProjectOrbGame game) {
        this.game = game;
        platformPieces = new Texture[] {
            game.assets.floatingPlatformMagenta,
            game.assets.floatingPlatformBlue,
            game.assets.floatingPlatformGreen
        };
        backgroundRegion = new TextureRegion(game.assets.backgroundRuins);
        backgroundMirrored = new TextureRegion(game.assets.backgroundRuins);
        backgroundMirrored.flip(true, false);
        guideArrowDown = new TextureRegion(game.assets.guideArrow);
        guideArrowDown.flip(false, true);

        player.setDeathDuration(game.assets.orbDeath.getAnimationDuration());

        worldCamera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        worldCamera.zoom = 1f;
        hudCamera.position.set(Constants.VIEW_WIDTH / 2f, Constants.VIEW_HEIGHT / 2f, 0f);
        worldCamera.update();
        hudCamera.update();
        spawnSection(0, false);
        updateGate();
    }

    @Override
    public void show() {
        game.audio.startAmbient();
    }

    @Override
    public void render(float delta) {
        delta = Math.min(delta, 1f / 30f);

        if ((Gdx.input.isKeyJustPressed(Input.Keys.C) || Gdx.input.isKeyJustPressed(Input.Keys.TAB)) && !paused) {
            codexOpen = !codexOpen;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (codexOpen) codexOpen = false;
            else paused = !paused;
        }
        if (paused && handlePauseInput()) return;

        if (!paused && !codexOpen) update(delta);
        updateCamera(delta);

        Gdx.gl.glClearColor(0.008f, 0.005f, 0.025f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        renderBackground();
        renderWorld();
        renderHud();
        if (codexOpen) renderCodex();
        if (paused) renderPause();
    }

    private void update(float delta) {
        gameTime += delta;
        shotCooldown = Math.max(0f, shotCooldown - delta);
        feedbackTimer = Math.max(0f, feedbackTimer - delta);
        shakeTimer = Math.max(0f, shakeTimer - delta);
        dashFxTimer = Math.max(0f, dashFxTimer - delta);
        bannerTimer = Math.max(0f, bannerTimer - delta);

        updateSectionProgress();
        updateGate();

        player.update(delta, level.getPlatforms(), gateActive ? gateRect : null);

        if (Math.abs(player.getX() - 210f) > 75f) learnedMove = true;
        if (player.consumeJumpEvent()) {
            learnedJump = true;
            game.audio.jump();
        }
        if (player.consumeDashEvent()) {
            learnedDash = true;
            game.audio.dash();
            shake(0.08f, 6f);
        }

        if (player.isDashing() && dashFxTimer <= 0f && !game.settings.isReducedMotion()) {
            effects.add(new EffectBurst(
                player.getX() - (player.isFacingRight() ? 40f : -40f),
                player.getY(), EffectBurst.Kind.DASH, 125f, 0.18f));
            dashFxTimer = 0.045f;
        }

        updateMouseWorld();
        handleShooting();
        updateEnemies(delta);
        updateProjectiles(delta);
        updateEffects(delta);

        if (player.getY() < -120f && player.getHealth() > 0) player.forceDeath();
        if (player.isDeathAnimationFinished()) handleLifeLost();

        if (portalActive && playerAtPortal() && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            game.audio.victory();
            game.setScreen(new VictoryScreen(game, gameTime, player.getLives(), totalCorrectHits,
                totalWrongHits, score, crystals));
        }
    }

    private boolean playerAtPortal() {
        return player.getBounds().overlaps(PORTAL_BOUNDS);
    }

    private void updateSectionProgress() {
        if (currentSection == 0 && !sectionCleared[0] && player.getX() > 1530f) {
            sectionCleared[0] = true;
            feedback("MOVIMENTO DOMINADO - siga para a câmara", 1.8f);
        }

        if (currentSection < 5 && sectionCleared[currentSection]) {
            Section next = level.getSection(currentSection + 1);
            if (player.getX() > next.startX + 40f) {
                currentSection++;
                bannerTimer = 2.4f;
                enemyProjectiles.clear();
                spawnSection(currentSection, false);
            }
        }

        // Os inimigos só saem da lista quando a animação de morte termina, então
        // "lista vazia" já significa "seção resolvida". Antes essa checagem
        // varria a lista toda com uma condição invertida difícil de ler.
        if (currentSection > 0 && sectionSpawned[currentSection]
            && !sectionCleared[currentSection] && enemies.isEmpty()) {
            sectionCleared[currentSection] = true;
            if (currentSection == 5) {
                portalActive = true;
                feedback("RIFT ESTABILIZADO - atravesse o portal", 4f);
            } else {
                feedback("CONCEITO DOMINADO - passagem liberada", 2.0f);
            }
        }
    }

    private void spawnSection(int section, boolean reset) {
        enemies.clear();
        enemyProjectiles.clear();
        playerProjectiles.clear();
        sectionSpawned[section] = true;
        if (reset) sectionCleared[section] = false;

        switch (section) {
            case 0 -> { }
            case 1 -> addEnemy(EnemyType.DIAMOND, 2780f);
            case 2 -> {
                addEnemy(EnemyType.TRIANGLE, 4350f);
                addEnemy(EnemyType.TRIANGLE, 5050f);
            }
            case 3 -> addEnemy(EnemyType.SQUARE, 6600f);
            case 4 -> addEnemy(EnemyType.HEXAGON, 8400f);
            case 5 -> addEnemy(EnemyType.BOSS, 10840f);
        }
    }

    /**
     * Os inimigos nascem APOIADOS NO PISO. Os sprites do pacote final têm
     * pernas e sombra: antes eles eram colocados a 575 de altura e ficavam
     * boiando, com os pontos fracos soltos fora do corpo.
     */
    private void addEnemy(EnemyType type, float x) {
        GeoEnemy enemy = new GeoEnemy(type, x, Constants.FLOOR_Y);
        enemy.setAnimationTimings(
            attackAnimationDuration(type),
            game.assets.enemyHurt(type).getAnimationDuration(),
            game.assets.enemyDeath(type).getAnimationDuration());
        if (type.isBoss()) enemy.setPowerUpDuration(game.assets.bossPowerUp.getAnimationDuration());
        enemies.add(enemy);
    }

    /** O boss alterna orbes e feixe; a janela precisa cobrir a mais longa. */
    private float attackAnimationDuration(EnemyType type) {
        if (type.isBoss()) {
            return Math.max(game.assets.bossOrbs.getAnimationDuration(),
                game.assets.bossBeam.getAnimationDuration());
        }
        return game.assets.enemyAttack(type).getAnimationDuration();
    }

    private void updateGate() {
        gateActive = currentSection < 5 && !sectionCleared[currentSection];
        if (gateActive) {
            Section section = level.getSection(currentSection);
            gateRect.set(section.endX - 88f, Constants.FLOOR_Y, 88f, 820f);
        }
    }

    private void updateMouseWorld() {
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY());
        worldViewport.unproject(mouseWorld);
    }

    private void handleShooting() {
        if (player.getHealth() <= 0 || !Gdx.input.isButtonPressed(Input.Buttons.LEFT) || shotCooldown > 0f) return;

        float dx = mouseWorld.x - player.getX();
        float dy = mouseWorld.y - player.getY();
        float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        dx /= len;
        dy /= len;

        float sx = player.getX() + dx * 48f;
        float sy = player.getY() + dy * 30f;
        playerProjectiles.add(new Projectile(
            sx, sy,
            dx * Constants.SHOT_SPEED,
            dy * Constants.SHOT_SPEED,
            Constants.SHOT_RADIUS,
            false, 1, false));

        shotCooldown = Constants.SHOT_COOLDOWN;
        learnedShoot = true;
        player.notifyShot();
        game.audio.shoot();
    }

    private void updateEnemies(float delta) {
        Iterator<GeoEnemy> iterator = enemies.iterator();
        while (iterator.hasNext()) {
            GeoEnemy enemy = iterator.next();
            enemy.update(delta, player, enemyProjectiles);
            if (enemy.consumeAttackEvent()) {
                game.audio.enemyAttack();
                shake(enemy.getType().isBoss() ? 0.18f : 0.09f,
                    enemy.getType().isBoss() ? 12f : 7f);
            }
            if (!enemy.isDefeated() && player.getBounds().overlaps(enemy.getBodyRect())) {
                if (player.damage(enemy.getContactDamage(), enemy.getX())) {
                    game.audio.hurt();
                    effects.add(new EffectBurst(player.getX(), player.getY(), EffectBurst.Kind.VOID, 110f, 0.22f));
                    shake(0.14f, 10f);
                }
            }
            if (enemy.isGone()) iterator.remove();
        }
    }

    private void updateProjectiles(float delta) {
        for (Projectile p : playerProjectiles) p.update(delta);
        for (Projectile p : enemyProjectiles) p.update(delta);

        for (Projectile p : playerProjectiles) {
            if (p.dead()) continue;
            for (GeoEnemy enemy : enemies) {
                if (enemy.isDefeated()) continue;
                GeoEnemy.HitResult result = enemy.testPlayerProjectile(p);
                if (result == GeoEnemy.HitResult.NONE) continue;

                if (result == GeoEnemy.HitResult.WRONG) {
                    totalWrongHits++;
                    score = Math.max(0, score - 25);
                    effects.add(new EffectBurst(p.x, p.y, EffectBurst.Kind.VOID, 100f, 0.22f));
                    feedback("ERROU O PONTO - desvie do ataque!", 1.25f);
                    shake(0.10f, 8f);
                } else {
                    totalCorrectHits++;
                    crystals++;
                    score += result == GeoEnemy.HitResult.WEAK_POINT ? 100
                        : result == GeoEnemy.HitResult.ROUND_COMPLETE ? 300
                        : enemy.getType().isBoss() ? 5000 : 900;
                    game.audio.weakPoint();
                    effects.add(new EffectBurst(p.x, p.y, EffectBurst.Kind.HIT, 105f, 0.20f));
                    shake(0.06f, 5f);

                    if (result == GeoEnemy.HitResult.WEAK_POINT) {
                        feedback("ACERTOU! - continue nos pontos iluminados", 1.0f);
                    } else if (result == GeoEnemy.HitResult.ROUND_COMPLETE) {
                        feedback("PROPRIEDADE RESOLVIDA - novo padrão", 1.4f);
                    } else if (result == GeoEnemy.HitResult.DEFEATED) {
                        boolean boss = enemy.getType().isBoss();
                        feedback(boss ? "CHEFE DESESTABILIZADO" : "FORMA DESFEITA", 1.8f);
                        effects.add(new EffectBurst(enemy.getX(), enemy.getY(),
                            EffectBurst.Kind.VOID, boss ? 320f : 180f, boss ? 0.65f : 0.4f));
                        shake(boss ? 0.5f : 0.2f, boss ? 20f : 11f);
                    }
                }
                break;
            }
        }

        Rectangle playerBounds = player.getBounds();
        for (Projectile p : enemyProjectiles) {
            if (p.dead()) continue;
            if (p.bounds().overlaps(playerBounds)) {
                p.life = 0f;
                if (player.damage(p.damage, p.x)) {
                    game.audio.hurt();
                    effects.add(new EffectBurst(player.getX(), player.getY(), EffectBurst.Kind.VOID, 115f, 0.24f));
                    shake(0.18f, 12f);
                }
            }
        }

        // O tiro do jogador atravessa as plataformas. Isso evita que o cenário esconda/bloqueie
        // os pontos fracos nesta versão de demonstração. Projéteis inimigos ainda colidem.
        for (Projectile p : enemyProjectiles) {
            if (!p.dead() && hitsWorld(p)) p.life = 0f;
        }

        playerProjectiles.removeIf(Projectile::dead);
        enemyProjectiles.removeIf(Projectile::dead);
    }

    private boolean hitsWorld(Projectile p) {
        Rectangle bounds = p.bounds();
        for (Platform platform : level.getPlatforms()) {
            if (bounds.overlaps(platform.bounds)) return true;
        }
        return gateActive && bounds.overlaps(gateRect);
    }

    private void updateEffects(float delta) {
        Iterator<EffectBurst> iterator = effects.iterator();
        while (iterator.hasNext()) {
            EffectBurst effect = iterator.next();
            effect.update(delta);
            if (effect.dead()) iterator.remove();
        }
    }

    private void handleLifeLost() {
        Section section = level.getSection(currentSection);
        if (!player.loseLifeAndRespawn(section.respawnX, 230f)) {
            game.setScreen(new GameOverScreen(game, gameTime, currentSection,
                totalCorrectHits, totalWrongHits, score));
            return;
        }
        spawnSection(currentSection, true);
        effects.clear();
        // O boss volta a existir, então o portal precisa voltar a ficar
        // inativo — senão dava para pular a luta e vencer direto.
        if (currentSection == 5) portalActive = false;
        feedback("VIDA PERDIDA - tentativa reiniciada", 2.2f);
        shake(0.28f, 16f);
    }

    private void shake(float time, float strength) {
        if (!game.settings.isScreenShakeEnabled()) return;
        shakeTimer = Math.max(shakeTimer, time);
        shakeStrength = Math.max(shakeStrength, strength);
    }

    private void feedback(String text, float duration) {
        feedback = text;
        feedbackTimer = duration;
    }

    private void updateCamera(float delta) {
        Section section = level.getSection(currentSection);
        float sectionCenter = (section.startX + section.endX) * 0.5f;

        float lookAhead = MathUtils.clamp(player.getVx() * 0.22f, -150f, 150f);
        float desiredPlayer = player.getX() + lookAhead;
        // A arena influencia só um pouco a câmera: dá contexto sem prender o ORB
        // no centro e mantém espaço à frente do movimento.
        float desired = desiredPlayer * 0.78f + sectionCenter * 0.22f;
        // O zoom entra no clamp: com zoom 1.06 fixo na arena a câmera passava
        // do fim do mundo e mostrava vazio nas bordas.
        float halfView = Constants.VIEW_WIDTH * 0.5f * worldCamera.zoom;
        float targetX = MathUtils.clamp(desired, halfView, Constants.WORLD_WIDTH - halfView);

        float follow = 1f - (float)Math.exp(-Constants.CAMERA_SMOOTH * delta);
        cameraBaseX = MathUtils.lerp(cameraBaseX, targetX, follow);
        cameraBaseY = MathUtils.lerp(cameraBaseY, Constants.VIEW_HEIGHT / 2f, follow);
        worldCamera.position.set(cameraBaseX, cameraBaseY, 0f);

        if (shakeTimer > 0f) {
            worldCamera.position.x += MathUtils.random(-shakeStrength, shakeStrength);
            worldCamera.position.y += MathUtils.random(-shakeStrength, shakeStrength);
        } else {
            shakeStrength = 0f;
        }

        // Pixel snapping reduz shimmering de pixel art durante a câmera suave.
        // O zoom fica travado em 1: qualquer zoom fracionário reintroduz o
        // shimmering que o snapping acabou de remover.
        worldCamera.position.x = Math.round(worldCamera.position.x);
        worldCamera.position.y = Math.round(worldCamera.position.y);
        worldCamera.update();
        hudCamera.update();
    }

    // ------------------------------------------------------------------ render

    private void renderBackground() {
        hudViewport.apply();
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();

        // Parallax real: o fundo anda a 22% da câmera e as cópias alternam
        // espelhadas, então nunca aparece emenda nem borda vazia por mais
        // longo que o mundo seja.
        float span = Constants.VIEW_WIDTH;
        float scroll = worldCamera.position.x * 0.22f;
        int first = MathUtils.floor(scroll / span);
        int last = MathUtils.floor((scroll + Constants.VIEW_WIDTH) / span);

        // Levemente rebaixado em brilho para o cenário nunca competir com
        // personagem, pontos fracos e projéteis.
        game.batch.setColor(0.74f, 0.76f, 0.88f, 1f);
        for (int n = first; n <= last; n++) {
            boolean mirrored = Math.floorMod(n, 2) == 1;
            game.batch.draw(mirrored ? backgroundMirrored : backgroundRegion,
                n * span - scroll, 0f, span, Constants.VIEW_HEIGHT);
        }

        // Escurece a faixa de gameplay, onde tudo que importa acontece.
        // Em degradê: a faixa única de alfa fixo deixava uma linha horizontal
        // dura atravessando a tela inteira.
        int steps = 8;
        float bandHeight = 430f;
        for (int i = 0; i < steps; i++) {
            float t = i / (float)steps;
            game.batch.setColor(0.018f, 0.012f, 0.045f, 0.052f);
            game.batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, bandHeight * (1f - t));
        }
        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    private void renderWorld() {
        worldViewport.apply();
        game.batch.setProjectionMatrix(worldCamera.combined);
        game.batch.begin();
        game.batch.setColor(Color.WHITE);

        for (Platform platform : level.getPlatforms()) drawPlatform(platform);

        if (gateActive) {
            // Colisão continua alta para bloquear a seção, mas visualmente existe
            // apenas uma porta + feixe de energia, evitando um sprite esticado.
            game.batch.setColor(0.35f, 0.75f, 1f, 0.20f);
            game.batch.draw(game.assets.pixel, gateRect.x + 34f, Constants.FLOOR_Y, 20f, 760f);
            game.batch.setColor(Color.WHITE);
            game.batch.draw(game.assets.doorBlue, gateRect.x - 28f, Constants.FLOOR_Y - 4f, 145f, 169f);
        }

        if (portalActive) {
            float pulse = 0.88f + 0.12f * MathUtils.sin(gameTime * 4f);
            game.batch.setColor(1f, 1f, 1f, pulse);
            game.batch.draw(game.assets.portal,
                PORTAL_BOUNDS.x + PORTAL_BOUNDS.width / 2f - 107f, Constants.FLOOR_Y - 8f, 215f, 190f);
            game.batch.setColor(Color.WHITE);
        }

        drawEnemies();
        drawProjectiles();
        drawPlayer();
        drawEffects();

        boolean onTarget = isAimOnWeakPoint();
        float aimSize = onTarget ? 39f : 34f;
        game.batch.setColor(onTarget ? AIM_LOCK : Color.WHITE);
        game.batch.draw(game.assets.crosshair,
            mouseWorld.x - aimSize / 2f, mouseWorld.y - aimSize / 2f, aimSize, aimSize);
        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    private boolean isAimOnWeakPoint() {
        for (GeoEnemy enemy : enemies) {
            if (enemy.isDefeated()) continue;
            for (WeakPoint point : enemy.getWeakPoints()) {
                if (point.hit) continue;
                float dx = mouseWorld.x - point.worldX(enemy.getX(), enemy.getRotation());
                float dy = mouseWorld.y - point.worldY(enemy.getY(), enemy.getRotation());
                float radius = enemy.weakPointHitRadius() + 12f;
                if (dx * dx + dy * dy <= radius * radius) return true;
            }
        }
        return false;
    }

    private void drawBackDecor() {
        // Decoração em camadas, sempre afastada do centro das arenas.
        // Todos os props usam a proporção nativa da textura: esticar sprite de
        // cenário em eixos diferentes era a origem do aspecto "borrachudo".
        decor(game.assets.pillarMagenta, 120f, 1.15f, 0.90f);
        decor(game.assets.crystalsBlue, 370f, 0.90f, 0.88f);
        decor(game.assets.bannerMagenta, 1580f, 0.90f, 0.92f);

        decor(game.assets.archStone, 1860f, 1.10f, 0.82f);
        decor(game.assets.crystalsMagenta, 3340f, 0.90f, 0.88f);

        decor(game.assets.pillarBlue, 3660f, 1.15f, 0.88f);
        decor(game.assets.ruinChunk, 5480f, 1.10f, 0.80f);

        decor(game.assets.archMagenta, 5740f, 1.10f, 0.82f);
        decor(game.assets.flowers, 7270f, 0.80f, 0.90f);

        decor(game.assets.pillarRuined, 7540f, 1.15f, 0.84f);
        decor(game.assets.crystalRock, 9080f, 0.95f, 0.90f);

        decor(game.assets.bannerBlue, 9350f, 0.90f, 0.88f);
        decor(game.assets.rocksLarge, 11780f, 0.95f, 0.82f);

        // Pequenos props de leitura narrativa — espaçados para não formar pilhas.
        decor(game.assets.crate, 1320f, 0.80f, 0.95f);
        decor(game.assets.barrel, 3440f, 0.78f, 0.95f);
        decor(game.assets.sign, 7420f, 0.80f, 0.95f);
        decor(game.assets.crystalPedestal, 9180f, 0.90f, 0.95f);
    }

    private void drawFrontDecor() {
        // Poucos elementos frontais, baixos, para dar profundidade sem esconder o ORB.
        decor(game.assets.grassRocks, 1650f, 0.60f, 0.92f);
        decor(game.assets.grassRocks, 5550f, 0.60f, 0.92f);
        decor(game.assets.grassRocks, 9200f, 0.60f, 0.92f);
    }

    /** Prop apoiado no piso, escala uniforme (sem distorção de proporção). */
    private void decor(Texture texture, float x, float scale, float alpha) {
        float w = texture.getWidth() * scale;
        float h = texture.getHeight() * scale;
        if (x + w < worldCamera.position.x - CULL_MARGIN || x > worldCamera.position.x + CULL_MARGIN) return;
        game.batch.setColor(1f, 1f, 1f, alpha);
        game.batch.draw(texture, x, Constants.FLOOR_Y - 4f, w, h);
        game.batch.setColor(Color.WHITE);
    }

    /** O ponto de respawn de cada seção agora é visível no mapa. */
    private void drawCheckpoints() {
        for (Section section : level.getSections()) {
            if (section.index == 0) continue;
            float x = section.respawnX - 55f;
            if (x < worldCamera.position.x - CULL_MARGIN || x > worldCamera.position.x + CULL_MARGIN) continue;
            boolean reached = section.index <= currentSection;
            float pulse = reached ? 0.85f + 0.15f * MathUtils.sin(gameTime * 3.4f) : 0.42f;
            game.batch.setColor(1f, 1f, 1f, pulse);
            game.batch.draw(game.assets.checkpoint, x, Constants.FLOOR_Y - 4f, 110f, 164f);
            game.batch.setColor(Color.WHITE);
        }
    }

    private void drawPlatform(Platform platform) {
        Rectangle r = platform.bounds;
        if (r.x + r.width < worldCamera.position.x - CULL_MARGIN
            || r.x > worldCamera.position.x + CULL_MARGIN) return;

        if (r.y <= 1f && r.width > 1000f) {
            drawGround(r);
            return;
        }

        // Plataformas elevadas usam as peças flutuantes do tileset FINAL, com
        // escala UNIFORME e repetição: antes uma peça de 139x100 era esticada
        // para 288x80, o que deformava o desenho em mais de 2x num eixo só.
        Texture piece = platformPiece(r);
        int tiles = Math.max(1, Math.round(r.width / piece.getWidth()));
        float scale = r.width / (tiles * (float)piece.getWidth());
        float h = piece.getHeight() * scale;
        float top = r.y + r.height;
        float tileW = r.width / tiles;
        for (int i = 0; i < tiles; i++) {
            game.batch.draw(piece, r.x + i * tileW, top - h, tileW, h);
        }
    }

    /**
     * Escolhe a peça cuja largura nativa cabe um número inteiro de vezes na
     * plataforma com a menor distorção possível; a cor varia por zona só para
     * o mapa não ficar monótono.
     */
    private Texture platformPiece(Rectangle r) {
        int zone = MathUtils.clamp((int)(r.x / 1900f), 0, 5);
        Texture best = platformPieces[0];
        float bestError = Float.MAX_VALUE;
        for (int i = 0; i < platformPieces.length; i++) {
            Texture t = platformPieces[(zone + i) % platformPieces.length];
            int tiles = Math.max(1, Math.round(r.width / t.getWidth()));
            float error = Math.abs(r.width / (tiles * (float)t.getWidth()) - 1f);
            // Empates ficam com o primeiro da rotação da zona, dando variedade.
            if (error < bestError - 0.0001f) {
                bestError = error;
                best = t;
            }
        }
        return best;
    }

    private void drawGround(Rectangle r) {
        Texture tile = game.assets.groundGrass;
        Texture alt = game.assets.groundGrassAlt;
        // Largura escolhida para que a altura escalada cubra exatamente do 0
        // até FLOOR_Y mantendo a proporção nativa do tile (83x104).
        float tileW = 101f;
        float start = Math.max(r.x, (float)Math.floor((worldCamera.position.x - 1100f) / tileW) * tileW);
        float end = Math.min(r.x + r.width, worldCamera.position.x + 1100f);

        // Base escura por baixo: o tile alternativo é um pouco mais baixo e sem
        // isso apareceria uma fresta.
        game.batch.setColor(0.06f, 0.045f, 0.09f, 1f);
        game.batch.draw(game.assets.pixel, start, 0f, Math.max(0f, end - start), Constants.FLOOR_Y * 0.5f);
        game.batch.setColor(Color.WHITE);

        for (float x = start; x < end; x += tileW) {
            int index = Math.abs((int)(x / tileW));
            Texture top = index % 3 == 0 ? alt : tile;
            float h = tileW * top.getHeight() / (float)top.getWidth();
            game.batch.draw(top, x, Constants.FLOOR_Y - h, tileW, h);
        }
    }

    private void drawEnemies() {
        for (GeoEnemy enemy : enemies) {
            if (enemy.getType().isBoss()) {
                drawBoss(enemy);
                if (!enemy.isDefeated()) drawWeakPointIndicators(enemy);
                continue;
            }

            EnemyType type = enemy.getType();
            float size = type.drawSize();
            float alpha = enemy.isDefeated() ? Math.max(0f, 1f - enemy.getDeathProgress()) : 1f;
            if (enemy.getFlash() > 0f || enemy.isHurt()) game.batch.setColor(1f, 0.66f, 0.88f, alpha);
            else game.batch.setColor(1f, 1f, 1f, alpha);

            game.batch.draw(enemyFrame(enemy),
                enemy.getDrawX(), enemy.getDrawY(),
                size / 2f, type.drawOriginY(),
                size, size,
                enemy.isFacingRight() ? 1f : -1f, 1f,
                0f);
            game.batch.setColor(Color.WHITE);

            if (!enemy.isDefeated()) drawWeakPointIndicators(enemy);
        }
    }

    private TextureRegion enemyFrame(GeoEnemy enemy) {
        EnemyType type = enemy.getType();
        if (enemy.isDefeated()) return game.assets.enemyDeath(type).getKeyFrame(enemy.getDeathVisualTime());
        if (enemy.isHurt()) return game.assets.enemyHurt(type).getKeyFrame(enemy.getHurtVisualTime());
        if (enemy.isAttacking()) return game.assets.enemyAttack(type).getKeyFrame(enemy.getAttackVisualTime());
        if (enemy.isMoving()) return game.assets.enemyMove(type).getKeyFrame(enemy.getStateTime());
        return game.assets.enemyIdle(type).getKeyFrame(enemy.getStateTime());
    }

    private void drawBoss(GeoEnemy enemy) {
        EnemyType type = EnemyType.BOSS;
        float size = type.drawSize();
        float originY = type.drawOriginY();
        TextureRegion frame;

        if (enemy.isDefeated()) {
            frame = game.assets.bossDeath.getKeyFrame(enemy.getDeathVisualTime());
        } else if (enemy.isPoweringUp()) {
            frame = game.assets.bossPowerUp.getKeyFrame(enemy.getPowerUpVisualTime());
        } else if (enemy.isAttacking()) {
            frame = enemy.getBossPhase() == 1
                ? game.assets.bossBeam.getKeyFrame(enemy.getAttackVisualTime())
                : game.assets.bossOrbs.getKeyFrame(enemy.getAttackVisualTime());
        } else if (enemy.getBossPhase() == 2) {
            frame = game.assets.bossEnraged.getKeyFrame(enemy.getStateTime());
        } else {
            frame = game.assets.bossIdle.getKeyFrame(enemy.getStateTime());
        }

        if (enemy.getBossPhase() == 1 && !enemy.isDefeated()) {
            // Fase do espelho: a cópia espelhada fica apagada e DESLOCADA; o
            // corpo real continua no lugar, que é onde os pontos fracos estão.
            // (Antes os dois desenhos e os pontos fracos viviam em coordenadas
            // completamente diferentes.)
            float mirrorOffset = type.halfWidth() * 2.1f;
            game.batch.setColor(0.72f, 0.70f, 0.90f, 0.45f);
            game.batch.draw(frame, enemy.getDrawX() - mirrorOffset, enemy.getDrawY(),
                size / 2f, originY, size, size, -1f, 1f, 0f);
            game.batch.setColor(Color.WHITE);
        }

        float alpha = enemy.isDefeated() ? Math.max(0f, 1f - enemy.getDeathProgress()) : 1f;
        if (enemy.getFlash() > 0f || enemy.isHurt()) game.batch.setColor(1f, 0.62f, 0.86f, alpha);
        else game.batch.setColor(1f, 1f, 1f, alpha);
        // A origem da rotação é o CENTRO DO CORPO, o mesmo ponto em torno do
        // qual os pontos fracos giram.
        game.batch.draw(frame,
            enemy.getDrawX(), enemy.getDrawY(),
            size / 2f, originY,
            size, size,
            enemy.isFacingRight() ? 1f : -1f, 1f,
            (float)Math.toDegrees(enemy.getRotation()));
        game.batch.setColor(Color.WHITE);
    }

    private void drawWeakPointIndicators(GeoEnemy enemy) {
        float pulse = 1f + 0.06f * MathUtils.sin(enemy.getStateTime() * 5f);

        for (WeakPoint point : enemy.getWeakPoints()) {
            if (point.hit) continue;

            float wx = point.worldX(enemy.getX(), enemy.getRotation());
            float wy = point.worldY(enemy.getY(), enemy.getRotation());
            float size = enemy.weakPointMarkerSize() * pulse;

            game.batch.setColor(Color.WHITE);
            game.batch.draw(game.assets.weakPoint, wx - size / 2f, wy - size / 2f, size, size);
        }
        game.batch.setColor(Color.WHITE);
    }

    private void drawProjectiles() {
        for (Projectile p : playerProjectiles) {
            float size = 44f;
            game.batch.draw(game.assets.playerShot, p.x - size / 2f, p.y - size / 2f, size, size);
        }
        for (Projectile p : enemyProjectiles) {
            float size = p.boss ? 54f : 44f;
            game.batch.draw(p.boss ? game.assets.bossShot : game.assets.enemyShot,
                p.x - size / 2f, p.y - size / 2f, size, size);
        }
    }

    private void drawEffects() {
        for (EffectBurst effect : effects) {
            Texture texture = switch (effect.kind) {
                case HIT -> game.assets.hitBurst;
                case VOID -> game.assets.voidBurst;
                case DASH -> game.assets.dashTrail;
            };
            float grow = 1f + effect.time / effect.duration * 0.35f;
            float size = effect.size * grow;
            game.batch.setColor(1f, 1f, 1f, effect.alpha());
            game.batch.draw(texture, effect.x - size / 2f, effect.y - size / 2f, size, size);
        }
        game.batch.setColor(Color.WHITE);
    }

    private void drawPlayer() {
        TextureRegion frame = player.getFrame(game.assets);
        float sx = player.isFacingRight() ? 1f : -1f;
        // A baseline do sprite (15 px acima da borda do canvas) encosta na base
        // da caixa de colisão. Era aqui que estava o pior bug da integração: o
        // ORB era desenhado 175 px ABAIXO dos próprios pés e ficava enterrado
        // no chão.
        float drawY = player.getFeetY() - Constants.CHAR_BASELINE * Constants.PLAYER_H;
        float alpha = player.isInvulnerable() && player.getHealth() > 0
            ? 0.55f + 0.45f * MathUtils.sin(gameTime * 26f) : 1f;
        game.batch.setColor(1f, 1f, 1f, alpha);
        game.batch.draw(frame,
            player.getX() - Constants.PLAYER_W / 2f,
            drawY,
            Constants.PLAYER_W / 2f, Constants.PLAYER_H / 2f,
            Constants.PLAYER_W, Constants.PLAYER_H,
            sx, 1f, 0f);
        game.batch.setColor(Color.WHITE);
    }

    // --------------------------------------------------------------------- HUD

    private void renderHud() {
        hudViewport.apply();
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();

        drawPlayerHud();
        drawEnemyHud();
        drawStatsHud();
        drawCombatHelp();
        drawBannerAndFeedback();

        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    private void drawPlayerHud() {
        hudPanel(28f, 890f, 520f, 165f);
        game.font.getData().setScale(1.65f);
        game.font.setColor(Color.WHITE);
        game.font.draw(game.batch, "ORB", 62f, 1009f);
        game.font.getData().setScale(1.55f);
        game.font.setColor(0.82f, 0.88f, 1f, 1f);
        game.font.draw(game.batch, "VIDA " + player.getHealth() + "/" + Constants.MAX_HEALTH, 62f, 970f);
        game.batch.setColor(0.10f, 0.08f, 0.18f, 1f);
        game.batch.draw(game.assets.pixel, 62f, 934f, 440f, 17f);
        game.batch.setColor(0.91f, 0.27f, 0.78f, 1f);
        game.batch.draw(game.assets.pixel, 64f, 936f,
            436f * player.getHealth() / (float)Constants.MAX_HEALTH, 13f);
        game.batch.setColor(Color.WHITE);
        for (int i = 0; i < player.getLives(); i++)
            game.batch.draw(game.assets.lifeOrb, 369f + i * 36f, 974f, 28f, 28f);
    }

    private void drawEnemyHud() {
        GeoEnemy active = firstActiveEnemy();
        if (active == null) return;

        float x = 600f;
        hudPanel(x, 900f, 720f, 155f);
        game.font.getData().setScale(1.45f);
        game.font.setColor(1f, 0.87f, 0.98f, 1f);
        game.font.draw(game.batch, active.getType().displayName(), x + 35f, 1010f);
        game.font.getData().setScale(1.43f);
        game.font.setColor(1f, 0.89f, 0.55f, 1f);
        game.font.draw(game.batch, "ALVOS " + active.getRemainingWeakPoints()
            + "/" + active.getWeakPointCount(), x + 535f, 1010f);
        game.font.getData().setScale(1.38f);
        game.font.setColor(Color.WHITE);
        game.font.draw(game.batch, active.getExplicitInstruction(), x + 35f, 974f);
        game.batch.setColor(0.07f, 0.035f, 0.11f, 1f);
        game.batch.draw(game.assets.pixel, x + 35f, 937f, 650f, 13f);
        float charge = active.isAttacking() ? 1f : Math.min(1f, active.getCharge());
        game.batch.setColor(charge > 0.75f ? CHARGE_HIGH : CHARGE_LOW);
        game.batch.draw(game.assets.pixel, x + 37f, 939f, 646f * charge, 9f);
        game.batch.setColor(Color.WHITE);
    }

    private void drawStatsHud() {
        int minutes = (int)(gameTime / 60f);
        int seconds = (int)(gameTime % 60f);

        hudPanel(1505f, 900f, 385f, 155f);
        game.font.getData().setScale(1.5f);
        game.font.setColor(Color.WHITE);
        game.font.draw(game.batch, String.format("CRISTAIS %03d", crystals), 1540f, 1010f);
        game.font.draw(game.batch, String.format("SCORE %06d", score), 1540f, 976f);
        game.font.draw(game.batch, String.format("TEMPO %02d:%02d", minutes, seconds), 1540f, 952f);
    }

    private void hudPanel(float x, float y, float width, float height) {
        game.batch.setColor(0.018f, 0.014f, 0.055f, 0.96f);
        game.batch.draw(game.assets.pixel, x, y, width, height);
        game.batch.setColor(Color.WHITE);
        game.batch.draw(game.assets.hudFrame, x, y, width, height);
    }

    private void drawObjectiveHud() {
        float x = 1470f, y = 116f, w = 420f, h = 126f;
        game.batch.setColor(0.018f, 0.014f, 0.055f, 0.94f);
        game.batch.draw(game.assets.pixel, x, y, w, h);
        game.batch.setColor(Color.WHITE);
        game.batch.draw(game.assets.hudFrame, x, y, w, h);
        game.font.getData().setScale(1.4f);
        game.font.setColor(0.28f, 0.88f, 1f, 1f);
        game.font.draw(game.batch, "OBJETIVO", x + 24f, y + 78f);
        game.font.getData().setScale(1.3f);
        game.font.setColor(Color.WHITE);
        game.font.draw(game.batch, objectiveText(), x + 24f, y + 49f);
        game.font.setColor(0.66f, 0.72f, 0.94f, 1f);
        game.font.draw(game.batch, "C / TAB  CÓDEX", x + 24f, y + 25f);
    }

    private String objectiveText() {
        if (portalActive) return "ATRAVESSE O RIFT";
        if (currentSection == 0) return "ALCANCE A CÂMARA";
        GeoEnemy active = firstActiveEnemy();
        return active == null ? "SIGA PARA A DIREITA" : "RESOLVA A FORMA " + active.getType().displayName();
    }

    private void drawCombatHelp() {
        String hint;
        if (portalActive) hint = playerAtPortal() ? "E  -  ATRAVESSAR O RIFT" : "SIGA PARA O PORTAL";
        else if (currentSection == 0 && !learnedMove) hint = "A / D  -  MOVA ORB ATÉ O SINAL";
        else if (currentSection == 0 && !learnedJump) hint = "ESPAÇO  -  SALTE SOBRE AS RUÍNAS";
        else if (currentSection == 0 && !learnedDash) hint = "SHIFT  -  USE O DASH";
        else if (currentSection == 0 && !learnedShoot) hint = "MOUSE  -  MIRE E ATIRE";
        else hint = "ACERTE OS ALVOS  -  DESVIE DOS ATAQUES";

        game.batch.setColor(0.018f, 0.014f, 0.05f, 0.91f);
        game.batch.draw(game.assets.pixel, 560f, 22f, 800f, 68f);
        game.batch.setColor(Color.WHITE);
        game.ui.textCentered(hint, 960f, 67f, 1.03f, Color.WHITE);
    }

    private void drawBannerAndFeedback() {
        if (bannerTimer > 0f) {
            float alpha = Math.min(1f, bannerTimer);
            game.batch.setColor(0.018f, 0.012f, 0.05f, 0.86f * alpha);
            game.batch.draw(game.assets.pixel, 575f, 570f, 770f, 104f);
            game.batch.setColor(Color.WHITE);
            Section section = level.getSection(currentSection);
            game.ui.textCentered(section.title, 960f, 642f, 1.45f,
                new Color(0.96f, 0.94f, 1f, alpha));
            game.ui.textCentered(section.subtitle, 960f, 606f, 0.75f,
                new Color(0.48f, 0.88f, 1f, alpha));
        }

        if (feedbackTimer > 0f) {
            game.batch.setColor(0.025f, 0.012f, 0.06f, 0.88f);
            game.batch.draw(game.assets.pixel, 655f, 842f, 610f, 48f);
            game.batch.setColor(Color.WHITE);
            game.font.getData().setScale(1.55f);
            game.font.setColor(1f, 0.72f, 0.94f, Math.min(1f, feedbackTimer));
            game.font.draw(game.batch, feedback, 720f, 874f);
        }
    }

    private GeoEnemy firstActiveEnemy() {
        for (GeoEnemy enemy : enemies) {
            if (!enemy.isDefeated()) return enemy;
        }
        return null;
    }

    /** Retorna true quando a acao trocou de tela e este frame deve terminar. */
    private boolean handlePauseInput() {
        mouseHud.set(Gdx.input.getX(), Gdx.input.getY());
        hudViewport.unproject(mouseHud);
        for (int i = 0; i < pauseButtons.length; i++) {
            if (pauseButtons[i].contains(mouseHud)) pauseSelected = i;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            pauseSelected = (pauseSelected + pauseButtons.length - 1) % pauseButtons.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            pauseSelected = (pauseSelected + 1) % pauseButtons.length;
        }
        boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || (pauseButtons[pauseSelected].contains(mouseHud)
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT));
        if (!activate) return false;

        switch (pauseSelected) {
            case 0 -> paused = false;
            case 1 -> { game.startGame(); return true; }
            case 2 -> { game.setScreen(new OptionsScreen(game, this)); return true; }
            case 3 -> { game.showMenu(); return true; }
            default -> { }
        }
        return false;
    }

    private void renderCodex() {
        hudViewport.apply();
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();
        game.batch.setColor(0.004f, 0.004f, 0.025f, 0.90f);
        game.batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);

        game.ui.panel(310f, 120f, 1300f, 840f, UiRenderer.CYAN, 1f);
        game.ui.crystalCorners(310f, 120f, 1300f, 840f, 62f, 1f);
        game.ui.textCentered("CÓDEX DAS FORMAS", 960f, 885f, 2.4f, Color.WHITE);
        game.ui.textCentered("Descobertas registradas durante esta expedição", 960f, 833f,
            0.88f, UiRenderer.CYAN);

        float y = 750f;
        for (int sectionIndex = 1; sectionIndex <= 5; sectionIndex++) {
            boolean unlocked = sectionSpawned[sectionIndex] || sectionCleared[sectionIndex];
            String title;
            String fact;
            switch (sectionIndex) {
                case 1 -> { title = "LOSANGO"; fact = "4 vértices opostos - simetria em dois eixos"; }
                case 2 -> { title = "TRIÂNGULO"; fact = "3 lados - 3 vértices - estrutura mínima rígida"; }
                case 3 -> { title = "QUADRADO"; fact = "4 lados iguais - 4 ângulos retos"; }
                case 4 -> { title = "HEXÁGONO"; fact = "6 lados - 6 vértices - padrão de encaixe"; }
                default -> { title = "NÚCLEO GEOMÉTRICO"; fact = "rotação, reflexão e simetria combinadas"; }
            }
            game.batch.setColor(unlocked ? 0.055f : 0.025f, 0.04f, unlocked ? 0.11f : 0.055f, 0.95f);
            game.batch.draw(game.assets.pixel, 430f, y - 54f, 1060f, 72f);
            game.batch.setColor(Color.WHITE);
            game.font.getData().setScale(1.66f);
            game.font.setColor(unlocked ? UiRenderer.MAGENTA : new Color(0.42f, 0.44f, 0.55f, 1f));
            game.font.draw(game.batch, unlocked ? title : "???", 470f, y - 5f);
            game.font.getData().setScale(1.33f);
            game.font.setColor(unlocked ? Color.WHITE : new Color(0.36f, 0.38f, 0.48f, 1f));
            game.font.draw(game.batch, unlocked ? fact : "Encontre esta forma para registrar a descoberta.", 770f, y - 5f);
            y -= 105f;
        }
        game.ui.textCentered("C / TAB / ESC  -  VOLTAR À PARTIDA", 960f, 165f, 0.84f,
            UiRenderer.SOFT_TEXT);
        game.batch.end();
    }

    private void renderPause() {
        hudViewport.apply();
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();

        game.batch.setColor(0.01f, 0.008f, 0.04f, 0.86f);
        game.batch.draw(game.assets.pixel, 0, 0, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);

        game.ui.panel(610f, 170f, 700f, 740f, UiRenderer.MAGENTA, 1f);
        game.ui.crystalCorners(610f, 170f, 700f, 740f, 62f, 1f);
        game.ui.textCentered("PAUSADO", 960f, 860f, 2.8f, Color.WHITE);
        game.ui.textCentered(level.getSection(currentSection).title + "  -  " + objectiveText(),
            960f, 735f, 0.82f, UiRenderer.CYAN);
        game.ui.button(pauseButtons[0], "CONTINUAR", pauseSelected == 0, true, UiRenderer.MAGENTA);
        game.ui.button(pauseButtons[1], "REINICIAR", pauseSelected == 1, true, UiRenderer.CYAN);
        game.ui.button(pauseButtons[2], "OPÇÕES", pauseSelected == 2, true, UiRenderer.CYAN);
        game.ui.button(pauseButtons[3], "SAIR PARA O MENU", pauseSelected == 3, true, UiRenderer.CYAN);

        game.batch.end();
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, true);
        hudViewport.update(width, height, true);
    }
}
