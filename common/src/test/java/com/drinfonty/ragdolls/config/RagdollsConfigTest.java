package com.drinfonty.ragdolls.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RagdollsConfigTest {
	@Test
	void testDefaultsAndReset() {
		RagdollsConfig config = new RagdollsConfig();
		assertTrue(config.ragdollsEnabled);
		assertEquals("Humanoids", config.entityScope);
		assertTrue(config.isHumanoidsEnabled());
		assertEquals("Natural", config.jointStiffness);
		assertEquals(15, config.ragdollLifetimeSeconds);

		config.entityScope = "PlayersOnly";
		config.jointStiffness = "Loose";
		config.ragdollLifetimeSeconds = 45;
		assertFalse(config.isHumanoidsEnabled());
		assertTrue(config.getJointConeScale() > 1.0f);

		config.resetToDefaults();
		assertEquals("Humanoids", config.entityScope);
		assertEquals("Natural", config.jointStiffness);
		assertEquals(15, config.ragdollLifetimeSeconds);
	}
}
