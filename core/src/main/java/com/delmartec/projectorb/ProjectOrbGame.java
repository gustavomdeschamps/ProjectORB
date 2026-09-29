package com.delmartec.projectorb;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.delmartec.projectorb.screens.MenuScreen;
import com.delmartec.projectorb.utils.Assets;
import com.delmartec.projectorb.utils.AudioManager;
import com.delmartec.projectorb.utils.GameSettings;
import com.delmartec.projectorb.utils.UiRenderer;

public class ProjectOrbGame extends Game {
    public SpriteBatch batch;
    public BitmapFont font;
    public BitmapFont titleFont;
    public Assets assets;
    public AudioManager audio;
    public GameSettings settings;
    public UiRenderer ui;

    @Override
    public void create() {
        batch = new SpriteBatch();
        // Silkscreen (OFL, fonts/Silkscreen-OFL.txt): pixel font desenhada numa
        // grade de 8 px por em. Gerada a 8 px, sem antialiasing (mono) e com
        // filtro Nearest; o UiRenderer a desenha só em escalas inteiras, então
        // cada pixel da letra tem o mesmo tamanho dos pixels da arte.
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Silkscreen-Regular.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter text = new FreeTypeFontGenerator.FreeTypeFontParameter();
        text.size = 8;
        text.mono = true;
        text.characters = FreeTypeFontGenerator.DEFAULT_CHARS
            + "ÁÀÂÃÉÊÍÓÔÕÚÜÇáàâãéêíóôõúüç°º";
        text.minFilter = Texture.TextureFilter.Nearest;
        text.magFilter = Texture.TextureFilter.Nearest;
        font = generator.generateFont(text);
        font.setUseIntegerPositions(true);
        // Instância separada para títulos: a escala do BitmapFont é estado
        // compartilhado, e os títulos usam escalas maiores.
        titleFont = generator.generateFont(text);
        titleFont.setUseIntegerPositions(true);
        generator.dispose();
        assets = new Assets();
        settings = new GameSettings();
        audio = new AudioManager(settings);
        ui = new UiRenderer(batch, font, titleFont, assets);
        setScreen(settings.isIntroEnabled() ? new com.delmartec.projectorb.screens.IntroScreen(this) : new MenuScreen(this));
    }

    public void startGame() {
        setScreen(new com.delmartec.projectorb.screens.GameScreen(this));
    }

    public void showMenu() {
        setScreen(new MenuScreen(this));
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        audio.dispose();
        assets.dispose();
        font.dispose();
        titleFont.dispose();
        batch.dispose();
    }
}
