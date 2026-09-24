package com.delmartec.projectorb.entities;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.delmartec.projectorb.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inimigo geométrico da demonstração educacional.
 *
 * A lógica pedagógica original foi preservada: o jogador responde atirando em
 * pontos específicos da própria forma, sem tela de quiz. O que mudou nesta
 * revisão:
 *
 * - a forma fica APOIADA NO CHÃO (os sprites do pacote final têm pernas e
 *   sombra; flutuando a 450 px do piso eles ficavam sem leitura);
 * - os pontos fracos são derivados da geometria real do sprite
 *   ({@link EnemyType}), então caem em cima dos vértices/lados de verdade;
 * - o ataque tem telegrafia: a animação começa antes do disparo e os
 *   projéteis saem no meio dela;
 * - cada forma tem um deslocamento próprio, usando as animações de
 *   walk/hover que já existiam processadas.
 */
public final class GeoEnemy {
    public enum HitResult { NONE, WEAK_POINT, ROUND_COMPLETE, WRONG, DEFEATED }

    private final EnemyType type;
    private final float homeX;
    private final float groundY;
    private final float baseY;
    private final List<WeakPoint> weakPoints = new ArrayList<>();
    private final Rectangle bodyRect = new Rectangle();

    private float x;
    private float y;
    private float patrolDir = 1f;
    private boolean facingRight = false;
    private boolean moving;

    private float stateTime;
    private float charge;
    private float rotation;
    private float flash;
    private float hurtVisual;
    private float powerUpVisual;
    private float deathVisual;
    private boolean defeated;
    private boolean attackEvent;

    // Telegrafia: attackTimer corre de 0 até attackDuration e o disparo
    // acontece em FIRE_POINT da animação, nunca no primeiro frame.
    private static final float FIRE_POINT = 0.55f;
    private float attackTimer = -1f;
    private boolean attackFired;
    private float punishCooldown;

    private int completedRounds;
    private final int totalRounds;
    private String request = "";

    // Durações reais das animações, injetadas pela GameScreen a partir do
    // Assets. Evita números mágicos dessincronizados do render.
    private float attackDuration = 0.50f;
    private float hurtDuration = 0.40f;
    private float deathDuration = 0.60f;
    private float powerUpDuration = 0.55f;

    public GeoEnemy(EnemyType type, float x, float groundY) {
        this.type = type;
        this.homeX = x;
        this.x = x;
        this.groundY = groundY;
        this.baseY = groundY + type.centerAboveFeet();
        this.y = baseY;
        this.totalRounds = type.isBoss() ? 6 : 1;
        buildRequest();
    }

    public void setAnimationTimings(float attack, float hurt, float death) {
        this.attackDuration = attack;
        this.hurtDuration = hurt;
        this.deathDuration = death;
    }

    public void setPowerUpDuration(float powerUpDuration) {
        this.powerUpDuration = powerUpDuration;
    }

    public void update(float delta, Player player, List<Projectile> enemyProjectiles) {
        stateTime += delta;
        flash = Math.max(0f, flash - delta);
        hurtVisual = Math.max(0f, hurtVisual - delta);
        powerUpVisual = Math.max(0f, powerUpVisual - delta);
        punishCooldown = Math.max(0f, punishCooldown - delta);

        if (defeated) {
            deathVisual += delta;
            moving = false;
            return;
        }

        facingRight = player.getX() >= x;
        rotation += rotationSpeed() * delta;

        if (attackTimer >= 0f) {
            // Janela de ataque: a forma para, a animação roda e o disparo sai
            // no meio dela. Parado + animação = telegrafia legível.
            moving = false;
            float previous = attackTimer;
            attackTimer += delta;
            float fireAt = attackDuration * FIRE_POINT;
            if (!attackFired && previous < fireAt && attackTimer >= fireAt) {
                attackFired = true;
                fire(player, enemyProjectiles);
            }
            if (attackTimer >= attackDuration) attackTimer = -1f;
            return;
        }

        move(delta, player);

        charge += chargeRate() * delta;
        if (charge >= 1f) beginAttack();
    }

    // ---------------------------------------------------------------- movimento

    private void move(float delta, Player player) {
        switch (type) {
            case TRIANGLE -> patrol(delta, 140f, 95f);
            case SQUARE -> patrol(delta, 90f, 62f);
            case HEXAGON -> patrol(delta, 175f, 82f);
            case DIAMOND -> hover(delta, player);
            case BOSS -> moving = false;
        }
    }

    private void patrol(float delta, float range, float speed) {
        x += patrolDir * speed * delta;
        if (x > homeX + range) { x = homeX + range; patrolDir = -1f; }
        else if (x < homeX - range) { x = homeX - range; patrolDir = 1f; }
        y = baseY;
        moving = true;
    }

    /** O losango flutua: mantém distância do jogador e balança na vertical. */
    private void hover(float delta, Player player) {
        float preferred = player.getX() + (player.getX() > homeX ? -230f : 230f);
        preferred = MathUtils.clamp(preferred, homeX - 130f, homeX + 130f);
        float dx = preferred - x;
        float step = 125f * delta;
        if (Math.abs(dx) <= step) { x = preferred; moving = false; }
        else { x += Math.signum(dx) * step; moving = true; }
        y = baseY + MathUtils.sin(stateTime * 2.1f) * 14f;
    }

    private float rotationSpeed() {
        if (!type.isBoss()) return 0f;
        // Fase 2 é a fase "espelho": sem giro, senão o reflexo perde o sentido
        // e a rotação acumulada daria um salto visual ao entrar na fase 3.
        return switch (getBossPhase()) {
            case 0 -> 0.22f;
            case 1 -> 0f;
            default -> 0.72f;
        };
    }

    private float chargeRate() {
        return switch (type) {
            // Intervalos entre 2,2 e 3,4 s mantêm o combate ativo sem virar
            // uma parede de projéteis. A versão anterior levava até 25 s.
            case DIAMOND -> 0.38f;
            case TRIANGLE -> 0.33f;
            case SQUARE -> 0.29f;
            case HEXAGON -> 0.41f;
            case BOSS -> 0.36f + getBossPhase() * 0.05f;
        };
    }

    // ------------------------------------------------------------------ ataque

    private void beginAttack() {
        if (defeated || attackTimer >= 0f) return;
        charge = 0f;
        attackTimer = 0f;
        attackFired = false;
        attackEvent = true;
    }

    private void fire(Player player, List<Projectile> projectiles) {
        flash = 0.15f;
        switch (type) {
            case DIAMOND -> {
                addAimed(projectiles, player, x, y, 640f, 13, false, -0.09f);
                addAimed(projectiles, player, x, y, 640f, 13, false, 0.09f);
            }
            case TRIANGLE -> {
                addAimed(projectiles, player, x, y, 570f, 15, false, -0.16f);
                addAimed(projectiles, player, x, y, 570f, 15, false, 0f);
                addAimed(projectiles, player, x, y, 570f, 15, false, 0.16f);
            }
            case SQUARE -> {
                addAimed(projectiles, player, x, y, 590f, 16, false, 0f);
                addRadial(projectiles, 4, 460f, 12, false, Math.PI / 4);
            }
            case HEXAGON -> {
                addAimed(projectiles, player, x, y, 620f, 14, false, 0f);
                addRadial(projectiles, 6, 500f, 12, false, Math.PI / 6);
            }
            case BOSS -> bossAttack(player, projectiles);
        }
    }

    private void bossAttack(Player player, List<Projectile> projectiles) {
        int phase = getBossPhase();
        if (phase == 0) {
            addRadial(projectiles, 8, 560f, 18, true, rotation);
            addAimed(projectiles, player, x, y, 700f, 20, true, 0f);
        } else if (phase == 1) {
            addAimed(projectiles, player, x - 120, y, 660f, 18, true, -0.10f);
            addAimed(projectiles, player, x + 120, y, 660f, 18, true, 0.10f);
            addRadial(projectiles, 6, 480f, 15, true, rotation);
        } else {
            addRadial(projectiles, 12, 650f, 19, true, rotation);
            addAimed(projectiles, player, x, y, 800f, 22, true, 0f);
        }
    }

    private void addRadial(List<Projectile> projectiles, int count, float speed, int damage, boolean boss, double offset) {
        for (int i = 0; i < count; i++) {
            double a = offset + i * Math.PI * 2.0 / count;
            projectiles.add(new Projectile(x, y,
                (float)Math.cos(a) * speed,
                (float)Math.sin(a) * speed,
                boss ? 15f : 12f, true, damage, boss));
        }
    }

    private void addAimed(List<Projectile> projectiles, Player player, float originX, float originY,
                          float speed, int damage, boolean boss, float angleOffset) {
        float dx = player.getX() - originX;
        float dy = player.getY() - originY;
        float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) len = 1f;
        dx /= len;
        dy /= len;
        double base = Math.atan2(dy, dx) + angleOffset;
        projectiles.add(new Projectile(originX, originY,
            (float)Math.cos(base) * speed,
            (float)Math.sin(base) * speed,
            boss ? 15f : 12f, true, damage, boss));
    }

    // -------------------------------------------------------------- acertos

    public HitResult testPlayerProjectile(Projectile p) {
        if (defeated || p.enemy || p.dead()) return HitResult.NONE;

        float pointRadius = weakPointHitRadius();
        for (WeakPoint point : weakPoints) {
            if (point.hit) continue;
            float wx = point.worldX(x, effectiveRotation());
            float wy = point.worldY(y, effectiveRotation());
            float dx = p.x - wx;
            float dy = p.y - wy;
            float rr = pointRadius + p.radius;
            if (dx * dx + dy * dy <= rr * rr) {
                point.hit = true;
                p.life = 0f;
                flash = 0.12f;
                hurtVisual = hurtDuration;

                if (allWeakPointsHit()) {
                    int oldPhase = getBossPhase();
                    completedRounds++;
                    charge = 0f;
                    attackTimer = -1f;
                    if (completedRounds >= totalRounds) {
                        defeated = true;
                        deathVisual = 0f;
                        return HitResult.DEFEATED;
                    }
                    if (type.isBoss() && getBossPhase() != oldPhase) powerUpVisual = powerUpDuration;
                    buildRequest();
                    return HitResult.ROUND_COMPLETE;
                }
                return HitResult.WEAK_POINT;
            }
        }

        // Só conta como erro se o tiro atingir de fato o corpo e não estiver
        // claramente a caminho de um ponto fraco ainda ativo.
        if (!isAimedAtRemainingWeakPoint(p, pointRadius) && hitsBody(p.x, p.y, p.radius)) {
            p.life = 0f;
            // Sem cooldown isso virava metralhadora: cada tiro no corpo
            // disparava uma salva inteira e o jogador tomava dano inevitável.
            if (punishCooldown <= 0f) {
                punishCooldown = 0.9f;
                beginAttack();
            }
            return HitResult.WRONG;
        }

        return HitResult.NONE;
    }

    private boolean hitsBody(float worldX, float worldY, float padding) {
        float dx = worldX - x;
        float dy = worldY - y;
        float angle = -effectiveRotation();
        float c = MathUtils.cos(angle);
        float s = MathUtils.sin(angle);
        float localX = dx * c - dy * s;
        float localY = dx * s + dy * c;
        return type.containsLocal(localX, localY, padding);
    }

    private boolean isAimedAtRemainingWeakPoint(Projectile p, float pointRadius) {
        float speed = (float)Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        if (speed < 0.001f) return false;
        float ux = p.vx / speed;
        float uy = p.vy / speed;
        float tolerance = pointRadius + p.radius + 12f;
        float tolerance2 = tolerance * tolerance;

        for (WeakPoint point : weakPoints) {
            if (point.hit) continue;
            float wx = point.worldX(x, effectiveRotation());
            float wy = point.worldY(y, effectiveRotation());
            float tx = wx - p.x;
            float ty = wy - p.y;
            float projection = tx * ux + ty * uy;
            if (projection < -8f) continue;
            float dist2 = tx * tx + ty * ty;
            float perpendicular2 = Math.max(0f, dist2 - projection * projection);
            if (perpendicular2 <= tolerance2) return true;
        }
        return false;
    }

    private boolean allWeakPointsHit() {
        for (WeakPoint point : weakPoints) if (!point.hit) return false;
        return true;
    }

    // ------------------------------------------------------- pontos fracos

    private void buildRequest() {
        weakPoints.clear();
        float hw = type.halfWidth();
        float hh = type.halfHeight();

        switch (type) {
            case TRIANGLE -> {
                request = "Meus três vértices me sustentam.";
                weakPoints.add(new WeakPoint(0f, hh * 0.78f));
                weakPoints.add(new WeakPoint(-hw * 0.70f, -hh * 0.72f));
                weakPoints.add(new WeakPoint(hw * 0.70f, -hh * 0.72f));
            }
            case SQUARE -> {
                request = "Meus quatro lados são iguais.";
                weakPoints.add(new WeakPoint(0f, hh * 0.76f));
                weakPoints.add(new WeakPoint(hw * 0.76f, 0f));
                weakPoints.add(new WeakPoint(0f, -hh * 0.76f));
                weakPoints.add(new WeakPoint(-hw * 0.76f, 0f));
            }
            case DIAMOND -> {
                request = "Meus quatro vértices se opõem dois a dois.";
                weakPoints.add(new WeakPoint(0f, hh * 0.76f));
                weakPoints.add(new WeakPoint(hw * 0.76f, 0f));
                weakPoints.add(new WeakPoint(0f, -hh * 0.76f));
                weakPoints.add(new WeakPoint(-hw * 0.76f, 0f));
            }
            case HEXAGON -> {
                request = "Seis ângulos fecham o meu contorno.";
                addEllipse(6, hw * 0.74f, hh * 0.76f, Math.PI / 2);
            }
            case BOSS -> buildBossRequest();
        }
    }

    private void buildBossRequest() {
        float hw = type.halfWidth();
        float hh = type.halfHeight();
        int phase = getBossPhase();

        if (phase == 0) {
            if (completedRounds == 0) {
                request = "FASE 1 - acerte os 6 VÉRTICES.";
                addEllipse(6, hw * 0.70f, hh * 0.75f, Math.PI / 2);
            } else {
                request = "FASE 1 - agora acerte os 6 LADOS.";
                addEllipse(6, hw * 0.70f, hh * 0.75f, Math.PI / 2 + Math.PI / 6);
            }
        } else if (phase == 1) {
            request = "FASE 2 - o reflexo é falso: acerte os 3 NÚCLEOS reais.";
            weakPoints.add(new WeakPoint(0f, hh * 0.45f));
            weakPoints.add(new WeakPoint(-hw * 0.55f, -hh * 0.35f));
            weakPoints.add(new WeakPoint(hw * 0.55f, -hh * 0.35f));
        } else {
            int r = completedRounds - 3;
            if (r == 0) {
                request = "FASE 3 - VÉRTICES em rotação.";
                addEllipse(6, hw * 0.70f, hh * 0.75f, Math.PI / 2);
            } else if (r == 1) {
                request = "FASE 3 - LADOS em rotação.";
                addEllipse(6, hw * 0.70f, hh * 0.75f, Math.PI / 2 + Math.PI / 6);
            } else {
                request = "FASE 3 - acerte os pontos SIMÉTRICOS.";
                weakPoints.add(new WeakPoint(-hw * 0.70f, 0f));
                weakPoints.add(new WeakPoint(hw * 0.70f, 0f));
            }
        }
    }

    private void addEllipse(int count, float rx, float ry, double start) {
        for (int i = 0; i < count; i++) {
            double a = start + i * Math.PI * 2.0 / count;
            weakPoints.add(new WeakPoint((float)Math.cos(a) * rx, (float)Math.sin(a) * ry));
        }
    }

    private float effectiveRotation() {
        return type.isBoss() && getBossPhase() != 1 ? rotation : 0f;
    }

    // --------------------------------------------------------------- consultas

    public boolean consumeAttackEvent() {
        boolean value = attackEvent;
        attackEvent = false;
        return value;
    }

    /** Caixa do corpo, usada para dano de contato. */
    public Rectangle getBodyRect() {
        bodyRect.set(x - type.halfWidth(), y - type.halfHeight(),
            type.halfWidth() * 2f, type.halfHeight() * 2f);
        return bodyRect;
    }

    public float weakPointHitRadius() { return type.isBoss() ? 31f : 24f; }

    /** Seis marcadores em volta de uma forma compacta se encostam no tamanho cheio. */
    public float weakPointMarkerSize() {
        if (type.isBoss()) return 44f;
        return weakPoints.size() >= 6 ? 30f : 34f;
    }

    public EnemyType getType() { return type; }
    public float getX() { return x; }
    public float getY() { return y; }
    /** Canto inferior do quad do sprite, para que os pés fiquem no chão. */
    public float getDrawY() { return y - type.drawOriginY(); }
    public float getDrawX() { return x - type.drawSize() / 2f; }
    public float getStateTime() { return stateTime; }
    public float getCharge() { return charge; }
    public float getRotation() { return effectiveRotation(); }
    public float getFlash() { return flash; }
    public boolean isFacingRight() { return facingRight; }
    public boolean isMoving() { return moving; }
    public boolean isDefeated() { return defeated; }
    public boolean isAttacking() { return attackTimer >= 0f; }
    public boolean isHurt() { return hurtVisual > 0f; }
    public boolean isPoweringUp() { return powerUpVisual > 0f; }
    public float getAttackVisualTime() { return Math.max(0f, attackTimer); }
    public float getHurtVisualTime() { return Math.max(0f, hurtDuration - hurtVisual); }
    public float getPowerUpVisualTime() { return Math.max(0f, powerUpDuration - powerUpVisual); }
    public float getDeathVisualTime() { return deathVisual; }
    public float getDeathProgress() { return deathDuration <= 0f ? 1f : Math.min(1f, deathVisual / deathDuration); }
    /** O inimigo só some depois que a animação de morte termina de verdade. */
    public boolean isGone() { return defeated && deathVisual >= deathDuration; }
    public int getCompletedRounds() { return completedRounds; }
    public int getTotalRounds() { return totalRounds; }

    public int getBossPhase() {
        if (!type.isBoss()) return -1;
        if (completedRounds < 2) return 0;
        if (completedRounds < 3) return 1;
        return 2;
    }

    public float getHealthRatio() {
        return Math.max(0f, (totalRounds - completedRounds) / (float)totalRounds);
    }

    public String getRequest() { return request; }
    public List<WeakPoint> getWeakPoints() { return Collections.unmodifiableList(weakPoints); }

    public int getRemainingWeakPoints() {
        int remaining = 0;
        for (WeakPoint point : weakPoints) if (!point.hit) remaining++;
        return remaining;
    }

    public int getWeakPointCount() { return weakPoints.size(); }

    public String getExplicitInstruction() {
        return switch (type) {
            case TRIANGLE -> "ATIRE NOS 3 VÉRTICES ILUMINADOS";
            case SQUARE -> "ATIRE NO MEIO DOS 4 LADOS";
            case DIAMOND -> "ATIRE NOS 4 VÉRTICES DO LOSANGO";
            case HEXAGON -> "ATIRE NOS 6 ÂNGULOS ILUMINADOS";
            case BOSS -> request.toUpperCase();
        };
    }

    public int getContactDamage() {
        return type.isBoss() ? Constants.CONTACT_DAMAGE * 2 : Constants.CONTACT_DAMAGE;
    }
}
