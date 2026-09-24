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
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Inconsolata-Bold.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter text = new FreeTypeFontGenerator.FreeTypeFontParameter();
        text.size = 18;
        text.characters = FreeTypeFontGenerator.DEFAULT_CHARS
            + "ÁÀÂÃÉÊÍÓÔÕÚÜÇáàâãéêíóôõúüç°º";
        text.minFilter = Texture.TextureFilter.Linear;
        text.magFilter = Texture.TextureFilter.Linear;
        font = generator.generateFont(text);
        FreeTypeFontGenerator.FreeTypeFontParameter titles = new FreeTypeFontGenerator.FreeTypeFontParameter();
        titles.size = 96;
        titles.characters = text.characters;
        titles.minFilter = Texture.TextureFilter.Linear;
        titles.magFilter = Texture.TextureFilter.Linear;
        titleFont = generator.generateFont(titles);
        generator.dispose();
        assets = new Assets();
        settings = new GameSettings();
        audio = new AudioManager(settings);
        ui = new UiRenderer(batch, font, titleFont, assets);
        setScreen(new MenuScreen(this));
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
