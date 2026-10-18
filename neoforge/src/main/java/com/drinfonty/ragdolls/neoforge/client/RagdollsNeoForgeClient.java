package com.drinfonty.ragdolls.neoforge.client;

import com.drinfonty.ragdolls.RagdollsMod;
import com.drinfonty.ragdolls.client.ClientRagdollManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

@EventBusSubscriber(modid = RagdollsMod.MOD_ID, value = Dist.CLIENT)
public final class RagdollsNeoForgeClient {
	private RagdollsNeoForgeClient() {
	}

	public static void init(IEventBus modBus) {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.level != null && !mc.isPaused()) {
				ClientRagdollManager.get().tick(mc.level, System.currentTimeMillis());
			}
		});

		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
			ClientRagdollManager.get().clearAll();
		});

		NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, event -> {
			if (event.getLevel() != null && event.getLevel().isClientSide() && event.getChunk() != null) {
				var pos = event.getChunk().getPos();
				ClientRagdollManager.get().clearChunk(pos.x(), pos.z());
			}
		});
	}
}
