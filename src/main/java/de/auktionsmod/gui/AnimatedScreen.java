package de.auktionsmod.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Basis fuer alle Fenster der Mod: beim Oeffnen gleitet das Fenster sanft von unten herein.
 * Unterklassen zeichnen ihr Panel in {@link #renderPanel} (hinter den Knoepfen)
 * und Texte in {@link #renderContent} (vor den Knoepfen).
 */
public abstract class AnimatedScreen extends Screen {
	private static final float OPEN_MS = 260f;
	private long openMs = -1;
	private float offset;
	/** Zeit seit dem letzten Frame in ms (fuer weiche Uebergaenge) */
	protected float frameMs;
	private long lastFrame;

	protected AnimatedScreen(Text title) {
		super(title);
	}

	/** 0..1, Fortschritt der Oeffnen-Animation */
	protected float openProgress() {
		if (openMs < 0) return 0;
		return easeOutCubic(Math.min(1, (System.currentTimeMillis() - openMs) / OPEN_MS));
	}

	@Override
	public final void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		if (openMs < 0) openMs = now;
		frameMs = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
		lastFrame = now;
		offset = (1 - openProgress()) * 14;
		int my = mouseY - Math.round(offset);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(0, offset);
		super.render(ctx, mouseX, my, delta);
		renderContent(ctx, mouseX, my, delta);
		ctx.getMatrices().popMatrix();
		renderOverlay(ctx);
	}

	@Override
	public final void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Abdunkeln/Unschaerfe ohne Verschiebung, damit kein Rand entsteht
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(0, -offset);
		super.renderBackground(ctx, mouseX, mouseY, delta);
		ctx.getMatrices().popMatrix();
		renderPanel(ctx, mouseX, mouseY, delta);
	}

	protected abstract void renderPanel(DrawContext ctx, int mouseX, int mouseY, float delta);

	protected abstract void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta);

	/** Ueber allem, ohne Verschiebung (z.B. Gewinner-Animation). */
	protected void renderOverlay(DrawContext ctx) {
		WinAnimation.render(ctx);
	}

	/** Weicher Uebergang eines Wertes zum Ziel (bildratenunabhaengig). */
	protected float approach(float current, float target, float speed) {
		float f = 1 - (float) Math.pow(1 - Math.min(0.99f, speed), frameMs / 16f);
		return current + (target - current) * f;
	}

	public static float easeOutCubic(float x) {
		return 1 - (float) Math.pow(1 - x, 3);
	}

	/** 0..1 pulsierend */
	public static float pulse(float periodMs) {
		return 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / periodMs * Math.PI * 2);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
