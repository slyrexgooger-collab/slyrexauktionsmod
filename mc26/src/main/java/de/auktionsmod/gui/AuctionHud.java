package de.auktionsmod.gui;

import de.auktionsmod.AuctionConfig;
import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import de.auktionsmod.Payment;
import de.auktionsmod.Theme;
import de.auktionsmod.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Info-Box waehrend der Auktion. Position, Groesse, Farben und Zeilen sind im Design-Menue einstellbar. */
public final class AuctionHud {
	public static final int WIDTH = 180;

	/** Alles, was die Box anzeigt – kommt entweder aus der echten Auktion oder ist eine Vorschau. */
	public record Model(ItemStack item, String seller, String state, int stateColor, float progress,
	                    String leader, double leaderBid, double minimum, List<Payment> lines) {}

	private AuctionHud() {}

	public static void render(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		AuctionManager m = AuctionManager.get();
		AuctionConfig c = AuctionConfig.get();
		if (!c.showHud || !m.hasSession()) return;
		Screen current = Compat.screen();
		if (current instanceof AuctionOverviewScreen || current instanceof DesignScreen) return;

		Model model = fromManager(m);
		float scale = Math.max(0.5f, Math.min(2f, c.hudScale / 100f));
		int w = Math.round(WIDTH * scale);
		int h = Math.round(height(model) * scale);
		int sw = ctx.guiWidth(), sh = ctx.guiHeight();
		int x, y;
		switch (Theme.HudPos.of(c.hudPosition)) {
			case OBEN_MITTE -> { x = (sw - w) / 2 + c.hudOffsetX - 6; y = c.hudOffsetY; }
			case OBEN_RECHTS -> { x = sw - w - c.hudOffsetX; y = c.hudOffsetY; }
			case UNTEN_LINKS -> { x = c.hudOffsetX; y = sh - h - c.hudOffsetY; }
			case UNTEN_RECHTS -> { x = sw - w - c.hudOffsetX; y = sh - h - c.hudOffsetY; }
			default -> { x = c.hudOffsetX; y = c.hudOffsetY; }
		}
		draw(ctx, mc.font, model, x, y, scale);
	}

	public static Model fromManager(AuctionManager m) {
		String state;
		int color;
		switch (m.phase()) {
			case RUNNING -> {
				long rem = m.remainingSeconds();
				state = "Noch " + Money.time(rem);
				color = rem <= 10 ? Theme.bad() : Theme.text();
			}
			case ENDED -> { state = "Beendet"; color = Theme.accent(); }
			case CANCELLED -> { state = "Abgebrochen"; color = Theme.bad(); }
			default -> { state = ""; color = Theme.text(); }
		}
		String lead = m.phase() == AuctionManager.Phase.ENDED ? m.winnerName() : m.leaderName();
		return new Model(m.item(), m.seller(), state, color, m.timeFraction(), lead, m.leaderBid(), m.nextMinimum(), m.validBids());
	}

	/** Beispieldaten fuer die Vorschau im Design-Menue. */
	public static Model preview() {
		String me = AuctionManager.myName().isEmpty() ? "Du" : AuctionManager.myName();
		List<Payment> lines = new ArrayList<>();
		String[][] demo = {{"Alex", "1500"}, {"Steve", "1200"}, {"Alex", "800"}, {"Notch", "500"}, {"Steve", "300"}, {"Bob", "200"}, {"Kim", "150"}, {"Max", "100"}};
		for (String[] d : demo) {
			Payment p = new Payment(d[0], Double.parseDouble(d[1]));
			p.valid = true;
			p.totalAfter = p.amount;
			lines.add(p);
		}
		return new Model(new ItemStack(Items.DIAMOND_SWORD), me, "Noch 2:31", Theme.text(), 0.6f, "Alex", 1500, 1510, lines);
	}

	public static int height(Model m) {
		AuctionConfig c = AuctionConfig.get();
		int lines = Math.min(Math.max(0, c.hudLines), m.lines().size());
		int h = c.hudShowSeller ? 62 : 52;
		if (lines > 0) h += 4 + lines * 10;
		return h + 4;
	}

	public static void draw(GuiGraphicsExtractor ctx, Font tr, Model m, int x, int y, float scale) {
		AuctionConfig c = AuctionConfig.get();
		ctx.pose().pushMatrix();
		ctx.pose().translate(x, y);
		ctx.pose().scale(scale, scale);

		int w = WIDTH, h = height(m);
		Ui.window(ctx, 0, 0, w, h);

		// Item + Titel
		Ui.card(ctx, 4, 5, 24, 25);
		ctx.item(m.item(), 6, 7);
		String title = (m.item().getCount() > 1 ? m.item().getCount() + "x " : "") + m.item().getHoverName().getString();
		Ui.text(ctx, tr, tr.plainSubstrByWidth(title, w - 34), 29, 6, Theme.accent());
		Ui.text(ctx, tr, m.state(), 29, 16, m.stateColor());
		Ui.textRight(ctx, tr, "Min " + Money.fmt(m.minimum()), w - 5, 16, Theme.muted());

		int y0 = 28;
		Ui.progress(ctx, 4, y0, w - 8, 2, m.progress());
		y0 += 6;

		if (c.hudShowSeller) {
			Ui.head(ctx, m.seller(), 5, y0, 8);
			Ui.text(ctx, tr, "Auktionator: ", 16, y0, Theme.muted());
			Ui.text(ctx, tr, tr.plainSubstrByWidth(m.seller(), w - 90), 16 + tr.width("Auktionator: "), y0, Theme.text());
			y0 += 10;
		}

		// Hoechstgebot
		Ui.card(ctx, 4, y0, w - 4, y0 + 14);
		if (m.leader() != null) {
			Ui.head(ctx, m.leader(), 7, y0 + 3, 8);
			Ui.text(ctx, tr, "★ " + tr.plainSubstrByWidth(m.leader(), 80), 18, y0 + 3, Theme.text());
			Ui.textRight(ctx, tr, Money.fmt(m.leaderBid()), w - 7, y0 + 3, Theme.good());
		} else {
			Ui.text(ctx, tr, "Noch kein Gebot", 7, y0 + 3, Theme.muted());
		}
		y0 += 18;

		// Letzte gueltige Gebote
		int lines = Math.min(Math.max(0, c.hudLines), m.lines().size());
		for (int i = 0; i < lines; i++) {
			Payment p = m.lines().get(i);
			Ui.text(ctx, tr, tr.plainSubstrByWidth(p.player, 90), 7, y0, i == 0 ? Theme.text() : Theme.muted());
			Ui.textRight(ctx, tr, Money.fmt(p.totalAfter), w - 7, y0, i == 0 ? Theme.good() : Theme.muted());
			y0 += 10;
		}

		ctx.pose().popMatrix();
	}
}
