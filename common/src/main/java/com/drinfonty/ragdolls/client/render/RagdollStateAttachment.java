package com.drinfonty.ragdolls.client.render;

import com.drinfonty.ragdolls.physics.RagdollPose;

/**
 * Duck interface mixed into {@code HumanoidRenderState} to attach a physics {@link RagdollPose}.
 */
public interface RagdollStateAttachment {
	RagdollPose ragdolls$getPose();

	void ragdolls$setPose(RagdollPose pose);
}
