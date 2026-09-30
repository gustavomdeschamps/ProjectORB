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
import com.delmartec.projectorb.level.StairGate;
import com.delmartec.projectorb.utils.Assets;
import com.delmartec.projectorb.utils.Backdrop;
import com.delmartec.projectorb.utils.ButtonPress;
import com.delmartec.projectorb.utils.Callout;
import com.delmartec.projectorb.utils.Constants;
import com.delmartec.projectorb.utils.FloatingText;
import com.delmartec.projectorb.utils.HeartMeter;
import com.delmartec.projectorb.utils.UiRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameScreen extends ScreenAdapter {
    private static final Color CHARGE_LOW = new Color(0.70f, 0.28f, 0.96f, 1f);
    private static final Color CHARGE_HIGH = new Color(1f, 0.16f, 0.36f, 1f);
    /** Mira sobre um alvo: lilás claro (o âmbar fica só nos alvos). */
    private static final Color AIM_LOCK = new Color(0.86f, 0.74f, 1f, 1f);
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
    /** Áreas clicáveis dos itens da pausa (preenchidas ao desenhar). */
    private final Rectangle[] pauseButtons = { new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle() };

    private final List<GeoEnemy> enemies = new ArrayList<>();
    private final List<Projectile> playerProjectiles = new ArrayList<>();
    private final List<Projectile> enemyProjectiles = new ArrayList<>();
    private final List<EffectBurst> effects = new ArrayList<>();
    /** Números que sobem do acerto/erro (rodada 3: no lugar dos textos na tela). */
    private final List<FloatingText> floats = new ArrayList<>();
    /** Texto no meio da tela: só momentos raros e importantes. */
    private final Callout callout = new Callout();
    /** O contador de alvos do HUD pula/acende por um instante quando muda. */
    private float hudCountFlash;
    private int lastRemaining = -1;
    private static final Color FLOAT_GAIN = new Color(0.96f, 0.94f, 1f, 1f);
    private static final Color FLOAT_LOSS = new Color(1f, 0.47f, 0.62f, 1f);

    private final boolean[] sectionSpawned = new boolean[6];
    private final boolean[] sectionCleared = new boolean[6];

    /** A seção atual ainda está fechada (a massa da escada bloqueia a saída). */
    private boolean gateActive;
    /** Escadinhas entre as seções 0-1, 1-2, 2-3, 3-4 e 4-5 (etapa B). */
    private final StairGate[] stairs = new StairGate[5];
    /** Sólidos do frame: plataformas do nível + massas/escadas (reaproveitada). */
    private final List<Platform> collision = new ArrayList<>();

    /** Paisagem em camadas de parallax (céu ... lago com reflexo ... névoa). */
    private final Backdrop backdrop;
    /** A arte da seta aponta para cima; espelhada ela aponta PARA o alvo. */
    private final TextureRegion guideArrowDown;

    private int currentSection = 0;
    private float shotCooldown = 0f;
    private float gameTime = 0f;
    /** Cartão com o nome da seção (entra, fica e sai animado). */
    private float bannerTimer = BANNER_TIME;
    private static final float BANNER_TIME = 2.6f;
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
        backdrop = new Backdrop(game.assets);
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
            56, 5, "idle", PI_X, Constants.FLOOR_Y);   // π decalcado: canvas 56 (tools/pi_rodada3.py)
        piScript = DialogueScript.load(Gdx.files.internal("dialogue/pi_tutorial.json"));
        piPortrait = game.assets.npcPortrait("pi");
        pi.play("appear");
        heartMeter = new HeartMeter(game.assets, game.settings);
        for (int i = 0; i < stairs.length; i++) stairs[i] = StairGate.forSection(level.getSection(i), level.getPlatforms());
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
        shakeTimer = Math.max(0f, shakeTimer - delta);
        dashFxTimer = Math.max(0f, dashFxTimer - delta);
        bannerTimer = Math.max(0f, bannerTimer - delta);

        updateSectionProgress();
        updateGate();
        updateStairs(delta);
        heartMeter.update(delta, player.getLives());

        collision.clear();
        collision.addAll(level.getPlatforms());
        for (StairGate stair : stairs) stair.addCollision(collision);
        player.update(delta, collision, null, acceptActions);

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
            if (dialogue.isActive()) player.respawn(level.getSection(currentSection).respawnX,
                spawnY(level.getSection(currentSection).respawnX));
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
                bannerTimer = BANNER_TIME;
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
            // Sem texto: a escada se rearranjando (som + tremor) já avisa que a
            // passagem abriu; na arena, o portal acende.
            if (currentSection == 5) portalActive = true;
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
        for (int i = 0; i < stairs.length; i++) {
            if (sectionCleared[i]) stairs[i].open();
        }
    }

    /** Avança as escadas: tremor e som ao começar, flash curto ao terminar. */
    private void updateStairs(float delta) {
        boolean still = game.settings.isReducedMotion();
        for (StairGate stair : stairs) {
            stair.update(delta);
            if (stair.consumeStarted()) {
                shake(StairGate.OPEN_TIME, 5f);          // shake() já respeita as opções
                game.audio.stairRumble();
            }
            if (stair.consumeFinished()) {
                shake(0.12f, 10f);
                if (!still) stair.requestFlash();
                game.audio.stairOpened();
            }
            stair.tickFlash(delta);
        }
    }

    /**
     * Altura de renascimento: o X não muda; se ali houver escada aberta, o ORB
     * renasce em pé em cima dela (antes nascia a y=230 no piso).
     */
    private float spawnY(float x) {
        float half = Constants.PLAYER_HIT_W / 2f;
        float top = Constants.FLOOR_Y;
        for (StairGate stair : stairs) top = Math.max(top, stair.topAt(x - half, x + half));
        return Math.max(230f, top + Constants.PLAYER_HIT_H / 2f + 1f);
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

                // Rodada 3: nada de frase na tela. Acerto = o alvo estilhaça,
                // som que sobe de tom a cada alvo, número pequeno subindo e o
                // contador do HUD pulando. Erro = faísca rosa, baque grave,
                // "-25" e tremor curto.
                if (result == GeoEnemy.HitResult.WRONG) {
                    totalWrongHits++;
                    score = Math.max(0, score - 25);
                    effects.add(new EffectBurst(p.x, p.y, EffectBurst.Kind.MISS, 52f, 0.30f));
                    floats.add(new FloatingText(p.x, p.y + 30f, "-25", FLOAT_LOSS));
                    game.audio.miss();
                    shake(0.10f, 8f);
                } else {
                    totalCorrectHits++;
                    crystals++;
                    int gain = result == GeoEnemy.HitResult.WEAK_POINT ? 100
                        : result == GeoEnemy.HitResult.ROUND_COMPLETE ? 300
                        : enemy.getType().isBoss() ? 5000 : 900;
                    score += gain;
                    int broken = enemy.getWeakPointCount() - enemy.getRemainingWeakPoints();
                    game.audio.shatter(0.9f + 0.08f * Math.max(0, broken));
                    effects.add(new EffectBurst(p.x, p.y, EffectBurst.Kind.SHATTER, 172f, 0.36f));
                    floats.add(new FloatingText(p.x, p.y + 36f, "+" + gain, FLOAT_GAIN));
                    shake(0.06f, 5f);

                    if (result == GeoEnemy.HitResult.ROUND_COMPLETE) {
                        game.audio.weakPoint();
                    } else if (result == GeoEnemy.HitResult.DEFEATED) {
                        boolean boss = enemy.getType().isBoss();
                        game.audio.weakPoint();
                        effects.add(new EffectBurst(enemy.getX(), enemy.getY(),
                            EffectBurst.Kind.VOID, boss ? 320f : 180f, boss ? 0.65f : 0.4f));
                        shake(boss ? 0.5f : 0.2f, boss ? 20f : 11f);
                        // o momento mais importante da fase: única frase no meio da tela
                        if (boss) {
                            callout.show("NÚCLEO PARTIDO", UiRenderer.LILAC);
                            game.audio.callout();
                        }
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
        for (StairGate stair : stairs) if (stair.blocks(bounds)) return true;
        return false;
    }

    private void updateEffects(float delta) {
        Iterator<EffectBurst> iterator = effects.iterator();
        while (iterator.hasNext()) {
            EffectBurst effect = iterator.next();
            effect.update(delta);
            if (effect.dead()) iterator.remove();
        }
        for (FloatingText f : floats) f.update(delta);
        floats.removeIf(FloatingText::dead);
        callout.update(delta);
        hudCountFlash = Math.max(0f, hudCountFlash - delta);
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
        if (!player.loseLifeAndRespawn(section.respawnX, spawnY(section.respawnX))) {
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
        } else {
            spawnSection(currentSection, true);
            // O boss volta a existir, então o portal precisa voltar a ficar
            // inativo — senão dava para pular a luta e vencer direto.
            if (currentSection == 5) portalActive = false;
        }
        // Sem texto: o coração do HUD quebra (animação própria) e o ORB
        // renasce piscando.
        effects.clear();
        shake(0.28f, 16f);
    }

    private void shake(float time, float strength) {
        if (!game.settings.isScreenShakeEnabled()) return;
        shakeTimer = Math.max(shakeTimer, time);
        shakeStrength = Math.max(shakeStrength, strength);
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

        // Paisagem na escala única (480x270 -> 1920x1080), levemente rebaixada
        // em brilho para o cenário nunca competir com personagem, pontos fracos
        // e projéteis.
        backdrop.draw(game.batch, worldCamera.position.x, gameTime, game.settings.isReducedMotion(),
            Backdrop.GAME_TINT);

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

        // Escadinhas (etapa B): massa fechada ou escada, no lugar da antiga barra.
        boolean still = game.settings.isReducedMotion();
        float camL = worldCamera.position.x - CULL_MARGIN, camR = worldCamera.position.x + CULL_MARGIN;
        for (StairGate stair : stairs) {
            if (stair.getStairRight() < camL || stair.getStairLeft() > camR) continue;
            stair.draw(game.batch, game.assets.stairClosed, game.assets.stairBlock, game.assets.stairTop,
                game.assets.stairCrackCyan, game.assets.stairCrackMagenta, game.assets.stairChip,
                game.assets.pixel, gameTime, still);
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

        // Laje de pedra (rodada 3) montada com peças na escala única: ponta
        // esquerda + miolos de 16 e 12 px em variantes (escolhidas pela
        // posição da plataforma, sem repetir a vizinha) + ponta direita. Se a
        // largura não fechar com 16/12, colunas lisas absorvem a sobra.
        // A pedra alterna de tom por zona só para o mapa não ficar monótono.
        boolean alt = MathUtils.clamp((int)(r.x / 1900f), 0, 5) % 2 == 1;
        Assets.PlatformSkin skin = alt ? game.assets.platformAlt : game.assets.platform;
        int px = Constants.PIXEL_SCALE;
        int h = skin.fill.getHeight() * px;
        int inner = Math.round(r.width / px) - skin.capLeft.getWidth() - skin.capRight.getWidth();
        int narrow = 0;
        while (narrow * 12 <= inner && (inner - narrow * 12) % 16 != 0) narrow++;
        if (narrow * 12 > inner) narrow = 0;
        int wide = (inner - narrow * 12) / 16;
        int spare = inner - wide * 16 - narrow * 12;
        float y = r.y + r.height - h;
        float x = r.x;
        game.batch.draw(skin.capLeft, x, y, skin.capLeft.getWidth() * px, h);
        x += skin.capLeft.getWidth() * px;
        int seed = hash((int)(r.x / px) * 7 + (int)r.y);
        Texture last = null;
        boolean[] isNarrow = pieceOrder(wide, narrow);
        for (int i = 0; i < isNarrow.length; i++) {
            Texture[] pool = isNarrow[i] ? skin.narrow : skin.wide;
            Texture t = pool[Integer.remainderUnsigned(hash(seed + i), pool.length)];
            if (t == last) t = pool[(java.util.Arrays.asList(pool).indexOf(t) + 1) % pool.length];
            game.batch.draw(t, x, y, t.getWidth() * px, h);
            x += t.getWidth() * px;
            last = t;
        }
        if (spare > 0) {
            game.batch.draw(skin.fill, x, y, spare * px, h);
            x += spare * px;
        }
        game.batch.draw(skin.capRight, x, y, skin.capRight.getWidth() * px, h);
    }

    /** Ordem dos miolos: os estreitos (12 px) espalhados entre os largos (16 px). */
    static boolean[] pieceOrder(int wide, int narrow) {
        int n = wide + narrow;
        boolean[] out = new boolean[n];
        for (int j = 0; j < narrow; j++) out[Math.min(n - 1, (int)((j + 0.5f) * n / narrow))] = true;
        return out;
    }

    /** Hash inteiro simples (mesmo de tools/mundo_rodada3.py: ground_variant). */
    static int hash(int i) {
        int h = i * 0x9E3779B1;
        return h ^ (h >>> 15);
    }

    /** Variante do ladrilho de chão de índice i (0..n-1). */
    static int groundVariant(int i, int n) {
        return Integer.remainderUnsigned(hash(i), n);
    }

    private void drawGround(Rectangle r) {
        // Ladrilhos 32x32 de arte (128x128 de mundo) em 5 variantes que emendam
        // em qualquer ordem; a variante sai do índice do ladrilho (sempre a
        // mesma no mesmo lugar). O topo coincide com FLOOR_Y.
        Texture[] tiles = game.assets.grounds;
        float tileW = tiles[0].getWidth() * Constants.PIXEL_SCALE;
        float tileH = tiles[0].getHeight() * Constants.PIXEL_SCALE;
        float start = Math.max(r.x, (float)Math.floor((worldCamera.position.x - 1100f) / tileW) * tileW);
        float end = Math.min(r.x + r.width, worldCamera.position.x + 1100f);
        for (float x = start; x < end; x += tileW) {
            Texture tile = tiles[groundVariant(Math.round(x / tileW), tiles.length)];
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
                case SHATTER -> game.assets.shatter;
                case MISS -> game.assets.miss;
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
        // números do acerto/erro, subindo do ponto (fonte de pixel, escala única)
        boolean still = game.settings.isReducedMotion();
        for (FloatingText f : floats) {
            if (!f.visible()) continue;
            game.ui.text(f.text, Math.round(f.x / PX) * PX, f.currentY(still), UiRenderer.TEXT, f.color, true);
        }
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
        callout.draw(game.ui, game.settings.isReducedMotion());
        if (dialogue.isActive()) dialogueBox.draw(game.batch, dialogue, dialoguePortrait);

        game.batch.setColor(Color.WHITE);
        game.batch.end();
    }

    // ---- HUD em pixels: tudo alinhado à grade de PIXEL_SCALE -------------

    private static final int PX = Constants.PIXEL_SCALE;
    // Rodada 3: menos neon. Vida em rosa-lilás, dash em azul-petróleo claro,
    // textos em lilás/branco; o âmbar fica só nos alvos de ponto fraco.
    private static final Color HUD_ON = new Color(0.93f, 0.45f, 0.78f, 1f);
    private static final Color HUD_OFF = new Color(0.24f, 0.13f, 0.36f, 1f);
    private static final Color HUD_DASH_ON = new Color(0.45f, 0.84f, 0.90f, 1f);
    private static final Color HUD_DASH_OFF = new Color(0.12f, 0.28f, 0.36f, 1f);
    private static final Color HUD_LABEL = new Color(0.82f, 0.70f, 1f, 1f);
    private static final Color HUD_TEXT = new Color(0.95f, 0.94f, 1f, 1f);
    private static final Color HINT = new Color(0.76f, 0.82f, 1f, 1f);
    private static final Color BANNER_TITLE = new Color(0.96f, 0.94f, 1f, 1f);
    private static final Color OVERLAY = new Color(0.01f, 0.008f, 0.04f, 1f);
    private static final float MENU_LEFT = 112f;

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
        if (active == null) {
            lastRemaining = -1;
            return;
        }
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
        int remaining = active.getRemainingWeakPoints();
        // contador: quando um alvo quebra, pula 1 pixel e acende (o feedback
        // do acerto fica aqui, não numa frase)
        if (lastRemaining >= 0 && remaining != lastRemaining) hudCountFlash = 0.35f;
        lastRemaining = remaining;
        String count = remaining + "/" + active.getWeakPointCount();
        game.ui.text(name, textRight - game.ui.textWidth(name, UiRenderer.TEXT), 1036f, UiRenderer.TEXT, HUD_LABEL, false);
        String line = property.label + "  " + count;
        float lw = game.ui.textWidth(line, UiRenderer.TEXT);
        game.ui.text(property.label, textRight - lw, 1000f, UiRenderer.TEXT, HUD_TEXT, false);
        boolean bump = hudCountFlash > 0f;
        float bumpY = bump && !game.settings.isReducedMotion() ? PX : 0f;
        game.ui.text(count, textRight - game.ui.textWidth(count, UiRenderer.TEXT), 1000f + bumpY, UiRenderer.TEXT,
            bump ? UiRenderer.LILAC : HUD_TEXT, false);

        // Carga do ataque inimigo em 8 pips (telegrafia), não em barra.
        float charge = active.isAttacking() ? 1f : Math.min(1f, active.getCharge());
        int lit = (int)Math.ceil(charge * 8);
        for (int k = 0; k < 8; k++) {
            game.batch.setColor(k < lit ? (charge > 0.75f ? CHARGE_HIGH : CHARGE_LOW) : HUD_OFF);
            game.batch.draw(game.assets.pixel, textRight - (8 - k) * 3 * PX + PX, 956f, 2 * PX, 2 * PX);
        }
        game.batch.setColor(Color.WHITE);
    }

    private void drawCrystalHud() {
        // Um único contador discreto.
        Texture icon = game.assets.hudCrystal;
        float x = 1888f - icon.getWidth() * PX;
        drawIcon(icon, x, 32f);
        String n = Integer.toString(crystals);
        game.ui.text(n, x - 12f - game.ui.textWidth(n, UiRenderer.TEXT), 64f, UiRenderer.TEXT, HUD_TEXT, false);
    }

    private void drawBannerAndHints() {
        // Dica de controle: só a tecla e uma palavra, e só em cima do portal.
        if (portalActive && playerAtPortal()) {
            float left = game.ui.keyCap("E", 11, 32f + 11 * PX, 24f, Gdx.input.isKeyPressed(Input.Keys.E));
            game.ui.text("PORTAL", left + 11 * PX + 16f, 64f, UiRenderer.TEXT, HINT, false);
        }
        if (bannerTimer > 0f && !dialogue.isActive()) drawSectionCard(level.getSection(currentSection).title);
    }

    /**
     * Cartão da seção: só o nome. Entrada: as letras chegam uma a uma
     * deslizando 3 pixels de arte da esquerda e um traço cresce embaixo.
     * Saída: as letras somem da esquerda para a direita e o traço encolhe.
     * Movimento reduzido: aparece e some inteiro.
     */
    private void drawSectionCard(String title) {
        float t = BANNER_TIME - bannerTimer;
        float in = 0.40f, out = 0.35f;
        int n = title.length();
        boolean still = game.settings.isReducedMotion();
        float x = 32f, top = 300f;
        float full = game.ui.textWidth(title, UiRenderer.TITLE);
        float reveal = still ? 1f : MathUtils.clamp(t / in, 0f, 1f);
        float hide = still ? 0f : MathUtils.clamp((t - (BANNER_TIME - out)) / out, 0f, 1f);
        for (int i = 0; i < n; i++) {
            float start = i / (float)n;
            float k = MathUtils.clamp((reveal - start) * n / 2f, 0f, 1f);
            boolean gone = hide * n > i;
            String before = title.substring(0, i);
            float cx = x + game.ui.textWidth(before, UiRenderer.TITLE);
            if (k <= 0f || gone) continue;
            float slide = Math.round((1f - k) * 3f) * -PX;
            game.ui.text(title.substring(i, i + 1), cx + slide, top, UiRenderer.TITLE, BANNER_TITLE, false);
        }
        float line = full * reveal * (1f - hide);
        game.batch.setColor(UiRenderer.LILAC);
        game.batch.draw(game.assets.pixel, x, top - game.ui.capHeight(UiRenderer.TITLE) - 5 * PX,
            Math.round(line / PX) * PX, PX);
        game.batch.setColor(Color.WHITE);
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
        int before = pauseSelected;
        for (int i = 0; i < pauseButtons.length; i++) {
            if (pauseButtons[i].contains(mouseHud)) pauseSelected = i;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            pauseSelected = (pauseSelected + pauseButtons.length - 1) % pauseButtons.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            pauseSelected = (pauseSelected + 1) % pauseButtons.length;
        }
        if (pauseSelected != before) game.audio.uiMove();
        boolean activate = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || (pauseButtons[pauseSelected].contains(mouseHud)
                && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT));
        if (activate) {
            pausePress.press(pauseSelected, game.settings);
            game.audio.uiConfirm();
        }
        return false;
    }

    /** Fatos do códex, sem frase de efeito: nome e o que a forma tem. */
    private static final String[][] CODEX = {
        { "LOSANGO", "4 lados iguais; as diagonais se cruzam a 90°" },
        { "TRIÂNGULO", "3 lados e 3 vértices; os ângulos somam 180°" },
        { "QUADRADO", "4 lados iguais e 4 ângulos retos" },
        { "HEXÁGONO", "6 lados; os ângulos internos somam 720°" },
        { "NÚCLEO", "muda de forma a cada fase; cada uma tem sua simetria" },
    };

    private void renderCodex() {
        hudViewport.apply();
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();
        game.batch.setColor(OVERLAY.r, OVERLAY.g, OVERLAY.b, 0.80f);
        game.batch.draw(game.assets.pixel, 0f, 0f, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);
        game.ui.shade(1180f, 0.55f);

        game.ui.text("CÓDEX", MENU_LEFT, 880f, UiRenderer.TITLE, UiRenderer.TEXT_BRIGHT, false);
        float top = 760f;
        for (int i = 0; i < CODEX.length; i++) {
            int sectionIndex = i + 1;
            boolean unlocked = sectionSpawned[sectionIndex] || sectionCleared[sectionIndex];
            game.ui.text(unlocked ? CODEX[i][0] : "???", MENU_LEFT, top, UiRenderer.TEXT,
                unlocked ? UiRenderer.TEAL : UiRenderer.TEXT_DIM, false);
            game.ui.text(unlocked ? CODEX[i][1] : "ainda não vista", MENU_LEFT, top - 44f, UiRenderer.TEXT,
                unlocked ? UiRenderer.TEXT_BRIGHT : UiRenderer.TEXT_DIM, false);
            top -= 120f;
        }
        // Só as teclas e uma palavra (da direita para a esquerda: a largura
        // de cada tecla depende do rótulo).
        float keysRight = MENU_LEFT + 300f;
        float kx = game.ui.keyCap("ESC", 17, keysRight, 72f, Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) - 12f;
        kx = game.ui.keyCap("TAB", 17, kx, 72f, Gdx.input.isKeyPressed(Input.Keys.TAB)) - 12f;
        game.ui.keyCap("C", 11, kx, 72f, Gdx.input.isKeyPressed(Input.Keys.C));
        game.ui.text("VOLTAR", keysRight + 24f, 112f, UiRenderer.TEXT, UiRenderer.TEXT_DIM, false);
        game.batch.end();
    }

    private void renderPause() {
        hudViewport.apply();
        game.batch.setProjectionMatrix(hudCamera.combined);
        game.batch.begin();

        game.batch.setColor(OVERLAY.r, OVERLAY.g, OVERLAY.b, 0.55f);
        game.batch.draw(game.assets.pixel, 0, 0, Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        game.batch.setColor(Color.WHITE);
        game.ui.shade(704f, 0.70f);

        game.ui.text("PAUSA", MENU_LEFT, 880f, UiRenderer.TITLE_BIG, UiRenderer.TEXT_BRIGHT, false);
        game.ui.text(level.getSection(currentSection).title, MENU_LEFT, 770f, UiRenderer.TEXT, UiRenderer.TEXT_DIM, false);
        HeartMeter.drawStatic(game.batch, game.assets, MENU_LEFT, 680f, player.getLives(), Constants.START_LIVES);
        String[] labels = { "CONTINUAR", "REINICIAR", "OPÇÕES", "SAIR PARA O MENU" };
        float top = 560f;
        for (int i = 0; i < labels.length; i++) {
            game.ui.menuItem(pauseButtons[i], labels[i], MENU_LEFT, top, i == 0 ? UiRenderer.TITLE : UiRenderer.TEXT,
                pauseSelected == i, pausePress.isPressed(i), UiRenderer.LILAC);
            top -= i == 0 ? 120f : 76f;
        }
        game.batch.end();
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, true);
        hudViewport.update(width, height, true);
    }
}
