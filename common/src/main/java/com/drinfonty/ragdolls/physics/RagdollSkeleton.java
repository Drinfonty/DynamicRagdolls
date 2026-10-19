package com.drinfonty.ragdolls.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Full 12-node articulated 6-limb Verlet ragdoll skeleton with tetrahedral torso truss,
 * anatomical joint-cone limits, world block collision, and water buoyancy.
 */
public final class RagdollSkeleton {
	public static final int NECK = 0;
	public static final int PELVIS = 1;
	public static final int LEFT_SHOULDER = 2;
	public static final int RIGHT_SHOULDER = 3;
	public static final int LEFT_HIP = 4;
	public static final int RIGHT_HIP = 5;
	public static final int CHEST_FRONT = 6;
	public static final int HEAD_TOP = 7;
	public static final int LEFT_HAND = 8;
	public static final int RIGHT_HAND = 9;
	public static final int LEFT_FOOT = 10;
	public static final int RIGHT_FOOT = 11;
	public static final int PARTICLE_COUNT = 12;

	private static final int SUBSTEPS = 4;
	private static final double BASE_GRAVITY_PER_TICK = 0.055;
	private static final double AIR_DAMPING = 0.985;
	private static final double WATER_DAMPING = 0.84;
	private static final double WATER_BUOYANCY_PER_TICK = 0.042;
	private static final double SETTLE_SPEED_SQ_THRESHOLD = 1.2e-5;
	private static final int SETTLE_TICKS_REQUIRED = 18;

	private final RagdollParticle[] particles = new RagdollParticle[PARTICLE_COUNT];
	private final List<DistanceConstraint> distanceConstraints = new ArrayList<>();
	private final List<JointAngleConstraint> jointConstraints = new ArrayList<>();
	private final double renderScale;
	private final double pixelUnit;

	private int settledTicks = 0;
	private boolean settled = false;

	public RagdollSkeleton(
		double originX,
		double originY,
		double originZ,
		float bodyYawDeg,
		float headPitchDeg,
		double renderScale,
		double vx,
		double vy,
		double vz,
		double impulseX,
		double impulseY,
		double impulseZ
	) {
		this.renderScale = renderScale > 0.05 ? renderScale : 1.0;
		this.pixelUnit = this.renderScale / 16.0;
		double u = this.pixelUnit;

		// Entity orientation in world space:
		// In Minecraft, yaw = 0 faces +Z, yaw = 90 faces -X, yaw = 180 faces -Z, yaw = -90 faces +X.
		double yawRad = Math.toRadians(bodyYawDeg);
		double forwardX = -Math.sin(yawRad);
		double forwardZ = Math.cos(yawRad);
		// Left vector when facing (forwardX, 0, forwardZ) with Up = (0, 1, 0):
		// Up x Forward = (0, 1, 0) x (forwardX, 0, forwardZ) = (forwardZ, 0, -forwardX)
		double leftX = forwardZ;
		double leftZ = -forwardX;

		// Construct the 12 particles in world space at standing pose
		this.particles[NECK] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 0.0, 24.0 * u, 0.0, 2.2 * u, 1.4);
		this.particles[PELVIS] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 0.0, 12.0 * u, 0.0, 2.2 * u, 1.6);
		this.particles[LEFT_SHOULDER] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 5.0 * u, 22.0 * u, 0.0, 1.8 * u, 1.0);
		this.particles[RIGHT_SHOULDER] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, -5.0 * u, 22.0 * u, 0.0, 1.8 * u, 1.0);
		this.particles[LEFT_HIP] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 1.9 * u, 12.0 * u, 0.0, 1.8 * u, 1.1);
		this.particles[RIGHT_HIP] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, -1.9 * u, 12.0 * u, 0.0, 1.8 * u, 1.1);
		this.particles[CHEST_FRONT] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 0.0, 18.0 * u, 3.5 * u, 1.8 * u, 0.8);

		this.particles[HEAD_TOP] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 0.0, 31.0 * u, 0.0, 3.6 * u, 1.0);
		this.particles[LEFT_HAND] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 5.5 * u, 12.0 * u, 0.5 * u, 1.8 * u, 0.6);
		this.particles[RIGHT_HAND] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, -5.5 * u, 12.0 * u, -0.5 * u, 1.8 * u, 0.6);
		this.particles[LEFT_FOOT] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, 2.0 * u, 1.8 * u, 0.3 * u, 1.8 * u, 0.8);
		this.particles[RIGHT_FOOT] = createNode(originX, originY, originZ, leftX, leftZ, forwardX, forwardZ, -2.0 * u, 1.8 * u, -0.3 * u, 1.8 * u, 0.8);

		// 1. Rigid torso truss distance constraints (all pairs of torso cage nodes)
		int[] torsoNodes = {NECK, PELVIS, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP, CHEST_FRONT};
		for (int i = 0; i < torsoNodes.length; i++) {
			for (int j = i + 1; j < torsoNodes.length; j++) {
				addRestDistance(torsoNodes[i], torsoNodes[j], 1.0);
			}
		}

		// 2. Primary limb bone distance constraints
		double headLength = addRestDistance(NECK, HEAD_TOP, 1.0);
		double leftArmLength = addRestDistance(LEFT_SHOULDER, LEFT_HAND, 1.0);
		double rightArmLength = addRestDistance(RIGHT_SHOULDER, RIGHT_HAND, 1.0);
		double leftLegLength = addRestDistance(LEFT_HIP, LEFT_FOOT, 1.0);
		double rightLegLength = addRestDistance(RIGHT_HIP, RIGHT_FOOT, 1.0);

		// 3. Anatomical joint angle cone constraints in torso frame (eX = Left, eY = Down, eZ = Back):
		// - Head points UP (-eY), allowed cone ~40 deg so head cube never dips into shoulders/torso
		this.jointConstraints.add(new JointAngleConstraint(NECK, HEAD_TOP, headLength, 0.0, -1.0, -0.05, Math.toRadians(40.0)));
		// - Left arm points Down-OutwardLeft-SlightlyForward (+0.42 eX, +0.87 eY, -0.25 eZ), allowed cone ~75 deg
		this.jointConstraints.add(new JointAngleConstraint(LEFT_SHOULDER, LEFT_HAND, leftArmLength, 0.42, 0.87, -0.25, Math.toRadians(75.0)));
		// - Right arm points Down-OutwardRight-SlightlyForward (-0.42 eX, +0.87 eY, -0.25 eZ), allowed cone ~75 deg
		this.jointConstraints.add(new JointAngleConstraint(RIGHT_SHOULDER, RIGHT_HAND, rightArmLength, -0.42, 0.87, -0.25, Math.toRadians(75.0)));
		// - Left leg points Down-OutwardLeft-Forward (+0.22 eX, +0.92 eY, -0.30 eZ), allowed cone ~62 deg
		this.jointConstraints.add(new JointAngleConstraint(LEFT_HIP, LEFT_FOOT, leftLegLength, 0.22, 0.92, -0.30, Math.toRadians(62.0)));
		// - Right leg points Down-OutwardRight-Forward (-0.22 eX, +0.92 eY, -0.30 eZ), allowed cone ~62 deg
		this.jointConstraints.add(new JointAngleConstraint(RIGHT_HIP, RIGHT_FOOT, rightLegLength, -0.22, 0.92, -0.30, Math.toRadians(62.0)));

		// 4. Apply initial entity velocity + directional death knockback impulse (with upper-body torque so ragdoll topples!)
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			RagdollParticle p = this.particles[i];
			double heightFactor = switch (i) {
				case HEAD_TOP -> 1.45;
				case NECK, LEFT_SHOULDER, RIGHT_SHOULDER, CHEST_FRONT -> 1.25;
				case LEFT_HAND, RIGHT_HAND -> 1.15;
				case PELVIS, LEFT_HIP, RIGHT_HIP -> 0.75;
				default -> 0.35;
			};
			double fallBiasX = (Math.abs(impulseX) + Math.abs(impulseZ) < 1.0e-4) ? (-forwardX * 0.06 + leftX * 0.025) * heightFactor : 0.0;
			double fallBiasZ = (Math.abs(impulseX) + Math.abs(impulseZ) < 1.0e-4) ? (-forwardZ * 0.06 + leftZ * 0.025) * heightFactor : 0.0;

			double totalVx = (vx + impulseX * heightFactor + fallBiasX) / SUBSTEPS;
			double totalVy = (vy + impulseY * (0.6 + 0.4 * heightFactor)) / SUBSTEPS;
			double totalVz = (vz + impulseZ * heightFactor + fallBiasZ) / SUBSTEPS;
			p.setVelocity(totalVx, totalVy, totalVz);
		}
	}

	private static RagdollParticle createNode(
		double ox, double oy, double oz,
		double leftX, double leftZ,
		double forwardX, double forwardZ,
		double localLeft, double localUp, double localForward,
		double radius, double mass
	) {
		double wx = ox + leftX * localLeft + forwardX * localForward;
		double wy = oy + localUp;
		double wz = oz + leftZ * localLeft + forwardZ * localForward;
		return new RagdollParticle(wx, wy, wz, radius, mass);
	}

	private double addRestDistance(int indexA, int indexB, double stiffness) {
		RagdollParticle a = this.particles[indexA];
		RagdollParticle b = this.particles[indexB];
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		this.distanceConstraints.add(new DistanceConstraint(indexA, indexB, dist, stiffness));
		return dist;
	}

	/**
	 * Advances the ragdoll physics simulation by one client tick (20 Hz) with ground dust disabled.
	 */
	public void step(
		RagdollWorld world,
		float gravityScale,
		float bounciness,
		float groundFriction,
		boolean waterBuoyancy,
		float jointConeScale,
		int solverIterations
	) {
		step(world, gravityScale, bounciness, groundFriction, waterBuoyancy, jointConeScale, solverIterations, false);
	}

	/**
	 * Advances the ragdoll physics simulation by one client tick (20 Hz).
	 */
	public void step(
		RagdollWorld world,
		float gravityScale,
		float bounciness,
		float groundFriction,
		boolean waterBuoyancy,
		float jointConeScale,
		int solverIterations,
		boolean groundDustParticles
	) {
		for (RagdollParticle p : this.particles) {
			p.snapshotRenderPosition();
		}

		if (this.settled) {
			RagdollParticle pelvis = this.particles[PELVIS];
			List<RagdollWorld.Box> support = world.getCollisionBoxes(
				pelvis.x - 0.5, pelvis.y - 0.6, pelvis.z - 0.5,
				pelvis.x + 0.5, pelvis.y + 0.2, pelvis.z + 0.5
			);
			if (support.isEmpty()) {
				this.settled = false;
				this.settledTicks = 0;
			} else {
				return;
			}
		}

		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
		for (RagdollParticle p : this.particles) {
			if (p.x < minX) minX = p.x;
			if (p.y < minY) minY = p.y;
			if (p.z < minZ) minZ = p.z;
			if (p.x > maxX) maxX = p.x;
			if (p.y > maxY) maxY = p.y;
			if (p.z > maxZ) maxZ = p.z;
		}
		List<RagdollWorld.Box> worldBoxes = world.getCollisionBoxes(
			minX - 1.2, minY - 1.5, minZ - 1.2,
			maxX + 1.2, maxY + 1.2, maxZ + 1.2
		);

		double dtRatio = 1.0 / SUBSTEPS;
		double subGravity = BASE_GRAVITY_PER_TICK * gravityScale * dtRatio * dtRatio;
		double subBuoyancy = WATER_BUOYANCY_PER_TICK * dtRatio * dtRatio;
		double subAirDamping = Math.pow(AIR_DAMPING, dtRatio);
		double subWaterDamping = Math.pow(WATER_DAMPING, dtRatio);

		double[] torsoEx = new double[3];
		double[] torsoEy = new double[3];
		double[] torsoEz = new double[3];

		int iterations = Math.max(2, solverIterations);
		for (int sub = 0; sub < SUBSTEPS; sub++) {
			for (RagdollParticle p : this.particles) {
				boolean inWater = waterBuoyancy && world.isWater(p.x, p.y, p.z);
				double damping = inWater ? subWaterDamping : subAirDamping;
				double vx = (p.x - p.prevX) * damping;
				double vy = (p.y - p.prevY) * damping;
				double vz = (p.z - p.prevZ) * damping;

				p.prevX = p.x;
				p.prevY = p.y;
				p.prevZ = p.z;

				double accelY = inWater ? (subBuoyancy - subGravity * 0.35) : -subGravity;
				p.x += vx;
				p.y += vy + accelY;
				p.z += vz;
				p.onGround = false;
			}

			for (int iter = 0; iter < iterations; iter++) {
				for (DistanceConstraint dc : this.distanceConstraints) {
					dc.solve(this.particles);
				}

				enforceTorsoRigidity(torsoEx, torsoEy, torsoEz);

				for (JointAngleConstraint jc : this.jointConstraints) {
					jc.solve(this.particles, torsoEx, torsoEy, torsoEz, jointConeScale);
				}

				solveTorsoLimbCollisions(torsoEx, torsoEy, torsoEz);
				solveLimbCapsuleCollisions();

				boolean spawnDust = groundDustParticles && iter == 0;
				for (int i = 0; i < PARTICLE_COUNT; i++) {
					if (i == CHEST_FRONT) {
						continue;
					}
					resolveParticleCollision(this.particles[i], worldBoxes, bounciness, groundFriction, world, spawnDust);
				}
			}
		}

		// Final polish pass to guarantee exact bone lengths and zero self-penetration after world collision
		for (DistanceConstraint dc : this.distanceConstraints) {
			dc.solve(this.particles);
		}
		enforceTorsoRigidity(torsoEx, torsoEy, torsoEz);
		solveTorsoLimbCollisions(torsoEx, torsoEy, torsoEz);
		solveLimbCapsuleCollisions();
		for (DistanceConstraint dc : this.distanceConstraints) {
			dc.solve(this.particles);
		}
		enforceTorsoRigidity(torsoEx, torsoEy, torsoEz);
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			if (i == CHEST_FRONT) {
				continue;
			}
			resolveParticleCollision(this.particles[i], worldBoxes, bounciness, groundFriction, world, false);
		}
		solveTorsoLimbCollisions(torsoEx, torsoEy, torsoEz);
		solveLimbCapsuleCollisions();

		double maxSpeedSq = 0.0;
		int groundedCount = 0;
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			if (i == CHEST_FRONT) {
				continue;
			}
			RagdollParticle p = this.particles[i];
			double dx = p.x - p.renderPrevX;
			double dy = p.y - p.renderPrevY;
			double dz = p.z - p.renderPrevZ;
			double speedSq = dx * dx + dy * dy + dz * dz;
			if (speedSq > maxSpeedSq) {
				maxSpeedSq = speedSq;
			}
			if (p.onGround) {
				groundedCount++;
			}
		}

		if (groundedCount >= 2 && maxSpeedSq < SETTLE_SPEED_SQ_THRESHOLD) {
			this.settledTicks++;
			if (this.settledTicks >= SETTLE_TICKS_REQUIRED) {
				this.settled = true;
				for (RagdollParticle p : this.particles) {
					p.snapshotRenderPosition();
					p.prevX = p.x;
					p.prevY = p.y;
					p.prevZ = p.z;
				}
			}
		} else {
			this.settledTicks = 0;
		}
	}

	/**
	 * Extracts the right-handed orthonormal torso frame (eX = Left, eY = Down, eZ = Back)
	 * and shape-matches the 7 torso particles to their exact anatomical rest offsets so the
	 * torso never buckles or collapses its shoulder/hip sockets into the chest.
	 */
	private void enforceTorsoRigidity(double[] eX, double[] eY, double[] eZ) {
		RagdollParticle neck = this.particles[NECK];
		RagdollParticle pelvis = this.particles[PELVIS];
		RagdollParticle leftShoulder = this.particles[LEFT_SHOULDER];
		RagdollParticle rightShoulder = this.particles[RIGHT_SHOULDER];
		RagdollParticle leftHip = this.particles[LEFT_HIP];
		RagdollParticle rightHip = this.particles[RIGHT_HIP];
		RagdollParticle chestFront = this.particles[CHEST_FRONT];

		double u = this.pixelUnit;

		// 1. Primary spine axis eY (Down: neck -> pelvis)
		eY[0] = (pelvis.x - neck.x) + 0.25 * (0.5 * (leftHip.x + rightHip.x) - 0.5 * (leftShoulder.x + rightShoulder.x));
		eY[1] = (pelvis.y - neck.y) + 0.25 * (0.5 * (leftHip.y + rightHip.y) - 0.5 * (leftShoulder.y + rightShoulder.y));
		eY[2] = (pelvis.z - neck.z) + 0.25 * (0.5 * (leftHip.z + rightHip.z) - 0.5 * (leftShoulder.z + rightShoulder.z));
		RagdollTransform.normalize(eY);

		// 2. Lateral axis eX (Left: rightShoulder -> leftShoulder + hip contribution)
		double sx = (leftShoulder.x - rightShoulder.x) + 0.5 * (leftHip.x - rightHip.x);
		double sy = (leftShoulder.y - rightShoulder.y) + 0.5 * (leftHip.y - rightHip.y);
		double sz = (leftShoulder.z - rightShoulder.z) + 0.5 * (leftHip.z - rightHip.z);
		double dot = sx * eY[0] + sy * eY[1] + sz * eY[2];
		eX[0] = sx - dot * eY[0];
		eX[1] = sy - dot * eY[1];
		eX[2] = sz - dot * eY[2];
		RagdollTransform.normalize(eX);

		// 3. Back axis eZ = eX x eY
		RagdollTransform.cross(eX, eY, eZ);
		RagdollTransform.normalize(eZ);

		// 4. Compute torso center (midpoint between neck and pelvis at 6u below neck)
		double spineMidX = 0.5 * (neck.x + pelvis.x);
		double spineMidY = 0.5 * (neck.y + pelvis.y);
		double spineMidZ = 0.5 * (neck.z + pelvis.z);

		double shoulderMidX = 0.5 * (leftShoulder.x + rightShoulder.x) + 4.0 * u * eY[0];
		double shoulderMidY = 0.5 * (leftShoulder.y + rightShoulder.y) + 4.0 * u * eY[1];
		double shoulderMidZ = 0.5 * (leftShoulder.z + rightShoulder.z) + 4.0 * u * eY[2];

		double hipMidX = 0.5 * (leftHip.x + rightHip.x) - 6.0 * u * eY[0];
		double hipMidY = 0.5 * (leftHip.y + rightHip.y) - 6.0 * u * eY[1];
		double hipMidZ = 0.5 * (leftHip.z + rightHip.z) - 6.0 * u * eY[2];

		double midX = 0.5 * spineMidX + 0.25 * shoulderMidX + 0.25 * hipMidX;
		double midY = 0.5 * spineMidY + 0.25 * shoulderMidY + 0.25 * hipMidY;
		double midZ = 0.5 * spineMidZ + 0.25 * shoulderMidZ + 0.25 * hipMidZ;

		// 5. Shape-match all 7 torso nodes to exact rigid coordinates in (eX, eY, eZ)
		neck.x = midX - 6.0 * u * eY[0];
		neck.y = midY - 6.0 * u * eY[1];
		neck.z = midZ - 6.0 * u * eY[2];

		pelvis.x = midX + 6.0 * u * eY[0];
		pelvis.y = midY + 6.0 * u * eY[1];
		pelvis.z = midZ + 6.0 * u * eY[2];

		leftShoulder.x = neck.x + 5.0 * u * eX[0] + 2.0 * u * eY[0];
		leftShoulder.y = neck.y + 5.0 * u * eX[1] + 2.0 * u * eY[1];
		leftShoulder.z = neck.z + 5.0 * u * eX[2] + 2.0 * u * eY[2];

		rightShoulder.x = neck.x - 5.0 * u * eX[0] + 2.0 * u * eY[0];
		rightShoulder.y = neck.y - 5.0 * u * eX[1] + 2.0 * u * eY[1];
		rightShoulder.z = neck.z - 5.0 * u * eX[2] + 2.0 * u * eY[2];

		leftHip.x = neck.x + 1.9 * u * eX[0] + 12.0 * u * eY[0];
		leftHip.y = neck.y + 1.9 * u * eX[1] + 12.0 * u * eY[1];
		leftHip.z = neck.z + 1.9 * u * eX[2] + 12.0 * u * eY[2];

		rightHip.x = neck.x - 1.9 * u * eX[0] + 12.0 * u * eY[0];
		rightHip.y = neck.y - 1.9 * u * eX[1] + 12.0 * u * eY[1];
		rightHip.z = neck.z - 1.9 * u * eX[2] + 12.0 * u * eY[2];

		chestFront.x = midX - 3.5 * u * eZ[0];
		chestFront.y = midY - 3.5 * u * eZ[1];
		chestFront.z = midZ - 3.5 * u * eZ[2];
	}

	/**
	 * Prevents all 5 limbs (head, left/right arms, left/right legs) from clipping into the
	 * torso volume [-4u, +4u] x [0, 12u] x [-2u, +2u] or the head volume in torso-local space.
	 */
	private void solveTorsoLimbCollisions(double[] eX, double[] eY, double[] eZ) {
		double u = this.pixelUnit;

		// 1. Left Arm (pivot at +5u, +2u, 0; length 10u; inner face at x_arm = -1u):
		// Keep LEFT_HAND outside the torso & head lateral plane (x_t >= 5.3u beside torso, >= 4.8u in front/back)
		// and prevent upside-down shoulder inversion (y_t >= 2.0u).
		clampLimbInTorsoSpace(LEFT_SHOULDER, LEFT_HAND, 10.0 * u, eX, eY, eZ, true, false);

		// 2. Right Arm (pivot at -5u, +2u, 0; length 10u; inner face at x_arm = +1u):
		clampLimbInTorsoSpace(RIGHT_SHOULDER, RIGHT_HAND, 10.0 * u, eX, eY, eZ, false, false);

		// 3. Left Leg (pivot at +1.9u, +12u, 0; length 10.2u):
		// Keep LEFT_FOOT below the torso hip plane (y_t >= 15.5u) and on the left side of the midline (x_t >= 1.9u).
		clampLimbInTorsoSpace(LEFT_HIP, LEFT_FOOT, 10.2 * u, eX, eY, eZ, true, true);

		// 4. Right Leg (pivot at -1.9u, +12u, 0; length 10.2u):
		clampLimbInTorsoSpace(RIGHT_HIP, RIGHT_FOOT, 10.2 * u, eX, eY, eZ, false, true);

		// 5. Head (pivot at 0, 0, 0; length 7u):
		// Ensure HEAD_TOP stays above the neck/shoulder plane (y_t <= -5.2u) so the 8x8x8 head cube never dips into the chest.
		RagdollParticle neck = this.particles[NECK];
		RagdollParticle head = this.particles[HEAD_TOP];
		double hdx = head.x - neck.x;
		double hdy = head.y - neck.y;
		double hdz = head.z - neck.z;
		double hx = hdx * eX[0] + hdy * eX[1] + hdz * eX[2];
		double hy = hdx * eY[0] + hdy * eY[1] + hdz * eY[2];
		double hz = hdx * eZ[0] + hdy * eZ[1] + hdz * eZ[2];
		double minHeadUpY = -5.2 * u;
		if (hy > minHeadUpY) {
			hy = minHeadUpY;
			double maxLat = 4.6 * u;
			double latSq = hx * hx + hz * hz;
			if (latSq > maxLat * maxLat) {
				double scale = maxLat / Math.sqrt(latSq);
				hx *= scale;
				hz *= scale;
			}
			double len = Math.sqrt(hx * hx + hy * hy + hz * hz);
			if (len > 1.0e-8) {
				double s = (7.0 * u) / len;
				hx *= s;
				hy *= s;
				hz *= s;
			}
			applyTorsoLocalPosition(neck, head, hx, hy, hz, eX, eY, eZ);
		}
	}

	private void clampLimbInTorsoSpace(
		int pivotIdx,
		int tipIdx,
		double limbLength,
		double[] eX,
		double[] eY,
		double[] eZ,
		boolean isLeft,
		boolean isLeg
	) {
		RagdollParticle neck = this.particles[NECK];
		RagdollParticle pivot = this.particles[pivotIdx];
		RagdollParticle tip = this.particles[tipIdx];
		double u = this.pixelUnit;

		double dx = tip.x - neck.x;
		double dy = tip.y - neck.y;
		double dz = tip.z - neck.z;

		double xt = dx * eX[0] + dy * eX[1] + dz * eX[2];
		double yt = dx * eY[0] + dy * eY[1] + dz * eY[2];
		double zt = dx * eZ[0] + dy * eZ[1] + dz * eZ[2];

		boolean modified = false;
		if (!isLeg) {
			// Arm vs Torso & Head exclusion:
			// Torso spans x_t in [-4u, +4u], y_t in [0, 12u], z_t in [-2u, +2u].
			// Left arm pivot is at (+5u, +2u, 0) with inner face 1u inward (at +4u when straight down).
			double minOutwardX = (Math.abs(zt) < 4.4 * u) ? 5.4 * u : 4.8 * u;
			if (isLeft) {
				if (xt < minOutwardX) {
					xt = minOutwardX;
					modified = true;
				}
			} else {
				if (xt > -minOutwardX) {
					xt = -minOutwardX;
					modified = true;
				}
			}
			// Prevent arm from inverting upside-down above shoulder (which flips the +3u outer arm width into the head/neck)
			double minArmY = 2.2 * u;
			if (yt < minArmY) {
				yt = minArmY;
				modified = true;
			}
		} else {
			// Leg vs Torso & Contralateral Leg exclusion:
			// Hip pivot is at (+/-1.9u, +12u, 0). Keep foot below y_t >= 15.8u so the leg never folds upward into the torso.
			double minLegY = 15.8 * u;
			if (yt < minLegY) {
				yt = minLegY;
				modified = true;
			}
			// Prevent backward scorpion hyperextension into the lower back
			double maxBackZ = 5.0 * u;
			if (zt > maxBackZ) {
				zt = maxBackZ;
				modified = true;
			}
			// Keep each leg on its own side of the sagittal plane so left and right legs don't cross through each other
			double minOutwardLegX = 1.9 * u;
			if (isLeft) {
				if (xt < minOutwardLegX) {
					xt = minOutwardLegX;
					modified = true;
				}
			} else {
				if (xt > -minOutwardLegX) {
					xt = -minOutwardLegX;
					modified = true;
				}
			}
		}

		if (modified) {
			// Re-normalize to exact limbLength from the joint pivot
			double pvx = (pivot.x - neck.x) * eX[0] + (pivot.y - neck.y) * eX[1] + (pivot.z - neck.z) * eX[2];
			double pvy = (pivot.x - neck.x) * eY[0] + (pivot.y - neck.y) * eY[1] + (pivot.z - neck.z) * eY[2];
			double pvz = (pivot.x - neck.x) * eZ[0] + (pivot.y - neck.y) * eZ[1] + (pivot.z - neck.z) * eZ[2];

			double relX = xt - pvx;
			double relY = yt - pvy;
			double relZ = zt - pvz;
			double len = Math.sqrt(relX * relX + relY * relY + relZ * relZ);
			if (len > 1.0e-8) {
				double scale = limbLength / len;
				xt = pvx + relX * scale;
				yt = pvy + relY * scale;
				zt = pvz + relZ * scale;
			}
			applyTorsoLocalPosition(neck, tip, xt, yt, zt, eX, eY, eZ);
		}
	}

	private static void applyTorsoLocalPosition(
		RagdollParticle neck,
		RagdollParticle tip,
		double xt,
		double yt,
		double zt,
		double[] eX,
		double[] eY,
		double[] eZ
	) {
		double newX = neck.x + xt * eX[0] + yt * eY[0] + zt * eZ[0];
		double newY = neck.y + xt * eX[1] + yt * eY[1] + zt * eZ[1];
		double newZ = neck.z + xt * eX[2] + yt * eY[2] + zt * eZ[2];

		double corrX = newX - tip.x;
		double corrY = newY - tip.y;
		double corrZ = newZ - tip.z;
		double corrLenSq = corrX * corrX + corrY * corrY + corrZ * corrZ;

		tip.x = newX;
		tip.y = newY;
		tip.z = newZ;

		// Damp velocity component moving against the collision normal
		if (corrLenSq > 1.0e-12) {
			double vx = tip.x - tip.prevX;
			double vy = tip.y - tip.prevY;
			double vz = tip.z - tip.prevZ;
			double vDotCorr = vx * corrX + vy * corrY + vz * corrZ;
			if (vDotCorr < 0.0) {
				double proj = vDotCorr / corrLenSq;
				tip.prevX += corrX * proj;
				tip.prevY += corrY * proj;
				tip.prevZ += corrZ * proj;
			}
		}
	}

	/**
	 * Resolves 3D capsule-vs-capsule collisions between all limb segments so arms and legs
	 * never clip through each other or the head.
	 */
	private void solveLimbCapsuleCollisions() {
		double u = this.pixelUnit;
		double limbDiameter = 4.2 * u;
		double headLimbRadiusSum = 5.6 * u;

		// Arm vs ipsilateral & contralateral Leg capsules
		solveCapsuleSegmentCollision(LEFT_SHOULDER, LEFT_HAND, LEFT_HIP, LEFT_FOOT, limbDiameter);
		solveCapsuleSegmentCollision(RIGHT_SHOULDER, RIGHT_HAND, RIGHT_HIP, RIGHT_FOOT, limbDiameter);
		solveCapsuleSegmentCollision(LEFT_SHOULDER, LEFT_HAND, RIGHT_HIP, RIGHT_FOOT, limbDiameter);
		solveCapsuleSegmentCollision(RIGHT_SHOULDER, RIGHT_HAND, LEFT_HIP, LEFT_FOOT, limbDiameter);

		// Left Arm vs Right Arm & Left Leg vs Right Leg
		solveCapsuleSegmentCollision(LEFT_SHOULDER, LEFT_HAND, RIGHT_SHOULDER, RIGHT_HAND, limbDiameter);
		solveCapsuleSegmentCollision(LEFT_HIP, LEFT_FOOT, RIGHT_HIP, RIGHT_FOOT, 3.8 * u);

		// Arms vs Head
		solveCapsuleSegmentCollision(LEFT_SHOULDER, LEFT_HAND, NECK, HEAD_TOP, headLimbRadiusSum);
		solveCapsuleSegmentCollision(RIGHT_SHOULDER, RIGHT_HAND, NECK, HEAD_TOP, headLimbRadiusSum);
	}

	private void solveCapsuleSegmentCollision(int p0Idx, int p1Idx, int q0Idx, int q1Idx, double minDist) {
		RagdollParticle p0 = this.particles[p0Idx];
		RagdollParticle p1 = this.particles[p1Idx];
		RagdollParticle q0 = this.particles[q0Idx];
		RagdollParticle q1 = this.particles[q1Idx];

		double ux = p1.x - p0.x, uy = p1.y - p0.y, uz = p1.z - p0.z;
		double vx = q1.x - q0.x, vy = q1.y - q0.y, vz = q1.z - q0.z;
		double wx = p0.x - q0.x, wy = p0.y - q0.y, wz = p0.z - q0.z;

		double a = ux * ux + uy * uy + uz * uz;
		double b = ux * vx + uy * vy + uz * vz;
		double c = vx * vx + vy * vy + vz * vz;
		double d = ux * wx + uy * wy + uz * wz;
		double e = vx * wx + vy * wy + vz * wz;
		double denom = a * c - b * b;

		double sN, sD = denom;
		double tN, tD = denom;

		if (denom < 1.0e-9) {
			sN = 0.0;
			sD = 1.0;
			tN = e;
			tD = c;
		} else {
			sN = (b * e - c * d);
			tN = (a * e - b * d);
			if (sN < 0.0) {
				sN = 0.0;
				tN = e;
				tD = c;
			} else if (sN > sD) {
				sN = sD;
				tN = e + b;
				tD = c;
			}
		}

		if (tN < 0.0) {
			tN = 0.0;
			if (-d < 0.0) {
				sN = 0.0;
			} else if (-d > a) {
				sN = sD;
			} else {
				sN = -d;
				sD = a;
			}
		} else if (tN > tD) {
			tN = tD;
			if ((-d + b) < 0.0) {
				sN = 0.0;
			} else if ((-d + b) > a) {
				sN = sD;
			} else {
				sN = (-d + b);
				sD = a;
			}
		}

		double s = Math.abs(sN) < 1.0e-9 ? 0.0 : sN / sD;
		double t = Math.abs(tN) < 1.0e-9 ? 0.0 : tN / tD;

		// Only separate along the movable distal portions of the segments
		if (s < 0.15 && t < 0.15) {
			return;
		}

		double cpx = p0.x + s * ux;
		double cpy = p0.y + s * uy;
		double cpz = p0.z + s * uz;
		double cqx = q0.x + t * vx;
		double cqy = q0.y + t * vy;
		double cqz = q0.z + t * vz;

		double dx = cpx - cqx;
		double dy = cpy - cqy;
		double dz = cpz - cqz;
		double distSq = dx * dx + dy * dy + dz * dz;

		if (distSq < minDist * minDist && distSq > 1.0e-12) {
			double dist = Math.sqrt(distSq);
			double pen = minDist - dist;
			double nx = dx / dist;
			double ny = dy / dist;
			double nz = dz / dist;

			double wp = Math.max(0.15, s) * p1.invMass;
			double wq = Math.max(0.15, t) * q1.invMass;
			double wSum = wp + wq;
			if (wSum > 1.0e-9) {
				double pushP = pen * (wp / wSum);
				double pushQ = pen * (wq / wSum);
				p1.x += nx * pushP;
				p1.y += ny * pushP;
				p1.z += nz * pushP;
				q1.x -= nx * pushQ;
				q1.y -= ny * pushQ;
				q1.z -= nz * pushQ;
			}
		}
	}

	private static void resolveParticleCollision(
		RagdollParticle p,
		List<RagdollWorld.Box> boxes,
		float bounciness,
		float groundFriction,
		RagdollWorld world,
		boolean spawnDust
	) {
		double r = p.radius;
		for (int i = 0, size = boxes.size(); i < size; i++) {
			RagdollWorld.Box box = boxes.get(i);
			if (!box.intersects(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r)) {
				continue;
			}

			double cx = Math.max(box.minX(), Math.min(p.x, box.maxX()));
			double cy = Math.max(box.minY(), Math.min(p.y, box.maxY()));
			double cz = Math.max(box.minZ(), Math.min(p.z, box.maxZ()));

			double dx = p.x - cx;
			double dy = p.y - cy;
			double dz = p.z - cz;
			double distSq = dx * dx + dy * dy + dz * dz;

			double nx = 0.0;
			double ny = 0.0;
			double nz = 0.0;
			double penetration = 0.0;

			if (distSq > 1.0e-10 && distSq < r * r) {
				double dist = Math.sqrt(distSq);
				nx = dx / dist;
				ny = dy / dist;
				nz = dz / dist;
				penetration = r - dist;
			} else if (distSq <= 1.0e-10) {
				double penTop = box.maxY() - p.y + r;
				double penBottom = p.y - box.minY() + r;
				double penWest = p.x - box.minX() + r;
				double penEast = box.maxX() - p.x + r;
				double penNorth = p.z - box.minZ() + r;
				double penSouth = box.maxZ() - p.z + r;

				penetration = penTop;
				nx = 0.0;
				ny = 1.0;
				nz = 0.0;

				if (penWest < penetration) {
					penetration = penWest;
					nx = -1.0; ny = 0.0; nz = 0.0;
				}
				if (penEast < penetration) {
					penetration = penEast;
					nx = 1.0; ny = 0.0; nz = 0.0;
				}
				if (penNorth < penetration) {
					penetration = penNorth;
					nx = 0.0; ny = 0.0; nz = -1.0;
				}
				if (penSouth < penetration) {
					penetration = penSouth;
					nx = 0.0; ny = 0.0; nz = 1.0;
				}
				if (penBottom < penetration) {
					penetration = penBottom;
					nx = 0.0; ny = -1.0; nz = 0.0;
				}
			} else {
				continue;
			}

			if (penetration > 0.0) {
				double vx = p.x - p.prevX;
				double vy = p.y - p.prevY;
				double vz = p.z - p.prevZ;

				p.x += nx * penetration;
				p.y += ny * penetration;
				p.z += nz * penetration;

				if (ny > 0.5) {
					p.onGround = true;
				}

				double vn = vx * nx + vy * ny + vz * nz;
				double vtX = vx - vn * nx;
				double vtY = vy - vn * ny;
				double vtZ = vz - vn * nz;

				if (spawnDust && world != null && ny > 0.5) {
					if (vn < -0.04) {
						float intensity = (float) Math.min(3.0, (-vn) * 18.0);
						world.spawnGroundDust(p.x, p.y - r, p.z, vx, vy, vz, intensity);
					} else {
						double speedVtSq = vtX * vtX + vtZ * vtZ;
						if (speedVtSq > 0.02 * 0.02 && Math.random() < 0.25) {
							world.spawnGroundDust(p.x, p.y - r, p.z, vtX, 0.0, vtZ, 0.5f);
						}
					}
				}

				double slipScale = Math.max(0.0, 1.0 - groundFriction * 0.45);
				double newVn = vn < -0.015 ? -vn * bounciness : 0.0;
				double newVx = vtX * slipScale + nx * newVn;
				double newVy = vtY * slipScale + ny * newVn;
				double newVz = vtZ * slipScale + nz * newVn;

				p.prevX = p.x - newVx;
				p.prevY = p.y - newVy;
				p.prevZ = p.z - newVz;
			}
		}
	}

	/**
	 * Computes the interpolated 6-limb {@link RagdollPose} for rendering at {@code partialTick}.
	 */
	public RagdollPose computePose(float partialTick) {
		double[][] pos = new double[PARTICLE_COUNT][3];
		for (int i = 0; i < PARTICLE_COUNT; i++) {
			RagdollParticle p = this.particles[i];
			pos[i][0] = p.lerpX(partialTick);
			pos[i][1] = p.lerpY(partialTick);
			pos[i][2] = p.lerpZ(partialTick);
		}

		double rootX = pos[PELVIS][0];
		double rootY = pos[PELVIS][1];
		double rootZ = pos[PELVIS][2];

		double[] torsoEy = {
			pos[PELVIS][0] - pos[NECK][0],
			pos[PELVIS][1] - pos[NECK][1],
			pos[PELVIS][2] - pos[NECK][2]
		};
		RagdollTransform.normalize(torsoEy);

		double sx = pos[LEFT_SHOULDER][0] - pos[RIGHT_SHOULDER][0];
		double sy = pos[LEFT_SHOULDER][1] - pos[RIGHT_SHOULDER][1];
		double sz = pos[LEFT_SHOULDER][2] - pos[RIGHT_SHOULDER][2];
		double sDotY = sx * torsoEy[0] + sy * torsoEy[1] + sz * torsoEy[2];
		double[] torsoEx = {
			sx - sDotY * torsoEy[0],
			sy - sDotY * torsoEy[1],
			sz - sDotY * torsoEy[2]
		};
		RagdollTransform.normalize(torsoEx);

		double[] torsoEz = new double[3];
		RagdollTransform.cross(torsoEx, torsoEy, torsoEz);
		RagdollTransform.normalize(torsoEz);

		RagdollPose.LimbTransform bodyTransform = RagdollTransform.toLimbTransform(
			pos[NECK][0] - rootX,
			pos[NECK][1] - rootY,
			pos[NECK][2] - rootZ,
			this.renderScale,
			torsoEx, torsoEy, torsoEz
		);

		double[] headEy = {
			pos[NECK][0] - pos[HEAD_TOP][0],
			pos[NECK][1] - pos[HEAD_TOP][1],
			pos[NECK][2] - pos[HEAD_TOP][2]
		};
		RagdollTransform.normalize(headEy);
		double[] headEx = new double[3];
		double[] headEz = new double[3];
		RagdollTransform.transportFrame(torsoEx, torsoEy, torsoEz, headEy, headEx, headEz);
		RagdollPose.LimbTransform headTransform = RagdollTransform.toLimbTransform(
			pos[NECK][0] - rootX,
			pos[NECK][1] - rootY,
			pos[NECK][2] - rootZ,
			this.renderScale,
			headEx, headEy, headEz
		);

		double u = this.pixelUnit;
		double[] leftShoulderSocket = {
			pos[NECK][0] + 5.0 * u * torsoEx[0] + 2.0 * u * torsoEy[0],
			pos[NECK][1] + 5.0 * u * torsoEx[1] + 2.0 * u * torsoEy[1],
			pos[NECK][2] + 5.0 * u * torsoEx[2] + 2.0 * u * torsoEy[2]
		};
		double[] rightShoulderSocket = {
			pos[NECK][0] - 5.0 * u * torsoEx[0] + 2.0 * u * torsoEy[0],
			pos[NECK][1] - 5.0 * u * torsoEx[1] + 2.0 * u * torsoEy[1],
			pos[NECK][2] - 5.0 * u * torsoEx[2] + 2.0 * u * torsoEy[2]
		};
		double[] leftHipSocket = {
			pos[NECK][0] + 1.9 * u * torsoEx[0] + 12.0 * u * torsoEy[0],
			pos[NECK][1] + 1.9 * u * torsoEx[1] + 12.0 * u * torsoEy[1],
			pos[NECK][2] + 1.9 * u * torsoEx[2] + 12.0 * u * torsoEy[2]
		};
		double[] rightHipSocket = {
			pos[NECK][0] - 1.9 * u * torsoEx[0] + 12.0 * u * torsoEy[0],
			pos[NECK][1] - 1.9 * u * torsoEx[1] + 12.0 * u * torsoEy[1],
			pos[NECK][2] - 1.9 * u * torsoEx[2] + 12.0 * u * torsoEy[2]
		};

		RagdollPose.LimbTransform leftArmTransform = computeLimbTransform(
			leftShoulderSocket, pos[LEFT_HAND], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
		);
		RagdollPose.LimbTransform rightArmTransform = computeLimbTransform(
			rightShoulderSocket, pos[RIGHT_HAND], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
		);
		RagdollPose.LimbTransform leftLegTransform = computeLimbTransform(
			leftHipSocket, pos[LEFT_FOOT], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
		);
		RagdollPose.LimbTransform rightLegTransform = computeLimbTransform(
			rightHipSocket, pos[RIGHT_FOOT], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
		);

		return new RagdollPose(
			rootX, rootY, rootZ,
			headTransform,
			bodyTransform,
			leftArmTransform,
			rightArmTransform,
			leftLegTransform,
			rightLegTransform
		);
	}

	private RagdollPose.LimbTransform computeLimbTransform(
		double[] pivot,
		double[] tip,
		double rootX,
		double rootY,
		double rootZ,
		double[] torsoEx,
		double[] torsoEy,
		double[] torsoEz
	) {
		double[] limbEy = {
			tip[0] - pivot[0],
			tip[1] - pivot[1],
			tip[2] - pivot[2]
		};
		RagdollTransform.normalize(limbEy);
		double[] limbEx = new double[3];
		double[] limbEz = new double[3];
		RagdollTransform.transportFrame(torsoEx, torsoEy, torsoEz, limbEy, limbEx, limbEz);
		return RagdollTransform.toLimbTransform(
			pivot[0] - rootX,
			pivot[1] - rootY,
			pivot[2] - rootZ,
			this.renderScale,
			limbEx, limbEy, limbEz
		);
	}

	public RagdollParticle getParticle(int index) {
		return this.particles[index];
	}

	public boolean isSettled() {
		return this.settled;
	}

	public double getRootX() {
		return this.particles[PELVIS].x;
	}

	public double getRootY() {
		return this.particles[PELVIS].y;
	}

	public double getRootZ() {
		return this.particles[PELVIS].z;
	}
}
