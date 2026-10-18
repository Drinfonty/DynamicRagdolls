package com.drinfonty.ragdolls.physics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RagdollPhysicsTest {

	private static final RagdollWorld FLAT_FLOOR_WORLD = new RagdollWorld() {
		@Override
		public List<Box> getCollisionBoxes(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			if (minY <= 0.0 && maxY >= -1.0) {
				return List.of(new Box(-100.0, -1.0, -100.0, 100.0, 0.0, 100.0));
			}
			return List.of();
		}

		@Override
		public boolean isWater(double x, double y, double z) {
			return false;
		}
	};

	private static final RagdollWorld WATER_WORLD = new RagdollWorld() {
		@Override
		public List<Box> getCollisionBoxes(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			return List.of(new Box(-100.0, -10.0, -100.0, 100.0, -9.0, 100.0));
		}

		@Override
		public boolean isWater(double x, double y, double z) {
			return y <= 5.0;
		}
	};

	@Test
	void testEulerZYXRoundTripAcrossOrientations() {
		double[][] testAngles = {
			{0.0, 0.0, 0.0},
			{0.4, -0.3, 0.7},
			{-1.1, 0.8, -2.2},
			{Math.PI, 0.0, Math.PI},
			{0.0, Math.PI / 2.0, 0.5} // gimbal lock
		};

		for (double[] angles : testAngles) {
			double[] m = RagdollTransform.eulerZYXToMatrix(angles[0], angles[1], angles[2]);
			float[] extracted = RagdollTransform.matrixToEulerZYX(
				m[0], m[1], m[2],
				m[3], m[4], m[5],
				m[6], m[7], m[8]
			);
			double[] reconstructed = RagdollTransform.eulerZYXToMatrix(extracted[0], extracted[1], extracted[2]);
			for (int i = 0; i < 9; i++) {
				assertEquals(m[i], reconstructed[i], 1.0e-4, "Matrix element " + i + " mismatch");
			}
		}
	}

	@Test
	void testTransportFrameProducesOrthonormalRightHandedBasis() {
		double[] torsoEx = {1.0, 0.0, 0.0};
		double[] torsoEy = {0.0, -1.0, 0.0};
		double[] torsoEz = {0.0, 0.0, -1.0};

		double[] limbEy = {0.4, -0.7, 0.5};
		RagdollTransform.normalize(limbEy);

		double[] outEx = new double[3];
		double[] outEz = new double[3];
		RagdollTransform.transportFrame(torsoEx, torsoEy, torsoEz, limbEy, outEx, outEz);

		// Unit lengths
		assertEquals(1.0, Math.hypot(outEx[0], Math.hypot(outEx[1], outEx[2])), 1.0e-6);
		assertEquals(1.0, Math.hypot(outEz[0], Math.hypot(outEz[1], outEz[2])), 1.0e-6);

		// Mutual orthogonality
		double dotXY = outEx[0] * limbEy[0] + outEx[1] * limbEy[1] + outEx[2] * limbEy[2];
		double dotYZ = limbEy[0] * outEz[0] + limbEy[1] * outEz[1] + limbEy[2] * outEz[2];
		double dotXZ = outEx[0] * outEz[0] + outEx[1] * outEz[1] + outEx[2] * outEz[2];
		assertEquals(0.0, dotXY, 1.0e-6);
		assertEquals(0.0, dotYZ, 1.0e-6);
		assertEquals(0.0, dotXZ, 1.0e-6);

		// Right-handed determinant = +1
		double[] cross = new double[3];
		RagdollTransform.cross(outEx, limbEy, cross);
		double det = cross[0] * outEz[0] + cross[1] * outEz[1] + cross[2] * outEz[2];
		assertEquals(1.0, det, 1.0e-6);
	}

	@Test
	void testRagdollFallsTopplesAndSettlesOnFloor() {
		RagdollSkeleton skeleton = new RagdollSkeleton(
			0.0, 0.0, 0.0,
			0.0f, 0.0f,
			0.9375,
			0.0, 0.0, 0.0,
			0.0, 0.08, -0.22
		);

		double initialNeckY = skeleton.getParticle(RagdollSkeleton.NECK).y;
		assertTrue(initialNeckY > 1.2, "Initial neck should be standing height");

		for (int tick = 0; tick < 80; tick++) {
			skeleton.step(FLAT_FLOOR_WORLD, 1.0f, 0.25f, 0.80f, true, 1.0f, 6);
		}

		// After toppling onto the Y=0 floor, all physical particles must stay above floor (y >= radius - epsilon)
		for (int i = 0; i < RagdollSkeleton.PARTICLE_COUNT; i++) {
			if (i == RagdollSkeleton.CHEST_FRONT) {
				continue;
			}
			RagdollParticle p = skeleton.getParticle(i);
			assertTrue(p.y >= p.radius - 1.0e-3, "Particle " + i + " penetrated below Y=0 floor: " + p.y);
		}

		// Neck should have fallen down close to the floor (< 0.65m)
		double finalNeckY = skeleton.getParticle(RagdollSkeleton.NECK).y;
		assertTrue(finalNeckY < 0.65, "Expected neck to topple onto ground, got Y=" + finalNeckY);

		// Skeleton should eventually settle
		assertTrue(skeleton.isSettled(), "Expected ragdoll skeleton to settle on flat ground within 80 ticks");

		RagdollPose pose = skeleton.computePose(1.0f);
		assertNotNull(pose.head());
		assertNotNull(pose.body());
		assertNotNull(pose.leftArm());
		assertNotNull(pose.rightArm());
		assertNotNull(pose.leftLeg());
		assertNotNull(pose.rightLeg());
	}

	@Test
	void testLimbsNeverClipThroughTorsoDuringFallOrForcedPenetration() {
		double scale = 0.9375;
		double u = scale / 16.0;
		RagdollSkeleton skeleton = new RagdollSkeleton(
			0.0, 0.0, 0.0,
			35.0f, 0.0f,
			scale,
			0.0, 0.0, 0.0,
			-0.18, 0.06, 0.14
		);

		// Force hands and feet directly into the center of the torso to test ejection
		RagdollParticle neck = skeleton.getParticle(RagdollSkeleton.NECK);
		RagdollParticle pelvis = skeleton.getParticle(RagdollSkeleton.PELVIS);
		double midX = 0.5 * (neck.x + pelvis.x);
		double midY = 0.5 * (neck.y + pelvis.y);
		double midZ = 0.5 * (neck.z + pelvis.z);

		skeleton.getParticle(RagdollSkeleton.LEFT_HAND).x = midX;
		skeleton.getParticle(RagdollSkeleton.LEFT_HAND).y = midY;
		skeleton.getParticle(RagdollSkeleton.LEFT_HAND).z = midZ;
		skeleton.getParticle(RagdollSkeleton.RIGHT_HAND).x = midX;
		skeleton.getParticle(RagdollSkeleton.RIGHT_HAND).y = midY;
		skeleton.getParticle(RagdollSkeleton.RIGHT_HAND).z = midZ;
		skeleton.getParticle(RagdollSkeleton.LEFT_FOOT).x = midX;
		skeleton.getParticle(RagdollSkeleton.LEFT_FOOT).y = midY;
		skeleton.getParticle(RagdollSkeleton.LEFT_FOOT).z = midZ;

		for (int tick = 0; tick < 60; tick++) {
			skeleton.step(FLAT_FLOOR_WORLD, 1.0f, 0.25f, 0.80f, true, 1.0f, 6);

			// Reconstruct torso orthonormal basis (eX, eY, eZ)
			double[] eY = {
				pelvis.x - neck.x,
				pelvis.y - neck.y,
				pelvis.z - neck.z
			};
			RagdollTransform.normalize(eY);
			RagdollParticle ls = skeleton.getParticle(RagdollSkeleton.LEFT_SHOULDER);
			RagdollParticle rs = skeleton.getParticle(RagdollSkeleton.RIGHT_SHOULDER);
			double sx = ls.x - rs.x, sy = ls.y - rs.y, sz = ls.z - rs.z;
			double dot = sx * eY[0] + sy * eY[1] + sz * eY[2];
			double[] eX = {sx - dot * eY[0], sy - dot * eY[1], sz - dot * eY[2]};
			RagdollTransform.normalize(eX);

			// Verify Left Hand is strictly outside the torso lateral boundary (x_t >= 4.7u)
			RagdollParticle lh = skeleton.getParticle(RagdollSkeleton.LEFT_HAND);
			double lhx = (lh.x - neck.x) * eX[0] + (lh.y - neck.y) * eX[1] + (lh.z - neck.z) * eX[2];
			assertTrue(lhx >= 4.7 * u, "Left hand clipped into torso at tick " + tick + ": lhx/u=" + (lhx / u));

			// Verify Right Hand is strictly outside the torso lateral boundary (x_t <= -4.7u)
			RagdollParticle rh = skeleton.getParticle(RagdollSkeleton.RIGHT_HAND);
			double rhx = (rh.x - neck.x) * eX[0] + (rh.y - neck.y) * eX[1] + (rh.z - neck.z) * eX[2];
			assertTrue(rhx <= -4.7 * u, "Right hand clipped into torso at tick " + tick + ": rhx/u=" + (rhx / u));

			// Verify Left and Right Feet stay below the torso hip plane (y_t >= 15.0u)
			RagdollParticle lf = skeleton.getParticle(RagdollSkeleton.LEFT_FOOT);
			double lfy = (lf.x - neck.x) * eY[0] + (lf.y - neck.y) * eY[1] + (lf.z - neck.z) * eY[2];
			assertTrue(lfy >= 15.0 * u, "Left foot folded into torso at tick " + tick + ": lfy/u=" + (lfy / u));

			RagdollParticle rf = skeleton.getParticle(RagdollSkeleton.RIGHT_FOOT);
			double rfy = (rf.x - neck.x) * eY[0] + (rf.y - neck.y) * eY[1] + (rf.z - neck.z) * eY[2];
			assertTrue(rfy >= 15.0 * u, "Right foot folded into torso at tick " + tick + ": rfy/u=" + (rfy / u));
		}
	}

	@Test
	void testDirectionalKnockbackPushesRagdollAlongImpulse() {
		RagdollSkeleton skeleton = new RagdollSkeleton(
			0.0, 0.5, 0.0,
			0.0f, 0.0f,
			0.9375,
			0.0, 0.0, 0.0,
			0.35, 0.15, 0.0 // Strong +X impulse
		);

		for (int tick = 0; tick < 20; tick++) {
			skeleton.step(FLAT_FLOOR_WORLD, 1.0f, 0.25f, 0.80f, true, 1.0f, 6);
		}

		assertTrue(skeleton.getRootX() > 0.3, "Expected ragdoll pelvis to move in +X knockback direction, got " + skeleton.getRootX());
	}

	@Test
	void testWaterBuoyancySlowsDescentComparedToAir() {
		RagdollSkeleton airSkeleton = new RagdollSkeleton(
			0.0, 2.0, 0.0, 0.0f, 0.0f, 0.9375,
			0.0, 0.0, 0.0, 0.0, 0.0, 0.0
		);
		RagdollSkeleton waterSkeleton = new RagdollSkeleton(
			0.0, 2.0, 0.0, 0.0f, 0.0f, 0.9375,
			0.0, 0.0, 0.0, 0.0, 0.0, 0.0
		);

		for (int tick = 0; tick < 15; tick++) {
			airSkeleton.step(WATER_WORLD, 1.0f, 0.25f, 0.80f, false, 1.0f, 6);
			waterSkeleton.step(WATER_WORLD, 1.0f, 0.25f, 0.80f, true, 1.0f, 6);
		}

		assertTrue(
			waterSkeleton.getRootY() > airSkeleton.getRootY() + 0.5,
			"Expected water buoyancy to keep ragdoll higher than freefall in air"
		);
	}
}
