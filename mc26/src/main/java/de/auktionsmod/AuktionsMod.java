package de.auktionsmod;

import de.auktionsmod.compat.Compat;
import de.auktionsmod.gui.AuctionEndScreen;
import de.auktionsmod.gui.AuctionHud;
import de.auktionsmod.gui.AuctionOverviewScreen;
import de.auktionsmod.gui.AuctionSetupScreen;
import de.auktionsmod.gui.WinAnimation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuktionsMod implements ClientModInitializer {
	public static final String MOD_ID = "auktionsmod";
	public static final Logger LOGGER = LoggerFactory.getLogger("Auktions-Mod");

	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		AuctionConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.auktionsmod.open", InputConstants.KEY_O, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) openMenu(client);
			AuctionManager m = AuctionManager.get();
			m.tick(client);
			// Nach der Gewinner-Animation den "Auktion beendet"-Bildschirm zeigen
			if (m.endScreenPending() && !WinAnimation.active()
					&& (Compat.screen() == null || Compat.screen() instanceof AuctionOverviewScreen)) {
				m.consumeEndScreen();
				if (AuctionConfig.get().openEndScreen && client.player != null) Compat.setScreen(new AuctionEndScreen());
			}
		});

		// Nur SYSTEM-Nachrichten des Servers koennen echte Zahlungen sein.
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> AuctionManager.get().onGameMessage(message, overlay));

		// Signierte Spieler-Chatnachrichten werden NIE als Zahlung gezaehlt, nur als Fake gemeldet.
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				AuctionManager.get().onPlayerChat(message));

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "auction_hud"), AuctionHud::render);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "win_animation"), WinAnimation::renderHud);

		LOGGER.info("Auktions-Mod geladen – Taste O öffnet das Menü");
	}

	private static void openMenu(Minecraft client) {
		if (client.player == null) return;
		AuctionManager m = AuctionManager.get();
		if (m.hasSession()) {
			boolean over = m.phase() == AuctionManager.Phase.ENDED || m.phase() == AuctionManager.Phase.CANCELLED;
			Compat.setScreen(over ? new AuctionEndScreen() : new AuctionOverviewScreen());
			return;
		}
		ItemStack held = client.player.getMainHandItem();
		if (held.isEmpty()) {
			AuctionManager.info(Component.literal("Nimm das Item, das du versteigern willst, in die Hand und drücke die Taste erneut.")
					.withStyle(ChatFormatting.YELLOW));
			return;
		}
		Compat.setScreen(new AuctionSetupScreen(held));
	}
}
