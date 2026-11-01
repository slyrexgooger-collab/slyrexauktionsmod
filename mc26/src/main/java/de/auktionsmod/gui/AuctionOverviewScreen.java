package de.auktionsmod.gui;

import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import de.auktionsmod.Payment;
import de.auktionsmod.Theme;
import net.minecraft.minecraft.gui.GuiGraphicsExtractor;
import net.minecraft.minecraft.gui.components.Tooltip;
import net.minecraft.minecraft.gui.components.Button;
import de.auktionsmod.compat.Compat;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Uebersicht der laufenden/beendeten Auktion: Gebote, Rangliste, offene Rueckzahlungen (nur Info). */
public class AuctionOverviewScreen extends AnimatedScreen {
	private static final int ROW = 15;
	private static final String[] TABS = {"Gebote", "Rangliste", "Rückzahlungen"};
	private static final long FLASH_MS = 1600;

	private static int tab = 0;
	private int page;
	private int seenRevision = -1;
	private int x0, y0, w, h, listTop, rows;
	private float tabX = -1;

	public AuctionOverviewScreen() {
		super(Component.literal("Auktion"));
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
			Button b = Button.builder(Component.literal(TABS[i] + " (" + count + ")"), btn -> {
				tab = idx;
				page = 0;
				rebuildWidgets();
			}).bounds(tx, y0 + 54, 96, 16).build();
			b.active = tab != i;
			addRenderableWidget(b);
			tx += 98;
		}

		// Seiten
		int pages = Math.max(1, (entryCount() + rows - 1) / rows);
		page = Math.min(page, pages - 1);
		if (pages > 1) {
			int py = y0 + 54;
			addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); })
					.bounds(x0 + w - 8 - 34, py, 16, 16).build()).active = page > 0;
			addRenderableWidget(Button.builder(Component.literal("▶"), b -> { page++; rebuildWidgets(); })
					.bounds(x0 + w - 8 - 16, py, 16, 16).build()).active = page < pages - 1;
		}

		// Untere Knoepfe
		int by = y0 + h - 26;
		int bw = (w - 16 - 9) / 4;
		int bx = x0 + 8;
		if (m.phase() == AuctionManager.Phase.RUNNING) {
			addRenderableWidget(Button.builder(Component.literal("Jetzt beenden"), b -> { m.endNow(); rebuildWidgets(); })
					.bounds(bx, by, bw, 20)
					.tooltip(Tooltip.create(Component.literal("Beendet die Auktion sofort – das Höchstgebot gewinnt."))).build());
			addRenderableWidget(Button.builder(Component.literal("§cAbbrechen"), b -> { m.cancel(); rebuildWidgets(); })
					.bounds(bx + bw + 3, by, bw, 20)
					.tooltip(Tooltip.create(Component.literal("Niemand gewinnt."))).build());
		} else {
			addRenderableWidget(Button.builder(Component.literal("Neue Auktion"), b -> { m.clear(); onClose(); })
					.bounds(bx, by, bw, 20)
					.tooltip(Tooltip.create(Component.literal("Schließt diese Auktion ab. Danach mit einem Item in der Hand O drücken."))).build());
			addRenderableWidget(Button.builder(Component.literal("Zu spät (" + m.latePayments().size() + ")"), b -> {
				Compat.setScreen(new AuctionEndScreen());
			}).bounds(bx + bw + 3, by, bw, 20)
					.tooltip(Tooltip.create(Component.literal("Wer nach dem Ende noch gezahlt hat"))).build());
		}
		addRenderableWidget(Button.builder(Component.literal("Design"), b -> {
			Compat.setScreen(new DesignScreen(this));
		}).bounds(bx + (bw + 3) * 2, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
				.bounds(bx + (bw + 3) * 3, by, bw, 20).build());
	}

	@Override
	public void tick() {
		super.tick();
		if (!m().hasSession()) {
			onClose();
			return;
		}
		if (m().revision() != seenRevision) rebuildWidgets();
	}

	@Override
	protected void renderPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		AuctionManager m = m();
		Ui.window(ctx, x0, y0, x0 + w, y0 + h);
		Ui.card(ctx, x0 + 6, y0 + 8, x0 + 46, y0 + 48);

		// Hoechstgebot-Karte – pulsiert kurz bei neuem Fuehrenden
		int lx1 = x0 + w - 156, lx2 = x0 + w - 6;
		long since = System.currentTimeMillis() - m.leaderChangedMs();
		if (m.leaderChangedMs() > 0 && since < 1800) {
			float f = 1 - since / 1800f;
			int a = (int) (f * (120 + 100 * pulse(400)));
			ctx.fill(lx1 - 2, y0 + 6, lx2 + 2, y0 + 50, Theme.accent(Math.max(0, Math.min(255, a))));
		}
		ctx.fill(lx1, y0 + 8, lx2, y0 + 48, 0xFF000000 | (Theme.background() & 0xFFFFFF)); // Glow nicht durchscheinen lassen
		Ui.card(ctx, lx1, y0 + 8, lx2, y0 + 48);

		// Liste
		Ui.card(ctx, x0 + 6, listTop - 13, x0 + w - 6, listTop + rows * ROW + 2);
		ctx.fill(x0 + 6, listTop - 2, x0 + w - 6, listTop - 1, Theme.border());
		List<Payment> bids = tab == 0 ? m.validBids() : List.of();
		int n = Math.min(rows, entryCount() - page * rows);
		long now = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {
			int ry = listTop + i * ROW;
			boolean hover = mouseX >= x0 + 6 && mouseX < x0 + w - 6 && mouseY >= ry && mouseY < ry + ROW;
			if (hover) ctx.fill(x0 + 7, ry, x0 + w - 7, ry + ROW, Theme.cardHover());
			else if (i % 2 == 1) ctx.fill(x0 + 7, ry, x0 + w - 7, ry + ROW, 0x18FFFFFF);
			// Neue Gebote leuchten kurz auf
			if (tab == 0) {
				int idx = page * rows + i;
				if (idx < bids.size()) {
					long age = now - bids.get(idx).createdMs;
					if (age < FLASH_MS) {
						int a = (int) (110 * (1 - age / (float) FLASH_MS));
						ctx.fill(x0 + 7, ry, x0 + w - 7, ry + ROW, Theme.accent(a));
					}
				}
			}
		}

		// Tab-Markierung gleitet
		float target = x0 + 8 + tab * 98;
		if (tabX < 0) tabX = target;
		tabX = approach(tabX, target, 0.25f);
		ctx.fill(Math.round(tabX), y0 + 70, Math.round(tabX) + 96, y0 + 72, Theme.accent());
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		AuctionManager m = m();
		var tr = font;

		// ---- Kopf: Item, Auktionator, Zeit
		float bob = (float) Math.sin(System.currentTimeMillis() / 500.0) * 1.2f;
		Ui.bigItem(ctx, m.item(), x0 + 10, Math.round(y0 + 12 + bob), 2f);
		String title = (m.item().getCount() > 1 ? m.item().getCount() + "x " : "") + m.item().getHoverName().getString();
		int midW = w - 156 - 56 - 8;
		Ui.text(ctx, tr, "§l" + tr.plainSubstrByWidth(title, midW - 8), x0 + 52, y0 + 9, Theme.accent());
		Ui.head(ctx, m.seller(), x0 + 52, y0 + 20, 9);
		Ui.text(ctx, tr, "Auktionator: ", x0 + 64, y0 + 21, Theme.muted());
		Ui.text(ctx, tr, m.seller(), x0 + 64 + tr.width("Auktionator: "), y0 + 21, Theme.text());

		String state;
		int stateColor;
		switch (m.phase()) {
			case RUNNING -> {
				long rem = m.remainingSeconds();
				state = "Läuft – noch " + Money.time(rem);
				stateColor = rem <= 10 ? (pulse(500) > 0.5f ? Theme.bad() : Theme.text()) : Theme.good();
			}
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
			Ui.text(ctx, tr, tr.plainSubstrByWidth(leader, 120), lx + 20, y0 + 22, Theme.text());
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
	}

	private void drawBids(GuiGraphicsExtractor ctx, AuctionManager m, int px, int right) {
		var tr = font;
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
		long now = System.currentTimeMillis();
		for (int i = 0; i < rows; i++) {
			int idx = page * rows + i;
			if (idx >= list.size()) break;
			Payment p = list.get(idx);
			// neue Zeilen gleiten von rechts herein
			long age = now - p.createdMs;
			int slide = age < 300 ? Math.round((1 - easeOutCubic(age / 300f)) * 24) : 0;
			int ry = listTop + i * ROW + 4;
			Ui.text(ctx, tr, p.time, c1 + slide, ry, Theme.muted());
			Ui.head(ctx, p.player, c2 + slide, ry - 1, 9);
			Ui.text(ctx, tr, tr.plainSubstrByWidth(p.player, c3 - c2 - 20), c2 + 12 + slide, ry, Theme.text());
			Ui.text(ctx, tr, "+" + Money.fmt(p.amount), c3 + slide, ry, Theme.muted());
			Ui.textRight(ctx, tr, Money.fmt(p.totalAfter), c4, ry, idx == 0 ? Theme.good() : Theme.text());
		}
	}

	private void drawRanking(GuiGraphicsExtractor ctx, AuctionManager m, int px, int right) {
		var tr = font;
		int cGebot = right - 170, cStatus = right - 90;
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
			Ui.text(ctx, tr, tr.plainSubstrByWidth(m.nameOf(k), cGebot - px - 36), px + 30, ry, Theme.text());
			Ui.text(ctx, tr, Money.fmt(m.bidOf(k)), cGebot, ry, idx == 0 ? Theme.good() : Theme.text());

			String status;
			int color;
			if (m.isWinner(k)) { status = "★ Gewinner"; color = Theme.good(); }
			else if (m.isLeader(k)) { status = "★ Führt"; color = Theme.good(); }
			else { status = "Überboten"; color = Theme.muted(); }
			Ui.text(ctx, tr, status, cStatus, ry, color);
		}
	}

	private void drawRefunds(GuiGraphicsExtractor ctx, AuctionManager m, int px, int right) {
		var tr = font;
		int cAmount = right - 200, cCmd = right - 130;
		Ui.text(ctx, tr, "Spieler", px, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Betrag", cAmount, listTop - 11, Theme.muted());
		Ui.text(ctx, tr, "Selbst zurückzahlen mit", cCmd, listTop - 11, Theme.muted());

		List<String> list = m.refundList();
		if (list.isEmpty()) {
			Ui.text(ctx, tr, m.phase() == AuctionManager.Phase.RUNNING
					? "Nichts offen. Überbotene stehen nach dem Ende hier." : "Niemandem muss etwas zurückgezahlt werden.", px, listTop + 4, Theme.muted());
			return;
		}
		for (int i = 0; i < rows; i++) {
			int idx = page * rows + i;
			if (idx >= list.size()) break;
			String k = list.get(idx);
			int ry = listTop + i * ROW + 4;
			Ui.head(ctx, m.nameOf(k), px, ry - 1, 9);
			Ui.text(ctx, tr, tr.plainSubstrByWidth(m.nameOf(k), cAmount - px - 18), px + 12, ry, Theme.text());
			double due = m.refundDue(k);
			Ui.text(ctx, tr, Money.fmt(due), cAmount, ry, Theme.bad());
			Ui.text(ctx, tr, tr.plainSubstrByWidth("/pay " + m.nameOf(k) + " " + Money.command(due), right - cCmd), cCmd, ry, Theme.muted());
		}
	}
}
