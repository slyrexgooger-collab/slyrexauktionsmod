package de.auktionsmod;

import de.auktionsmod.gui.AuctionHud;
import de.auktionsmod.gui.AuctionOverviewScreen;
import de.auktionsmod.gui.AuctionSetupScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuktionsMod implements ClientModInitializer {
	public static final String MOD_ID = "auktionsmod";
	public static final Logger LOGGER = LoggerFactory.getLogger("Auktions-Mod");

	private static KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		AuctionConfig.load();

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.auktionsmod.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) openMenu(client);
			AuctionManager.get().tick(client);
		});

		// Nur SYSTEM-Nachrichten des Servers koennen echte Zahlungen sein.
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> AuctionManager.get().onGameMessage(message, overlay));

		// Signierte Spieler-Chatnachrichten werden NIE als Zahlung gezaehlt, nur als Fake gemeldet.
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				AuctionManager.get().onPlayerChat(message));

		HudElementRegistry.addLast(Identifier.of(MOD_ID, "auction_hud"), AuctionHud::render);

		LOGGER.info("Auktions-Mod geladen – Taste O öffnet das Menü");
	}

	private static void openMenu(MinecraftClient client) {
		if (client.player == null) return;
		AuctionManager m = AuctionManager.get();
		if (m.hasSession()) {
			client.setScreen(new AuctionOverviewScreen());
			return;
		}
		ItemStack held = client.player.getMainHandStack();
		if (held.isEmpty()) {
			AuctionManager.info(Text.literal("Nimm das Item, das du versteigern willst, in die Hand und drücke die Taste erneut.")
					.formatted(Formatting.YELLOW));
			return;
		}
		client.setScreen(new AuctionSetupScreen(held));
	}
}
