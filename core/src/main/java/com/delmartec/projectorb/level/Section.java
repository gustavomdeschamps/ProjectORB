package com.delmartec.projectorb.level;

public class Section {
    public final int index;
    public final String title;
    public final String subtitle;
    public final float startX;
    public final float endX;
    public final float respawnX;

    public Section(int index, String title, String subtitle, float startX, float endX, float respawnX) {
        this.index = index;
        this.title = title;
        this.subtitle = subtitle;
        this.startX = startX;
        this.endX = endX;
        this.respawnX = respawnX;
    }
}
