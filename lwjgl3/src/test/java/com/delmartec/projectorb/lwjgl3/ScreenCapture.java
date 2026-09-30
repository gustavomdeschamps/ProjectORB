package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

import java.io.File;

/**
 * Captura 1:1 (1920x1080, a resolução nativa do jogo) de uma tela, num
 * FrameBuffer: a imagem não passa pela reamostragem da janela.
 * 'delta' é o tempo que a tela avança nesse render (0 = congelado).
 */
final class ScreenCapture {
    private ScreenCapture() { }

    static void capture(Screen screen, String path, float delta) {
        int w = 1920, h = 1080;
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fbo.begin();
        screen.resize(w, h);
        screen.render(delta);
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        fbo.end();
        screen.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Pixmap flipped = new Pixmap(w, h, pixmap.getFormat());
        for (int y = 0; y < h; y++) flipped.drawPixmap(pixmap, 0, y, w, 1, 0, h - 1 - y, w, 1);
        new File(path).getAbsoluteFile().getParentFile().mkdirs();
        PixmapIO.writePNG(Gdx.files.absolute(new File(path).getAbsolutePath()), flipped);
        flipped.dispose();
        pixmap.dispose();
        fbo.dispose();
    }
}
