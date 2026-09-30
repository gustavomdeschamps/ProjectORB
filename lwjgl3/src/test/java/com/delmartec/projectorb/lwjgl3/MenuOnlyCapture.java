package com.delmartec.projectorb.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.delmartec.projectorb.ProjectOrbGame;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/**
 * Tela de início em 1920x1080 com relógio zerado, botão 0 selecionado e mouse
 * fixo fora dos botões. Autocontido para rodar também no baseline.
 */
public final class MenuOnlyCapture {
    private MenuOnlyCapture() { }

    public static void main(String[] args) throws Exception {
        String out = new File(System.getProperty("smoke.out", "../tmp/menu-only")).getCanonicalPath();
        new File(out).mkdirs();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project ORB — Menu Only");
        config.setWindowedMode(640, 360);
        new Lwjgl3Application(new ProjectOrbGame() {
            int frame;

            @Override public void create() {
                super.create();
                showMenu();
            }

            @Override public void render() {
                final Input real = Gdx.input;
                Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
                    (p, m, a) -> switch (m.getName()) {
                        case "getX", "getY" -> a == null ? 0 : m.invoke(real, a);
                        case "isKeyJustPressed", "isKeyPressed", "isButtonPressed", "isButtonJustPressed" -> false;
                        default -> m.invoke(real, a);
                    });
                try {
                    if (++frame == 10) {
                        set("stateTime", 0f);
                        set("selected", 0);
                        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, 1920, 1080, false);
                        fbo.begin();
                        getScreen().resize(1920, 1080);
                        getScreen().render(0f);
                        Pixmap px = Pixmap.createFromFrameBuffer(0, 0, 1920, 1080);
                        fbo.end();
                        Pixmap f = new Pixmap(1920, 1080, px.getFormat());
                        for (int y = 0; y < 1080; y++) f.drawPixmap(px, 0, y, 1920, 1, 0, 1079 - y, 1920, 1);
                        PixmapIO.writePNG(Gdx.files.absolute(out + File.separator + "menu.png"), f);
                        f.dispose(); px.dispose(); fbo.dispose();
                        Gdx.app.exit();
                        return;
                    }
                    super.render();
                } finally {
                    Gdx.input = real;
                }
            }

            private void set(String name, Object v) {
                try {
                    Field fl = getScreen().getClass().getDeclaredField(name);
                    fl.setAccessible(true);
                    fl.set(getScreen(), v);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }, config);
    }
}
