package de.auktionsmod.gui;

import de.auktionsmod.AuctionConfig;
import de.auktionsmod.Money;
import de.auktionsmod.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/**
 * Kleine Gewinner-Animation: Konfetti faellt, ein Banner mit Kopf, Name, Betrag und Item springt
 * in die Mitte und blendet nach ein paar Sekunden wieder aus.
 */
public final class WinAnimation {
	private static final long DURATION = 6000;
	private static final int PARTICLES = 90;

	private static long startMs = -1;
	private static String winner = "";
	private static double amount;
	private static ItemStack item = ItemStack.EMPTY;

	// Konfetti: Startposition (0..1), Geschwindigkeit, Pendeln, Farbe, Groesse
	private static final float[] px = new float[PARTICLES], speed = new float[PARTICLES],
			sway = new float[PARTICLES], phase = new float[PARTICLES], delay = new float[PARTICLES];
	private static final int[] color = new int[PARTICLES], size = new int[PARTICLES];

	private WinAnimation() {}

	public static void start(String name, double bid, ItemStack stack) {
		AuctionConfig c = AuctionConfig.get();
		if (!c.winAnimation) return;
		winner = name;
		amount = bid;
		item = stack.copy();
		startMs = System.currentTimeMillis();

		Random r = new Random();
		int[] palette = {Theme.accentRgb(), Theme.good() & 0xFFFFFF, 0xFFFFFF, 0xFF5577, 0x55CCFF, 0xFFE14D, 0xB36BFF};
		for (int i = 0; i < PARTICLES; i++) {
			px[i] = r.nextFloat();
			speed[i] = 0.06f + r.nextFloat() * 0.09f;   // Pixel pro ms
			sway[i] = 4 + r.nextFloat() * 14;
			phase[i] = r.nextFloat() * 6.28f;
			delay[i] = r.nextFloat() * 1400;
			color[i] = palette[r.nextInt(palette.length)];
			size[i] = 2 + r.nextInt(3);
		}

		if (c.winSound) {
			MinecraftClient.getInstance().getSoundManager()
					.play(PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
		}
	}

	public static boolean active() {
		return startMs >= 0 && System.currentTimeMillis() - startMs < DURATION;
	}

	/** Als HUD-Element registriert. */
	public static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden || mc.currentScreen instanceof AuctionOverviewScreen) return;
		render(ctx);
	}

	/** Zeichnet die Animation ueber den ganzen Bildschirm (auch aus Screens heraus aufrufbar). */
	public static void render(DrawContext ctx) {
		if (!active()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		float t = System.currentTimeMillis() - startMs;
		int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

		// Ein- und Ausblenden
		float fadeIn = Math.min(1, t / 300f);
		float fadeOut = Math.min(1, (DURATION - t) / 700f);
		float alpha = Math.max(0, Math.min(fadeIn, fadeOut));

		// Abdunkeln
		ctx.fill(0, 0, sw, sh, argb((int) (0x70 * alpha), 0x000000));

		// Konfetti
		for (int i = 0; i < PARTICLES; i++) {
			float lt = t - delay[i];
			if (lt < 0) continue;
			int y = (int) (-10 + lt * speed[i]);
			if (y > sh) continue;
			int x = (int) (px[i] * sw + Math.sin(lt / 260f + phase[i]) * sway[i]);
			int s = size[i];
			// "Drehen": Breite wechselt mit der Zeit
			int wdt = Math.max(1, (int) Math.abs(Math.cos(lt / 180f + phase[i]) * s * 1.6f));
			ctx.fill(x, y, x + wdt, y + s, argb((int) (255 * alpha), color[i]));
		}

		// Banner springt rein (ease-out-back)
		float pop = easeOutBack(Math.min(1, t / 550f));
		int bw = 240, bh = 84;
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(sw / 2f, sh / 2f - 10);
		ctx.getMatrices().scale(pop, pop);
		int x1 = -bw / 2, y1 = -bh / 2, x2 = bw / 2, y2 = bh / 2;
		int a = (int) (255 * alpha);

		// Leuchtender, pulsierender Rahmen
		float pulse = 0.5f + 0.5f * (float) Math.sin(t / 160f);
		int glow = (int) ((60 + 80 * pulse) * alpha);
		ctx.fill(x1 - 3, y1 - 3, x2 + 3, y2 + 3, argb(glow, Theme.accentRgb()));
		ctx.fill(x1, y1, x2, y2, argb((int) (235 * alpha), Theme.mix(Theme.background() & 0xFFFFFF, 0x000000, 0.2f)));
		ctx.fill(x1, y1, x2, y1 + 2, argb(a, Theme.accentRgb()));
		ctx.fill(x1, y2 - 2, x2, y2, argb(a, Theme.accentRgb()));

		if (a > 8) {
			// Titel
			String title = "★ GEWINNER ★";
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(0, y1 + 8);
			ctx.getMatrices().scale(1.5f, 1.5f);
			ctx.drawTextWithShadow(tr, title, -tr.getWidth(title) / 2, 0, argb(a, Theme.accentRgb()));
			ctx.getMatrices().popMatrix();

			// Kopf + Name gross
			int headSize = 24;
			int nameW = tr.getWidth(winner) * 2;
			int rowW = headSize + 8 + nameW;
			int rx = -rowW / 2;
			int ry = y1 + 26;
			Ui.head(ctx, winner, rx, ry, headSize);
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(rx + headSize + 8, ry + 4);
			ctx.getMatrices().scale(2f, 2f);
			ctx.drawTextWithShadow(tr, winner, 0, 0, argb(a, 0xFFFFFF));
			ctx.getMatrices().popMatrix();

			// Item + Betrag
			String line = (item.getCount() > 1 ? item.getCount() + "x " : "") + item.getName().getString() + "  für  " + Money.fmt(amount);
			line = tr.trimToWidth(line, bw - 34);
			int lw = 18 + tr.getWidth(line);
			int lx = -lw / 2;
			int ly = y2 - 22;
			ctx.drawItem(item, lx, ly - 4);
			ctx.drawTextWithShadow(tr, line, lx + 18, ly, argb(a, Theme.good() & 0xFFFFFF));
		}
		ctx.getMatrices().popMatrix();
	}

	private static float easeOutBack(float x) {
		float c1 = 1.70158f, c3 = c1 + 1;
		return (float) (1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2));
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}
}
