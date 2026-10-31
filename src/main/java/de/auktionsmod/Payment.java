package de.auktionsmod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Payment {
	public enum Status {
		/** Wartet auf Kontostand-Pruefung */
		PENDING("… Prüfe", 0xFFFFFF55),
		/** Kontostand ist um genau diesen Betrag gestiegen -> echte Zahlung */
		CONFIRMED("✔ Echt", 0xFF55FF55),
		/** Kontostand-Pruefung ist ausgeschaltet */
		UNCHECKED("~ Ungeprüft", 0xFFAAAAAA),
		/** Nicht eindeutig zuordenbar -> bitte manuell bestaetigen/ablehnen */
		UNCLEAR("? Unklar", 0xFFFFAA00),
		/** Kontostand ist NICHT gestiegen -> gefaelscht */
		REJECTED("✘ Fake", 0xFFFF5555);

		public final String label;
		public final int color;

		Status(String label, int color) {
			this.label = label;
			this.color = color;
		}

		public boolean counts() {
			return this == CONFIRMED || this == UNCHECKED;
		}
	}

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	public final String player;
	public final double amount;
	public final String time = LocalTime.now().format(TIME);
	/** Nach dem Ende der Auktion eingegangen -> zaehlt nicht als Gebot, wird erstattet */
	public final boolean late;
	public Status status;
	public boolean manual;

	public Payment(String player, double amount, boolean late, Status status) {
		this.player = player;
		this.amount = amount;
		this.late = late;
		this.status = status;
	}

	public String key() {
		return AuctionManager.key(player);
	}
}
