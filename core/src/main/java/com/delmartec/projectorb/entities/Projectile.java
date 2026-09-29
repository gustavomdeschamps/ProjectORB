package com.delmartec.projectorb.entities;

import com.badlogic.gdx.math.Rectangle;
import com.delmartec.projectorb.utils.Constants;

public class Projectile {
    public float x, y, vx, vy;
    /** Posição no início do último update: o teste de acerto varre prev -> atual. */
    public float prevX, prevY;
    public final float radius;
    public final boolean enemy;
    public final int damage;
    public final boolean boss;
    public float life = 3.5f;
    /** Arte do tiro (só visual): um dos STYLE_*. */
    public int style = STYLE_PLAYER;

    public static final int STYLE_PLAYER = 0;
    public static final int STYLE_ENEMY = 1;
    public static final int STYLE_BOSS_ORB = 2;
    public static final int STYLE_BOSS_VOLLEY = 3;

    // Reutilizado: bounds() era chamado por projétil por frame em dois laços
    // diferentes e alocava um Rectangle novo a cada vez.
    private final Rectangle bounds = new Rectangle();

    public Projectile(float x, float y, float vx, float vy, float radius, boolean enemy, int damage, boolean boss) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.prevX = x; this.prevY = y;
        this.radius = radius; this.enemy = enemy; this.damage = damage; this.boss = boss;
    }

    public void update(float delta) {
        prevX = x;
        prevY = y;
        x += vx * delta;
        y += vy * delta;
        life -= delta;
    }

    public Rectangle bounds() {
        return bounds.set(x - radius, y - radius, radius * 2f, radius * 2f);
    }

    public boolean dead() {
        return life <= 0f
            || x < -200f || x > Constants.WORLD_WIDTH + 200f
            || y < -250f || y > Constants.WORLD_HEIGHT + 270f;
    }
}
