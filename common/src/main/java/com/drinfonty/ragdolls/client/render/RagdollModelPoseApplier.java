package com.drinfonty.ragdolls.client.render;

import com.drinfonty.ragdolls.client.compat.emf.EMFCompat;
import com.drinfonty.ragdolls.client.mixin.ModelPartAccessor;
import com.drinfonty.ragdolls.physics.RagdollPose;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Applies a 6-limb {@link RagdollPose} directly to a {@link HumanoidModel} or any compatible
 * {@link Model} (such as {@code IllagerModel} or models with custom Entity Model Features / OptiFine CEM parts).
 */
public final class RagdollModelPoseApplier {
	private static final ThreadLocal<Boolean> RAGDOLL_RENDERING = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final Field CHILDREN_FIELD;

	static {
		EMFCompat.init();
		Field f = null;
		try {
			f = ModelPart.class.getDeclaredField("children");
			f.setAccessible(true);
		} catch (Throwable ignored) {
		}
		CHILDREN_FIELD = f;
	}

	private RagdollModelPoseApplier() {
	}

	public static boolean isRagdollRendering() {
		return RAGDOLL_RENDERING.get();
	}

	public static void setRagdollRendering(boolean rendering) {
		RAGDOLL_RENDERING.set(rendering);
	}

	public static void applyIfPresent(Object modelObj, Object stateObj) {
		if (!(stateObj instanceof RagdollStateAttachment attachment)) {
			return;
		}
		RagdollPose pose = attachment.ragdolls$getPose();
		if (pose == null) {
			return;
		}
		if (modelObj instanceof Model<?> model) {
			apply(model, pose);
		}
	}

	public static void apply(Model<?> model, RagdollPose pose) {
		if (model == null || pose == null) {
			return;
		}

		if (model instanceof HumanoidModel<?> humanoidModel) {
			applyHumanoidParts(humanoidModel, pose);
		}

		ModelPart root = model.root();
		if (root != null) {
			applyToRootChildren(root, pose);
		}
	}

	public static void apply(HumanoidModel<?> model, RagdollPose pose) {
		apply((Model<?>) model, pose);
	}

	private static void applyHumanoidParts(HumanoidModel<?> model, RagdollPose pose) {
		applyLimbPart(model.head, pose.head());
		if (model.hat != null) {
			model.hat.resetPose();
		}
		applyLimbPart(model.body, pose.body());
		applyLimbPart(model.leftArm, pose.leftArm());
		applyLimbPart(model.rightArm, pose.rightArm());
		applyLimbPart(model.leftLeg, pose.leftLeg());
		applyLimbPart(model.rightLeg, pose.rightLeg());
	}

	private static void applyToRootChildren(ModelPart root, RagdollPose pose) {
		// Standard vanilla parts fallback
		if (root.hasChild("head")) {
			applyLimbPart(root.getChild("head"), pose.head());
		}
		if (root.hasChild("body")) {
			applyLimbPart(root.getChild("body"), pose.body());
		}
		if (root.hasChild("left_arm")) {
			applyLimbPart(root.getChild("left_arm"), pose.leftArm());
		}
		if (root.hasChild("right_arm")) {
			applyLimbPart(root.getChild("right_arm"), pose.rightArm());
		}
		if (root.hasChild("left_leg")) {
			applyLimbPart(root.getChild("left_leg"), pose.leftLeg());
		}
		if (root.hasChild("right_leg")) {
			applyLimbPart(root.getChild("right_leg"), pose.rightLeg());
		}
		if (root.hasChild("arms")) {
			root.getChild("arms").visible = false;
		}

		// Inspect all children (including custom EMF / CEM parts)
		Map<String, ModelPart> children = getChildren(root);
		if (children == null || children.isEmpty()) {
			return;
		}

		for (Map.Entry<String, ModelPart> entry : children.entrySet()) {
			String rawName = entry.getKey();
			ModelPart part = entry.getValue();
			if (part == null) {
				continue;
			}
			applyPartByName(rawName, part, pose);
		}
	}

	private static void applyPartByName(String rawName, ModelPart part, RagdollPose pose) {
		String name = rawName.toLowerCase(Locale.ROOT);

		if (name.equals("arms")) {
			// Crossed arms on Illagers or custom CEM packs: hide so articulated arms render
			part.visible = false;
			return;
		}

		if (name.equals("hat") || name.equals("headwear")) {
			part.resetPose();
			return;
		}

		if (isLeftArm(name)) {
			applyLimbPart(part, pose.leftArm());
		} else if (isRightArm(name)) {
			applyLimbPart(part, pose.rightArm());
		} else if (isLeftLeg(name)) {
			applyLimbPart(part, pose.leftLeg());
		} else if (isRightLeg(name)) {
			applyLimbPart(part, pose.rightLeg());
		} else if (isHead(name)) {
			applyLimbPart(part, pose.head());
		} else if (isBody(name)) {
			applyLimbPart(part, pose.body());
		}
	}

	private static void applyLimbPart(ModelPart part, RagdollPose.LimbTransform transform) {
		if (part == null || transform == null) {
			return;
		}
		part.visible = true;
		part.setPos(transform.x(), transform.y(), transform.z());
		part.setRotation(transform.xRot(), transform.yRot(), transform.zRot());
		resetChildrenRecursively(part);
	}

	private static void resetChildrenRecursively(ModelPart parent) {
		if (parent == null) {
			return;
		}
		Map<String, ModelPart> children = getChildren(parent);
		if (children == null || children.isEmpty()) {
			return;
		}
		for (ModelPart child : children.values()) {
			if (child != null) {
				child.resetPose();
				resetChildrenRecursively(child);
			}
		}
	}

	private static Map<String, ModelPart> getChildren(ModelPart part) {
		if (part == null) {
			return null;
		}
		try {
			if ((Object) part instanceof ModelPartAccessor accessor) {
				return accessor.ragdolls$getChildren();
			}
		} catch (Throwable ignored) {
		}
		if (CHILDREN_FIELD != null) {
			try {
				@SuppressWarnings("unchecked")
				Map<String, ModelPart> map = (Map<String, ModelPart>) CHILDREN_FIELD.get(part);
				return map;
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static boolean isLeftArm(String name) {
		return name.equals("left_arm")
			|| name.equals("leftarm")
			|| name.equals("arm0")
			|| name.equals("arm_left")
			|| ((name.contains("left") || name.contains("l_"))
				&& (name.contains("arm") || name.contains("hand") || name.contains("sleeve")))
			|| name.startsWith("left_arm");
	}

	private static boolean isRightArm(String name) {
		return name.equals("right_arm")
			|| name.equals("rightarm")
			|| name.equals("arm1")
			|| name.equals("arm_right")
			|| ((name.contains("right") || name.contains("r_"))
				&& (name.contains("arm") || name.contains("hand") || name.contains("sleeve")))
			|| name.startsWith("right_arm");
	}

	private static boolean isLeftLeg(String name) {
		return name.equals("left_leg")
			|| name.equals("leftleg")
			|| name.equals("leg0")
			|| name.equals("leg_left")
			|| ((name.contains("left") || name.contains("l_"))
				&& (name.contains("leg") || name.contains("foot") || name.contains("pants")))
			|| name.startsWith("left_leg");
	}

	private static boolean isRightLeg(String name) {
		return name.equals("right_leg")
			|| name.equals("rightleg")
			|| name.equals("leg1")
			|| name.equals("leg_right")
			|| ((name.contains("right") || name.contains("r_"))
				&& (name.contains("leg") || name.contains("foot") || name.contains("pants")))
			|| name.startsWith("right_leg");
	}

	private static boolean isHead(String name) {
		return name.equals("head")
			|| name.equals("head2")
			|| (name.contains("head") && !name.contains("hat") && !name.contains("wear")
				&& !name.contains("gear") && !name.contains("helmet"));
	}

	private static boolean isBody(String name) {
		return name.equals("body")
			|| name.equals("body2")
			|| name.equals("torso")
			|| name.equals("chest")
			|| name.equals("waist")
			|| name.contains("body")
			|| name.contains("torso");
	}
}
