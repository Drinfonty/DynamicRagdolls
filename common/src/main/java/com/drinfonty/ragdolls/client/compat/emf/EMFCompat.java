package com.drinfonty.ragdolls.client.compat.emf;

import com.drinfonty.ragdolls.client.ClientRagdollManager;
import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;

/**
 * Safe reflection-based integration with Entity Model Features (EMF) APIs.
 */
public final class EMFCompat {
	private static boolean initialized = false;

	private EMFCompat() {
	}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		try {
			Class<?> apiClass = Class.forName("traben.entity_model_features.EMFAnimationApi");
			Method registerMethod = apiClass.getMethod("registerPauseCondition", Function.class);
			registerMethod.invoke(null, (Function<Object, Boolean>) emfEntity -> {
				if (emfEntity instanceof Entity entity) {
					return ClientRagdollManager.get().hasActiveRagdollForEntity(entity.getId());
				}
				return false;
			});
		} catch (Throwable ignored) {
			// EMF not present or different version; ignored safely
		}
	}
}
