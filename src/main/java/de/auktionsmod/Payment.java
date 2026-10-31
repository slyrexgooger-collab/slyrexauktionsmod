package de.auktionsmod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Payment {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	public final String player;
	public final double amount;
	public final String time = LocalTime.now().format(TIME);
	/** Nach dem Ende der Auktion eingegangen -> zaehlt nicht als Gebot, wird erstattet */
	public final boolean late;

	public Payment(String player, double amount, boolean late) {
		this.player = player;
		this.amount = amount;
		this.late = late;
	}

	public String key() {
		return AuctionManager.key(player);
	}
}
