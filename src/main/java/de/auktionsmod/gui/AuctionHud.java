package de.auktionsmod.gui;

import de.auktionsmod.AuctionConfig;
import de.auktionsmod.AuctionManager;
import de.auktionsmod.Money;
import de.auktionsmod.Payment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;

/** Kleine Info-Box oben links, solange eine Auktion laeuft. */
public final class AuctionHud {
	private AuctionHud() {}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		AuctionManager m = AuctionManager.get();
		if (!AuctionConfig.get().showHud || mc.options.hudHidden || !m.hasSession()) return;
		if (mc.currentScreen instanceof AuctionOverviewScreen) return;
		TextRenderer tr = mc.textRenderer;

		int x = 6, y = 6, w = 195;
		List<Payment> pays = m.payments();
		int shown = Math.min(4, pays.size());
		int h = 46 + shown * 10;

		ctx.fill(x - 3, y - 3, x + w, y + h, 0xA0000000);
		ctx.fill(x - 3, y - 3, x + w, y - 2, 0xFFFFAA00);

		ctx.drawItem(m.item(), x, y);
		String title = m.item().getCount() + "x " + m.item().getName().getString();
		ctx.drawTextWithShadow(tr, tr.trimToWidth(title, w - 24), x + 20, y + 1, 0xFFFFFFFF);

		String state;
		int stateColor;
		switch (m.phase()) {
			case RUNNING -> {
				long rem = m.remainingSeconds();
				state = "Restzeit: " + Money.time(rem);
				stateColor = rem <= 10 ? 0xFFFF5555 : 0xFF55FF55;
			}
			case ENDED -> { state = "Beendet"; stateColor = 0xFFFFAA00; }
			case CANCELLED -> { state = "Abgebrochen"; stateColor = 0xFFFF5555; }
			default -> { state = ""; stateColor = 0xFFFFFFFF; }
		}
		ctx.drawTextWithShadow(tr, state, x + 20, y + 10, stateColor);

		String lead;
		if (m.phase() == AuctionManager.Phase.ENDED && m.winnerName() != null) lead = "Gewinner: " + m.winnerName() + " " + Money.fmt(m.leaderBid());
		else if (m.leaderName() != null) lead = "Höchstgebot: " + m.leaderName() + " " + Money.fmt(m.leaderBid());
		else lead = "Noch kein gültiges Gebot";
		ctx.drawTextWithShadow(tr, tr.trimToWidth(lead, w - 4), x, y + 22, 0xFFFFFF55);
		ctx.drawTextWithShadow(tr, "Erhalten: " + Money.fmt(m.totalReceived()), x, y + 32, 0xFFAAAAAA);

		int ry = y + 44;
		for (int i = pays.size() - 1; i >= pays.size() - shown; i--) {
			Payment p = pays.get(i);
			String line = tr.trimToWidth(p.player, 70);
			ctx.drawTextWithShadow(tr, line, x, ry, 0xFFFFFFFF);
			ctx.drawTextWithShadow(tr, Money.fmt(p.amount), x + 72, ry, 0xFF55FF55);
			if (p.late) ctx.drawTextWithShadow(tr, "nach Ende", x + 132, ry, 0xFFFFFF55);
			ry += 10;
		}
	}
}
