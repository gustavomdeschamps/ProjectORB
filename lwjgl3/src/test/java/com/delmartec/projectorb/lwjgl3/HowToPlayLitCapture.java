package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.delmartec.projectorb.ProjectOrbGame;
import com.delmartec.projectorb.screens.HowToPlayScreen;
import com.delmartec.projectorb.screens.MenuScreen;

import java.io.File;
import java.lang.reflect.Proxy;

/**
 * Captura a tela "Como jogar" com ESPAÇO, D e o botão esquerdo simulados como
 * pressionados, para conferir as teclas acesas. Não entra no build.
 */
public final class HowToPlayLitCapture {
    private HowToPlayLitCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File("../tmp/a4").getCanonicalPath();
        new File(out).mkdirs();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(1280, 720);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frames;

            @Override public void create() {
                super.create();
                setScreen(new HowToPlayScreen(this, (MenuScreen) getScreen()));
            }

            @Override public void render() {
                final Input real = Gdx.input;
                Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
                    (p, m, a) -> switch (m.getName()) {
                        case "isKeyPressed" -> (Integer) a[0] == Input.Keys.SPACE || (Integer) a[0] == Input.Keys.D;
                        case "isButtonPressed" -> (Integer) a[0] == Input.Buttons.LEFT;
                        case "isKeyJustPressed", "isButtonJustPressed" -> false;
                        default -> m.invoke(real, a);
                    });
                if (++frames == 10) {
                    FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
                    fbo.begin();
                    getScreen().resize(1920, 1080);
                    getScreen().render(0f);
                    Pixmap px = Pixmap.createFromFrameBuffer(0, 0, 1920, 1080);
                    fbo.end();
                    Pixmap flipped = new Pixmap(1920, 1080, px.getFormat());
                    for (int y = 0; y < 1080; y++) flipped.drawPixmap(px, 0, y, 1920, 1, 0, 1079 - y, 1920, 1);
                    PixmapIO.writePNG(Gdx.files.absolute(out + File.separator + "como-jogar-acesa.png"), flipped);
                    Gdx.app.exit();
                }
                super.render();
            }
        }, config);
    }
}
