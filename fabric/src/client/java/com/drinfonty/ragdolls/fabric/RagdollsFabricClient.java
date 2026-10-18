package com.drinfonty.ragdolls.fabric;

import com.drinfonty.ragdolls.RagdollsMod;
import com.drinfonty.ragdolls.client.ClientRagdollManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class RagdollsFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RagdollsMod.LOGGER.info("Initializing RagdollsMod Fabric client entry point!");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && !client.isPaused()) {
				ClientRagdollManager.get().tick(client.level, System.currentTimeMillis());
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientRagdollManager.get().clearAll();
		});

		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			var pos = chunk.getPos();
			ClientRagdollManager.get().clearChunk(pos.x(), pos.z());
		});
	}
}
