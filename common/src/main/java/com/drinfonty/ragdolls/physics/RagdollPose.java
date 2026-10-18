package com.drinfonty.ragdolls.physics;

/**
 * Holds the ModelPart-space pose of all 6 humanoid body parts for rendering a ragdoll frame.
 */
public record RagdollPose(
	double rootX,
	double rootY,
	double rootZ,
	LimbTransform head,
	LimbTransform body,
	LimbTransform leftArm,
	LimbTransform rightArm,
	LimbTransform leftLeg,
	LimbTransform rightLeg
) {
	/**
	 * ModelPart position (in 1/16th block model pixels) and ZYX Euler rotations (in radians).
	 */
	public record LimbTransform(
		float x,
		float y,
		float z,
		float xRot,
		float yRot,
		float zRot
	) {
	}
}
