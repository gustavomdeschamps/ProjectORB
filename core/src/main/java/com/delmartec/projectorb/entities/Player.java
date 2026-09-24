package com.delmartec.projectorb.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.delmartec.projectorb.level.Platform;
import com.delmartec.projectorb.utils.Assets;
import com.delmartec.projectorb.utils.Constants;

import java.util.List;

public class Player {
    private enum VisualState { IDLE, WALK, JUMP, DASH, ATTACK, HURT, DEATH }

    private float x;
    private float y;
    private float vx;
    private float vy;
    private int health = Constants.MAX_HEALTH;
    private int lives = Constants.START_LIVES;
    private int jumpCount = 0;
    private boolean grounded = false;
    private boolean facingRight = true;
    private float coyoteTimer = 0f;
    private float jumpBuffer = 0f;
    private float dashTimer = 0f;
    private float dashCooldown = 0f;
    private float invulnerable = 0f;
    private float shootVisual = 0f;
    private float hurtVisual = 0f;
    private float deathVisual = 0f;
    private boolean jumpedThisFrame;
    private boolean dashedThisFrame;

    // Reutilizadas: getBounds() era chamada várias vezes por frame dentro dos
    // laços de colisão e alocava um Rectangle a cada chamada.
    private final Rectangle bounds = new Rectangle();

    private VisualState visualState = VisualState.IDLE;
    private float visualTime = 0f;

    /** Duração real da animação de morte, injetada a partir do Assets. */
    private float deathDuration = 0.60f;

    public Player(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setDeathDuration(float deathDuration) {
        this.deathDuration = deathDuration;
    }

    public void update(float delta, List<Platform> platforms, Rectangle gate) {
        dashCooldown = Math.max(0f, dashCooldown - delta);
        invulnerable = Math.max(0f, invulnerable - delta);
        shootVisual = Math.max(0f, shootVisual - delta);
        hurtVisual = Math.max(0f, hurtVisual - delta);
        coyoteTimer = Math.max(0f, coyoteTimer - delta);
        jumpBuffer = Math.max(0f, jumpBuffer - delta);
        jumpedThisFrame = false;
        dashedThisFrame = false;

        if (health <= 0) {
            deathVisual += delta;
            vx = approach(vx, 0f, Constants.PLAYER_FRICTION * delta);
            setVisualState(VisualState.DEATH, delta);
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) jumpBuffer = Constants.JUMP_BUFFER;

        float move = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) move -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) move += 1f;
        if (move != 0f) facingRight = move > 0f;

        if (dashTimer > 0f) {
            dashTimer -= delta;
            x += vx * delta;
            resolveHorizontal(platforms, gate);
            // O dash antigo congelava o eixo Y por completo: o jogador
            // atravessava o topo das plataformas sem nunca resolver a colisão
            // vertical e o estado "grounded" ficava velho. Agora o dash é
            // horizontal mas continua respeitando o cenário.
            boolean wasGrounded = grounded;
            grounded = false;
            resolveVertical(platforms, gate);
            if (!grounded && wasGrounded) coyoteTimer = Constants.COYOTE_TIME;
            clampToWorld();
            setVisualState(VisualState.DASH, delta);
            return;
        }

        float target = move * Constants.PLAYER_SPEED;
        float change = move == 0f ? Constants.PLAYER_FRICTION : Constants.PLAYER_ACCEL;
        vx = approach(vx, target, change * delta);

        boolean canJump = grounded || coyoteTimer > 0f || jumpCount < 2;
        if (jumpBuffer > 0f && canJump) {
            vy = Constants.JUMP_SPEED;
            grounded = false;
            coyoteTimer = 0f;
            jumpBuffer = 0f;
            jumpCount++;
            jumpedThisFrame = true;
        }

        if ((Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_LEFT) ||
             Gdx.input.isKeyJustPressed(Input.Keys.SHIFT_RIGHT)) && dashCooldown <= 0f) {
            float dir = move != 0f ? Math.signum(move) : (facingRight ? 1f : -1f);
            vx = dir * Constants.DASH_SPEED;
            vy *= 0.25f;
            dashTimer = Constants.DASH_TIME;
            dashCooldown = Constants.DASH_COOLDOWN;
            invulnerable = Math.max(invulnerable, Constants.DASH_TIME + 0.05f);
            dashedThisFrame = true;
            setVisualState(VisualState.DASH, delta);
            return;
        }

        vx = MathUtils.clamp(vx, -Constants.DASH_SPEED, Constants.DASH_SPEED);
        vy += Constants.GRAVITY * delta;
        vy = Math.max(vy, -1350f);

        x += vx * delta;
        resolveHorizontal(platforms, gate);

        boolean wasGrounded = grounded;
        grounded = false;
        y += vy * delta;
        resolveVertical(platforms, gate);

        if (!grounded && wasGrounded && !jumpedThisFrame) {
            // Saiu da borda andando: ganha coyote time e o primeiro salto
            // passa a contar como consumido (antes dava dois saltos no ar).
            coyoteTimer = Constants.COYOTE_TIME;
            jumpCount = 1;
        }

        clampToWorld();

        VisualState desired;
        if (hurtVisual > 0f) desired = VisualState.HURT;
        else if (!grounded) desired = VisualState.JUMP;
        else if (shootVisual > 0f) desired = VisualState.ATTACK;
        else if (Math.abs(vx) > 35f) desired = VisualState.WALK;
        else desired = VisualState.IDLE;
        setVisualState(desired, delta);
    }

    private void clampToWorld() {
        x = MathUtils.clamp(x, Constants.PLAYER_HIT_W / 2f,
            Constants.WORLD_WIDTH - Constants.PLAYER_HIT_W / 2f);
    }

    private void setVisualState(VisualState next, float delta) {
        if (next != visualState) {
            visualState = next;
            visualTime = 0f;
        } else {
            visualTime += delta;
        }
    }

    private void resolveHorizontal(List<Platform> platforms, Rectangle gate) {
        for (Platform p : platforms) resolveHorizontalAgainst(p.bounds);
        if (gate != null) resolveHorizontalAgainst(gate);
    }

    private void resolveHorizontalAgainst(Rectangle r) {
        Rectangle b = getBounds();
        if (!b.overlaps(r)) return;
        float half = Constants.PLAYER_HIT_W / 2f;
        if (vx > 0f) x = r.x - half;
        else if (vx < 0f) x = r.x + r.width + half;
        else {
            // Parado dentro do bloco (empurrado por outra resolução): sai pelo
            // lado mais próximo em vez de ficar preso na quina.
            float leftGap = (b.x + b.width) - r.x;
            float rightGap = (r.x + r.width) - b.x;
            x = leftGap < rightGap ? r.x - half : r.x + r.width + half;
        }
        vx = 0f;
    }

    private void resolveVertical(List<Platform> platforms, Rectangle gate) {
        for (Platform p : platforms) resolveVerticalAgainst(p.bounds);
        if (gate != null) resolveVerticalAgainst(gate);
    }

    private void resolveVerticalAgainst(Rectangle r) {
        Rectangle b = getBounds();
        if (!b.overlaps(r)) return;
        float half = Constants.PLAYER_HIT_H / 2f;
        if (vy <= 0f) {
            y = r.y + r.height + half;
            vy = 0f;
            grounded = true;
            jumpCount = 0;
            coyoteTimer = 0f;
        } else {
            y = r.y - half;
            vy = 0f;
        }
    }

    private float approach(float current, float target, float amount) {
        if (current < target) return Math.min(current + amount, target);
        return Math.max(current - amount, target);
    }

    public boolean damage(int amount) { return damage(amount, x); }

    public boolean damage(int amount, float sourceX) {
        if (health <= 0 || invulnerable > 0f || dashTimer > 0f) return false;
        health = Math.max(0, health - amount);
        invulnerable = 0.75f;
        // Reacao curta e controlavel: comunica a direcao do dano sem tirar o
        // comando do jogador por uma animacao longa.
        vx = sourceX <= x ? 285f : -285f;
        vy = Math.max(vy, 210f);
        if (health <= 0) {
            deathVisual = 0f;
            setVisualState(VisualState.DEATH, 0f);
        } else {
            hurtVisual = 0.36f;
            setVisualState(VisualState.HURT, 0f);
        }
        return true;
    }

    public void forceDeath() {
        if (health <= 0) return;
        health = 0;
        deathVisual = 0f;
        setVisualState(VisualState.DEATH, 0f);
    }

    public boolean isDeathAnimationFinished() {
        return health <= 0 && deathVisual >= deathDuration;
    }

    public boolean loseLifeAndRespawn(float spawnX, float spawnY) {
        lives--;
        if (lives <= 0) return false;
        health = Constants.MAX_HEALTH;
        respawn(spawnX, spawnY);
        return true;
    }

    public void respawn(float spawnX, float spawnY) {
        x = spawnX;
        y = spawnY;
        vx = 0f;
        vy = 0f;
        invulnerable = 1.2f;
        dashTimer = 0f;
        dashCooldown = 0f;
        jumpBuffer = 0f;
        coyoteTimer = 0f;
        jumpCount = 0;
        shootVisual = 0f;
        hurtVisual = 0f;
        deathVisual = 0f;
        visualState = VisualState.IDLE;
        visualTime = 0f;
    }

    public void notifyShot() {
        if (health <= 0) return;
        shootVisual = 0.28f;
        if (grounded && hurtVisual <= 0f && dashTimer <= 0f) setVisualState(VisualState.ATTACK, 0f);
    }

    public TextureRegion getFrame(Assets assets) {
        Animation<TextureRegion> animation = switch (visualState) {
            case IDLE -> assets.orbIdle;
            case WALK -> assets.orbWalk;
            case JUMP -> assets.orbJump;
            case DASH -> assets.orbDash;
            case ATTACK -> assets.orbAttack;
            case HURT -> assets.orbHurt;
            case DEATH -> assets.orbDeath;
        };
        // O PlayMode já foi definido no Assets; passar um "looping" avulso
        // aqui só criava a chance de divergir dele.
        return animation.getKeyFrame(visualTime);
    }

    public Rectangle getBounds() {
        return bounds.set(
            x - Constants.PLAYER_HIT_W / 2f,
            y - Constants.PLAYER_HIT_H / 2f,
            Constants.PLAYER_HIT_W,
            Constants.PLAYER_HIT_H
        );
    }

    public float getX() { return x; }
    public float getY() { return y; }
    /** Base da caixa de colisão: é onde a baseline do sprite deve encostar. */
    public float getFeetY() { return y - Constants.PLAYER_HIT_H / 2f; }
    public float getVx() { return vx; }
    public float getVy() { return vy; }
    public int getHealth() { return health; }
    public int getLives() { return lives; }
    public boolean isGrounded() { return grounded; }
    public boolean isFacingRight() { return facingRight; }
    public boolean isDashing() { return dashTimer > 0f; }
    public boolean isInvulnerable() { return invulnerable > 0f; }
    public float getDashCooldown() { return dashCooldown; }
    public boolean consumeJumpEvent() { boolean v = jumpedThisFrame; jumpedThisFrame = false; return v; }
    public boolean consumeDashEvent() { boolean v = dashedThisFrame; dashedThisFrame = false; return v; }
}
