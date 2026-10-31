package de.auktionsmod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Payment {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	public final String player;
	public final double amount;
	public final String time = LocalTime.now().format(TIME);
	/** Gueltiges Gebot? Ungueltige (unter Mindestgebot, nach Ende) werden nicht angezeigt/gezaehlt, nur erstattet. */
	public boolean valid;
	/** Gesamtgebot des Spielers nach dieser Zahlung (nur bei gueltigen) */
	public double totalAfter;

	public Payment(String player, double amount) {
		this.player = player;
		this.amount = amount;
	}

	public String key() {
		return AuctionManager.key(player);
	}
}
