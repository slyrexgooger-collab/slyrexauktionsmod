package de.auktionsmod.gui;

import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import de.auktionsmod.Payment;
import de.auktionsmod.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Uebersicht der laufenden/beendeten Auktion: Gebote, Rangliste, Rueckzahlungen. */
public class AuctionOverviewScreen extends Screen {
	private static final int ROW = 15;
	private static final String[] TABS = {"Gebote", "Rangliste", "Rückzahlungen"};

	private static int tab = 0;
	private int page;
	private int seenRevision = -1;
	private int x0, y0, w, h, listTop, rows;

	public AuctionOverviewScreen() {
		super(Text.literal("Auktion"));
	}

	private static AuctionManager m() {
		return AuctionManager.get();
	}

	private int entryCount() {
		AuctionManager m = m();
		return switch (tab) {
			case 1 -> m.ranking().size();
			case 2 -> m.refundList().size();
			default -> m.validBids().size();
		};
	}

	@Override
	protected void init() {
		AuctionManager m = m();
		seenRevision = m.revision();
		w = Math.min(width - 12, 440);
		h = Math.min(height - 12, 256);
		x0 = (width - w) / 2;
		y0 = (height - h) / 2;
		listTop = y0 + 86;
		rows = Math.max(1, (y0 + h - 34 - listTop) / ROW);

		// Tabs
		int tx = x0 + 8;
		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			int count = switch (i) {
				case 1 -> m.ranking().size();
				case 2 -> m.refundList().size();
				default -> m.validBids().size();
			};
			ButtonWidget b = ButtonWidget.builder(Text.literal(TABS[i] + " (" + count + ")"), btn -> {
				tab = idx;
				page = 0;
				clearAndInit();
			}).dimensions(tx, y0 + 54, 96, 16).build();
			b.active = tab != i;
			addDrawableChild(b);
			tx += 98;
		}

		// Seiten
		int pages = Math.max(1, (entryCount() + rows - 1) / rows);
		page = Math.min(page, pages - 1);
		if (pages > 1) {
			int py = y0 + 54;
			addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> { page = Math.max(0, page - 1); clearAndInit(); })
					.dimensions(x0 + w - 8 - 34, py, 16, 16).build()).active = page > 0;
			addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> { page++; clearAndInit(); })
					.dimensions(x0 + w - 8 - 16, py, 16, 16).build()).active = page < pages - 1;
		}

		// Erstatten-Knoepfe in Rangliste / Rueckzahlungen
		if (tab == 1 || tab == 2) {
			List<String> keys = tab == 1 ? m.ranking() : m.refundList();
			for (int i = 0; i < rows; i++) {
				int idx = page * rows + i;
				if (idx >= keys.size()) break;
				String k = keys.get(idx);
				double due = m.refundDue(k);
				if (due > 0) {
					addDrawableChild(ButtonWidget.builder(Text.literal("Erstatten"), b -> m.refund(k))
							.dimensions(x0 + w - 8 - 58, listTop + i * ROW + 1, 54, 13)
							.tooltip(Tooltip.of(Text.literal("Sendet /pay " + m.nameOf(k) + " " + Money.command(due)))).build());
				}
			}
		}

		// Untere Knoepfe (Rueckzahlungen nur einzeln im Tab "Rückzahlungen")
		int by = y0 + h - 26;
		boolean running = m.phase() == AuctionManager.Phase.RUNNING;
		int count = running ? 4 : 3;
		int bw = (w - 16 - 3 * (count - 1)) / count;
		int bx = x0 + 8;
		if (running) {
			addDrawableChild(ButtonWidget.builder(Text.literal("Jetzt beenden"), b -> { m.endNow(); clearAndInit(); })
					.dimensions(bx, by, bw, 20)
					.tooltip(Tooltip.of(Text.literal("Beendet die Auktion sofort – das Höchstgebot gewinnt."))).build());
			bx += bw + 3;
			addDrawableChild(ButtonWidget.builder(Text.literal("§cAbbrechen"), b -> { m.cancel(); clearAndInit(); })
					.dimensions(bx, by, bw, 20)
					.tooltip(Tooltip.of(Text.literal("Niemand gewinnt – danach unter Rückzahlungen einzeln erstatten."))).build());
		} else {
			addDrawableChild(ButtonWidget.builder(Text.literal("Neue Auktion"), b -> { m.clear(); close(); })
					.dimensions(bx, by, bw, 20)
					.tooltip(Tooltip.of(Text.literal("Schließt diese Auktion ab. Danach mit einem Item in der Hand O drücken."))).build());
		}
		bx += bw + 3;
		addDrawableChild(ButtonWidget.builder(Text.literal("Design"), b -> {
			if (client != null) client.setScreen(new DesignScreen(this));
		}).dimensions(bx, by, bw, 20).build());
		bx += bw + 3;
		addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
				.dimensions(bx, by, bw, 20).build());
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
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.renderBackground(ctx, mouseX, mouseY, delta);
		Ui.window(ctx, x0, y0, x0 + w, y0 + h);
		Ui.card(ctx, x0 + 6, y0 + 8, x0 + 46, y0 + 48);
		Ui.card(ctx, x0 + w - 156, y0 + 8, x0 + w - 6, y0 + 48);
		Ui.card(ctx, x0 + 6, listTop - 13, x0 + w - 6, listTop + rows * ROW + 2);
		ctx.fill(x0 + 6, listTop - 2, x0 + w - 6, listTop - 1, Theme.border());
		// Zeilen-Hover + Streifen
		int n = Math.min(rows, entryCount() - page * rows);
		for (int i = 0; i < n; i++) {
			int ry = listTop + i * ROW;
			boolean hover = mouseX >= x0 + 6 && mouseX < x0 + w - 6 && mouseY >= ry && mouseY < ry + ROW;
			if (hover) ctx.fill(x0 + 7, ry, x0 + w - 7, ry + ROW, Theme.cardHover());
			else if (i % 2 == 1) ctx.fill(x0 + 7, ry, x0 + w - 7, ry + ROW, 0x18FFFFFF);
		}
		// aktiver Tab unterstrichen
		ctx.fill(x0 + 8 + tab * 98, y0 + 70, x0 + 8 + tab * 98 + 96, y0 + 72, Theme.accent());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		AuctionManager m = m();
		var tr = textRenderer;

		// ---- Kopf: Item, Auktionator, Zeit
		Ui.bigItem(ctx, m.item(), x0 + 10, y0 + 12, 2f);
		String title = (m.item().getCount() > 1 ? m.item().getCount() + "x " : "") + m.item().getName().getString();
		int midW = w - 156 - 56 - 8;
		Ui.text(ctx, tr, "§l" + tr.trimToWidth(title, midW - 8), x0 + 52, y0 + 9, Theme.accent());
		Ui.head(ctx, m.seller(), x0 + 52, y0 + 20, 9);
		Ui.text(ctx, tr, "Auktionator: ", x0 + 64, y0 + 21, Theme.muted());
		Ui.text(ctx, tr, m.seller(), x0 + 64 + tr.getWidth("Auktionator: "), y0 + 21, Theme.text());

		String state;
		int stateColor;
		switch (m.phase()) {
			case RUNNING -> { state = "Läuft – noch " + Money.time(m.remainingSeconds()); stateColor = m.remainingSeconds() <= 10 ? Theme.bad() : Theme.good(); }
			case ENDED -> { state = "Beendet"; stateColor = Theme.accent(); }
			case CANCELLED -> { state = "Abgebrochen"; stateColor = Theme.bad(); }
			default -> { state = ""; stateColor = Theme.text(); }
		}
		Ui.text(ctx, tr, state, x0 + 52, y0 + 33, stateColor);
		Ui.progress(ctx, x0 + 52, y0 + 44, midW - 4, 3, m.timeFraction());

		// ---- Hoechstgebot-Karte
		int lx = x0 + w - 152;
		boolean ended = m.phase() == AuctionManager.Phase.ENDED;
		String leader = ended ? m.winnerName() : m.leaderName();
		Ui.text(ctx, tr, ended ? "Gewinner" : "Höchstgebot", lx, y0 + 11, Theme.muted());
		if (leader != null) {
			Ui.head(ctx, leader, lx, y0 + 22, 16);
			Ui.text(ctx, tr, tr.trimToWidth(leader, 120), lx + 20, y0 + 22, Theme.text());
			Ui.text(ctx, tr, Money.fmt(m.leaderBid()), lx + 20, y0 + 32, Theme.good());
		} else {
			Ui.text(ctx, tr, "Noch kein gültiges Gebot", lx, y0 + 26, Theme.muted());
		}
		if (m.phase() == AuctionManager.Phase.RUNNING) {
			Ui.textRight(ctx, tr, "Min. " + Money.fmt(m.nextMinimum()), x0 + w - 10, y0 + 11, Theme.accent());
		}

		// ---- Info rechts neben den Tabs
		String sum = "Erhalten: §a" + Money.fmt(m.totalValid());
		int infoRight = x0 + w - 8 - (entryCount() > rows ? 40 : 0);
		Ui.textRight(ctx, tr, sum, infoRight, y0 + 58, Theme.muted());

		// ---- Liste
		int px = x0 + 10;
		int right = x0 + w - 10;
		switch (tab) {
			case 1 -> drawRanking(ctx, m, px, right);
			case 2 -> drawRefunds(ctx, m, px, right);
			default -> drawBids(ctx, m, px, right);
		}

		WinAnimation.render(ctx);
	}

	private void drawBids(DrawContext ctx, AuctionManager m, int px, int right) {
		var tr = textRenderer;
		int c1 = px, c2 = px + 52, c3 = right - 150, c4 = right;
		Ui.text(ctx, tr, "Zeit", c1, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Spieler", c2, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Zahlung", c3, listTop - 11, Theme.muted());
		Ui.textRight(ctx, tr, "Gebot gesamt", c4, listTop - 11, Theme.muted());

		List<Payment> list = m.validBids();
		if (list.isEmpty()) {
			Ui.text(ctx, tr, "Noch keine gültigen Gebote.", px, listTop + 4, Theme.muted());
			return;
		}
		for (int i = 0; i < rows; i++) {
			int idx = page * rows + i;
			if (idx >= list.size()) break;
			Payment p = list.get(idx);
			int ry = listTop + i * ROW + 4;
			Ui.text(ctx, tr, p.time, c1, ry, Theme.muted());
			Ui.head(ctx, p.player, c2, ry - 1, 9);
			Ui.text(ctx, tr, tr.trimToWidth(p.player, c3 - c2 - 20), c2 + 12, ry, Theme.text());
			Ui.text(ctx, tr, "+" + Money.fmt(p.amount), c3, ry, Theme.muted());
			Ui.textRight(ctx, tr, Money.fmt(p.totalAfter), c4, ry, idx == 0 ? Theme.good() : Theme.text());
		}
	}

	private void drawRanking(DrawContext ctx, AuctionManager m, int px, int right) {
		var tr = textRenderer;
		int cGebot = right - 170, cStatus = right - 110;
		Ui.text(ctx, tr, "#", px, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Spieler", px + 18, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Gebot", cGebot, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Status", cStatus, listTop - 11, Theme.muted());

		List<String> list = m.ranking();
		if (list.isEmpty()) {
			Ui.text(ctx, tr, "Noch keine gültigen Gebote.", px, listTop + 4, Theme.muted());
			return;
		}
		for (int i = 0; i < rows; i++) {
			int idx = page * rows + i;
			if (idx >= list.size()) break;
			String k = list.get(idx);
			int ry = listTop + i * ROW + 4;
			Ui.text(ctx, tr, (idx + 1) + ".", px, ry, idx == 0 ? Theme.accent() : Theme.muted());
			Ui.head(ctx, m.nameOf(k), px + 18, ry - 1, 9);
			Ui.text(ctx, tr, tr.trimToWidth(m.nameOf(k), cGebot - px - 36), px + 30, ry, Theme.text());
			Ui.text(ctx, tr, Money.fmt(m.bidOf(k)), cGebot, ry, idx == 0 ? Theme.good() : Theme.text());

			String status;
			int color;
			if (m.isWinner(k)) { status = "★ Gewinner"; color = Theme.good(); }
			else if (m.isLeader(k)) { status = "★ Führt"; color = Theme.good(); }
			else if (m.refundDue(k) <= 0 && m.refundedOf(k) > 0) { status = "Erstattet"; color = Theme.accent(); }
			else { status = "Überboten"; color = Theme.muted(); }
			Ui.text(ctx, tr, status, cStatus, ry, color);
		}
	}

	private void drawRefunds(DrawContext ctx, AuctionManager m, int px, int right) {
		var tr = textRenderer;
		int cOpen = right - 190, cDone = right - 125;
		Ui.text(ctx, tr, "Spieler", px, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Offen", cOpen, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Erstattet", cDone, listTop - 11, Theme.muted());

		List<String> list = m.refundList();
		if (list.isEmpty()) {
			Ui.text(ctx, tr, m.phase() == AuctionManager.Phase.RUNNING
					? "Nichts offen. Überbotene werden nach dem Ende hier gelistet." : "Keine Rückzahlungen nötig.", px, listTop + 4, Theme.muted());
			return;
		}
		for (int i = 0; i < rows; i++) {
			int idx = page * rows + i;
			if (idx >= list.size()) break;
			String k = list.get(idx);
			int ry = listTop + i * ROW + 4;
			Ui.head(ctx, m.nameOf(k), px, ry - 1, 9);
			Ui.text(ctx, tr, tr.trimToWidth(m.nameOf(k), cOpen - px - 18), px + 12, ry, Theme.text());
			double due = m.refundDue(k);
			Ui.text(ctx, tr, due > 0 ? Money.fmt(due) : "–", cOpen, ry, due > 0 ? Theme.bad() : Theme.muted());
			double done = m.refundedOf(k);
			Ui.text(ctx, tr, done > 0 ? Money.fmt(done) : "–", cDone, ry, done > 0 ? Theme.good() : Theme.muted());
			if (due <= 0) Ui.textRight(ctx, tr, "✔", right - 4, ry, Theme.good());
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
