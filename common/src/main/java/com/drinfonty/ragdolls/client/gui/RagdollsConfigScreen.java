package com.drinfonty.ragdolls.client.gui;

import com.drinfonty.ragdolls.config.RagdollsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RagdollsConfigScreen extends Screen {
	private final Screen parent;

	public RagdollsConfigScreen(Screen parent) {
		super(Component.literal("Ragdolls Configuration"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		RagdollsConfig config = RagdollsConfig.get();

		int buttonWidth = 230;
		int buttonHeight = 20;
		int x = (this.width - buttonWidth) / 2;
		int startY = this.height / 2 - 90;
		int colWidth = 112;
		int leftX = x;
		int rightX = x + 118;

		// Row 0 Left: Toggle Ragdolls Enabled
		this.addRenderableWidget(Button.builder(
			getEnabledMessage(config),
			btn -> {
				config.ragdollsEnabled = !config.ragdollsEnabled;
				btn.setMessage(getEnabledMessage(config));
			}
		).bounds(leftX, startY, colWidth, buttonHeight).build());

		// Row 0 Right: Toggle Entity Scope (PlayersOnly vs Humanoids)
		this.addRenderableWidget(Button.builder(
			getScopeMessage(config),
			btn -> {
				config.entityScope = config.isHumanoidsEnabled() ? "PlayersOnly" : "Humanoids";
				btn.setMessage(getScopeMessage(config));
			}
		).bounds(rightX, startY, colWidth, buttonHeight).build());

		// Row 1 Left: Toggle Joint Stiffness
		this.addRenderableWidget(Button.builder(
			getStiffnessMessage(config),
			btn -> {
				config.jointStiffness = switch (config.jointStiffness) {
					case "Loose" -> "Natural";
					case "Natural" -> "Stiff";
					default -> "Loose";
				};
				btn.setMessage(getStiffnessMessage(config));
			}
		).bounds(leftX, startY + 25, colWidth, buttonHeight).build());

		// Row 1 Right: Toggle Water Buoyancy
		this.addRenderableWidget(Button.builder(
			getBuoyancyMessage(config),
			btn -> {
				config.waterBuoyancy = !config.waterBuoyancy;
				btn.setMessage(getBuoyancyMessage(config));
			}
		).bounds(rightX, startY + 25, colWidth, buttonHeight).build());

		// Row 2 Left: Toggle Armor & Items
		this.addRenderableWidget(Button.builder(
			getArmorMessage(config),
			btn -> {
				config.renderArmorAndItems = !config.renderArmorAndItems;
				btn.setMessage(getArmorMessage(config));
			}
		).bounds(leftX, startY + 50, colWidth, buttonHeight).build());

		// Row 2 Right: Toggle Death Smoke
		this.addRenderableWidget(Button.builder(
			getSmokeMessage(config),
			btn -> {
				config.deathSmokeParticles = !config.deathSmokeParticles;
				btn.setMessage(getSmokeMessage(config));
			}
		).bounds(rightX, startY + 50, colWidth, buttonHeight).build());

		// Row 3 Left: Toggle Ground Dust Particles
		this.addRenderableWidget(Button.builder(
			getDustMessage(config),
			btn -> {
				config.groundDustParticles = !config.groundDustParticles;
				btn.setMessage(getDustMessage(config));
			}
		).bounds(leftX, startY + 75, colWidth, buttonHeight).build());

		// Row 3 Right: Slider for Ragdoll Lifetime (1s - 60s)
		this.addRenderableWidget(new AbstractSliderButton(
			rightX, startY + 75, colWidth, buttonHeight,
			Component.empty(),
			(double) (config.ragdollLifetimeSeconds - 1) / 59.0
		) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.literal("Lifetime: " + config.ragdollLifetimeSeconds + "s"));
			}

			@Override
			protected void applyValue() {
				config.ragdollLifetimeSeconds = 1 + (int) Math.round(this.value * 59.0);
			}
		});

		// Row 4 Left: Slider for Knockback Force (0.2x - 3.0x)
		this.addRenderableWidget(new AbstractSliderButton(
			leftX, startY + 100, colWidth, buttonHeight,
			Component.empty(),
			(double) (config.knockbackMultiplier - 0.2f) / 2.8f
		) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.literal("Knockback: " + (Math.round(config.knockbackMultiplier * 10.0f) / 10.0f) + "x"));
			}

			@Override
			protected void applyValue() {
				config.knockbackMultiplier = 0.2f + (float) (this.value * 2.8f);
			}
		});

		// Row 4 Right: Slider for Gravity Scale (0.2x - 2.0x)
		this.addRenderableWidget(new AbstractSliderButton(
			rightX, startY + 100, colWidth, buttonHeight,
			Component.empty(),
			(double) (config.gravityScale - 0.2f) / 1.8f
		) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.literal("Gravity: " + (Math.round(config.gravityScale * 10.0f) / 10.0f) + "x"));
			}

			@Override
			protected void applyValue() {
				config.gravityScale = 0.2f + (float) (this.value * 1.8f);
			}
		});

		// Row 5 Left: Slider for Bounciness (0.0 - 0.8)
		this.addRenderableWidget(new AbstractSliderButton(
			leftX, startY + 125, colWidth, buttonHeight,
			Component.empty(),
			(double) config.bounciness / 0.8f
		) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.literal("Bounce: " + Math.round(config.bounciness * 100.0f) + "%"));
			}

			@Override
			protected void applyValue() {
				config.bounciness = (float) (this.value * 0.8f);
			}
		});

		// Row 5 Right: Slider for Ground Friction (0.1 - 1.0)
		this.addRenderableWidget(new AbstractSliderButton(
			rightX, startY + 125, colWidth, buttonHeight,
			Component.empty(),
			(double) (config.groundFriction - 0.1f) / 0.9f
		) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.literal("Friction: " + Math.round(config.groundFriction * 100.0f) + "%"));
			}

			@Override
			protected void applyValue() {
				config.groundFriction = 0.1f + (float) (this.value * 0.9f);
			}
		});

		// Row 6 Left: Slider for Max Active Ragdolls (4 - 100)
		this.addRenderableWidget(new AbstractSliderButton(
			leftX, startY + 150, colWidth, buttonHeight,
			Component.empty(),
			(double) (config.maxActiveRagdolls - 4) / 96.0
		) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.literal("Max: " + config.maxActiveRagdolls));
			}

			@Override
			protected void applyValue() {
				config.maxActiveRagdolls = 4 + (int) Math.round(this.value * 96.0);
			}
		});

		// Row 6 Right: Reset
		this.addRenderableWidget(Button.builder(
			Component.literal("Reset"),
			btn -> {
				config.resetToDefaults();
				this.rebuildWidgets();
			}
		).bounds(rightX, startY + 150, colWidth, buttonHeight).build());

		// Row 7 Center: Done
		this.addRenderableWidget(Button.builder(
			Component.literal("Done"),
			btn -> {
				config.save();
				this.onClose();
			}
		).bounds(x, startY + 176, buttonWidth, buttonHeight).build());
	}

	private static Component getEnabledMessage(RagdollsConfig config) {
		return Component.literal("Ragdolls: " + (config.ragdollsEnabled ? "ON" : "OFF"));
	}

	private static Component getScopeMessage(RagdollsConfig config) {
		return Component.literal("Targets: " + (config.isHumanoidsEnabled() ? "Humanoids" : "Players"));
	}

	private static Component getStiffnessMessage(RagdollsConfig config) {
		return Component.literal("Joints: " + config.jointStiffness);
	}

	private static Component getBuoyancyMessage(RagdollsConfig config) {
		return Component.literal("Buoyancy: " + (config.waterBuoyancy ? "ON" : "OFF"));
	}

	private static Component getArmorMessage(RagdollsConfig config) {
		return Component.literal("Equipment: " + (config.renderArmorAndItems ? "ON" : "OFF"));
	}

	private static Component getSmokeMessage(RagdollsConfig config) {
		return Component.literal("Death Smoke: " + (config.deathSmokeParticles ? "ON" : "OFF"));
	}

	private static Component getDustMessage(RagdollsConfig config) {
		return Component.literal("Ground Dust: " + (config.groundDustParticles ? "ON" : "OFF"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
		this.extractTransparentBackground(extractor);
		extractor.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
		super.extractRenderState(extractor, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		RagdollsConfig.get().save();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}
}
