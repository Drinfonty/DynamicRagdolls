package com.drinfonty.ragdolls.fabric;

import com.drinfonty.ragdolls.RagdollsMod;
import net.fabricmc.api.ModInitializer;

public class RagdollsFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		RagdollsMod.init();
	}
}
