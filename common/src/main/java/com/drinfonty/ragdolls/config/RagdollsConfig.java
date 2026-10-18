package com.drinfonty.ragdolls.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class RagdollsConfig {
	private static final File CONFIG_FILE = new File("config", "ragdolls.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean ragdollsEnabled = true;
	public String entityScope = "PlayersOnly"; // PlayersOnly, Humanoids
	public String jointStiffness = "Natural"; // Loose, Natural, Stiff
	public boolean waterBuoyancy = true;
	public boolean renderArmorAndItems = true;
	public boolean deathSmokeParticles = false;
	public int ragdollLifetimeSeconds = 15; // Range: 1 to 60 seconds
	public int maxActiveRagdolls = 32; // Range: 4 to 100
	public float knockbackMultiplier = 1.0f; // Range: 0.2 to 3.0
	public float gravityScale = 1.0f; // Range: 0.2 to 2.0
	public float bounciness = 0.25f; // Range: 0.0 to 0.8
	public float groundFriction = 0.80f; // Range: 0.1 to 1.0

	private static RagdollsConfig instance;

	public static RagdollsConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public static RagdollsConfig load() {
		RagdollsConfig config = new RagdollsConfig();
		if (CONFIG_FILE.exists()) {
			try (FileReader reader = new FileReader(CONFIG_FILE)) {
				JsonElement element = JsonParser.parseReader(reader);
				if (element != null && element.isJsonObject()) {
					JsonObject obj = element.getAsJsonObject();
					if (obj.has("ragdollsEnabled")) config.ragdollsEnabled = obj.get("ragdollsEnabled").getAsBoolean();
					if (obj.has("entityScope")) config.entityScope = obj.get("entityScope").getAsString();
					if (obj.has("jointStiffness")) config.jointStiffness = obj.get("jointStiffness").getAsString();
					if (obj.has("waterBuoyancy")) config.waterBuoyancy = obj.get("waterBuoyancy").getAsBoolean();
					if (obj.has("renderArmorAndItems")) config.renderArmorAndItems = obj.get("renderArmorAndItems").getAsBoolean();
					if (obj.has("deathSmokeParticles")) config.deathSmokeParticles = obj.get("deathSmokeParticles").getAsBoolean();
					if (obj.has("ragdollLifetimeSeconds")) config.ragdollLifetimeSeconds = obj.get("ragdollLifetimeSeconds").getAsInt();
					if (obj.has("maxActiveRagdolls")) config.maxActiveRagdolls = obj.get("maxActiveRagdolls").getAsInt();
					if (obj.has("knockbackMultiplier")) config.knockbackMultiplier = obj.get("knockbackMultiplier").getAsFloat();
					if (obj.has("gravityScale")) config.gravityScale = obj.get("gravityScale").getAsFloat();
					if (obj.has("bounciness")) config.bounciness = obj.get("bounciness").getAsFloat();
					if (obj.has("groundFriction")) config.groundFriction = obj.get("groundFriction").getAsFloat();
				}
			} catch (Exception e) {
				System.err.println("[Ragdolls] Failed to load config: " + e.getMessage());
			}
		}

		// Fallbacks & validation
		if (config.entityScope == null || (!config.entityScope.equals("PlayersOnly") && !config.entityScope.equals("Humanoids"))) {
			config.entityScope = "PlayersOnly";
		}
		if (config.jointStiffness == null || (!config.jointStiffness.equals("Loose") && !config.jointStiffness.equals("Natural") && !config.jointStiffness.equals("Stiff"))) {
			config.jointStiffness = "Natural";
		}
		config.ragdollLifetimeSeconds = Math.max(1, Math.min(60, config.ragdollLifetimeSeconds));
		config.maxActiveRagdolls = Math.max(4, Math.min(100, config.maxActiveRagdolls));
		config.knockbackMultiplier = Math.max(0.2f, Math.min(3.0f, config.knockbackMultiplier));
		config.gravityScale = Math.max(0.2f, Math.min(2.0f, config.gravityScale));
		config.bounciness = Math.max(0.0f, Math.min(0.8f, config.bounciness));
		config.groundFriction = Math.max(0.1f, Math.min(1.0f, config.groundFriction));

		config.save();
		return config;
	}

	public void save() {
		try {
			File parent = CONFIG_FILE.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}
			try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			System.err.println("[Ragdolls] Failed to save config: " + e.getMessage());
		}
	}

	public void resetToDefaults() {
		this.ragdollsEnabled = true;
		this.entityScope = "PlayersOnly";
		this.jointStiffness = "Natural";
		this.waterBuoyancy = true;
		this.renderArmorAndItems = true;
		this.deathSmokeParticles = false;
		this.ragdollLifetimeSeconds = 15;
		this.maxActiveRagdolls = 32;
		this.knockbackMultiplier = 1.0f;
		this.gravityScale = 1.0f;
		this.bounciness = 0.25f;
		this.groundFriction = 0.80f;
	}

	public boolean isHumanoidsEnabled() {
		return "Humanoids".equalsIgnoreCase(this.entityScope);
	}

	public float getJointConeScale() {
		return switch (this.jointStiffness) {
			case "Loose" -> 1.35f;
			case "Stiff" -> 0.65f;
			default -> 1.0f;
		};
	}

	public int getSolverIterations() {
		return switch (this.jointStiffness) {
			case "Loose" -> 4;
			case "Stiff" -> 8;
			default -> 6;
		};
	}
}
