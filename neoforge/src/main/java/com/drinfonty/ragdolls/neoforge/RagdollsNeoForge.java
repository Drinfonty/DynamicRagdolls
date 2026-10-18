package com.drinfonty.ragdolls.neoforge;

import com.drinfonty.ragdolls.RagdollsMod;
import com.drinfonty.ragdolls.client.gui.RagdollsConfigScreen;
import com.drinfonty.ragdolls.neoforge.client.RagdollsNeoForgeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod("ragdolls")
public class RagdollsNeoForge {
	public RagdollsNeoForge(ModContainer container) {
		RagdollsMod.init();
		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new RagdollsConfigScreen(parent));
			RagdollsNeoForgeClient.init(container.getEventBus());
		}
	}
}
