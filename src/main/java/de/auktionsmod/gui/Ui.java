package de.auktionsmod.gui;

import de.auktionsmod.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;

/** Gemeinsame Zeichen-Funktionen fuer alle Fenster und das HUD. */
public final class Ui {
	private Ui() {}

	/** Hauptfenster mit Rahmen und farbiger Kopfleiste. */
	public static void window(DrawContext ctx, int x1, int y1, int x2, int y2) {
		ctx.fill(x1, y1, x2, y2, Theme.background());
		outline(ctx, x1, y1, x2, y2, Theme.border());
		ctx.fill(x1, y1, x2, y1 + 2, Theme.accent());
	}

	/** Karte / Listenhintergrund */
	public static void card(DrawContext ctx, int x1, int y1, int x2, int y2) {
		ctx.fill(x1, y1, x2, y2, Theme.card());
	}

	public static void outline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y1 + 1, color);
		ctx.fill(x1, y2 - 1, x2, y2, color);
		ctx.fill(x1, y1, x1 + 1, y2, color);
		ctx.fill(x2 - 1, y1, x2, y2, color);
	}

	public static void progress(DrawContext ctx, int x, int y, int w, int h, float fraction) {
		ctx.fill(x, y, x + w, y + h, 0x60000000);
		int fw = Math.round(w * Math.max(0, Math.min(1, fraction)));
		int color = fraction < 0.15f ? Theme.bad() : Theme.accent();
		if (fw > 0) ctx.fill(x, y, x + fw, y + h, color);
	}

	public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		ctx.drawTextWithShadow(tr, s, x, y, color);
	}

	public static void textRight(DrawContext ctx, TextRenderer tr, String s, int right, int y, int color) {
		ctx.drawTextWithShadow(tr, s, right - tr.getWidth(s), y, color);
	}

	public static void textCenter(DrawContext ctx, TextRenderer tr, String s, int cx, int y, int color) {
		ctx.drawTextWithShadow(tr, s, cx - tr.getWidth(s) / 2, y, color);
	}

	/** Item vergroessert zeichnen (scale 1 = 16px). */
	public static void bigItem(DrawContext ctx, ItemStack stack, int x, int y, float scale) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate(x, y);
		ctx.getMatrices().scale(scale, scale);
		ctx.drawItem(stack, 0, 0);
		ctx.getMatrices().popMatrix();
	}

	/** Kopf eines Spielers (Skin aus der Tab-Liste). Ohne Skin: Platzhalter in Akzentfarbe. */
	public static void head(DrawContext ctx, String name, int x, int y, int size) {
		PlayerListEntry entry = entry(name);
		if (entry != null) {
			PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), x, y, size);
		} else {
			ctx.fill(x, y, x + size, y + size, Theme.accent(0xA0));
		}
	}

	private static PlayerListEntry entry(String name) {
		if (name == null || name.isEmpty()) return null;
		ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
		if (nh == null) return null;
		return nh.getPlayerListEntry(name);
	}
}
