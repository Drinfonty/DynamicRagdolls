package com.drinfonty.ragdolls.physics;

/**
 * Pure math utilities for mapping 3D world-space ragdoll limb frames to Minecraft ModelPart
 * coordinates and ZYX Euler angles.
 */
public final class RagdollTransform {
	public static final double MODEL_ORIGIN_Y_PIXELS = 24.016; // 1.501 * 16.0

	private RagdollTransform() {
	}

	/**
	 * Converts a world-space joint pivot offset (relative to rootX, rootY, rootZ) and a
	 * right-handed orthonormal world basis [eX, eY, eZ] (Left, Down, Back) into a
	 * {@link RagdollPose.LimbTransform} for {@code ModelPart}.
	 *
	 * @param dxWorld     World X offset from ragdoll root
	 * @param dyWorld     World Y offset from ragdoll root
	 * @param dzWorld     World Z offset from ragdoll root
	 * @param renderScale Total entity render scale (e.g. 0.9375 for players, 1.0 for humanoids)
	 * @param eX          Unit world vector for +X_model (Left)
	 * @param eY          Unit world vector for +Y_model (Down)
	 * @param eZ          Unit world vector for +Z_model (Back = eX x eY)
	 */
	public static RagdollPose.LimbTransform toLimbTransform(
		double dxWorld,
		double dyWorld,
		double dzWorld,
		double renderScale,
		double[] eX,
		double[] eY,
		double[] eZ
	) {
		double invScale = renderScale > 1.0e-6 ? 16.0 / renderScale : 16.0;
		float px = (float) (-dxWorld * invScale);
		float py = (float) (MODEL_ORIGIN_Y_PIXELS - dyWorld * invScale);
		float pz = (float) (dzWorld * invScale);

		// R_model = F * [eX, eY, eZ], where F = diag(-1, -1, 1) from LivingEntityRenderer's scale(-1, -1, 1)
		double r00 = -eX[0];
		double r01 = -eY[0];
		double r02 = -eZ[0];
		double r10 = -eX[1];
		double r11 = -eY[1];
		double r12 = -eZ[1];
		double r20 = eX[2];
		double r21 = eY[2];
		double r22 = eZ[2];

		float[] euler = matrixToEulerZYX(r00, r01, r02, r10, r11, r12, r20, r21, r22);
		return new RagdollPose.LimbTransform(px, py, pz, euler[0], euler[1], euler[2]);
	}

	/**
	 * Extracts (xRot, yRot, zRot) in radians from a 3x3 rotation matrix R = Rz(zRot) * Ry(yRot) * Rx(xRot),
	 * matching {@code new Quaternionf().rotationZYX(zRot, yRot, xRot)}.
	 */
	public static float[] matrixToEulerZYX(
		double r00, double r01, double r02,
		double r10, double r11, double r12,
		double r20, double r21, double r22
	) {
		double clampedSinY = Math.max(-1.0, Math.min(1.0, -r20));
		double yRot = Math.asin(clampedSinY);
		double cosY = Math.cos(yRot);

		double xRot;
		double zRot;
		if (Math.abs(cosY) > 1.0e-5) {
			xRot = Math.atan2(r21, r22);
			zRot = Math.atan2(r10, r00);
		} else {
			xRot = 0.0;
			zRot = Math.atan2(-r01, r11);
		}
		return new float[]{(float) xRot, (float) yRot, (float) zRot};
	}

	/**
	 * Reconstructs the 3x3 rotation matrix R = Rz(zRot) * Ry(yRot) * Rx(xRot) for verification.
	 */
	public static double[] eulerZYXToMatrix(double xRot, double yRot, double zRot) {
		double cx = Math.cos(xRot), sx = Math.sin(xRot);
		double cy = Math.cos(yRot), sy = Math.sin(yRot);
		double cz = Math.cos(zRot), sz = Math.sin(zRot);

		return new double[]{
			cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
			sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
			-sy,     cy * sx,                cy * cx
		};
	}

	/**
	 * Transports a parent orthonormal frame (torsoEx, torsoEy, torsoEz) to a limb whose primary
	 * axis (+Y_model) is limbEy using the shortest-arc rotation from torsoEy to limbEy.
	 * Guarantees twist-free, right-handed orthonormal limb axes [outEx, limbEy, outEz].
	 */
	public static void transportFrame(
		double[] torsoEx,
		double[] torsoEy,
		double[] torsoEz,
		double[] limbEy,
		double[] outEx,
		double[] outEz
	) {
		double dot = torsoEy[0] * limbEy[0] + torsoEy[1] * limbEy[1] + torsoEy[2] * limbEy[2];
		if (dot > 0.99999) {
			System.arraycopy(torsoEx, 0, outEx, 0, 3);
			System.arraycopy(torsoEz, 0, outEz, 0, 3);
			return;
		}
		if (dot < -0.99999) {
			// 180-degree flip around torsoEx (pitch flip)
			System.arraycopy(torsoEx, 0, outEx, 0, 3);
			outEz[0] = -torsoEz[0];
			outEz[1] = -torsoEz[1];
			outEz[2] = -torsoEz[2];
			return;
		}

		// Rodrigues / shortest-arc rotation mapping u = torsoEy -> v = limbEy:
		// R(x) = x + (u x v) x x + ((u x v) x ((u x v) x x)) / (1 + u.v)
		// For x = torsoEx (which is orthogonal to u = torsoEy):
		// u x torsoEx = torsoEz, and (u x v) . torsoEx = (torsoEx x u) . v = -torsoEz . v
		// More directly: rotate torsoEx by shortest arc from u to v:
		double k = 1.0 / (1.0 + dot);
		double vDotEx = limbEy[0] * torsoEx[0] + limbEy[1] * torsoEx[1] + limbEy[2] * torsoEx[2];
		outEx[0] = torsoEx[0] - vDotEx * (torsoEy[0] + limbEy[0]) * k;
		outEx[1] = torsoEx[1] - vDotEx * (torsoEy[1] + limbEy[1]) * k;
		outEx[2] = torsoEx[2] - vDotEx * (torsoEy[2] + limbEy[2]) * k;
		normalize(outEx);

		// Right-handed cross product: outEz = outEx x limbEy
		cross(outEx, limbEy, outEz);
		normalize(outEz);
		// Re-orthogonalize outEx = limbEy x outEz
		cross(limbEy, outEz, outEx);
		normalize(outEx);
	}

	public static void normalize(double[] v) {
		double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		if (len > 1.0e-9) {
			v[0] /= len;
			v[1] /= len;
			v[2] /= len;
		}
	}

	public static void cross(double[] a, double[] b, double[] out) {
		double cx = a[1] * b[2] - a[2] * b[1];
		double cy = a[2] * b[0] - a[0] * b[2];
		double cz = a[0] * b[1] - a[1] * b[0];
		out[0] = cx;
		out[1] = cy;
		out[2] = cz;
	}
}
