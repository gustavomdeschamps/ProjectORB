package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.screens.IntroScreen;

import java.io.File;

/**
 * Grava a abertura quadro a quadro (tempo fixo, independente da máquina):
 * -Dsmoke.out=pasta -Drec.fps=60 -Drec.every=1 (grava 1 de cada N quadros).
 * PNG 1920x1080 por quadro; o vídeo é montado com ffmpeg (tools/gravar_abertura.sh).
 */
public final class IntroRecorder {
    private IntroRecorder() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/abertura")).getCanonicalPath();
        new File(out).mkdirs();
        int fps = Integer.getInteger("rec.fps", 60);
        int every = Integer.getInteger("rec.every", 1);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Intro Recorder");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new ProjectOrbGame() {
            @Override public void create() {
                super.create();
                FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
                IntroScreen intro = new IntroScreen(this, false);
                intro.setRecording(fbo);
                int total = Math.round(IntroScreen.DURATION * fps);
                // -Drec.video=saida.mp4: quadros crus direto para o ffmpeg (sem PNG em disco)
                String video = System.getProperty("rec.video");
                Process ff = null;
                java.io.OutputStream pipe = null;
                if (video != null) {
                    try {
                        ff = new ProcessBuilder(System.getProperty("rec.ffmpeg", "ffmpeg"), "-y", "-loglevel", "error",
                            "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "1920x1080", "-r", Integer.toString(fps), "-i", "-",
                            "-i", System.getProperty("rec.audio"), "-c:v", "libx264", "-preset", "medium", "-crf", "18",
                            "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest", video)
                            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
                        pipe = new java.io.BufferedOutputStream(ff.getOutputStream(), 1 << 22);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                byte[] row = new byte[1920 * 3];
                for (int i = 0; i < total; i++) {
                    fbo.begin();
                    intro.resize(1920, 1080);
                    intro.render(1f / fps);
                    if (pipe != null) {
                        Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1920, 1080);
                        java.nio.ByteBuffer buf = p.getPixels();
                        try {
                            for (int y = 1079; y >= 0; y--) {
                                for (int x = 0; x < 1920; x++) {
                                    int o = (y * 1920 + x) * 4;
                                    row[x * 3] = buf.get(o);
                                    row[x * 3 + 1] = buf.get(o + 1);
                                    row[x * 3 + 2] = buf.get(o + 2);
                                }
                                pipe.write(row);
                            }
                        } catch (java.io.IOException e) {
                            throw new RuntimeException(e);
                        }
                        p.dispose();
                    } else if (i % every == 0) {
                        Pixmap p = Pixmap.createFromFrameBuffer(0, 0, 1920, 1080);
                        Pixmap f = new Pixmap(1920, 1080, Pixmap.Format.RGB888);
                        f.setBlending(Pixmap.Blending.None);
                        for (int y = 0; y < 1080; y++) f.drawPixmap(p, 0, y, 1920, 1, 0, 1079 - y, 1920, 1);
                        PixmapIO.writePNG(Gdx.files.absolute(out + File.separator + String.format("f%05d.png", i / every)), f, 1, true);
                        f.dispose();
                        p.dispose();
                    }
                    fbo.end();
                }
                fbo.dispose();
                if (pipe != null) {
                    try {
                        pipe.close();
                        ff.waitFor();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                Gdx.app.exit();
            }
        }, config);
    }
}
