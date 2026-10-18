package com.drinfonty.ragdolls.client.mixin;

import com.drinfonty.ragdolls.client.ClientRagdollManager;
import com.drinfonty.ragdolls.config.RagdollsConfig;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void ragdolls$onTick(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() || !RagdollsConfig.get().ragdollsEnabled) {
			return;
		}
		if (self.isDeadOrDying() || self.deathTime > 0) {
			ClientRagdollManager.get().onEntityDeath(self, self.getLastDamageSource());
		}
	}

	@Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
	private void ragdolls$onHandleEntityEvent(byte status, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() || !RagdollsConfig.get().ragdollsEnabled) {
			return;
		}
		if (status == 3) {
			ClientRagdollManager.get().onEntityDeath(self, self.getLastDamageSource());
		} else if (status == 60 && !RagdollsConfig.get().deathSmokeParticles) {
			if (ClientRagdollManager.get().hasActiveRagdollForEntity(self.getId())) {
				ci.cancel();
			}
		}
	}
}
