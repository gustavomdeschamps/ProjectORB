package com.delmartec.projectorb.utils;

public final class Constants {
    private Constants() {}

    public static final float VIEW_WIDTH = 1920f;
    public static final float VIEW_HEIGHT = 1080f;
    public static final float WORLD_WIDTH = 12000f;
    public static final float WORLD_HEIGHT = 1080f;

    // ---------------------------------------------------------------------
    // ANCORAGEM DOS SPRITES PROCESSADOS
    //
    // O pacote FINAL foi normalizado com baseline fixa: todo frame de
    // personagem vive em um canvas 224x224 e encosta no chão na linha 209,
    // ou seja, sobra sempre 15 px vazios abaixo do corpo. O boss usa o mesmo
    // critério em 384x384 (linha 359, 25 px de sobra).
    //
    // Converter isso em fração da altura desenhada é o que mantém o sprite
    // colado no chão em qualquer escala. É a ÚNICA fonte de verdade do
    // alinhamento vertical — nada de multiplicadores mágicos espalhados pelo
    // render.
    // ---------------------------------------------------------------------
    public static final float CHAR_BASELINE = 15f / 224f;
    public static final float BOSS_BASELINE = 25f / 384f;

    /** Escala 1:1 com o canvas de origem: evita reamostragem extra da pixel art. */
    public static final float PLAYER_W = 224f;
    public static final float PLAYER_H = 224f;
    /** Escala de combate: formas legíveis sem cobrir plataformas nem alvos. */
    public static final float ENEMY_SIZE = 280f;
    public static final float BOSS_SIZE = 500f;

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
    /** Tolerância para pular logo depois de sair de uma borda. */
    public static final float COYOTE_TIME = 0.11f;
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
