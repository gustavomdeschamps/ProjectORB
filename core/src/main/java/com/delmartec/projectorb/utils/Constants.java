package com.delmartec.projectorb.utils;

public final class Constants {
    private Constants() {}

    public static final float VIEW_WIDTH = 1920f;
    public static final float VIEW_HEIGHT = 1080f;
    public static final float WORLD_WIDTH = 12000f;
    public static final float WORLD_HEIGHT = 1080f;

    // ---------------------------------------------------------------------
    // ESCALA E ANCORAGEM DOS SPRITES (assets/sprites/, medidos por
    // tools/build_orb_assets.py e gravados em sprites/manifest.json)
    //
    // Toda arte é desenhada em escala INTEIRA do tamanho nativo: fundo 4x,
    // player 4x, inimigos e boss 2x, mundo e UI 1x. Escala fracionária
    // deixaria pixels de tamanhos desiguais.
    //
    // O player vive num canvas 48x48; a linha mais baixa dos pés fica a 4 px
    // da borda. Converter isso em fração da altura desenhada é o que mantém o
    // sprite colado no chão — é a ÚNICA fonte de verdade do alinhamento
    // vertical do player. A dos inimigos fica em EnemyType.
    // ---------------------------------------------------------------------
    public static final int BACKGROUND_SCALE = 4;
    public static final int PLAYER_CANVAS = 48;
    public static final int PLAYER_SCALE = 4;
    public static final float CHAR_BASELINE = 4f / PLAYER_CANVAS;

    public static final float PLAYER_W = PLAYER_CANVAS * PLAYER_SCALE;
    public static final float PLAYER_H = PLAYER_CANVAS * PLAYER_SCALE;

    public static final float PLAYER_HIT_W = 58f;
    public static final float PLAYER_HIT_H = 78f;
    public static final float PLAYER_SPEED = 430f;
    public static final float PLAYER_ACCEL = 2600f;
    public static final float PLAYER_FRICTION = 3000f;
    public static final float GRAVITY = -2250f;
    public static final float JUMP_SPEED = 900f;
    public static final float DASH_SPEED = 1120f;
    public static final float DASH_TIME = 0.17f;
    public static final float DASH_COOLDOWN = 0.65f;
    /** Tolerância para o salto registrado pouco antes de tocar o chão. */
    public static final float JUMP_BUFFER = 0.11f;

    public static final int MAX_HEALTH = 100;
    public static final int START_LIVES = 3;
    public static final float SHOT_SPEED = 1220f;
    public static final float SHOT_COOLDOWN = 0.17f;
    public static final float SHOT_RADIUS = 14f;
    public static final int CONTACT_DAMAGE = 12;

    public static final float FLOOR_Y = 126f;
    public static final float CAMERA_SMOOTH = 7.5f;
}
