package com.delmartec.projectorb.lwjgl3;

import com.delmartec.projectorb.entities.EnemyType;
import com.delmartec.projectorb.entities.GeoEnemy;
import com.delmartec.projectorb.entities.Projectile;
import com.delmartec.projectorb.entities.WeakPoint;
import com.delmartec.projectorb.utils.Constants;

/**
 * Checagem automatizada (sem janela): um tiro do jogador cuja trajetória passa
 * dentro do raio de acerto de um ponto fraco tem que acertá-lo, qualquer que
 * seja a fase do tiro em relação ao frame. Usa o pior caso do jogo, delta de
 * 1/30 s (o GameScreen limita o delta a esse valor). Não entra no build.
 */
public final class ProjectileSweepCheck {
    private static final float DT = 1f / 30f;

    private ProjectileSweepCheck() { }

    public static void main(String[] args) {
        int trials = 0;
        int misses = 0;
        String example = null;
        for (EnemyType type : EnemyType.values()) {
            int pointCount = new GeoEnemy(type, 1000f, Constants.FLOOR_Y).getWeakPointCount();
            for (int target = 0; target < pointCount; target++) {
                for (float frac = 0f; frac < 0.99f; frac += 0.05f) {
                    for (float phase = 0f; phase < 41f; phase += 1.5f) {
                        for (int side = -1; side <= 1; side += 2) {
                            GeoEnemy enemy = new GeoEnemy(type, 1000f, Constants.FLOOR_Y);
                            WeakPoint point = enemy.getWeakPoints().get(target);
                            // Isola o alvo: os demais já foram acertados (situação
                            // do último ponto de uma rodada). Sem isso um tiro que
                            // atravessa o alvo é "salvo" por outro ponto no caminho.
                            for (WeakPoint other : enemy.getWeakPoints()) other.hit = other != point;
                            float wx = point.worldX(enemy.getX(), enemy.getRotation());
                            float wy = point.worldY(enemy.getY(), enemy.getRotation());
                            float rr = enemy.weakPointHitRadius() + Constants.SHOT_RADIUS;
                            float offset = side * frac * rr;

                            // Tiro radial, de fora para dentro, deslocado lateralmente.
                            float ox = wx - enemy.getX();
                            float oy = wy - enemy.getY();
                            float len = (float) Math.sqrt(ox * ox + oy * oy);
                            float ux = len < 0.001f ? 1f : ox / len;
                            float uy = len < 0.001f ? 0f : oy / len;
                            float px = -uy, py = ux;
                            float start = 320f + phase;
                            Projectile p = new Projectile(
                                wx + ux * start + px * offset, wy + uy * start + py * offset,
                                -ux * Constants.SHOT_SPEED, -uy * Constants.SHOT_SPEED,
                                Constants.SHOT_RADIUS, false, 1, false);

                            GeoEnemy.HitResult result = GeoEnemy.HitResult.NONE;
                            for (int i = 0; i < 40 && !p.dead(); i++) {
                                p.update(DT);
                                result = enemy.testPlayerProjectile(p);
                                if (result != GeoEnemy.HitResult.NONE) break;
                            }
                            trials++;
                            boolean hitWeakPoint = result == GeoEnemy.HitResult.WEAK_POINT
                                || result == GeoEnemy.HitResult.ROUND_COMPLETE
                                || result == GeoEnemy.HitResult.DEFEATED;
                            if (!hitWeakPoint) {
                                misses++;
                                if (example == null) example = type + " ponto " + target
                                    + " desvio " + Math.round(Math.abs(offset)) + "/" + Math.round(rr) + " px";
                            }
                        }
                    }
                }
            }
        }
        System.out.println("tentativas=" + trials + " atravessaram=" + misses
            + (example != null ? " (ex.: " + example + ")" : ""));
        System.out.println(misses == 0 ? "PROJECTILE SWEEP CHECK: PASS" : "PROJECTILE SWEEP CHECK: FAIL");
        System.exit(misses == 0 ? 0 : 1);
    }
}
