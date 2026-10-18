package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.ClientRagdollManager;
import com.drinfonty.ragdolls.client.RagdollInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Shadow
	@Final
	private EntityRenderDispatcher entityRenderDispatcher;

	@Inject(method = "submitEntities", at = @At("TAIL"))
	private void ragdolls$submitRagdolls(
		PoseStack poseStack,
		LevelRenderState levelRenderState,
		SubmitNodeCollector collector,
		CallbackInfo ci
	) {
		Collection<RagdollInstance> active = ClientRagdollManager.get().getActiveRagdolls();
		if (active.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc != null ? mc.level : null;
		float partialTick = mc != null ? mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 1.0f;

		Vec3 camPos = levelRenderState.cameraRenderState.pos;
		double camX = camPos.x();
		double camY = camPos.y();
		double camZ = camPos.z();

		for (RagdollInstance instance : active) {
			HumanoidRenderState state = instance.prepareRenderState(level, partialTick);
			this.entityRenderDispatcher.submit(
				state,
				levelRenderState.cameraRenderState,
				state.x - camX,
				state.y - camY,
				state.z - camZ,
				poseStack,
				collector
			);
		}
	}
}
