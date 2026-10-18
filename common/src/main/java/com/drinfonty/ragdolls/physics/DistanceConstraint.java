package com.drinfonty.ragdolls.physics;

/**
 * Distance constraint keeping two ragdoll particles at a target rest length.
 */
public final class DistanceConstraint {
	public final int indexA;
	public final int indexB;
	public final double restLength;
	public final double stiffness;

	public DistanceConstraint(int indexA, int indexB, double restLength, double stiffness) {
		this.indexA = indexA;
		this.indexB = indexB;
		this.restLength = restLength;
		this.stiffness = stiffness;
	}

	public void solve(RagdollParticle[] particles) {
		RagdollParticle a = particles[this.indexA];
		RagdollParticle b = particles[this.indexB];

		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		double distSq = dx * dx + dy * dy + dz * dz;
		if (distSq < 1.0e-12) {
			dy = 1.0e-4;
			distSq = dy * dy;
		}
		double dist = Math.sqrt(distSq);
		double totalInvMass = a.invMass + b.invMass;
		if (totalInvMass <= 0.0) {
			return;
		}

		double diff = (dist - this.restLength) / (dist * totalInvMass) * this.stiffness;
		a.x += dx * a.invMass * diff;
		a.y += dy * a.invMass * diff;
		a.z += dz * a.invMass * diff;

		b.x -= dx * b.invMass * diff;
		b.y -= dy * b.invMass * diff;
		b.z -= dz * b.invMass * diff;
	}
}
