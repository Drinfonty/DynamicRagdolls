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
		// - Head points UP (-eY), allowed cone ~50 deg
		this.jointConstraints.add(new JointAngleConstraint(NECK, HEAD_TOP, headLength, 0.0, -1.0, -0.08, Math.toRadians(50.0)));
		// - Left arm points Down-Left-SlightlyForward (+0.25 eX, +0.92 eY, -0.25 eZ), allowed cone ~105 deg
		this.jointConstraints.add(new JointAngleConstraint(LEFT_SHOULDER, LEFT_HAND, leftArmLength, 0.25, 0.92, -0.25, Math.toRadians(105.0)));
		// - Right arm points Down-Right-SlightlyForward (-0.25 eX, +0.92 eY, -0.25 eZ), allowed cone ~105 deg
		this.jointConstraints.add(new JointAngleConstraint(RIGHT_SHOULDER, RIGHT_HAND, rightArmLength, -0.25, 0.92, -0.25, Math.toRadians(105.0)));
		// - Left leg points Down-SlightlyLeft-Forward (+0.08 eX, +0.94 eY, -0.32 eZ), allowed cone ~75 deg
		this.jointConstraints.add(new JointAngleConstraint(LEFT_HIP, LEFT_FOOT, leftLegLength, 0.08, 0.94, -0.32, Math.toRadians(75.0)));
		// - Right leg points Down-SlightlyRight-Forward (-0.08 eX, +0.94 eY, -0.32 eZ), allowed cone ~75 deg
		this.jointConstraints.add(new JointAngleConstraint(RIGHT_HIP, RIGHT_FOOT, rightLegLength, -0.08, 0.94, -0.32, Math.toRadians(75.0)));

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
	 * Advances the ragdoll physics simulation by one client tick (20 Hz).
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

				enforceTorsoChirality(torsoEx, torsoEy, torsoEz);

				for (JointAngleConstraint jc : this.jointConstraints) {
					jc.solve(this.particles, torsoEx, torsoEy, torsoEz, jointConeScale);
				}

				solveMinSeparation(LEFT_FOOT, RIGHT_FOOT, 3.2 * this.pixelUnit);
				solveMinSeparation(LEFT_HAND, RIGHT_HAND, 2.5 * this.pixelUnit);

				for (RagdollParticle p : this.particles) {
					resolveParticleCollision(p, worldBoxes, bounciness, groundFriction);
				}
			}
		}

		for (DistanceConstraint dc : this.distanceConstraints) {
			dc.solve(this.particles);
		}
		enforceTorsoChirality(torsoEx, torsoEy, torsoEz);
		for (RagdollParticle p : this.particles) {
			resolveParticleCollision(p, worldBoxes, bounciness, groundFriction);
		}

		double maxSpeedSq = 0.0;
		int groundedCount = 0;
		for (RagdollParticle p : this.particles) {
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

	private void solveMinSeparation(int idxA, int idxB, double minDist) {
		RagdollParticle a = this.particles[idxA];
		RagdollParticle b = this.particles[idxB];
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		double distSq = dx * dx + dy * dy + dz * dz;
		if (distSq < minDist * minDist && distSq > 1.0e-10) {
			double dist = Math.sqrt(distSq);
			double push = (minDist - dist) * 0.5 / dist;
			a.x -= dx * push;
			a.y -= dy * push;
			a.z -= dz * push;
			b.x += dx * push;
			b.y += dy * push;
			b.z += dz * push;
		}
	}

	private void enforceTorsoChirality(double[] eX, double[] eY, double[] eZ) {
		RagdollParticle neck = this.particles[NECK];
		RagdollParticle pelvis = this.particles[PELVIS];
		RagdollParticle leftShoulder = this.particles[LEFT_SHOULDER];
		RagdollParticle rightShoulder = this.particles[RIGHT_SHOULDER];
		RagdollParticle chestFront = this.particles[CHEST_FRONT];

		eY[0] = pelvis.x - neck.x;
		eY[1] = pelvis.y - neck.y;
		eY[2] = pelvis.z - neck.z;
		RagdollTransform.normalize(eY);

		double sx = leftShoulder.x - rightShoulder.x;
		double sy = leftShoulder.y - rightShoulder.y;
		double sz = leftShoulder.z - rightShoulder.z;
		double dot = sx * eY[0] + sy * eY[1] + sz * eY[2];
		eX[0] = sx - dot * eY[0];
		eX[1] = sy - dot * eY[1];
		eX[2] = sz - dot * eY[2];
		RagdollTransform.normalize(eX);

		RagdollTransform.cross(eX, eY, eZ);
		RagdollTransform.normalize(eZ);

		double midX = 0.5 * (neck.x + pelvis.x);
		double midY = 0.5 * (neck.y + pelvis.y);
		double midZ = 0.5 * (neck.z + pelvis.z);

		double cx = chestFront.x - midX;
		double cy = chestFront.y - midY;
		double cz = chestFront.z - midZ;
		double targetDepth = 3.5 * this.pixelUnit;
		double signedBackDist = cx * eZ[0] + cy * eZ[1] + cz * eZ[2];
		if (signedBackDist > -targetDepth * 0.5) {
			double correction = (-targetDepth - signedBackDist);
			chestFront.x += eZ[0] * correction;
			chestFront.y += eZ[1] * correction;
			chestFront.z += eZ[2] * correction;
		}
	}

	private static void resolveParticleCollision(
		RagdollParticle p,
		List<RagdollWorld.Box> boxes,
		float bounciness,
		float groundFriction
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

		RagdollPose.LimbTransform leftArmTransform = computeLimbTransform(
			pos[LEFT_SHOULDER], pos[LEFT_HAND], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
		);
		RagdollPose.LimbTransform rightArmTransform = computeLimbTransform(
			pos[RIGHT_SHOULDER], pos[RIGHT_HAND], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
		);
		RagdollPose.LimbTransform leftLegTransform = computeLimbTransform(
			pos[LEFT_HIP], pos[LEFT_FOOT], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
		);
		RagdollPose.LimbTransform rightLegTransform = computeLimbTransform(
			pos[RIGHT_HIP], pos[RIGHT_FOOT], rootX, rootY, rootZ, torsoEx, torsoEy, torsoEz
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
