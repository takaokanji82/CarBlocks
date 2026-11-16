package com.carblocks.game;

/** 绘图接口：安卓上由 Canvas 实现，测试时由 Java2D 实现。 */
public interface Gfx {
    void rrect(float x, float y, float w, float h, float r, int argb);

    /** 以 (cx, cy) 为中心绘制一行文字。 */
    void text(String s, float cx, float cy, float size, int argb, boolean bold);
}
