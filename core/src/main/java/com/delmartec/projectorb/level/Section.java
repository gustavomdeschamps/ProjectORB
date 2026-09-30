package com.delmartec.projectorb.level;

public class Section {
    public final int index;
    /** Nome curto da seção (o cartão mostra só ele; a legenda técnica saiu na rodada 3). */
    public final String title;
    public final float startX;
    public final float endX;
    public final float respawnX;

    public Section(int index, String title, float startX, float endX, float respawnX) {
        this.index = index;
        this.title = title;
        this.startX = startX;
        this.endX = endX;
        this.respawnX = respawnX;
    }
}
