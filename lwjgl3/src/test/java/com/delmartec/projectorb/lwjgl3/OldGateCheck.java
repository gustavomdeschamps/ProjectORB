package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FileTextureData;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.entities.Player;
import com.delmartec.projectorb.level.StairGate;
import com.delmartec.projectorb.screens.GameScreen;
import com.delmartec.projectorb.utils.UiRenderer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rodada 3, etapa 2: FALHA se a barra/portão antigo for desenhado.
 * Troca o SpriteBatch do jogo por um que registra toda textura usada e
 * percorre as 6 seções (escada fechada, abrindo e aberta; câmera no começo,
 * meio e fim de cada seção), a pausa e o códex. Compara cada textura desenhada
 * (pelo conteúdo do arquivo, SHA-256) com a arte descartada em
 * art-source/descartado_rodada3/portao_antigo/ e pelo nome (gate/wall).
 * Também falha se algum arquivo em assets/ tiver o mesmo conteúdo da arte
 * antiga. Controle positivo: a massa da escada TEM que aparecer no registro.
 */
public final class OldGateCheck {
    private OldGateCheck() { }

    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        File root = new File("..").getCanonicalFile();
        Set<String> oldHashes = new HashSet<>();
        File[] old = new File(root, "art-source/descartado_rodada3/portao_antigo").listFiles();
        if (old == null || old.length == 0) {
            System.out.println("OLD GATE CHECK: FAIL\n  - arte antiga de referência não encontrada");
            System.exit(1);
        }
        for (File f : old) oldHashes.add(sha(f));
        // nenhum arquivo em assets/ com o conteúdo da arte antiga
        scanAssets(new File(root, "assets"), oldHashes);

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Old Gate Check");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new Check(oldHashes), config);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void scanAssets(File dir, Set<String> oldHashes) throws Exception {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) scanAssets(f, oldHashes);
            else if (f.getName().endsWith(".png") && oldHashes.contains(sha(f))) {
                failures.add("arquivo com a arte do portão antigo em assets/: " + f.getPath());
            }
        }
    }

    static String sha(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(Files.readAllBytes(f.toPath()));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** SpriteBatch que anota cada textura desenhada. */
    static final class RecordingBatch extends SpriteBatch {
        final Set<Texture> used = new HashSet<>();

        @Override
        protected void switchTexture(Texture texture) {
            used.add(texture);
            super.switchTexture(texture);
        }
    }

    private static final class Check extends ProjectOrbGame {
        private final Set<String> oldHashes;
        private RecordingBatch recorder;

        Check(Set<String> oldHashes) { this.oldHashes = oldHashes; }

        @Override public void create() {
            super.create();
            recorder = new RecordingBatch();
            recorder.setBlendFunctionSeparate(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch = recorder;
            ui = new UiRenderer(batch, font, titleFont, assets);
            startGame();
        }

        private boolean done;

        @Override public void render() {
            if (done) return;
            final Input real = Gdx.input;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
                (p, m, a) -> switch (m.getName()) {
                    case "isKeyJustPressed", "isKeyPressed", "isButtonPressed", "isButtonJustPressed" -> false;
                    default -> m.invoke(real, a);
                });
            try {
                runTour();
                report();
            } catch (Exception e) {
                failures.add("erro: " + e);
                report();
            } finally {
                Gdx.input = real;
            }
        }

        private void runTour() throws Exception {
            GameScreen screen = (GameScreen) getScreen();
            screen.skipTutorial();
            StairGate[] stairs = (StairGate[]) get(screen, "stairs");
            boolean[] cleared = (boolean[]) get(screen, "sectionCleared");
            float[][] cams = { { 960, 1800, 2700 }, { 2400, 2700, 3300 }, { 4000, 4650, 5500 },
                { 6000, 6600, 7300 }, { 7800, 8400, 9100 }, { 9800, 10800, 11040 } };
            for (int s = 0; s < 6; s++) {
                for (int k = 0; k < s; k++) cleared[k] = true;
                set(screen, "currentSection", s);
                ((Player) get(screen, "player")).respawn(cams[s][0], 230f);
                Method spawn = GameScreen.class.getDeclaredMethod("spawnSection", int.class, boolean.class);
                spawn.setAccessible(true);
                spawn.invoke(screen, s, false);
                for (float cx : cams[s]) {
                    set(screen, "cameraBaseX", cx);
                    // escada da seção: fechada, abrindo (meio), aberta
                    screen.render(0f);
                    if (s < 5) {
                        StairGate st = stairs[s];
                        st.open();
                        for (int i = 0; i < 40; i++) { st.update(StairGate.OPEN_TIME / 80f); screen.render(0f); }
                        st.update(StairGate.OPEN_TIME);
                        screen.render(0f);
                        // volta a fechada para a próxima câmera (nova escada da seção)
                        stairs[s] = StairGate.forSection(
                            ((com.delmartec.projectorb.level.LevelDemo) get(screen, "level")).getSection(s),
                            ((com.delmartec.projectorb.level.LevelDemo) get(screen, "level")).getPlatforms());
                    }
                }
                for (int k = 0; k <= s && k < 5; k++) stairs[k].openImmediately();
            }
            set(screen, "codexOpen", true);
            screen.render(0f);
            set(screen, "codexOpen", false);
            set(screen, "paused", true);
            screen.render(0f);
        }

        private void report() {
            boolean sawStair = false;
            Map<String, Integer> counts = new HashMap<>();
            for (Texture t : recorder.used) {
                TextureData data = t.getTextureData();
                if (!(data instanceof FileTextureData)) continue;
                File f = ((FileTextureData) data).getFileHandle().file();
                String path = f.getPath().replace('\\', '/');
                if (path.contains("world/stair/block_closed")) sawStair = true;
                String name = f.getName().toLowerCase();
                if (name.contains("gate") || name.contains("wall")) failures.add("textura com nome do portão antigo desenhada: " + path);
                try {
                    if (f.exists() && oldHashes.contains(sha(f))) failures.add("arte do portão antigo desenhada: " + path);
                } catch (Exception e) {
                    failures.add("não li " + path + ": " + e);
                }
                counts.merge(path.substring(0, Math.max(0, path.lastIndexOf('/'))), 1, Integer::sum);
            }
            if (!sawStair) failures.add("controle: a massa da escada não apareceu no registro (o registro não funciona?)");
            System.out.println("  texturas desenhadas: " + recorder.used.size() + " (por pasta: " + counts + ")");
            System.out.println(failures.isEmpty() ? "OLD GATE CHECK: PASS" : "OLD GATE CHECK: FAIL");
            for (String f : failures) System.out.println("  - " + f);
            done = true;
            Gdx.app.exit();
        }
    }

    private static Object get(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
