package de.auktionsmod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

/** Unterschiede zwischen den Minecraft-Versionen – Variante fuer 26.1. */
public final class Compat {
	private Compat() {}

	public static Screen screen() {
		return Minecraft.getInstance().screen;
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static ChatComponent chat() {
		return Minecraft.getInstance().gui.getChat();
	}
}
