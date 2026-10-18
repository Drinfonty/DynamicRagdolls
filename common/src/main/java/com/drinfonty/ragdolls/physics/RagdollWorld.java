package com.drinfonty.ragdolls.physics;

import java.util.List;

/**
 * Loader- and client-neutral world collision & fluid query interface for ragdoll physics.
 */
public interface RagdollWorld {
	record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		public boolean intersects(double bMinX, double bMinY, double bMinZ, double bMaxX, double bMaxY, double bMaxZ) {
			return this.maxX > bMinX && this.minX < bMaxX
				&& this.maxY > bMinY && this.minY < bMaxY
				&& this.maxZ > bMinZ && this.minZ < bMaxZ;
		}
	}

	/**
	 * Returns world-space solid collision boxes overlapping the given query AABB.
	 */
	List<Box> getCollisionBoxes(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

	/**
	 * Returns true if the given world-space point is submerged in water or fluid.
	 */
	boolean isWater(double x, double y, double z);
}
