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
import com.delmartec.projectorb.utils.PixelViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.dialogue.DialogueBox;
import com.delmartec.projectorb.dialogue.DialogueRunner;
import com.delmartec.projectorb.dialogue.DialogueLine;
import com.delmartec.projectorb.dialogue.DialogueScript;
import com.delmartec.projectorb.entities.Npc;
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
import com.delmartec.projectorb.utils.ButtonPress;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.HeartMeter;
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
    /** Fração da carga do ataque a partir da qual o inimigo telegrafa. */
    private static final float CHARGE_TELL = 0.7f;
    private static final float GLITCH_PERIOD = 3.7f;
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
    private final Viewport worldViewport = new PixelViewport(worldCamera);
    private final Viewport hudViewport = new PixelViewport(hudCamera);
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

    // Fundo em camadas de parallax, repetidas em wrap simples (sem espelhar:
    // o espelhamento punha a mesma estrutura refletida ao lado dela mesma).
    // Cada camada tem período próprio (a largura da textura, escolhida pelo
    // gerador para não ser múltipla de nenhuma outra), então duas camadas
    // nunca alinham a mesma estrutura.
    /** Fator de parallax por camada: céu quase parado ... chão do fundo. */
    // céu, estrelas, montanhas, bruma alta, cristais, ruínas, bruma baixa, fragmentos, frente
    private static final float[] PARALLAX = { 0f, 0.02f, 0.06f, 0.09f, 0.14f, 0.22f, 0.28f, 0.18f, 0.40f };
    /** Deriva própria da bruma e dos fragmentos (px de mundo/s); desligada em movimento reduzido. */
    private static final float[] DRIFT = { 0f, 0f, 0f, 4f, 0f, 0f, 6f, 5f, 0f };
    private static final float TWINKLE_PERIOD = 1.3f;
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
    private final ButtonPress pausePress = new ButtonPress();
    private boolean paused = false;
    private boolean codexOpen = false;
    private boolean portalActive = false;
    private boolean learnedMove;
    private boolean learnedJump;
    private boolean learnedDash;
    private boolean learnedShoot;

    // ---- Tutorial com o Pi (seção 0) --------------------------------------
    /** Linha dos pés do Pi e posição: perto de onde o ORB pousa. */
    private static final float PI_X = 470f;
    /** Alvo de treino: um ponto fraco iluminado flutuando. */
    private static final float TARGET_X = 860f, TARGET_Y = 520f;
    private final Npc pi;
    private final DialogueScript piScript;
    private final TextureRegion piPortrait;
    private boolean tutorialStarted;
    private boolean tutorialDone;
    /** Passo em que o tutorial estava (retoma daqui se o ORB morrer). */
    private int tutorialStep;
    private boolean targetActive;
    private final DialogueRunner.Listener piListener = new DialogueRunner.Listener() {
        @Override public void onLineStart(DialogueScript script, int index, DialogueLine line) {
            tutorialStep = index;
            if ("spawnTarget".equals(line.event)) targetActive = true;
            pi.play(line.anim != null ? line.anim : "talk");
            // apontando: vira para o alvo (ou para o caminho à direita), não para o ORB
            pi.lookAt("point".equals(line.anim) ? (targetActive ? TARGET_X : PI_X + 1000f) : null);
            if ("openGate".equals(line.event)) {
                sectionCleared[0] = true;
                game.audio.weakPoint();
            }
        }

        @Override public void onFinished(DialogueScript script) {
            tutorialDone = true;
            pi.setState("idle");
        }
    };
    private float resumeGuard = 0f;
    private final HeartMeter heartMeter;
    /** Diálogo em andamento (Pi, Octógono). Ver startDialogue(). */
    private final DialogueRunner dialogue = new DialogueRunner();
    private DialogueBox dialogueBox;
    private TextureRegion dialoguePortrait;
    /** O botão do mouse precisa ser solto antes do próximo tiro. */
    private boolean waitShootRelease = false;

    public GameScreen(ProjectOrbGame game) {
        this.game = game;
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
        pi = new Npc("pi", game.assets.npcAnimations("pi", 0.12f,
            new String[] { "idle", "walk", "talk", "point" }, new String[] { "wave", "cheer", "appear" }),
            Constants.PLAYER_CANVAS, 4, "idle", PI_X, Constants.FLOOR_Y);
        piScript = DialogueScript.load(Gdx.files.internal("dialogue/pi_tutorial.json"));
        piPortrait = game.assets.npcPortrait("pi");
        pi.play("appear");
        heartMeter = new HeartMeter(game.assets, game.settings);
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

    /**
     * Começa um roteiro de falas. Durante o diálogo o jogador não leva dano e
     * os controles do jogo só valem nos passos interativos (waitFor).
     */
    public void startDialogue(DialogueScript script, int fromIndex, TextureRegion portrait,
                              DialogueRunner.Listener listener) {
        if (dialogueBox == null) dialogueBox = new DialogueBox(game);
        dialoguePortrait = portrait;
        dialogue.setListener(listener);
        dialogue.start(script, fromIndex);
    }

    /** Evento do jogo para o passo interativo atual (move, jump, dash, shoot...). */
    private void dialogueSignal(String event) {
        if (dialogue.isActive()) dialogue.signal(event);
    }

    private void guardResumeInput() {
        resumeGuard = RESUME_INPUT_GUARD;
        waitShootRelease = true;
    }

    private void update(float delta) {
        gameTime += delta;
        resumeGuard = Math.max(0f, resumeGuard - delta);
        boolean acceptActions = resumeGuard <= 0f;
        if (dialogue.isActive()) {
            dialogue.update(delta);
            dialogueBox.update(delta, dialogue);
            if (!dialogue.isInteractive()) {
                // Fala comum: ESPAÇO/clique avançam a fala e não chegam ao jogo.
                if (acceptActions && (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                    || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                    || Gdx.input.isButtonJustPressed(Input.Buttons.LEFT))) {
                    dialogue.advance();
                    waitShootRelease = true;
                }
                acceptActions = false;
            }
        }
        // Os controles do jogo só valem fora do diálogo ou nos passos interativos.
        player.setControlsLocked(dialogue.isActive() && !dialogue.isInteractive());
        shotCooldown = Math.max(0f, shotCooldown - delta);
        feedbackTimer = Math.max(0f, feedbackTimer - delta);
        shakeTimer = Math.max(0f, shakeTimer - delta);
        dashFxTimer = Math.max(0f, dashFxTimer - delta);
        bannerTimer = Math.max(0f, bannerTimer - delta);

        updateSectionProgress();
        updateGate();
        heartMeter.update(delta, player.getLives());

        player.update(delta, level.getPlatforms(), gateActive ? gateRect : null, acceptActions);

        if (Math.abs(player.getX() - 210f) > 75f) learnedMove = true;
        // "andar" = movimento de verdade (não só estar longe do nascimento)
        if (Math.abs(player.getVx()) > 60f && player.isGrounded()) dialogueSignal("move");
        if (player.consumeJumpEvent()) {
            learnedJump = true;
            dialogueSignal(player.getJumpCount() >= 2 ? "doubleJump" : "jump");
            game.audio.jump();
        }
        if (player.consumeDashEvent()) {
            learnedDash = true;
            dialogueSignal("dash");
            game.audio.dash();
            shake(0.08f, 6f);
        }

        if (player.isDashing() && dashFxTimer <= 0f && !game.settings.isReducedMotion()) {
            effects.add(new EffectBurst(
                player.getX() - (player.isFacingRight() ? 40f : -40f),
                player.getY(), EffectBurst.Kind.DASH, 125f, 0.18f));
            dashFxTimer = 0.045f;
        }

        if (currentSection == 0) pi.update(delta, player.getX());
        updateMouseWorld();
        handleShooting(acceptActions);
        updateEnemies(delta);
        updateProjectiles(delta);
        updateEffects(delta);

        if (player.getY() < -120f && player.getHealth() > 0) {
            // Durante um diálogo o ORB não morre: cair só o traz de volta.
            if (dialogue.isActive()) player.respawn(level.getSection(currentSection).respawnX, 230f);
            else player.forceDeath();
        }
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
        updateTutorial();

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
        dialogueSignal("shoot");
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
                if (!dialogue.isActive() && player.damage(enemy.getContactDamage(), enemy.getX())) {
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
            if (targetActive && hitsTrainingTarget(p)) {
                p.life = 0f;
                targetActive = false;
                crystals++;
                game.audio.weakPoint();
                effects.add(new EffectBurst(TARGET_X, TARGET_Y, EffectBurst.Kind.HIT, 105f, 0.30f));
                dialogueSignal("hitTarget");
                continue;
            }
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
                if (!dialogue.isActive() && player.damage(p.damage, p.x)) {
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

    /**
     * Tutorial do Pi: ele se materializa (appear) e, quando termina, começa o
     * roteiro. Se o diálogo não está ativo e o tutorial não acabou (ex.: o ORB
     * morreu), retoma do passo em que estava.
     */
    private void updateTutorial() {
        if (tutorialDone || currentSection != 0) return;
        if (!tutorialStarted) {
            if (!pi.getState().equals("appear") || pi.isAnimationFinished()) {
                tutorialStarted = true;
                startDialogue(piScript, 0, piPortrait, piListener);
            }
            return;
        }
        if (!dialogue.isActive()) startDialogue(piScript, tutorialStep, piPortrait, piListener);
        // Sem animação própria na fala: fala enquanto escreve, repousa depois.
        DialogueLine line = dialogue.isActive() ? dialogue.line() : null;
        if (line != null && line.anim == null) pi.setState(dialogue.isLineComplete() ? "idle" : "talk");
        if (line != null && "talk".equals(line.anim) && dialogue.isLineComplete()) pi.setState("idle");
    }

    /**
     * Pula o tutorial (portão da seção 0 aberto). Usado pelos testes
     * automatizados e pelo smoke visual, que precisam dos controles livres.
     */
    public void skipTutorial() {
        tutorialStarted = true;
        tutorialDone = true;
        targetActive = false;
        dialogue.stop();
        sectionCleared[0] = true;
        pi.setState("idle");
    }

    private boolean hitsTrainingTarget(Projectile p) {
        // mesmo teste de varredura dos pontos fracos (raio 24 + raio do tiro)
        float r = 24f + p.radius;
        float dx = p.x - p.prevX, dy = p.y - p.prevY;
        float len2 = dx * dx + dy * dy;
        float t = len2 < 1e-6f ? 0f : MathUtils.clamp(((TARGET_X - p.prevX) * dx + (targetY() - p.prevY) * dy) / len2, 0f, 1f);
        float cx = p.prevX + dx * t - TARGET_X, cy = p.prevY + dy * t - targetY();
        return cx * cx + cy * cy <= r * r;
    }

    private float targetY() {
        return TARGET_Y + (game.settings.isReducedMotion() ? 0f : MathUtils.sin(gameTime * 2.4f) * 12f);
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

        // Parallax em 9 camadas na escala única (480x270 -> 1920x1080). O
        // deslocamento é arredondado para múltiplos de PIXEL_SCALE para os
        // pixels da arte ficarem na grade. Levemente rebaixado em brilho para o
        // cenário nunca competir com personagem, pontos fracos e projéteis.
        int step = Constants.PIXEL_SCALE;
        boolean drift = !game.settings.isReducedMotion();
        // A paisagem já nasce com baixo contraste (tools/orb_background.py):
        // sem tinta por cima e sem faixa escura em degradê.
        game.batch.setColor(Color.WHITE);
        for (int layer = 0; layer < game.assets.backgroundLayers.length; layer++) {
            Texture texture = game.assets.backgroundLayers[layer];
            // estrelas piscam alternando dois quadros (paradas em movimento reduzido)
            if (layer == 1 && drift && (int)(gameTime / TWINKLE_PERIOD) % 2 == 1) texture = game.assets.starsTwinkle;
            float period = texture.getWidth() * step;
            float height = texture.getHeight() * step;
            float offset = worldCamera.position.x * PARALLAX[layer] + (drift ? gameTime * DRIFT[layer] : 0f);
            float scroll = Math.round(offset / step) * step;
            float first = (float)Math.floor(scroll / period) * period - scroll;
            for (float x = first; x < Constants.VIEW_WIDTH; x += period) {
                game.batch.draw(texture, x, 0f, period, height);
            }
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
            // gate.png x PIXEL_SCALE tem exatamente a largura da colisão (88 px).
            game.batch.draw(game.assets.gate, gateRect.x, Constants.FLOOR_Y,
                game.assets.gate.getWidth() * Constants.PIXEL_SCALE,
                game.assets.gate.getHeight() * Constants.PIXEL_SCALE);
        }

        if (portalActive) {
            float pulse = 0.88f + 0.12f * MathUtils.sin(gameTime * 4f);
            game.batch.setColor(1f, 1f, 1f, pulse);
            float pw = game.assets.portal.getWidth() * Constants.PIXEL_SCALE;
            float ph = game.assets.portal.getHeight() * Constants.PIXEL_SCALE;
            game.batch.draw(game.assets.portal,
                PORTAL_BOUNDS.x + PORTAL_BOUNDS.width / 2f - pw / 2f, Constants.FLOOR_Y, pw, ph);
            game.batch.setColor(Color.WHITE);
        }

        drawEnemies();
        drawProjectiles();
        if (currentSection == 0) pi.draw(game.batch);
        if (targetActive) {
            Texture marker = game.assets.weakPointBoss;
            float size = marker.getWidth() * Constants.PIXEL_SCALE;
            float pulse = 0.8f + 0.2f * MathUtils.sin(gameTime * 6f);
            game.batch.setColor(1f, 1f, 1f, game.settings.isReducedMotion() ? 1f : pulse);
            game.batch.draw(marker, TARGET_X - size / 2f, targetY() - size / 2f, size, size);
            game.batch.setColor(Color.WHITE);
        }
        drawPlayer();
        drawEffects();

        boolean onTarget = isAimOnWeakPoint();
        float aimSize = game.assets.crosshair.getWidth() * Constants.PIXEL_SCALE;
        game.batch.setColor(onTarget ? AIM_LOCK : Color.WHITE);
        game.batch.draw(game.assets.crosshair,
            mouseWorld.x - aimSize / 2f, mouseWorld.y - aimSize / 2f, aimSize, aimSize);
        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    private boolean isAimOnWeakPoint() {
        if (targetActive) {
            float dx = mouseWorld.x - TARGET_X, dy = mouseWorld.y - targetY();
            if (dx * dx + dy * dy <= 36f * 36f) return true;
        }
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

        // Plataformas elevadas montadas com peças de pixel art na escala única:
        // ponta + módulos inteiros (caixa + vão) + colunas lisas que absorvem
        // a sobra + ponta espelhada. Nada é esticado fora da escala inteira.
        // A cor alterna por zona só para o mapa não ficar monótono.
        boolean alt = MathUtils.clamp((int)(r.x / 1900f), 0, 5) % 2 == 1;
        Assets.PlatformSkin skin = alt ? game.assets.platformAlt : game.assets.platform;
        TextureRegion capRight = alt ? platformAltCapRight : platformCapRight;
        int px = Constants.PIXEL_SCALE;
        int cap = skin.cap.getWidth() * px;
        int module = skin.module.getWidth() * px;
        int h = skin.module.getHeight() * px;
        int inner = Math.round(r.width / px) * px - cap * 2;
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
        // Tile 16x32 de arte (64x128 de mundo); o topo coincide com FLOOR_Y.
        Texture tile = game.assets.ground;
        float tileW = tile.getWidth() * Constants.PIXEL_SCALE;
        float tileH = tile.getHeight() * Constants.PIXEL_SCALE;
        float start = Math.max(r.x, (float)Math.floor((worldCamera.position.x - 1100f) / tileW) * tileW);
        float end = Math.min(r.x + r.width, worldCamera.position.x + 1100f);
        for (float x = start; x < end; x += tileW) {
            game.batch.draw(tile, x, Constants.FLOOR_Y - tileH, tileW, tileH);
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
            // A arte hostil já tem o próprio flash branco no dano; a antiga é tingida.
            boolean ownFlash = game.assets.enemyCharge(type) != null;
            if (!ownFlash && (enemy.getFlash() > 0f || enemy.isHurt())) game.batch.setColor(1f, 0.66f, 0.88f, alpha);
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
        Animation<TextureRegion> appear = game.assets.enemyAppear(type);
        if (appear != null && !appear.isAnimationFinished(enemy.getStateTime())) {
            return appear.getKeyFrame(enemy.getStateTime());
        }
        // Telegrafia: a partir de 70% da carga os olhos acendem e o corpo treme.
        Animation<TextureRegion> charge = game.assets.enemyCharge(type);
        if (charge != null && enemy.getCharge() >= CHARGE_TELL) {
            return charge.getKeyFrame(game.settings.isReducedMotion() ? 0f : enemy.getStateTime());
        }
        // Glitch ocasional (a cada GLITCH_PERIOD s, defasado por inimigo); não em movimento reduzido.
        Animation<TextureRegion> glitch = game.assets.enemyGlitch(type);
        if (glitch != null && !game.settings.isReducedMotion()) {
            float t = (enemy.getStateTime() + enemy.getX() * 0.001f) % GLITCH_PERIOD;
            if (t < glitch.getAnimationDuration()) return glitch.getKeyFrame(t);
        }
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
        // O pulso é de brilho, não de escala: o marcador fica na escala única.
        float pulse = 0.82f + 0.18f * MathUtils.sin(enemy.getStateTime() * 5f);
        float markerSize = enemy.weakPointMarkerSize();
        Texture marker = markerSize >= 44f ? game.assets.weakPointBoss
            : markerSize <= 30f ? game.assets.weakPointSmall : game.assets.weakPoint;
        float size = marker.getWidth() * Constants.PIXEL_SCALE;

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
        for (Projectile p : playerProjectiles) drawShot(p);
        for (Projectile p : enemyProjectiles) drawShot(p);
    }

    /**
     * Um dos 8 sprites pré-girados (passos de 45°), posicionado para que a
     * cabeça do tiro fique exatamente na posição/colisão do projétil.
     */
    private void drawShot(Projectile p) {
        float angle = MathUtils.atan2(p.vy, p.vx);
        int dir = Math.floorMod(Math.round(angle / (MathUtils.PI / 4f)), 8);
        Texture texture = game.assets.shots[p.style][dir];
        float snapped = dir * MathUtils.PI / 4f;
        float[] head = Assets.SHOT_HEAD[p.style];
        float px = Constants.PIXEL_SCALE;
        float hx = (head[0] * MathUtils.cos(snapped) - head[1] * MathUtils.sin(snapped)) * px;
        float hy = (head[0] * MathUtils.sin(snapped) + head[1] * MathUtils.cos(snapped)) * px;
        float w = texture.getWidth() * px;
        float h = texture.getHeight() * px;
        game.batch.draw(texture, p.x - hx - w / 2f, p.y - hy - h / 2f, w, h);
    }

    private void drawEffects() {
        for (EffectBurst effect : effects) {
            Animation<TextureRegion> animation = switch (effect.kind) {
                case HIT -> game.assets.hitBurst;
                case VOID -> effect.size >= 250f ? game.assets.voidBurstBig
                    : effect.size >= 150f ? game.assets.voidBurstMid : game.assets.voidBurst;
                case DASH -> game.assets.dashTrail;
            };
            // O crescimento está desenhado nos frames; o tamanho pedido escolhe
            // a variante (normal/média/grande), sempre na escala única.
            TextureRegion frame = animation.getKeyFrame(
                Math.min(0.999f, effect.time / effect.duration) * animation.getAnimationDuration());
            float w = frame.getRegionWidth() * Constants.PIXEL_SCALE;
            float h = frame.getRegionHeight() * Constants.PIXEL_SCALE;
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

        // Cantos diferentes para coisas diferentes: ORB em cima à esquerda,
        // alvo/chefe em cima à direita, cristais embaixo à direita, seção e
        // dicas embaixo à esquerda. Sem caixas, degradês ou brilho.
        drawPlayerHud();
        drawTargetHud();
        drawCrystalHud();
        drawBannerAndHints();
        if (dialogue.isActive()) dialogueBox.draw(game.batch, dialogue, dialoguePortrait);

        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    // ---- HUD em pixels: tudo alinhado à grade de PIXEL_SCALE -------------

    private static final int PX = Constants.PIXEL_SCALE;
    private static final Color CODEX_LOCKED_TITLE = new Color(0.42f, 0.44f, 0.55f, 1f);
    private static final Color CODEX_LOCKED_TEXT = new Color(0.36f, 0.38f, 0.48f, 1f);
    private static final Color HUD_ON = new Color(0.96f, 0.18f, 0.82f, 1f);
    private static final Color HUD_OFF = new Color(0.24f, 0.13f, 0.36f, 1f);
    private static final Color HUD_DASH_ON = new Color(0.32f, 0.85f, 0.92f, 1f);
    private static final Color HUD_DASH_OFF = new Color(0.12f, 0.28f, 0.36f, 1f);
    private static final Color HUD_LABEL = new Color(0.82f, 0.70f, 1f, 1f);
    private static final Color HUD_TEXT = new Color(0.95f, 0.94f, 1f, 1f);
    private static final Color HUD_COUNT = new Color(1f, 0.89f, 0.55f, 1f);
    private static final Color HINT = new Color(0.76f, 0.82f, 1f, 1f);
    private static final Color BANNER_TITLE = new Color(0.96f, 0.94f, 1f, 1f);
    private static final Color BANNER_SUB = new Color(0.48f, 0.88f, 1f, 1f);
    private static final Color FEEDBACK = new Color(1f, 0.72f, 0.94f, 1f);
    private final Color fade = new Color();

    private void drawIcon(Texture texture, float x, float y) {
        game.batch.draw(texture, x, y, texture.getWidth() * PX, texture.getHeight() * PX);
    }

    private void drawPlayerHud() {
        // Rosto do ORB (recorte do idle), corações das vidas ao lado e, embaixo
        // deles, a saúde numa barra reta segmentada. Nada de anéis.
        Texture face = game.assets.hudOrb;
        drawIcon(face, 32f, 936f);
        float hx = 32f + face.getWidth() * PX + 4 * PX;
        heartMeter.draw(game.batch, hx, 972f, Constants.START_LIVES);
        drawSegments(hx, 948f, 10, player.getHealth() / (float)Constants.MAX_HEALTH, HUD_ON, HUD_OFF);

        // Dash: setas ">>" e, embaixo, 3 segmentos que recarregam.
        float dx = hx + HeartMeter.rowWidth(game.assets, Constants.START_LIVES) + 4 * PX;
        float ready = 1f - player.getDashCooldown() / Constants.DASH_COOLDOWN;
        Texture dash = game.assets.hudDash;
        if (ready < 1f) game.batch.setColor(1f, 1f, 1f, 0.45f);
        drawIcon(dash, dx, 980f);
        game.batch.setColor(Color.WHITE);
        drawSegments(dx, 948f, 3, ready, HUD_DASH_ON, HUD_DASH_OFF);
    }

    /** Barra reta de 'count' segmentos (3x2 pixels de arte, 1 de vão), 'filled' acesa. */
    private void drawSegments(float x, float y, int count, float filled, Color on, Color off) {
        int lit = MathUtils.ceil(count * MathUtils.clamp(filled, 0f, 1f) - 0.001f);
        for (int k = 0; k < count; k++) {
            game.batch.setColor(k < lit ? on : off);
            game.batch.draw(game.assets.pixel, x + k * 4 * PX, y, 3 * PX, 2 * PX);
        }
        game.batch.setColor(Color.WHITE);
    }

    private void drawTargetHud() {
        GeoEnemy active = firstActiveEnemy();
        if (active == null) return;
        float right = 1888f;
        GeoEnemy.TargetProperty property = active.getTargetProperty();

        if (active.getType().isBoss()) {
            // O chefe vira o próprio polígono: uma lâmpada por rodada, que se
            // apaga quando a rodada é resolvida.
            Texture hex = game.assets.hudBossHex;
            float size = hex.getWidth() * PX;
            float hx = right - size, hy = 1048f - size;
            drawIcon(hex, hx, hy);
            float ccx = hx + size / 2f, ccy = hy + size / 2f;
            int remaining = active.getTotalRounds() - active.getCompletedRounds();
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(90 + 60 * i);
                float ox = Math.round(13 * Math.cos(a)) * PX, oy = Math.round(13 * Math.sin(a)) * PX;
                Texture lamp = i < remaining ? game.assets.hudLampOn : game.assets.hudLampOff;
                drawIcon(lamp, ccx + ox - lamp.getWidth() * PX / 2f + PX / 2f, ccy + oy - lamp.getHeight() * PX / 2f + PX / 2f);
            }
            right = hx - 16f;
        }

        Texture icon = game.assets.propertyIcon(active.getType(), property);
        float isz = icon.getWidth() * PX;
        drawIcon(icon, right - isz, 1048f - isz);
        float textRight = right - isz - 16f;
        String name = active.getType().displayName();
        String count = active.getRemainingWeakPoints() + "/" + active.getWeakPointCount();
        game.ui.text(name, textRight - game.ui.textWidth(name, UiRenderer.TEXT), 1036f, UiRenderer.TEXT, HUD_LABEL, false);
        String line = property.label + "  " + count;
        float lw = game.ui.textWidth(line, UiRenderer.TEXT);
        game.ui.text(property.label, textRight - lw, 1000f, UiRenderer.TEXT, HUD_TEXT, false);
        game.ui.text(count, textRight - game.ui.textWidth(count, UiRenderer.TEXT), 1000f, UiRenderer.TEXT, HUD_COUNT, false);

        // Carga do ataque inimigo em 8 pips (telegrafia), não em barra.
        float charge = active.isAttacking() ? 1f : Math.min(1f, active.getCharge());
        int lit = (int)Math.ceil(charge * 8);
        for (int k = 0; k < 8; k++) {
            game.batch.setColor(k < lit ? (charge > 0.75f ? CHARGE_HIGH : CHARGE_LOW) : HUD_OFF);
            game.batch.draw(game.assets.pixel, textRight - (8 - k) * 3 * PX + PX, 956f, 2 * PX, 2 * PX);
        }
        game.batch.setColor(Color.WHITE);

        if (feedbackTimer > 0f) {
            fade.set(FEEDBACK).a = Math.min(1f, feedbackTimer);
            game.ui.text(feedback, 1888f - game.ui.textWidth(feedback, UiRenderer.TEXT), 912f,
                UiRenderer.TEXT, fade, false);
        }
    }

    private void drawCrystalHud() {
        // Um único contador discreto.
        Texture icon = game.assets.hudCrystal;
        float x = 1888f - icon.getWidth() * PX;
        drawIcon(icon, x, 32f);
        String n = Integer.toString(crystals);
        game.ui.text(n, x - 12f - game.ui.textWidth(n, UiRenderer.TEXT), 64f, UiRenderer.TEXT, HUD_TEXT, false);
    }

    private String objectiveText() {
        if (portalActive) return "ATRAVESSE O RIFT";
        if (currentSection == 0) return "ALCANCE A CÂMARA";
        GeoEnemy active = firstActiveEnemy();
        return active == null ? "SIGA PARA A DIREITA" : "RESOLVA A FORMA " + active.getType().displayName();
    }

    private void drawBannerAndHints() {
        // Dica de controle: só a tecla e uma palavra, sobre o piso escuro.
        if (portalActive && playerAtPortal()) {
            float left = game.ui.keyCap("E", 11, 32f + 11 * PX, 24f, Gdx.input.isKeyPressed(Input.Keys.E));
            game.ui.text("PORTAL", left + 11 * PX + 16f, 64f, UiRenderer.TEXT, HINT, false);
        } else if (portalActive) {
            game.ui.text("SIGA PARA O PORTAL", 32f, 52f, UiRenderer.TEXT, HINT, false);
        }

        if (bannerTimer > 0f && !dialogue.isActive()) {
            float alpha = Math.min(1f, bannerTimer);
            Section section = level.getSection(currentSection);
            fade.set(BANNER_TITLE).a = alpha;
            game.ui.text(section.title, 32f, 300f, UiRenderer.TITLE, fade, false);
            fade.set(BANNER_SUB).a = alpha;
            game.ui.text(section.subtitle, 32f, 248f, UiRenderer.TEXT, fade, false);
        }
        // Sem inimigo ativo (seção resolvida, tutorial), o recado vai para
        // o canto das mensagens de progresso, acima do banner.
        if (feedbackTimer > 0f && firstActiveEnemy() == null) {
            fade.set(FEEDBACK).a = Math.min(1f, feedbackTimer);
            game.ui.text(feedback, 32f, 360f, UiRenderer.TEXT, fade, false);
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
        // O botão afunda e a ação acontece quando ele volta (ButtonPress).
        switch (pausePress.update(Gdx.graphics.getDeltaTime())) {
            case 0 -> {
                paused = false;
                guardResumeInput();
                return false;
            }
            case 1 -> { game.startGame(); return true; }
            case 2 -> { game.setScreen(new OptionsScreen(game, this)); return true; }
            case 3 -> { game.showMenu(); return true; }
            default -> { }
        }
        if (pausePress.isBusy()) return false;
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
        if (activate) pausePress.press(pauseSelected, game.settings);
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
            game.ui.text(unlocked ? title : "???", 470f, y - 5f, UiRenderer.TEXT,
                unlocked ? UiRenderer.MAGENTA : CODEX_LOCKED_TITLE, false);
            game.ui.text(unlocked ? fact : "Encontre esta forma para registrar.", 470f, y - 41f,
                UiRenderer.TEXT, unlocked ? Color.WHITE : CODEX_LOCKED_TEXT, false);
            y -= 105f;
        }
        // Só as teclas e uma palavra.
        float kx = game.ui.keyCap("ESC", 17, 1040f, 140f, Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) - 12f;
        kx = game.ui.keyCap("TAB", 17, kx, 140f, Gdx.input.isKeyPressed(Input.Keys.TAB)) - 12f;
        game.ui.keyCap("C", 11, kx, 140f, Gdx.input.isKeyPressed(Input.Keys.C));
        game.ui.text("VOLTAR", 1064f, 180f, UiRenderer.TEXT, UiRenderer.SOFT_TEXT, false);
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
        game.ui.textCentered("PAUSADO", 960f, 860f, 2.8f, Color.WHITE);
        game.ui.textCentered(level.getSection(currentSection).title + "  -  " + objectiveText(),
            960f, 735f, 0.82f, UiRenderer.CYAN);
        float hw = HeartMeter.rowWidth(game.assets, Constants.START_LIVES);
        HeartMeter.drawStatic(game.batch, game.assets, 960f - hw / 2f, 620f, player.getLives(), Constants.START_LIVES);
        game.ui.button(pauseButtons[0], "CONTINUAR", pauseSelected == 0, true, UiRenderer.MAGENTA, pausePress.isPressed(0));
        game.ui.button(pauseButtons[1], "REINICIAR", pauseSelected == 1, true, UiRenderer.CYAN, pausePress.isPressed(1));
        game.ui.button(pauseButtons[2], "OPÇÕES", pauseSelected == 2, true, UiRenderer.CYAN, pausePress.isPressed(2));
        game.ui.button(pauseButtons[3], "SAIR PARA O MENU", pauseSelected == 3, true, UiRenderer.CYAN, pausePress.isPressed(3));

        game.batch.end();
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, true);
        hudViewport.update(width, height, true);
    }
}
