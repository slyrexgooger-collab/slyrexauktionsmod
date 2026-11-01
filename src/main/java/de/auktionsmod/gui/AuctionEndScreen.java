package de.auktionsmod.gui;

import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import de.auktionsmod.Payment;
import de.auktionsmod.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Wird nach dem Ende der Auktion gezeigt: Gewinner + alle, die zu spaet gezahlt haben (live). */
public class AuctionEndScreen extends AnimatedScreen {
	private static final int W = 380, H = 240, ROW = 16;

	private int x0, y0, listTop, rows, page;
	private int seenRevision = -1;
	private final long openedMs = System.currentTimeMillis();

	public AuctionEndScreen() {
		super(Text.literal("Auktion beendet"));
	}

	private static AuctionManager m() {
		return AuctionManager.get();
	}

	@Override
	protected void init() {
		AuctionManager m = m();
		seenRevision = m.revision();
		x0 = (width - W) / 2;
		y0 = Math.max(4, (height - H) / 2);
		listTop = y0 + 104;
		rows = Math.max(1, (y0 + H - 50 - listTop) / ROW);

		List<Payment> late = m.latePayments();
		int pages = Math.max(1, (late.size() + rows - 1) / rows);
		page = Math.min(page, pages - 1);
		if (pages > 1) {
			addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> { page = Math.max(0, page - 1); clearAndInit(); })
					.dimensions(x0 + W - 8 - 34, y0 + 82, 16, 14).build()).active = page > 0;
			addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> { page++; clearAndInit(); })
					.dimensions(x0 + W - 8 - 16, y0 + 82, 16, 14).build()).active = page < pages - 1;
		}

		int by = y0 + H - 26;
		int bw = (W - 16 - 6) / 3;
		addDrawableChild(ButtonWidget.builder(Text.literal("Details"), b -> {
			if (client != null) client.setScreen(new AuctionOverviewScreen());
		}).dimensions(x0 + 8, by, bw, 20).tooltip(Tooltip.of(Text.literal("Alle Gebote, Rangliste und Rückzahlungen"))).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Neue Auktion"), b -> { m.clear(); close(); })
				.dimensions(x0 + 8 + bw + 3, by, bw, 20)
				.tooltip(Tooltip.of(Text.literal("Schließt diese Auktion ab. Danach mit einem Item in der Hand O drücken."))).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
				.dimensions(x0 + 8 + (bw + 3) * 2, by, bw, 20).build());
	}

	@Override
	public void tick() {
		super.tick();
		if (!m().hasSession()) {
			close();
			return;
		}
		if (m().revision() != seenRevision) clearAndInit();
	}

	@Override
	protected void renderPanel(DrawContext ctx, int mouseX, int mouseY, float delta) {
		Ui.window(ctx, x0, y0, x0 + W, y0 + H);

		// Gewinner-Karte mit wanderndem Glanz
		int cx1 = x0 + 8, cy1 = y0 + 24, cx2 = x0 + W - 8, cy2 = y0 + 74;
		Ui.card(ctx, cx1, cy1, cx2, cy2);
		if (m().winnerName() != null) {
			float t = ((System.currentTimeMillis() - openedMs) % 3200) / 3200f;
			int sx = cx1 - 40 + Math.round(t * (cx2 - cx1 + 80));
			for (int i = 0; i < 24; i++) {
				int a = (int) (40 * Math.sin(Math.PI * i / 24.0));
				int x = sx + i;
				if (x >= cx1 && x < cx2) ctx.fill(x, cy1, x + 1, cy2, (a << 24) | 0xFFFFFF);
			}
			ctx.fill(cx1, cy1, cx1 + 2, cy2, Theme.accent());
		}

		// Liste der Zuspaetzahler
		Ui.card(ctx, x0 + 8, listTop - 4, x0 + W - 8, listTop + rows * ROW + 2);
		int n = Math.min(rows, m().latePayments().size() - page * rows);
		for (int i = 0; i < n; i++) {
			int ry = listTop + i * ROW;
			boolean hover = mouseX >= x0 + 8 && mouseX < x0 + W - 8 && mouseY >= ry && mouseY < ry + ROW;
			if (hover) ctx.fill(x0 + 9, ry, x0 + W - 9, ry + ROW, Theme.cardHover());
			else if (i % 2 == 1) ctx.fill(x0 + 9, ry, x0 + W - 9, ry + ROW, 0x18FFFFFF);
		}
	}

	@Override
	protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
		AuctionManager m = m();
		var tr = textRenderer;
		boolean cancelled = m.phase() == AuctionManager.Phase.CANCELLED;

		// Kopf
		Ui.text(ctx, tr, cancelled ? "§lAuktion abgebrochen" : "§lAuktion beendet", x0 + 8, y0 + 9, cancelled ? Theme.bad() : Theme.accent());
		String label = "Auktionator: ";
		int sx = x0 + W - 8 - tr.getWidth(label + m.seller()) - 12;
		Ui.text(ctx, tr, label, sx, y0 + 9, Theme.muted());
		Ui.head(ctx, m.seller(), sx + tr.getWidth(label), y0 + 8, 9);
		Ui.text(ctx, tr, m.seller(), sx + tr.getWidth(label) + 12, y0 + 9, Theme.text());

		// Gewinner
		String winner = m.winnerName();
		Ui.bigItem(ctx, m.item(), x0 + W - 8 - 44, y0 + 33, 2f);
		if (winner != null) {
			Ui.head(ctx, winner, x0 + 16, y0 + 33, 32);
			Ui.text(ctx, tr, "★ Gewinner", x0 + 56, y0 + 32, Theme.accent());
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(x0 + 56, y0 + 43);
			ctx.getMatrices().scale(1.5f, 1.5f);
			ctx.drawTextWithShadow(tr, tr.trimToWidth(winner, 150), 0, 0, Theme.text());
			ctx.getMatrices().popMatrix();
			String what = (m.item().getCount() > 1 ? m.item().getCount() + "x " : "") + m.item().getName().getString();
			Ui.text(ctx, tr, tr.trimToWidth(what + " für ", 170) , x0 + 56, y0 + 60, Theme.muted());
			Ui.text(ctx, tr, Money.fmt(m.leaderBid()), x0 + 56 + tr.getWidth(tr.trimToWidth(what + " für ", 170)), y0 + 60, Theme.good());
		} else {
			Ui.text(ctx, tr, cancelled ? "Die Auktion wurde abgebrochen – niemand gewinnt." : "Es gab kein gültiges Gebot.",
					x0 + 16, y0 + 45, Theme.muted());
		}

		// Zu spaet gezahlt
		List<Payment> late = m.latePayments();
		Ui.text(ctx, tr, "§lZu spät gezahlt §r(" + late.size() + ")", x0 + 8, y0 + 86, Theme.text());
		if (!late.isEmpty()) {
			int right = x0 + W - 8 - (late.size() > rows ? 40 : 0);
			Ui.textRight(ctx, tr, "Summe: " + Money.fmt(m.totalLate()), right, y0 + 86, Theme.bad());
		}

		int px = x0 + 12, right = x0 + W - 12;
		if (late.isEmpty()) {
			float p = pulse(1600);
			Ui.textCenter(ctx, tr, "✔ Niemand hat zu spät gezahlt.", x0 + W / 2, listTop + 4 + Math.round(p), Theme.good());
			Ui.textCenter(ctx, tr, "Neue Zahlungen erscheinen hier automatisch.", x0 + W / 2, listTop + 18, Theme.muted());
		}
		long now = System.currentTimeMillis();
		for (int i = 0; i < rows; i++) {
			int idx = page * rows + i;
			if (idx >= late.size()) break;
			Payment p = late.get(idx);
			// Zeilen erscheinen nacheinander bzw. gleiten bei neuem Eintrag herein
			long appear = Math.max(openedMs + 120 + i * 70L, p.createdMs);
			float f = Math.min(1, Math.max(0, (now - appear) / 260f));
			if (f <= 0) continue;
			int slide = Math.round((1 - easeOutCubic(f)) * 20);
			int ry = listTop + i * ROW + 4;
			Ui.text(ctx, tr, p.time, px + slide, ry, Theme.muted());
			Ui.head(ctx, p.player, px + 52 + slide, ry - 1, 9);
			Ui.text(ctx, tr, tr.trimToWidth(p.player, 90), px + 64 + slide, ry, Theme.text());
			Ui.text(ctx, tr, Money.fmt(p.amount), px + 160 + slide, ry, Theme.bad());
			Ui.text(ctx, tr, "+" + Money.time(p.secondsAfterEnd) + " zu spät", px + 222 + slide, ry, Theme.muted());
			if (now - p.createdMs < 1500) Ui.textRight(ctx, tr, "NEU", right, ry, Theme.accent());
		}

		Ui.textCenter(ctx, tr, "Zu späte Zahlungen zählen nicht – zahle sie selbst per /pay zurück.",
				x0 + W / 2, y0 + H - 40, Theme.muted());
	}
}
