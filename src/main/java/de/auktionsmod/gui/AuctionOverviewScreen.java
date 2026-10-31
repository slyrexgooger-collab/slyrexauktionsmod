package de.auktionsmod.gui;

import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import de.auktionsmod.Payment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Uebersicht: laufende Auktion, alle eingegangenen Zahlungen mit Echtheits-Status, Bieter & Rueckerstattungen. */
public class AuctionOverviewScreen extends Screen {
	private static final int ROW = 14;

	private int seenRevision = -1;
	private int payPage, bidPage;
	private int left, top, panelW, panelH, payW, listTop, payRows, bidRows;

	public AuctionOverviewScreen() {
		super(Text.literal("Auktion"));
	}

	private AuctionManager m() {
		return AuctionManager.get();
	}

	@Override
	protected void init() {
		AuctionManager m = m();
		seenRevision = m.revision();
		panelW = Math.min(width - 12, 460);
		panelH = Math.min(height - 12, 260);
		left = (width - panelW) / 2;
		top = (height - panelH) / 2;
		payW = (int) (panelW * 0.58);
		listTop = top + 52;
		int listBottom = top + panelH - 54;
		payRows = Math.max(1, (listBottom - listTop) / ROW);
		bidRows = payRows;

		// ---- Zahlungen (neueste zuerst)
		List<Payment> pays = new ArrayList<>(m.payments());
		java.util.Collections.reverse(pays);
		int payPages = Math.max(1, (pays.size() + payRows - 1) / payRows);
		payPage = Math.min(payPage, payPages - 1);
		if (payPages > 1) {
			int py = listTop + payRows * ROW + 1;
			addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> { payPage = Math.max(0, payPage - 1); clearAndInit(); })
					.dimensions(left + 6, py, 16, 12).build()).active = payPage > 0;
			addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> { payPage++; clearAndInit(); })
					.dimensions(left + 24, py, 16, 12).build()).active = payPage < payPages - 1;
		}

		// ---- Bieter mit Erstatten-Knopf
		List<String> bidders = m.bidderKeys();
		int bidPages = Math.max(1, (bidders.size() + bidRows - 1) / bidRows);
		bidPage = Math.min(bidPage, bidPages - 1);
		int bx = left + payW + 8;
		for (int i = 0; i < bidRows; i++) {
			int idx = bidPage * bidRows + i;
			if (idx >= bidders.size()) break;
			String k = bidders.get(idx);
			double due = m.refundDue(k);
			if (due > 0) {
				addDrawableChild(ButtonWidget.builder(Text.literal("Erstatten"), b -> m.refund(k))
						.dimensions(left + panelW - 56, listTop + i * ROW, 50, 12)
						.tooltip(Tooltip.of(Text.literal("Sendet /pay " + m.nameOf(k) + " " + Money.command(due)))).build());
			}
		}
		if (bidPages > 1) {
			int py = listTop + bidRows * ROW + 1;
			addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> { bidPage = Math.max(0, bidPage - 1); clearAndInit(); })
					.dimensions(bx, py, 16, 12).build()).active = bidPage > 0;
			addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> { bidPage++; clearAndInit(); })
					.dimensions(bx + 18, py, 16, 12).build()).active = bidPage < bidPages - 1;
		}

		// ---- Untere Knoepfe
		int by = top + panelH - 26;
		int bw = (panelW - 12 - 6) / 3;
		int x0 = left + 6;
		AuctionManager.Phase ph = m.phase();
		if (ph == AuctionManager.Phase.RUNNING) {
			addDrawableChild(ButtonWidget.builder(Text.literal("§eJetzt beenden"), b -> { m.endNow(); clearAndInit(); })
					.dimensions(x0, by, bw, 20)
					.tooltip(Tooltip.of(Text.literal("Beendet die Auktion sofort, der Höchstbietende gewinnt."))).build());
			addDrawableChild(ButtonWidget.builder(Text.literal("§cAbbrechen"), b -> { m.cancel(); clearAndInit(); })
					.dimensions(x0 + bw + 3, by, bw, 20)
					.tooltip(Tooltip.of(Text.literal("Niemand gewinnt – alle Zahlungen werden per /pay zurückgezahlt."))).build());
		} else {
			addDrawableChild(ButtonWidget.builder(Text.literal("§bAlle erstatten"), b -> { m.refundAll(); clearAndInit(); })
					.dimensions(x0, by, bw, 20)
					.tooltip(Tooltip.of(Text.literal("Zahlt allen Überbotenen ihr Geld per /pay zurück: " + Money.fmt(m.totalRefundDue())))).build())
					.active = m.totalRefundDue() > 0;
			addDrawableChild(ButtonWidget.builder(Text.literal("§aNeue Auktion"), b -> { m.clear(); close(); })
					.dimensions(x0 + bw + 3, by, bw, 20)
					.tooltip(Tooltip.of(Text.literal("Schließt diese Auktion ab. Danach kannst du mit einem Item in der Hand eine neue starten."))).build());
		}
		addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
				.dimensions(x0 + (bw + 3) * 2, by, bw, 20).build());
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
		ctx.fill(left, top, left + panelW, top + panelH, 0xD0101018);
		ctx.fill(left, top, left + panelW, top + 1, 0xFFFFAA00);
		// Spaltenhintergruende
		ctx.fill(left + 4, listTop - 12, left + payW, listTop + payRows * ROW, 0x40FFFFFF);
		ctx.fill(left + payW + 4, listTop - 12, left + panelW - 4, listTop + bidRows * ROW, 0x40FFFFFF);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		AuctionManager m = m();

		// ---- Kopf
		int x = left + 8, y = top + 6;
		ctx.drawItem(m.item(), x, y);
		ctx.drawStackOverlay(textRenderer, m.item(), x, y);
		ctx.drawTextWithShadow(textRenderer, "§6§l" + m.item().getCount() + "x " + m.item().getName().getString(), x + 20, y + 1, 0xFFFFFFFF);

		String state;
		switch (m.phase()) {
			case RUNNING -> state = "§aLäuft – noch " + Money.time(m.remainingSeconds());
			case ENDED -> state = "§6Beendet";
			case CANCELLED -> state = "§cAbgebrochen";
			default -> state = "";
		}
		ctx.drawTextWithShadow(textRenderer, state, x + 20, y + 11, 0xFFFFFFFF);

		String lead;
		if (m.phase() == AuctionManager.Phase.ENDED) {
			lead = m.winnerName() != null ? "§aGewinner: §f§l" + m.winnerName() + "§r§a – " + Money.fmt(m.leaderBid()) + " §7(Item übergeben!)" : "§7Kein gültiges Gebot";
		} else if (m.leaderName() != null) {
			lead = "§eHöchstgebot: §f" + m.leaderName() + " §e" + Money.fmt(m.leaderBid());
		} else {
			lead = "§7Noch kein gültiges Gebot";
		}
		ctx.drawTextWithShadow(textRenderer, lead, x, y + 25, 0xFFFFFFFF);

		String right1 = "Erhalten: §a" + Money.fmt(m.totalReceived());
		int rx = left + panelW - 8;
		ctx.drawTextWithShadow(textRenderer, right1, rx - textRenderer.getWidth(right1.replaceAll("§.", "")), y + 1, 0xFFAAAAAA);

		// ---- Zahlungen
		int px = left + 7;
		ctx.drawTextWithShadow(textRenderer, "§nZeit", px, listTop - 10, 0xFFFFFFFF);
		ctx.drawTextWithShadow(textRenderer, "§nSpieler", px + 46, listTop - 10, 0xFFFFFFFF);
		ctx.drawTextWithShadow(textRenderer, "§nBetrag", px + 128, listTop - 10, 0xFFFFFFFF);

		List<Payment> pays = new ArrayList<>(m.payments());
		java.util.Collections.reverse(pays);
		if (pays.isEmpty()) {
			ctx.drawTextWithShadow(textRenderer, "§7Noch keine Zahlungen eingegangen.", px, listTop + 2, 0xFFFFFFFF);
		}
		for (int i = 0; i < payRows; i++) {
			int idx = payPage * payRows + i;
			if (idx >= pays.size()) break;
			Payment p = pays.get(idx);
			int ry = listTop + i * ROW + 2;
			ctx.drawTextWithShadow(textRenderer, p.time, px, ry, 0xFF888888);
			ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(p.player, 80), px + 46, ry, 0xFFFFFFFF);
			ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(Money.fmt(p.amount), 60), px + 128, ry, 0xFF55FF55);
			if (p.late) ctx.drawTextWithShadow(textRenderer, "§e(nach Ende)", px + 190, ry, 0xFFFFFFFF);
		}

		// ---- Bieter
		int bx = left + payW + 8;
		ctx.drawTextWithShadow(textRenderer, "§nBieter", bx, listTop - 10, 0xFFFFFFFF);
		ctx.drawTextWithShadow(textRenderer, "§nGebot", bx + 78, listTop - 10, 0xFFFFFFFF);
		List<String> bidders = m.bidderKeys();
		for (int i = 0; i < bidRows; i++) {
			int idx = bidPage * bidRows + i;
			if (idx >= bidders.size()) break;
			String k = bidders.get(idx);
			int ry = listTop + i * ROW + 2;
			boolean win = m.isWinner(k);
			String name = (win ? "§a★ " : "") + m.nameOf(k);
			ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, 76), bx, ry, 0xFFFFFFFF);
			double total = m.bidOf(k) + m.lateOf(k);
			ctx.drawTextWithShadow(textRenderer, Money.fmt(total), bx + 78, ry, win ? 0xFF55FF55 : 0xFFFFFFFF);
			if (m.refundDue(k) <= 0 && m.refundedOf(k) > 0) {
				ctx.drawTextWithShadow(textRenderer, "§bErstattet", left + panelW - 52, ry, 0xFFFFFFFF);
			}
		}

		// ---- Legende
		ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(
						"§7Nachrichten mit \"flüstert\", \"->\", [MSG] usw. werden nie als Zahlung gezählt.", panelW - 12),
				left + 6, top + panelH - 38, 0xFFFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
