package com.drinfonty.ragdolls.physics;

/**
 * A single Verlet-integrated point-mass particle in an articulated ragdoll skeleton.
 */
public final class RagdollParticle {
	public double x;
	public double y;
	public double z;

	public double prevX;
	public double prevY;
	public double prevZ;

	public double renderPrevX;
	public double renderPrevY;
	public double renderPrevZ;

	public final double radius;
	public final double invMass;
	public boolean onGround;

	public RagdollParticle(double x, double y, double z, double radius, double mass) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.prevX = x;
		this.prevY = y;
		this.prevZ = z;
		this.renderPrevX = x;
		this.renderPrevY = y;
		this.renderPrevZ = z;
		this.radius = radius;
		this.invMass = mass > 0.0 ? 1.0 / mass : 0.0;
		this.onGround = false;
	}

	public void snapshotRenderPosition() {
		this.renderPrevX = this.x;
		this.renderPrevY = this.y;
		this.renderPrevZ = this.z;
	}

	public void setVelocity(double vx, double vy, double vz) {
		this.prevX = this.x - vx;
		this.prevY = this.y - vy;
		this.prevZ = this.z - vz;
	}

	public void addImpulse(double ix, double iy, double iz) {
		this.prevX -= ix * this.invMass;
		this.prevY -= iy * this.invMass;
		this.prevZ -= iz * this.invMass;
	}

	public double vx() {
		return this.x - this.prevX;
	}

	public double vy() {
		return this.y - this.prevY;
	}

	public double vz() {
		return this.z - this.prevZ;
	}

	public double lerpX(float partialTick) {
		return this.renderPrevX + (this.x - this.renderPrevX) * partialTick;
	}

	public double lerpY(float partialTick) {
		return this.renderPrevY + (this.y - this.renderPrevY) * partialTick;
	}

	public double lerpZ(float partialTick) {
		return this.renderPrevZ + (this.z - this.renderPrevZ) * partialTick;
	}
}
