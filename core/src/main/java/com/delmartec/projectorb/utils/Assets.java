package com.delmartec.projectorb.utils;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.delmartec.projectorb.entities.EnemyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central de assets do Project ORB.
 *
 * Os sprites ativos vêm de processed_remade/, preservando processed/ como backup.
 * e normalizados a partir do pacote FINAL. As quatro formas da direção visual
 * nova (triângulo, quadrado, losango, hexágono) substituíram por completo o
 * círculo e o pentágono do protótipo V2 — aqueles dois sprites tinham fundo
 * escuro opaco e destoavam do resto.
 */
public class Assets {
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

    // INIMIGOS
    public final Animation<TextureRegion> triangleIdle;
    public final Animation<TextureRegion> triangleWalk;
    public final Animation<TextureRegion> triangleAttack;
    public final Animation<TextureRegion> triangleHurt;
    public final Animation<TextureRegion> triangleDeath;
    public final Animation<TextureRegion> squareIdle;
    public final Animation<TextureRegion> squareWalk;
    public final Animation<TextureRegion> squareAttack;
    public final Animation<TextureRegion> squareHurt;
    public final Animation<TextureRegion> squareDeath;
    public final Animation<TextureRegion> diamondIdle;
    public final Animation<TextureRegion> diamondHover;
    public final Animation<TextureRegion> diamondAttack;
    public final Animation<TextureRegion> diamondHurt;
    public final Animation<TextureRegion> diamondDeath;
    public final Animation<TextureRegion> hexagonIdle;
    public final Animation<TextureRegion> hexagonWalk;
    public final Animation<TextureRegion> hexagonAttack;
    public final Animation<TextureRegion> hexagonHurt;
    public final Animation<TextureRegion> hexagonDeath;

    // BOSS
    public final Animation<TextureRegion> bossIdle;
    public final Animation<TextureRegion> bossPowerUp;
    public final Animation<TextureRegion> bossOrbs;
    public final Animation<TextureRegion> bossBeam;
    public final Animation<TextureRegion> bossEnraged;
    public final Animation<TextureRegion> bossDeath;

    // EFEITOS / MIRA
    public final Texture weakPoint;
    public final Texture guideArrow;
    public final Texture crosshair;
    public final Texture playerShot;
    public final Texture enemyShot;
    public final Texture bossShot;
    public final Texture hitBurst;
    public final Texture dashTrail;
    public final Texture voidBurst;

    // MUNDO
    public final Texture groundGrass;
    public final Texture groundGrassAlt;
    public final Texture floatingPlatformMagenta;
    public final Texture floatingPlatformBlue;
    public final Texture floatingPlatformGreen;
    public final Texture crystalsMagenta;
    public final Texture crystalsBlue;
    public final Texture flowers;
    public final Texture grassRocks;
    public final Texture pillarMagenta;
    public final Texture pillarBlue;
    public final Texture ruinChunk;
    public final Texture pillarRuined;
    public final Texture archMagenta;
    public final Texture archStone;
    public final Texture bannerMagenta;
    public final Texture bannerBlue;
    public final Texture checkpoint;
    public final Texture portal;
    public final Texture crate;
    public final Texture barrel;
    public final Texture sign;
    public final Texture rocksLarge;
    public final Texture crystalPedestal;
    public final Texture doorBlue;
    public final Texture crystalRock;

    // BACKGROUND
    public final Texture backgroundRuins;

    // UI
    public final Texture lifeOrb;
    public final Texture menuFrame;
    public final Texture buttonFrame;
    public final Texture hudFrame;
    public final Texture bossBarFrame;
    public final Texture pixel;
    public final Texture orbShadow;

    public Assets() {
        orbIdle = animation(0.18f, "processed/player/orb/idle/idle_", 4, Animation.PlayMode.LOOP);
        orbWalk = animation(0.09f, "processed/player/orb/walk/walk_", 6, Animation.PlayMode.LOOP);
        // O pacote final não possui uma linha JUMP. Para o ar reaproveitamos um
        // par de frames de idle, que lê como "flutuando" — os frames de walk
        // faziam o ORB parecer estar caminhando no vazio.
        orbJump = animationFromPaths(0.22f, Animation.PlayMode.LOOP_PINGPONG,
            "processed/player/orb/idle/idle_02.png",
            "processed/player/orb/idle/idle_04.png");
        orbDash = animation(0.034f, "processed/player/orb/dash/dash_", 5, Animation.PlayMode.NORMAL);
        orbAttack = animation(0.07f, "processed/player/orb/attack/attack_", 4, Animation.PlayMode.NORMAL);
        orbHurt = animation(0.09f, "processed/player/orb/hurt/hurt_", 4, Animation.PlayMode.NORMAL);
        orbDeath = animation(0.12f, "processed/player/orb/death/death_", 5, Animation.PlayMode.NORMAL);

        triangleIdle = animation(0.18f, "processed/enemies/triangle/idle/idle_", 4, Animation.PlayMode.LOOP);
        triangleWalk = animation(0.10f, "processed/enemies/triangle/walk/walk_", 6, Animation.PlayMode.LOOP);
        triangleAttack = animation(0.08f, "processed/enemies/triangle/attack/attack_", 6, Animation.PlayMode.NORMAL);
        triangleHurt = animation(0.10f, "processed/enemies/triangle/hurt/hurt_", 4, Animation.PlayMode.NORMAL);
        triangleDeath = animation(0.12f, "processed/enemies/triangle/death/death_", 5, Animation.PlayMode.NORMAL);

        squareIdle = animation(0.18f, "processed/enemies/square/idle/idle_", 4, Animation.PlayMode.LOOP);
        squareWalk = animation(0.12f, "processed/enemies/square/walk/walk_", 6, Animation.PlayMode.LOOP);
        squareAttack = animation(0.09f, "processed/enemies/square/attack/attack_", 5, Animation.PlayMode.NORMAL);
        squareHurt = animation(0.10f, "processed/enemies/square/hurt/hurt_", 4, Animation.PlayMode.NORMAL);
        squareDeath = animation(0.12f, "processed/enemies/square/death/death_", 5, Animation.PlayMode.NORMAL);

        diamondIdle = animation(0.18f, "processed/enemies/diamond/idle/idle_", 4, Animation.PlayMode.LOOP);
        diamondHover = animation(0.11f, "processed/enemies/diamond/hover/hover_", 7, Animation.PlayMode.LOOP_PINGPONG);
        diamondAttack = animation(0.09f, "processed/enemies/diamond/attack/attack_", 6, Animation.PlayMode.NORMAL);
        diamondHurt = animation(0.10f, "processed/enemies/diamond/hurt/hurt_", 5, Animation.PlayMode.NORMAL);
        diamondDeath = animation(0.12f, "processed/enemies/diamond/death/death_", 6, Animation.PlayMode.NORMAL);

        hexagonIdle = animation(0.18f, "processed/enemies/hexagon/idle/idle_", 4, Animation.PlayMode.LOOP);
        hexagonWalk = animation(0.11f, "processed/enemies/hexagon/walk/walk_", 6, Animation.PlayMode.LOOP);
        hexagonAttack = animation(0.09f, "processed/enemies/hexagon/attack/attack_", 5, Animation.PlayMode.NORMAL);
        hexagonHurt = animation(0.10f, "processed/enemies/hexagon/hurt/hurt_", 4, Animation.PlayMode.NORMAL);
        hexagonDeath = animation(0.12f, "processed/enemies/hexagon/death/death_", 5, Animation.PlayMode.NORMAL);

        bossIdle = animation(0.16f, "processed/boss/idle/idle_", 5, Animation.PlayMode.LOOP);
        bossPowerUp = animation(0.11f, "processed/boss/powerup/powerup_", 5, Animation.PlayMode.NORMAL);
        bossOrbs = animation(0.10f, "processed/boss/orbs/orbs_", 5, Animation.PlayMode.NORMAL);
        bossBeam = animation(0.10f, "processed/boss/beam/beam_", 5, Animation.PlayMode.NORMAL);
        bossEnraged = animation(0.12f, "processed/boss/enraged/enraged_", 3, Animation.PlayMode.LOOP_PINGPONG);
        // Só três frames: com 0.14 a destruição final durava 0.42 s e passava
        // quase despercebida.
        bossDeath = animation(0.24f, "processed/boss/death/death_", 3, Animation.PlayMode.NORMAL);

        weakPoint = load("processed/ui/weakpoint_marker.png");
        guideArrow = load("processed/ui/guide_arrow.png");
        crosshair = load("processed/ui/crosshair.png");

        playerShot = load("effects/player_shot.png");
        enemyShot = load("effects/enemy_shot.png");
        bossShot = load("effects/boss_shot.png");
        hitBurst = load("effects/hit_burst.png");
        dashTrail = load("effects/dash_trail.png");
        voidBurst = load("effects/void_burst.png");

        groundGrass = load("processed/world/tiles/ground_grass.png");
        groundGrassAlt = load("processed/world/tiles/ground_grass_alt.png");
        floatingPlatformMagenta = load("processed/world/tiles/float_platform_magenta.png");
        floatingPlatformBlue = load("processed/world/tiles/float_platform_blue.png");
        floatingPlatformGreen = load("processed/world/tiles/float_platform_green.png");
        crystalsMagenta = load("processed/world/decor/crystals_magenta.png");
        crystalsBlue = load("processed/world/decor/crystals_blue.png");
        flowers = load("processed/world/decor/flowers.png");
        grassRocks = load("processed/world/decor/grass_rocks.png");
        pillarMagenta = load("processed/world/decor/pillar_magenta.png");
        pillarBlue = load("processed/world/decor/pillar_blue.png");
        ruinChunk = load("processed/world/decor/ruin_chunk.png");
        pillarRuined = load("processed/world/decor/pillar_ruined.png");
        archMagenta = load("processed/world/decor/arch_magenta.png");
        archStone = load("processed/world/decor/arch_stone.png");
        bannerMagenta = load("processed/world/decor/banner_magenta.png");
        bannerBlue = load("processed/world/decor/banner_blue.png");
        checkpoint = load("processed/world/props/checkpoint.png");
        portal = load("processed/world/props/portal.png");
        crate = load("processed/world/props/crate.png");
        barrel = load("processed/world/props/barrel.png");
        sign = load("processed/world/props/sign.png");
        rocksLarge = load("processed/world/decor/rocks_large.png");
        crystalPedestal = load("processed/world/props/crystal_pedestal.png");
        doorBlue = load("processed/world/props/door_blue.png");
        crystalRock = load("processed/world/decor/crystal_rock.png");

        backgroundRuins = load("processed/background/ruins_gameplay.png");

        lifeOrb = load("ui/life_orb.png");
        menuFrame = load("processed_remade/ui/menu_frame.png");
        buttonFrame = load("processed_remade/ui/button_frame.png");
        hudFrame = load("processed_remade/ui/hud_frame.png");
        bossBarFrame = load("processed_remade/ui/boss_bar_frame.png");
        pixel = load("ui/pixel.png");
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

    // ------------------------------------------------------------------
    // Acesso por tipo: o render e a lógica leem a MESMA animação, então as
    // durações nunca divergem do que aparece na tela.
    // ------------------------------------------------------------------

    public Animation<TextureRegion> enemyIdle(EnemyType type) {
        return switch (type) {
            case TRIANGLE -> triangleIdle;
            case SQUARE -> squareIdle;
            case DIAMOND -> diamondIdle;
            case HEXAGON -> hexagonIdle;
            case BOSS -> bossIdle;
        };
    }

    public Animation<TextureRegion> enemyMove(EnemyType type) {
        return switch (type) {
            case TRIANGLE -> triangleWalk;
            case SQUARE -> squareWalk;
            case DIAMOND -> diamondHover;
            case HEXAGON -> hexagonWalk;
            case BOSS -> bossIdle;
        };
    }

    public Animation<TextureRegion> enemyAttack(EnemyType type) {
        return switch (type) {
            case TRIANGLE -> triangleAttack;
            case SQUARE -> squareAttack;
            case DIAMOND -> diamondAttack;
            case HEXAGON -> hexagonAttack;
            case BOSS -> bossOrbs;
        };
    }

    public Animation<TextureRegion> enemyHurt(EnemyType type) {
        return switch (type) {
            case TRIANGLE -> triangleHurt;
            case SQUARE -> squareHurt;
            case DIAMOND -> diamondHurt;
            case HEXAGON -> hexagonHurt;
            case BOSS -> bossIdle;
        };
    }

    public Animation<TextureRegion> enemyDeath(EnemyType type) {
        return switch (type) {
            case TRIANGLE -> triangleDeath;
            case SQUARE -> squareDeath;
            case DIAMOND -> diamondDeath;
            case HEXAGON -> hexagonDeath;
            case BOSS -> bossDeath;
        };
    }

    private Texture load(String path) {
        if (path.startsWith("processed/")) {
            path = "processed_remade/" + path.substring("processed/".length());
        } else if (path.startsWith("effects/")) {
            path = "processed_remade/" + path;
        } else if (path.equals("ui/life_orb.png")) {
            path = "processed_remade/ui/life_orb.png";
        }
        // Cache por caminho: orbJump reaproveita frames do idle e, sem isso, o
        // mesmo PNG virava duas texturas de GPU independentes.
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

    private Animation<TextureRegion> animation(float frameDuration, String prefix, int count, Animation.PlayMode mode) {
        Array<TextureRegion> frames = new Array<>();
        for (int i = 1; i <= count; i++) {
            String path = prefix + String.format("%02d", i) + ".png";
            frames.add(new TextureRegion(load(path)));
        }
        Animation<TextureRegion> animation = new Animation<>(frameDuration, frames);
        animation.setPlayMode(mode);
        return animation;
    }

    private Animation<TextureRegion> animationFromPaths(float frameDuration, Animation.PlayMode mode, String... paths) {
        Array<TextureRegion> frames = new Array<>();
        for (String path : paths) frames.add(new TextureRegion(load(path)));
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
