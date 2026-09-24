package com.delmartec.projectorb.entities;

import com.badlogic.gdx.math.Rectangle;
import com.delmartec.projectorb.utils.Constants;

public class Projectile {
    public float x, y, vx, vy;
    public final float radius;
    public final boolean enemy;
    public final int damage;
    public final boolean boss;
    public float life = 3.5f;

    // Reutilizado: bounds() era chamado por projétil por frame em dois laços
    // diferentes e alocava um Rectangle novo a cada vez.
    private final Rectangle bounds = new Rectangle();

    public Projectile(float x, float y, float vx, float vy, float radius, boolean enemy, int damage, boolean boss) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.radius = radius; this.enemy = enemy; this.damage = damage; this.boss = boss;
    }

    public void update(float delta) {
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
