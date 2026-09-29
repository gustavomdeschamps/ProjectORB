package com.delmartec.projectorb.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
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
import com.delmartec.projectorb.utils.Assets;
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
    /**
     * Ao sair da pausa ou do códex, pulo/dash/tiro/interação ficam bloqueados
     * por este tempo. Sem isso, o ESPAÇO que confirmava "CONTINUAR" virava um
     * pulo e o clique no botão virava um tiro no mesmo frame.
     */
    private static final float RESUME_INPUT_GUARD = 0.15f;

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

    // Fundo em camadas de parallax. Cada camada alterna cópias normais e
    // espelhadas: repetição infinita sem emenda visível, sem arte tileável.
    private final TextureRegion[] backgroundLayers;
    private final TextureRegion[] backgroundLayersMirrored;
    /** Fator de parallax de cada camada (céu parado ... chão mais rápido). */
    private static final float[] PARALLAX = { 0f, 0.03f, 0.06f, 0.09f, 0.12f, 0.16f, 0.22f, 0.30f, 0.38f };
    /** Ponta direita das plataformas = ponta esquerda espelhada. */
    private final TextureRegion platformCapRight;
    private final TextureRegion platformAltCapRight;
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
    private float resumeGuard = 0f;
    /** O botão do mouse precisa ser solto antes do próximo tiro. */
    private boolean waitShootRelease = false;

    public GameScreen(ProjectOrbGame game) {
        this.game = game;
        int layers = game.assets.backgroundLayers.length;
        backgroundLayers = new TextureRegion[layers];
        backgroundLayersMirrored = new TextureRegion[layers];
        for (int i = 0; i < layers; i++) {
            backgroundLayers[i] = new TextureRegion(game.assets.backgroundLayers[i]);
            backgroundLayersMirrored[i] = new TextureRegion(game.assets.backgroundLayers[i]);
            backgroundLayersMirrored[i].flip(true, false);
        }
        platformCapRight = new TextureRegion(game.assets.platform.cap);
        platformCapRight.flip(true, false);
        platformAltCapRight = new TextureRegion(game.assets.platformAlt.cap);
        platformAltCapRight.flip(true, false);
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
            if (!codexOpen) guardResumeInput();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (codexOpen) {
                codexOpen = false;
                guardResumeInput();
            } else {
                paused = !paused;
                if (!paused) guardResumeInput();
            }
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

    private void guardResumeInput() {
        resumeGuard = RESUME_INPUT_GUARD;
        waitShootRelease = true;
    }

    private void update(float delta) {
        gameTime += delta;
        resumeGuard = Math.max(0f, resumeGuard - delta);
        boolean acceptActions = resumeGuard <= 0f;
        shotCooldown = Math.max(0f, shotCooldown - delta);
        feedbackTimer = Math.max(0f, feedbackTimer - delta);
        shakeTimer = Math.max(0f, shakeTimer - delta);
        dashFxTimer = Math.max(0f, dashFxTimer - delta);
        bannerTimer = Math.max(0f, bannerTimer - delta);

        updateSectionProgress();
        updateGate();

        player.update(delta, level.getPlatforms(), gateActive ? gateRect : null, acceptActions);

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
        handleShooting(acceptActions);
        updateEnemies(delta);
        updateProjectiles(delta);
        updateEffects(delta);

        if (player.getY() < -120f && player.getHealth() > 0) player.forceDeath();
        if (player.isDeathAnimationFinished()) handleLifeLost();

        if (acceptActions && portalActive && playerAtPortal() && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
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

    private void handleShooting(boolean acceptActions) {
        boolean pressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        if (waitShootRelease) {
            // O clique que fechou a pausa ainda pode estar pressionado.
            if (!pressed) waitShootRelease = false;
            return;
        }
        if (!acceptActions || player.getHealth() <= 0 || !pressed || shotCooldown > 0f) return;

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
        if (sectionCleared[currentSection]) {
            // A seção já estava resolvida (ex.: um projétil inimigo ainda no ar
            // depois da morte do inimigo). Não refaz a luta: os inimigos não
            // voltam, o portão continua aberto e o portal final continua ativo.
            enemyProjectiles.clear();
            playerProjectiles.clear();
            feedback("VIDA PERDIDA - seção já resolvida", 2.2f);
        } else {
            spawnSection(currentSection, true);
            // O boss volta a existir, então o portal precisa voltar a ficar
            // inativo — senão dava para pular a luta e vencer direto.
            if (currentSection == 5) portalActive = false;
            feedback("VIDA PERDIDA - tentativa reiniciada", 2.2f);
        }
        effects.clear();
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

        // Parallax real em 9 camadas, todas a 4x (480x270 -> 1920x1080). O
        // deslocamento é arredondado para múltiplos de 4 px para os pixels da
        // arte ficarem na grade. Levemente rebaixado em brilho para o cenário
        // nunca competir com personagem, pontos fracos e projéteis.
        float span = Constants.VIEW_WIDTH;
        int step = Constants.BACKGROUND_SCALE;
        game.batch.setColor(0.74f, 0.76f, 0.88f, 1f);
        for (int layer = 0; layer < backgroundLayers.length; layer++) {
            float scroll = Math.round(worldCamera.position.x * PARALLAX[layer] / step) * step;
            int first = MathUtils.floor(scroll / span);
            int last = MathUtils.floor((scroll + Constants.VIEW_WIDTH) / span);
            for (int n = first; n <= last; n++) {
                boolean mirrored = Math.floorMod(n, 2) == 1;
                game.batch.draw(mirrored ? backgroundLayersMirrored[layer] : backgroundLayers[layer],
                    n * span - scroll, 0f, span, Constants.VIEW_HEIGHT);
            }
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
            // gate.png tem exatamente a largura da colisão (88 px) e vai em 1x.
            game.batch.draw(game.assets.gate, gateRect.x, Constants.FLOOR_Y,
                game.assets.gate.getWidth(), game.assets.gate.getHeight());
        }

        if (portalActive) {
            float pulse = 0.88f + 0.12f * MathUtils.sin(gameTime * 4f);
            game.batch.setColor(1f, 1f, 1f, pulse);
            float pw = game.assets.portal.getWidth() * 2f;
            float ph = game.assets.portal.getHeight() * 2f;
            game.batch.draw(game.assets.portal,
                PORTAL_BOUNDS.x + PORTAL_BOUNDS.width / 2f - pw / 2f, Constants.FLOOR_Y, pw, ph);
            game.batch.setColor(Color.WHITE);
        }

        drawEnemies();
        drawProjectiles();
        drawPlayer();
        drawEffects();

        boolean onTarget = isAimOnWeakPoint();
        float aimSize = game.assets.crosshair.getWidth() * 2f;
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

    private void drawPlatform(Platform platform) {
        Rectangle r = platform.bounds;
        if (r.x + r.width < worldCamera.position.x - CULL_MARGIN
            || r.x > worldCamera.position.x + CULL_MARGIN) return;

        if (r.y <= 1f && r.width > 1000f) {
            drawGround(r);
            return;
        }

        // Plataformas elevadas em 1x, montadas com as peças da arte oficial:
        // ponta + módulos inteiros (caixa + vão) + colunas lisas que absorvem
        // a sobra + ponta espelhada. Nada é esticado fora da escala inteira.
        // A cor alterna por zona só para o mapa não ficar monótono.
        boolean alt = MathUtils.clamp((int)(r.x / 1900f), 0, 5) % 2 == 1;
        Assets.PlatformSkin skin = alt ? game.assets.platformAlt : game.assets.platform;
        TextureRegion capRight = alt ? platformAltCapRight : platformCapRight;
        int cap = skin.cap.getWidth();
        int module = skin.module.getWidth();
        int h = skin.module.getHeight();
        int inner = Math.round(r.width) - cap * 2;
        int modules = Math.max(0, inner / module);
        int spare = inner - modules * module;
        int fillLeft = spare / 2;
        int fillRight = spare - fillLeft;
        float top = r.y + r.height;
        float y = top - h;
        float x = r.x;

        game.batch.draw(skin.cap, x, y, cap, h);
        x += cap;
        if (fillLeft > 0) game.batch.draw(skin.fill, x, y, fillLeft, h);
        x += fillLeft;
        for (int i = 0; i < modules; i++) {
            game.batch.draw(skin.module, x, y, module, h);
            x += module;
        }
        if (fillRight > 0) game.batch.draw(skin.fill, x, y, fillRight, h);
        x += fillRight;
        game.batch.draw(capRight, x, y, cap, h);
    }

    private void drawGround(Rectangle r) {
        // Tile 64x126 em 1x: a altura é exatamente FLOOR_Y.
        Texture tile = game.assets.ground;
        float tileW = tile.getWidth();
        float start = Math.max(r.x, (float)Math.floor((worldCamera.position.x - 1100f) / tileW) * tileW);
        float end = Math.min(r.x + r.width, worldCamera.position.x + 1100f);
        for (float x = start; x < end; x += tileW) {
            game.batch.draw(tile, x, Constants.FLOOR_Y - tile.getHeight(), tileW, tile.getHeight());
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
        // O pulso é de brilho, não de escala: o marcador fica sempre em 2x.
        float pulse = 0.82f + 0.18f * MathUtils.sin(enemy.getStateTime() * 5f);
        float markerSize = enemy.weakPointMarkerSize();
        Texture marker = markerSize >= 44f ? game.assets.weakPointBoss
            : markerSize <= 30f ? game.assets.weakPointSmall : game.assets.weakPoint;
        float size = marker.getWidth() * 2f;

        game.batch.setColor(1f, 1f, 1f, pulse);
        for (WeakPoint point : enemy.getWeakPoints()) {
            if (point.hit) continue;
            float wx = point.worldX(enemy.getX(), enemy.getRotation());
            float wy = point.worldY(enemy.getY(), enemy.getRotation());
            game.batch.draw(marker, wx - size / 2f, wy - size / 2f, size, size);
        }
        game.batch.setColor(Color.WHITE);
    }

    private void drawProjectiles() {
        // Tamanhos nativos x escala inteira: 11 px x4 = 44 e 18 px x3 = 54.
        float playerSize = game.assets.playerShot.getWidth() * 4f;
        for (Projectile p : playerProjectiles) {
            game.batch.draw(game.assets.playerShot, p.x - playerSize / 2f, p.y - playerSize / 2f,
                playerSize, playerSize);
        }
        for (Projectile p : enemyProjectiles) {
            Texture texture = p.boss ? game.assets.bossShot : game.assets.enemyShot;
            float size = texture.getWidth() * (p.boss ? 3f : 4f);
            game.batch.draw(texture, p.x - size / 2f, p.y - size / 2f, size, size);
        }
    }

    private void drawEffects() {
        for (EffectBurst effect : effects) {
            Animation<TextureRegion> animation = switch (effect.kind) {
                case HIT -> game.assets.hitBurst;
                case VOID -> game.assets.voidBurst;
                case DASH -> game.assets.dashTrail;
            };
            // O crescimento está desenhado nos frames; o tamanho pedido vira a
            // escala inteira mais próxima do frame nativo.
            TextureRegion frame = animation.getKeyFrame(
                Math.min(0.999f, effect.time / effect.duration) * animation.getAnimationDuration());
            float scale = Math.max(1, Math.round(effect.size / frame.getRegionWidth()));
            float w = frame.getRegionWidth() * scale;
            float h = frame.getRegionHeight() * scale;
            game.batch.draw(frame, effect.x - w / 2f, effect.y - h / 2f, w, h);
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
        float orb = game.assets.lifeOrbSmall.getWidth() * 2f;
        for (int i = 0; i < player.getLives(); i++)
            game.batch.draw(game.assets.lifeOrbSmall, 369f + i * 36f, 974f, orb, orb);
    }

    private void drawEnemyHud() {
        GeoEnemy active = firstActiveEnemy();
        if (active == null) return;

        float x = 600f;
        game.ui.dangerPanel(x, 900f, 720f, 155f);
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
        game.ui.hudPanel(x, y, width, height);
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
            case 0 -> {
                paused = false;
                guardResumeInput();
            }
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
