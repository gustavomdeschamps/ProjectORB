package com.delmartec.projectorb.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.delmartec.projectorb.entities.EnemyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central de assets do Project ORB.
 *
 * Toda a arte vem de UM diretório: assets/sprites/, gerado por
 * tools/build_orb_assets.py a partir da arte oficial em
 * assets/ProjetoFinal_TCC/. Os caminhos abaixo são os caminhos reais dos
 * arquivos — não há remapeamento. A arte antiga (processed/,
 * processed_remade/, ...) é só backup e não é lida.
 *
 * As animações são lidas por prefixo (nome_01.png, nome_02.png, ...) até o
 * primeiro arquivo ausente, então a contagem de frames nunca diverge do que o
 * gerador escreveu.
 */
public class Assets {
    private static final String ROOT = "sprites/";

    private final List<Texture> textures = new ArrayList<>();
    private final Map<String, Texture> byPath = new HashMap<>();

    // PLAYER
    public final Animation<TextureRegion> orbIdle;
    public final Animation<TextureRegion> orbWalk;
    public final Animation<TextureRegion> orbJump;
    public final Animation<TextureRegion> orbDash;
    public final Animation<TextureRegion> orbAttack;
    public final Animation<TextureRegion> orbHurt;
    public final Animation<TextureRegion> orbDeath;

    // INIMIGOS (idle, move, attack, hurt, death por tipo)
    private final Map<EnemyType, Animation<TextureRegion>[]> enemyAnims = new HashMap<>();

    // BOSS
    public final Animation<TextureRegion> bossIdle;
    public final Animation<TextureRegion> bossPowerUp;
    public final Animation<TextureRegion> bossOrbs;
    public final Animation<TextureRegion> bossBeam;
    public final Animation<TextureRegion> bossEnraged;
    public final Animation<TextureRegion> bossDeath;

    // EFEITOS / MIRA (desenhados a PIXEL_SCALE como todo o resto)
    public final Texture weakPoint;      // 9 px
    public final Texture weakPointSmall; // 7 px
    public final Texture weakPointBoss;  // 11 px
    public final Texture guideArrow;
    public final Texture crosshair;
    /**
     * Tiros de projetofinal-29 em 8 direções pré-desenhadas (índice = ângulo /
     * 45°, anti-horário a partir da direita): girar em ângulo livre quebraria
     * a grade de pixel. Primeiro índice: Projectile.STYLE_*.
     */
    public final Texture[][] shots = new Texture[4][8];
    /**
     * Onde fica a "cabeça" de cada tiro no sprite apontando para a direita,
     * em pixels de arte a partir do centro do canvas 32x32 (x para a frente,
     * y para cima). Medido pelo gerador (manifest.json, "shots.head_px"): é
     * esse ponto que coincide com a posição/colisão do projétil.
     */
    public static final float[][] SHOT_HEAD = { { 6.87f, 0.35f }, { 4.0f, 0.28f }, { 5.61f, 0.56f }, { 5.09f, 0.85f } };
    public final Animation<TextureRegion> hitBurst;
    public final Animation<TextureRegion> voidBurst;
    public final Animation<TextureRegion> voidBurstMid;
    public final Animation<TextureRegion> voidBurstBig;
    public final Animation<TextureRegion> dashTrail;

    // MUNDO
    public final Texture ground;
    public final PlatformSkin platform;
    public final PlatformSkin platformAlt;
    public final Texture gate;
    public final Texture portal;

    // FUNDO: camadas de parallax (de trás para frente) e a composição dos menus
    public final Texture[] backgroundLayers;
    public final Texture menuBackground;

    // UI
    public final Texture lifeOrb;
    public final Texture panel;
    public final Texture panelCyan;
    public final Texture panelRed;
    public final Texture pixel;
    public final Texture orbShadow;

    // HUD (A3): ícones em pixel art, desenhados a PIXEL_SCALE
    public final Texture hudOrb;
    public final Texture hudCrystal;
    public final Texture hudDash;
    public final Texture hudBossHex;
    public final Texture hudLampOn;
    public final Texture hudLampOff;
    private final Map<String, Texture> propIcons = new HashMap<>();

    /** Peças de plataforma em 1x: ponta, módulo repetível e coluna lisa. */
    public static final class PlatformSkin {
        public final Texture cap;
        public final Texture module;
        public final Texture fill;

        PlatformSkin(Texture cap, Texture module, Texture fill) {
            this.cap = cap;
            this.module = module;
            this.fill = fill;
        }
    }

    public Assets() {
        orbIdle = animation(0.16f, "player/idle_", Animation.PlayMode.LOOP);
        orbWalk = animation(0.09f, "player/walk_", Animation.PlayMode.LOOP);
        // subida/ápice/descida: o Player escolhe o frame pela velocidade vertical
        orbJump = animation(0.22f, "player/jump_", Animation.PlayMode.NORMAL);
        orbDash = animation(0.034f, "player/dash_", Animation.PlayMode.NORMAL);
        orbAttack = animation(0.07f, "player/attack_", Animation.PlayMode.NORMAL);
        orbHurt = animation(0.09f, "player/hurt_", Animation.PlayMode.NORMAL);
        orbDeath = animation(0.12f, "player/death_", Animation.PlayMode.NORMAL);

        loadEnemy(EnemyType.TRIANGLE, "enemies/triangle/");
        loadEnemy(EnemyType.SQUARE, "enemies/square/");
        loadEnemy(EnemyType.DIAMOND, "enemies/diamond/");
        loadEnemy(EnemyType.HEXAGON, "enemies/hexagon/");

        bossIdle = animation(0.16f, "boss/idle_", Animation.PlayMode.LOOP);
        bossPowerUp = animation(0.09f, "boss/powerup_", Animation.PlayMode.NORMAL);
        // orbs e beam têm a mesma duração (0,5 s): a janela de ataque cobre as duas.
        bossOrbs = animation(0.10f, "boss/orbs_", Animation.PlayMode.NORMAL);
        bossBeam = animation(0.10f, "boss/beam_", Animation.PlayMode.NORMAL);
        bossEnraged = animation(0.12f, "boss/enraged_", Animation.PlayMode.LOOP_PINGPONG);
        bossDeath = animation(0.12f, "boss/death_", Animation.PlayMode.NORMAL);
        enemyAnims.put(EnemyType.BOSS, anims(bossIdle, bossIdle, bossOrbs, bossIdle, bossDeath));

        weakPoint = load("ui/weakpoint.png");
        weakPointSmall = load("ui/weakpoint_small.png");
        weakPointBoss = load("ui/weakpoint_boss.png");
        guideArrow = load("ui/guide_arrow.png");
        crosshair = load("ui/crosshair.png");

        String[] shotRoles = { "player", "enemy", "boss_orb", "boss_volley" };
        for (int role = 0; role < shotRoles.length; role++) {
            for (int dir = 0; dir < 8; dir++) shots[role][dir] = load("fx/shot_" + shotRoles[role] + "_" + dir + ".png");
        }
        hitBurst = animation(1f, "fx/hit_burst_", Animation.PlayMode.NORMAL);
        voidBurst = animation(1f, "fx/void_burst_", Animation.PlayMode.NORMAL);
        voidBurstMid = animation(1f, "fx/void_burst_mid_", Animation.PlayMode.NORMAL);
        voidBurstBig = animation(1f, "fx/void_burst_big_", Animation.PlayMode.NORMAL);
        dashTrail = animation(1f, "fx/dash_trail_", Animation.PlayMode.NORMAL);

        ground = load("world/ground.png");
        platform = new PlatformSkin(load("world/platform_cap.png"),
            load("world/platform_module.png"), load("world/platform_fill.png"));
        platformAlt = new PlatformSkin(load("world/platform_alt_cap.png"),
            load("world/platform_alt_module.png"), load("world/platform_alt_fill.png"));
        gate = load("world/gate.png");
        portal = load("world/portal.png");

        backgroundLayers = new Texture[] {
            load("background/00_ceu.png"),
            load("background/01_nuvem1.png"),
            load("background/02_nuvem2.png"),
            load("background/03_nuvem3.png"),
            load("background/04_nuvem4.png"),
            load("background/05_montanhas.png"),
            load("background/06_estruturas.png"),
            load("background/07_lago.png"),
            load("background/08_chao.png"),
        };
        menuBackground = load("background/menu.png");

        lifeOrb = load("ui/life_orb.png");
        panel = load("ui/panel.png");
        panelCyan = load("ui/panel_cyan.png");
        panelRed = load("ui/panel_red.png");
        pixel = load("ui/pixel.png");
        hudOrb = load("ui/hud_orb.png");
        hudCrystal = load("ui/hud_crystal.png");
        hudDash = load("ui/hud_dash.png");
        hudBossHex = load("ui/hud_boss_hex.png");
        hudLampOn = load("ui/hud_lamp_on.png");
        hudLampOff = load("ui/hud_lamp_off.png");
        for (String name : new String[] { "tri_vertices", "diamond_vertices", "square_sides", "hex_angles",
            "hex_vertices", "hex_sides", "hex_cores", "hex_symmetry" }) {
            propIcons.put(name, load("ui/prop_" + name + ".png"));
        }

        Pixmap shadow = new Pixmap(64, 24, Pixmap.Format.RGBA8888);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 64; x++) {
                float dx = (x - 31.5f) / 31.5f;
                float dy = (y - 11.5f) / 11.5f;
                float alpha = Math.max(0f, 1f - dx * dx - dy * dy) * 0.42f;
                shadow.setColor(0.01f, 0.005f, 0.035f, alpha);
                shadow.drawPixel(x, y);
            }
        }
        orbShadow = new Texture(shadow);
        orbShadow.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        textures.add(orbShadow);
        shadow.dispose();
    }

    private void loadEnemy(EnemyType type, String dir) {
        enemyAnims.put(type, anims(
            animation(0.14f, dir + "idle_", Animation.PlayMode.LOOP),
            animation(0.09f, dir + "move_", Animation.PlayMode.LOOP),
            animation(0.08f, dir + "attack_", Animation.PlayMode.NORMAL),
            animation(0.10f, dir + "hurt_", Animation.PlayMode.NORMAL),
            animation(0.10f, dir + "death_", Animation.PlayMode.NORMAL)));
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static Animation<TextureRegion>[] anims(Animation<TextureRegion>... list) {
        return list;
    }

    // ------------------------------------------------------------------
    // Acesso por tipo: o render e a lógica leem a MESMA animação, então as
    // durações nunca divergem do que aparece na tela.
    // ------------------------------------------------------------------

    /** Ícone da propriedade pedida: forma do inimigo + o que acertar em destaque. */
    public Texture propertyIcon(EnemyType type, com.delmartec.projectorb.entities.GeoEnemy.TargetProperty property) {
        String key = switch (type) {
            case TRIANGLE -> "tri_vertices";
            case DIAMOND -> "diamond_vertices";
            case SQUARE -> "square_sides";
            case HEXAGON -> "hex_angles";
            case BOSS -> switch (property) {
                case SIDES -> "hex_sides";
                case CORES -> "hex_cores";
                case SYMMETRY -> "hex_symmetry";
                default -> "hex_vertices";
            };
        };
        return propIcons.get(key);
    }

    public Animation<TextureRegion> enemyIdle(EnemyType type) { return enemyAnims.get(type)[0]; }
    public Animation<TextureRegion> enemyMove(EnemyType type) { return enemyAnims.get(type)[1]; }
    public Animation<TextureRegion> enemyAttack(EnemyType type) { return enemyAnims.get(type)[2]; }
    public Animation<TextureRegion> enemyHurt(EnemyType type) { return enemyAnims.get(type)[3]; }
    public Animation<TextureRegion> enemyDeath(EnemyType type) { return enemyAnims.get(type)[4]; }

    private Texture load(String relative) {
        String path = ROOT + relative;
        Texture cached = byPath.get(path);
        if (cached != null) return cached;

        Texture texture = new Texture(path);
        // Pixel-perfect obrigatório: nunca interpolar pixel art.
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        textures.add(texture);
        byPath.put(path, texture);
        return texture;
    }

    /** Lê prefixo_01.png, prefixo_02.png, ... até o primeiro arquivo ausente. */
    private Animation<TextureRegion> animation(float frameDuration, String prefix, Animation.PlayMode mode) {
        Array<TextureRegion> frames = new Array<>();
        for (int i = 1; ; i++) {
            String relative = prefix + String.format("%02d", i) + ".png";
            FileHandle file = Gdx.files.internal(ROOT + relative);
            if (!file.exists()) break;
            frames.add(new TextureRegion(load(relative)));
        }
        if (frames.isEmpty()) throw new GdxRuntimeException("Animação sem frames: " + ROOT + prefix + "NN.png");
        Animation<TextureRegion> animation = new Animation<>(frameDuration, frames);
        animation.setPlayMode(mode);
        return animation;
    }

    public void dispose() {
        for (Texture texture : textures) texture.dispose();
        textures.clear();
        byPath.clear();
    }
}
