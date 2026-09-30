package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.delmartec.projectorb.entities.EnemyType;
import com.delmartec.projectorb.entities.GeoEnemy;
import com.delmartec.projectorb.entities.WeakPoint;
import com.delmartec.projectorb.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Checagem automatizada: cada ponto fraco continua sobre o vértice (ou o meio
 * do lado, no quadrado) da silhueta desenhada. Para cada alvo, na mesma conta
 * do jogo (GeoEnemy/EnemyType), no idle_01 do inimigo:
 *  1. o pixel do alvo é opaco (está no corpo);
 *  2. andando pelo raio centro->alvo, a borda da silhueta fica no raio esperado
 *     (vértice: raio externo; lado: apótema), com folga para os espinhos;
 *  3. é mesmo um canto (a ±20° a borda fica mais perto) ou o meio de um lado
 *     (a ±20° a borda fica mais longe).
 * Não entra no build de produção.
 */
public final class WeakPointAlignmentCheck {
    private WeakPointAlignmentCheck() { }

    static final List<String> failures = new ArrayList<>();
    /** Avisos da arte antiga (ainda não redesenhada): não reprovam, só aparecem. */
    static final List<String> pending = new ArrayList<>();

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Weak Point Alignment Check");
        config.setWindowedMode(320, 180);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void create() {
                for (EnemyType type : EnemyType.values()) {
                    if (type.isBoss()) continue;   // chefe: checado por passo de giro (BossAlignmentCheck)
                    check(type);
                }
                Gdx.app.exit();
            }
        }, config);
        System.out.println(failures.isEmpty() ? "WEAK POINT ALIGNMENT CHECK: PASS" : "WEAK POINT ALIGNMENT CHECK: FAIL");
        for (String f : failures) System.out.println("  - " + f);
        for (String f : pending) System.out.println("  PENDENTE (arte antiga, ainda não redesenhada): " + f);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void check(EnemyType type) {
        String dir = switch (type) {
            case TRIANGLE -> "triangle";
            case SQUARE -> "square";
            case DIAMOND -> "diamond";
            case HEXAGON -> "hexagon";
            default -> throw new IllegalStateException();
        };
        Pixmap pix = new Pixmap(Gdx.files.internal("sprites/enemies/" + dir + "/idle_01.png"));
        // arte nova (F2 v2) tem animação de carga; só ela reprova o teste
        List<String> out = Gdx.files.internal("sprites/enemies/" + dir + "/charge_01.png").exists() ? failures : pending;
        GeoEnemy enemy = new GeoEnemy(type, 1000f, Constants.FLOOR_Y);
        float px = Constants.PIXEL_SCALE;
        int canvas = pix.getWidth();
        // centro do corpo em pixels de arte (mesma conta do desenho: quad em getDrawX/getDrawY)
        float cx = (enemy.getX() - enemy.getDrawX()) / px;
        float cy = canvas - (enemy.getY() - enemy.getDrawY()) / px;   // y para baixo
        boolean sides = enemy.getTargetProperty() == GeoEnemy.TargetProperty.SIDES;
        float n = sidesOf(type);
        float outer = type.halfWidth() > 0 ? outerArt(type) : 0f;
        float expected = sides ? outer * (float)Math.cos(Math.PI / n) : outer;

        for (WeakPoint w : enemy.getWeakPoints()) {
            float wx = (w.worldX(enemy.getX(), 0f) - enemy.getDrawX()) / px;
            float wy = canvas - (w.worldY(enemy.getY(), 0f) - enemy.getDrawY()) / px;
            String where = type.displayName() + " alvo em (" + Math.round(wx) + "," + Math.round(wy) + ")";
            if (!opaque(pix, wx, wy)) out.add(where + ": fora do corpo (pixel transparente)");
            double ang = Math.atan2(-(wy - cy), wx - cx);
            float edge = boundary(pix, cx, cy, ang);
            if (edge < expected - 1.5f || edge > expected + 6f) {
                out.add(where + String.format(": borda a r=%.1f, esperado ~%.1f", edge, expected));
            }
            float a = boundary(pix, cx, cy, ang + Math.toRadians(20)), b = boundary(pix, cx, cy, ang - Math.toRadians(20));
            if (!sides && !(a < edge - 0.5f && b < edge - 0.5f)) {
                out.add(where + String.format(": não é um vértice (borda %.1f; a ±20°: %.1f / %.1f)", edge, a, b));
            }
            if (sides && !(a > edge + 0.5f && b > edge + 0.5f)) {
                out.add(where + String.format(": não é o meio de um lado (borda %.1f; a ±20°: %.1f / %.1f)", edge, a, b));
            }
        }
        System.out.printf("  %-10s %d alvos checados (borda esperada r=%.1f)%n", dir, enemy.getWeakPointCount(), expected);
        pix.dispose();
    }

    static int sidesOf(EnemyType t) {
        return switch (t) { case TRIANGLE -> 3; case SQUARE, DIAMOND -> 4; default -> 6; };
    }

    /** Raio externo em pixels de arte (halfWidth/halfHeight vêm dele; aqui recupera pelo maior). */
    static float outerArt(EnemyType t) {
        // o vértice mais distante do centro: max(halfWidth, halfHeight) só vale se houver vértice no eixo;
        // para o triângulo (vértice em cima) é halfHeight.
        return Math.max(t.halfWidth(), t.halfHeight()) / Constants.PIXEL_SCALE
            / (t == EnemyType.SQUARE ? (float)Math.cos(Math.PI / 4) : 1f);
    }

    static boolean opaque(Pixmap p, float x, float y) {
        int ix = (int)Math.floor(x), iy = (int)Math.floor(y);
        if (ix < 0 || iy < 0 || ix >= p.getWidth() || iy >= p.getHeight()) return false;
        return (p.getPixel(ix, iy) & 0xff) != 0;
    }

    /** Raio (arte) até a borda: primeiro ponto seguido de 2 px transparentes. */
    static float boundary(Pixmap p, float cx, float cy, double ang) {
        float cos = (float)Math.cos(ang), sin = (float)Math.sin(ang);
        float last = 0f;
        int gap = 0;
        for (float r = 0f; r < p.getWidth(); r += 0.25f) {
            if (opaque(p, cx + r * cos, cy - r * sin)) { last = r; gap = 0; }
            else if (last > 0f && ++gap >= 8) break;
        }
        return last;
    }
}
