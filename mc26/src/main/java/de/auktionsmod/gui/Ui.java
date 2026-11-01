package de.auktionsmod.gui;

import de.auktionsmod.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.ItemStack;

/** Gemeinsame Zeichen-Funktionen fuer alle Fenster und das HUD. */
public final class Ui {
	private Ui() {}

	/** Hauptfenster mit Rahmen und farbiger Kopfleiste. */
	public static void window(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2) {
		ctx.fill(x1, y1, x2, y2, Theme.background());
		outline(ctx, x1, y1, x2, y2, Theme.border());
		ctx.fill(x1, y1, x2, y1 + 2, Theme.accent());
	}

	/** Karte / Listenhintergrund */
	public static void card(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2) {
		ctx.fill(x1, y1, x2, y2, Theme.card());
	}

	public static void outline(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
		ctx.fill(x1, y1, x2, y1 + 1, color);
		ctx.fill(x1, y2 - 1, x2, y2, color);
		ctx.fill(x1, y1, x1 + 1, y2, color);
		ctx.fill(x2 - 1, y1, x2, y2, color);
	}

	public static void progress(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float fraction) {
		ctx.fill(x, y, x + w, y + h, 0x60000000);
		int fw = Math.round(w * Math.max(0, Math.min(1, fraction)));
		int color = fraction < 0.15f ? Theme.bad() : Theme.accent();
		if (fw > 0) ctx.fill(x, y, x + fw, y + h, color);
	}

	public static void text(GuiGraphicsExtractor ctx, Font tr, String s, int x, int y, int color) {
		ctx.text(tr, s, x, y, color);
	}

	public static void textRight(GuiGraphicsExtractor ctx, Font tr, String s, int right, int y, int color) {
		ctx.text(tr, s, right - tr.width(s), y, color);
	}

	public static void textCenter(GuiGraphicsExtractor ctx, Font tr, String s, int cx, int y, int color) {
		ctx.text(tr, s, cx - tr.width(s) / 2, y, color);
	}

	/** Item vergroessert zeichnen (scale 1 = 16px). */
	public static void bigItem(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y, float scale) {
		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.pose().scale(scale, scale);
		ctx.item(stack, 0, 0);
		ctx.pose().popMatrix();
	}

	/** Kopf eines Spielers (Skin aus der Tab-Liste). Ohne Skin: Platzhalter in Akzentfarbe. */
	public static void head(GuiGraphicsExtractor ctx, String name, int x, int y, int size) {
		PlayerInfo entry = entry(name);
		if (entry != null) {
			PlayerFaceExtractor.extractRenderState(ctx, entry.getSkin(), x, y, size);
		} else {
			ctx.fill(x, y, x + size, y + size, Theme.accent(0xA0));
		}
	}

	private static PlayerInfo entry(String name) {
		if (name == null || name.isEmpty()) return null;
		ClientPacketListener nh = Minecraft.getInstance().getConnection();
		if (nh == null) return null;
		return nh.getPlayerInfo(name);
	}
}
