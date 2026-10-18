package com.drinfonty.ragdolls.client.integration;

import com.drinfonty.ragdolls.client.gui.RagdollsConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return RagdollsConfigScreen::new;
	}
}
