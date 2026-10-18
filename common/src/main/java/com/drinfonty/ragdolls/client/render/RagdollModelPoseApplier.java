package com.drinfonty.ragdolls.client.render;

import com.drinfonty.ragdolls.physics.RagdollPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Applies a 6-limb {@link RagdollPose} directly to a {@link HumanoidModel}'s ModelParts.
 */
public final class RagdollModelPoseApplier {
	private RagdollModelPoseApplier() {
	}

	public static void applyIfPresent(Object modelObj, Object stateObj) {
		if (modelObj instanceof HumanoidModel<?> model
			&& stateObj instanceof RagdollStateAttachment attachment) {
			RagdollPose pose = attachment.ragdolls$getPose();
			if (pose != null) {
				apply(model, pose);
			}
		}
	}

	public static void apply(HumanoidModel<?> model, RagdollPose pose) {
		applyPart(model.head, pose.head());
		if (model.hat != null) {
			// In 26.2 hat is a child of head, so keep its relative offset/rotation zeroed
			model.hat.setPos(0.0f, 0.0f, 0.0f);
			model.hat.setRotation(0.0f, 0.0f, 0.0f);
		}
		applyPart(model.body, pose.body());
		applyPart(model.leftArm, pose.leftArm());
		applyPart(model.rightArm, pose.rightArm());
		applyPart(model.leftLeg, pose.leftLeg());
		applyPart(model.rightLeg, pose.rightLeg());
	}

	private static void applyPart(ModelPart part, RagdollPose.LimbTransform transform) {
		if (part == null || transform == null) {
			return;
		}
		part.setPos(transform.x(), transform.y(), transform.z());
		part.setRotation(transform.xRot(), transform.yRot(), transform.zRot());
	}
}
