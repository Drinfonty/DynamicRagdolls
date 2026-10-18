package com.drinfonty.ragdolls.physics;

/**
 * Anatomical cone angle constraint restricting a limb's direction relative to a reference axis
 * defined in the torso's orthonormal coordinate frame (eX = Left, eY = Down, eZ = Back).
 */
public final class JointAngleConstraint {
	public final int pivotIndex;
	public final int tipIndex;
	public final double limbLength;
	public final double localAxisX;
	public final double localAxisY;
	public final double localAxisZ;
	public final double baseMaxAngleRad;

	public JointAngleConstraint(
		int pivotIndex,
		int tipIndex,
		double limbLength,
		double localAxisX,
		double localAxisY,
		double localAxisZ,
		double baseMaxAngleRad
	) {
		this.pivotIndex = pivotIndex;
		this.tipIndex = tipIndex;
		this.limbLength = limbLength;
		double len = Math.sqrt(localAxisX * localAxisX + localAxisY * localAxisY + localAxisZ * localAxisZ);
		this.localAxisX = len > 1.0e-8 ? localAxisX / len : 0.0;
		this.localAxisY = len > 1.0e-8 ? localAxisY / len : 1.0;
		this.localAxisZ = len > 1.0e-8 ? localAxisZ / len : 0.0;
		this.baseMaxAngleRad = baseMaxAngleRad;
	}

	/**
	 * Projects the tip particle back inside the allowed cone around the torso-relative axis.
	 */
	public void solve(
		RagdollParticle[] particles,
		double[] torsoEx,
		double[] torsoEy,
		double[] torsoEz,
		double coneScale
	) {
		RagdollParticle pivot = particles[this.pivotIndex];
		RagdollParticle tip = particles[this.tipIndex];

		// Transform local preferred axis into world space using torso basis
		double refX = this.localAxisX * torsoEx[0] + this.localAxisY * torsoEy[0] + this.localAxisZ * torsoEz[0];
		double refY = this.localAxisX * torsoEx[1] + this.localAxisY * torsoEy[1] + this.localAxisZ * torsoEz[1];
		double refZ = this.localAxisX * torsoEx[2] + this.localAxisY * torsoEy[2] + this.localAxisZ * torsoEz[2];

		double dx = tip.x - pivot.x;
		double dy = tip.y - pivot.y;
		double dz = tip.z - pivot.z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist < 1.0e-7) {
			tip.x = pivot.x + refX * this.limbLength;
			tip.y = pivot.y + refY * this.limbLength;
			tip.z = pivot.z + refZ * this.limbLength;
			return;
		}

		double dirX = dx / dist;
		double dirY = dy / dist;
		double dirZ = dz / dist;

		double maxAngle = Math.min(Math.PI * 0.85, this.baseMaxAngleRad * coneScale);
		double minCos = Math.cos(maxAngle);
		double dot = dirX * refX + dirY * refY + dirZ * refZ;

		if (dot < minCos) {
			// Decompose dir into component along ref and perpendicular to ref
			double perpX = dirX - dot * refX;
			double perpY = dirY - dot * refY;
			double perpZ = dirZ - dot * refZ;
			double perpLen = Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);

			double sinMax = Math.sin(maxAngle);
			double clampedX;
			double clampedY;
			double clampedZ;
			if (perpLen > 1.0e-7) {
				clampedX = refX * minCos + (perpX / perpLen) * sinMax;
				clampedY = refY * minCos + (perpY / perpLen) * sinMax;
				clampedZ = refZ * minCos + (perpZ / perpLen) * sinMax;
			} else {
				clampedX = refX;
				clampedY = refY;
				clampedZ = refZ;
			}

			double targetX = pivot.x + clampedX * this.limbLength;
			double targetY = pivot.y + clampedY * this.limbLength;
			double targetZ = pivot.z + clampedZ * this.limbLength;

			// Apply soft projection (80% toward tip, 20% reaction on pivot)
			double corrX = targetX - tip.x;
			double corrY = targetY - tip.y;
			double corrZ = targetZ - tip.z;
			double totalInvMass = pivot.invMass + tip.invMass;
			if (totalInvMass > 0.0) {
				double tipShare = (tip.invMass / totalInvMass) * 0.65;
				double pivotShare = (pivot.invMass / totalInvMass) * 0.65;
				tip.x += corrX * tipShare;
				tip.y += corrY * tipShare;
				tip.z += corrZ * tipShare;
				pivot.x -= corrX * pivotShare;
				pivot.y -= corrY * pivotShare;
				pivot.z -= corrZ * pivotShare;
			}
		}
	}
}
