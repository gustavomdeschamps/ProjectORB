package com.delmartec.projectorb.utils;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Viewport de pixel art: mostra o mundo de 1920x1080 (480x270 pixels de arte)
 * na maior escala em que cada pixel de arte ocupa um número INTEIRO de pixels
 * da tela, centralizado, com barras no que sobrar. O FitViewport esticava por
 * um fator qualquer (a 1280x720 cada pixel de arte virava 2,67 px e os pixels
 * ficavam de tamanhos diferentes).
 *
 * 1280x720 -> 2 px por pixel de arte (960x540 + barras); 1920x1080 -> 4 (1:1);
 * 2560x1440 -> 5 (2400x1350 + barras); 3440x1440 -> 5.
 * Janela menor que 480x270: cai no ajuste livre (sem pixel inteiro possível).
 */
public final class PixelViewport extends Viewport {
    public PixelViewport(Camera camera) {
        setWorldSize(Constants.VIEW_WIDTH, Constants.VIEW_HEIGHT);
        setCamera(camera);
    }

    @Override
    public void update(int screenWidth, int screenHeight, boolean centerCamera) {
        int[] b = bounds(screenWidth, screenHeight);
        setScreenBounds(b[0], b[1], b[2], b[3]);
        apply(centerCamera);
    }

    /** Área desenhada {x, y, largura, altura} numa janela; o resto são barras. */
    public static int[] bounds(int screenWidth, int screenHeight) {
        float fit = Math.min(screenWidth / Constants.VIEW_WIDTH, screenHeight / Constants.VIEW_HEIGHT);
        // escala em passos de 1/PIXEL_SCALE = pixel de arte inteiro na tela
        float scale = (float)Math.floor(fit * Constants.PIXEL_SCALE) / Constants.PIXEL_SCALE;
        if (scale <= 0f) scale = fit;
        int w = Math.round(Constants.VIEW_WIDTH * scale);
        int h = Math.round(Constants.VIEW_HEIGHT * scale);
        return new int[] { (screenWidth - w) / 2, (screenHeight - h) / 2, w, h };
    }
}
