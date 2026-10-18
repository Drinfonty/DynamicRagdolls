package com.drinfonty.ragdolls;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RagdollsMod {
	public static final String MOD_ID = "ragdolls";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * Per-death diagnostics, off unless {@code -Dragdolls.debug=true} is passed. The Gradle dev
	 * run configurations set it; a real launcher never does, so players never see it.
	 */
	public static final boolean DEBUG = Boolean.getBoolean("ragdolls.debug");

	public static void init() {
		LOGGER.info("Initializing RagdollsMod common entry point!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
