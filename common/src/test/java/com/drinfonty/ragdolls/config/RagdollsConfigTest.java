package com.drinfonty.ragdolls.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RagdollsConfigTest {
	@Test
	void testDefaultsAndReset() {
		RagdollsConfig config = new RagdollsConfig();
		assertTrue(config.ragdollsEnabled);
		assertEquals("PlayersOnly", config.entityScope);
		assertFalse(config.isHumanoidsEnabled());
		assertEquals("Natural", config.jointStiffness);
		assertEquals(15, config.ragdollLifetimeSeconds);

		config.entityScope = "Humanoids";
		config.jointStiffness = "Loose";
		config.ragdollLifetimeSeconds = 45;
		assertTrue(config.isHumanoidsEnabled());
		assertTrue(config.getJointConeScale() > 1.0f);

		config.resetToDefaults();
		assertEquals("PlayersOnly", config.entityScope);
		assertEquals("Natural", config.jointStiffness);
		assertEquals(15, config.ragdollLifetimeSeconds);
	}
}
