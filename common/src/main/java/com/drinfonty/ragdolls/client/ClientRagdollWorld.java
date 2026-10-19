package com.drinfonty.ragdolls.client;

import com.drinfonty.ragdolls.physics.RagdollWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * ClientLevel-backed implementation of {@link RagdollWorld} for block VoxelShape collision
 * and water buoyancy queries.
 */
public final class ClientRagdollWorld implements RagdollWorld {
	private final ClientLevel level;

	public ClientRagdollWorld(ClientLevel level) {
		this.level = level;
	}

	@Override
	public List<Box> getCollisionBoxes(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		List<Box> boxes = new ArrayList<>();
		int x0 = Mth.floor(minX);
		int y0 = Mth.floor(minY);
		int z0 = Mth.floor(minZ);
		int x1 = Mth.floor(maxX);
		int y1 = Mth.floor(maxY);
		int z1 = Mth.floor(maxZ);

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int bx = x0; bx <= x1; bx++) {
			for (int by = y0; by <= y1; by++) {
				for (int bz = z0; bz <= z1; bz++) {
					pos.set(bx, by, bz);
					if (!this.level.hasChunkAt(pos)) {
						continue;
					}
					BlockState state = this.level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					VoxelShape shape = state.getCollisionShape(this.level, pos);
					if (shape.isEmpty()) {
						continue;
					}
					for (AABB aabb : shape.toAabbs()) {
						double bMinX = bx + aabb.minX;
						double bMinY = by + aabb.minY;
						double bMinZ = bz + aabb.minZ;
						double bMaxX = bx + aabb.maxX;
						double bMaxY = by + aabb.maxY;
						double bMaxZ = bz + aabb.maxZ;
						if (bMaxX > minX && bMinX < maxX
							&& bMaxY > minY && bMinY < maxY
							&& bMaxZ > minZ && bMinZ < maxZ) {
							boxes.add(new Box(bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ));
						}
					}
				}
			}
		}
		return boxes;
	}

	@Override
	public boolean isWater(double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		if (!this.level.hasChunkAt(pos)) {
			return false;
		}
		FluidState fluid = this.level.getFluidState(pos);
		if (!fluid.is(FluidTags.WATER)) {
			return false;
		}
		double fluidTop = pos.getY() + fluid.getHeight(this.level, pos);
		return y <= fluidTop;
	}

	@Override
	public void spawnGroundDust(double x, double y, double z, double vx, double vy, double vz, float intensity) {
		BlockPos groundPos = BlockPos.containing(x, y - 0.2, z);
		if (!this.level.hasChunkAt(groundPos)) {
			return;
		}
		BlockState state = this.level.getBlockState(groundPos);
		if (state.isAir()) {
			groundPos = groundPos.below();
			if (!this.level.hasChunkAt(groundPos)) {
				return;
			}
			state = this.level.getBlockState(groundPos);
			if (state.isAir()) {
				return;
			}
		}

		net.minecraft.core.particles.BlockParticleOption blockParticle =
			new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state);

		if (intensity >= 1.0f) {
			// Ground impact/bounce: burst of debris
			int count = Math.min(5, Math.max(2, (int) intensity));
			for (int i = 0; i < count; i++) {
				double spreadAngle = (Math.PI * 2.0 * i) / count + (Math.random() - 0.5) * 0.4;
				double spreadSpeed = 0.05 + Math.random() * 0.08 * intensity;
				double px = x + (Math.random() - 0.5) * 0.25;
				double py = y + 0.02;
				double pz = z + (Math.random() - 0.5) * 0.25;
				double pvx = Math.cos(spreadAngle) * spreadSpeed + vx * 0.2;
				double pvy = 0.08 + Math.random() * 0.12 * Math.min(2.0, intensity);
				double pvz = Math.sin(spreadAngle) * spreadSpeed + vz * 0.2;
				this.level.addParticle(blockParticle, px, py, pz, pvx, pvy, pvz);
			}

			// Hard landing: add subtle dust plume puff
			if (intensity >= 2.0f) {
				this.level.addParticle(
					net.minecraft.core.particles.ParticleTypes.DUST_PLUME,
					x, y + 0.05, z,
					0.0, 0.03, 0.0
				);
			}
		} else {
			// Sliding/skidding across the block
			double px = x + (Math.random() - 0.5) * 0.2;
			double py = y + 0.02;
			double pz = z + (Math.random() - 0.5) * 0.2;
			double pvx = -vx * 0.35 + (Math.random() - 0.5) * 0.04;
			double pvy = 0.03 + Math.random() * 0.03;
			double pvz = -vz * 0.35 + (Math.random() - 0.5) * 0.04;
			this.level.addParticle(blockParticle, px, py, pz, pvx, pvy, pvz);
		}
	}
}
