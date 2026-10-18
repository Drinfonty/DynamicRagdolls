package com.drinfonty.ragdolls.client.render;

import com.drinfonty.ragdolls.physics.RagdollPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Applies a 6-limb {@link RagdollPose} directly to a {@link HumanoidModel} or any compatible
 * {@link Model} (such as {@code IllagerModel} for Pillagers, Vindicators, Evokers, and Illusioners).
 */
public final class RagdollModelPoseApplier {
	private RagdollModelPoseApplier() {
	}

	public static void applyIfPresent(Object modelObj, Object stateObj) {
		if (!(stateObj instanceof RagdollStateAttachment attachment)) {
			return;
		}
		RagdollPose pose = attachment.ragdolls$getPose();
		if (pose == null) {
			return;
		}
		if (modelObj instanceof HumanoidModel<?> humanoidModel) {
			apply(humanoidModel, pose);
		} else if (modelObj instanceof Model<?> genericModel) {
			applyToGenericModel(genericModel, pose);
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

	private static void applyToGenericModel(Model<?> model, RagdollPose pose) {
		ModelPart root = model.root();
		if (root == null) {
			return;
		}
		if (root.hasChild("head")) {
			ModelPart head = root.getChild("head");
			applyPart(head, pose.head());
			if (head.hasChild("hat")) {
				ModelPart hat = head.getChild("hat");
				hat.setPos(0.0f, 0.0f, 0.0f);
				hat.setRotation(0.0f, 0.0f, 0.0f);
			}
		}
		if (root.hasChild("body")) {
			applyPart(root.getChild("body"), pose.body());
		}
		if (root.hasChild("left_arm")) {
			ModelPart leftArm = root.getChild("left_arm");
			leftArm.visible = true;
			applyPart(leftArm, pose.leftArm());
		}
		if (root.hasChild("right_arm")) {
			ModelPart rightArm = root.getChild("right_arm");
			rightArm.visible = true;
			applyPart(rightArm, pose.rightArm());
		}
		if (root.hasChild("left_leg")) {
			applyPart(root.getChild("left_leg"), pose.leftLeg());
		}
		if (root.hasChild("right_leg")) {
			applyPart(root.getChild("right_leg"), pose.rightLeg());
		}
		if (root.hasChild("arms")) {
			// Hide static crossed-arms part on IllagerModel so the articulated left_arm/right_arm render
			root.getChild("arms").visible = false;
		}
	}

	private static void applyPart(ModelPart part, RagdollPose.LimbTransform transform) {
		if (part == null || transform == null) {
			return;
		}
		part.setPos(transform.x(), transform.y(), transform.z());
		part.setRotation(transform.xRot(), transform.yRot(), transform.zRot());
	}
}
