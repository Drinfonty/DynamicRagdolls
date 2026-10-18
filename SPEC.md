# Specification: Ragdolls Mod

Ragdolls is a client-only Minecraft mod (supporting both **Fabric** and **NeoForge**) that replaces the static sideways death animation of players (and optionally humanoid mobs) with a full 6-limb articulated physics ragdoll simulation.

## 1. Environment & Support
*   **Modern Branch (`mc-26.2`)**:
    *   **Minecraft Version**: 26.2 (Java 25)
    *   **Modding Toolchain**: Fabric Loader (>=0.19.3), Fabric Loom (1.17-SNAPSHOT), NeoForge (26.2.0.81), NeoForge ModDev (2.0.143)
*   **Additional Version Branches (Planned / Supported Layout)**:
    *   `mc-26.1`: Minecraft 26.1.2 (Java 25)
    *   `mc-1.21.11`: Minecraft 1.21.11 (Java 21)
    *   `mc-1.21.10`: Minecraft 1.21.9 - 1.21.10 (Java 21)
    *   `mc-1.21.8`: Minecraft 1.21.5 - 1.21.8 (Java 21)
    *   `mc-1.21.4`: Minecraft 1.21.2 - 1.21.4 (Java 21)
    *   `mc-1.21.1`: Minecraft 1.21 - 1.21.1 (Java 21)
*   **Side**: Client-Side Only (Must run on vanilla/unmodded servers without registry mismatch or server-side dependencies).

---

## 2. Features

### 2.1 6-Limb Articulated Ragdoll Physics (`RagdollSkeleton`)
When a player (or enabled humanoid entity) is killed:
1.  **Death Detection**: Detects death client-side via `LivingEntity.handleEntityEvent((byte) 3)` and `LivingEntity.tick` (`isDeadOrDying()` / `deathTime > 0`).
2.  **Articulated 12-Node Verlet Skeleton**:
    *   **Torso Truss (7 nodes)**: `NECK`, `PELVIS`, `LEFT_SHOULDER`, `RIGHT_SHOULDER`, `LEFT_HIP`, `RIGHT_HIP`, and `CHEST_FRONT` connected by rigid distance constraints and shape-matched to a true 3D orthonormal frame $(\mathbf{e}_x, \mathbf{e}_y, \mathbf{e}_z)$ (`enforceTorsoRigidity`) so the torso never buckles or collapses its shoulder/hip sockets.
    *   **Distal Limb Nodes (5 nodes)**: `HEAD_TOP`, `LEFT_HAND`, `RIGHT_HAND`, `LEFT_FOOT`, and `RIGHT_FOOT` connected to their respective neck, shoulder, and hip pivots.
3.  **Anatomical Joint Angle Constraints & Self-Collision (`JointAngleConstraint`, `RagdollSkeleton`)**:
    *   Enforces directional cone limits in torso-local space for the neck, left/right shoulders, and left/right hips.
    *   **Torso OBB vs. Limb Self-Collision (`solveTorsoLimbCollisions`)**: Prevents arms, legs, and head from clipping into the torso volume (`[-4u, +4u] x [0, 12u] x [-2u, +2u]`) or inverting above the shoulder into the head/neck.
    *   **3D Capsule-vs-Capsule Limb Collisions (`solveLimbCapsuleCollisions`)**: Resolves closest-point segment-to-segment separation between arms, legs, and the head so limbs collide against each other rather than overlapping.
    *   Transports limb frames from the torso frame via shortest-arc quaternion rotation (parallel transport) so limbs never twist around their long axis like a drill.

### 2.2 Directional Death Impulse & Knockback
*   **Momentum Transfer**: Inherits the dying entity's velocity (`getDeltaMovement()`), yaw (`yBodyRot`), pitch (`xRot`), and walking limb swing pose at the exact moment of death.
*   **Directional Hit Force**: Queries `getLastDamageSource()` (with a nearby local-player attack fallback on vanilla servers) to apply a directional physical impulse to the upper torso, head, and limbs away from the attacker or explosion center.
*   **Weapon Scaling**: Scales knockback impulse by weapon category (Fist/Blunt `0.6x`, Sword `1.1x`, Axe `1.5x`, Mace/Explosion `2.2x`, Projectile `0.9x`) multiplied by the user's configured `knockbackMultiplier`.

### 2.3 World Block Collision & Fluid Physics
*   **VoxelShape Collision**: Queries surrounding block collision shapes (`BlockState.getCollisionShape`) via the loader-neutral `RagdollWorld` interface, resolving sphere-vs-AABB collisions for all physical skeleton nodes against full blocks, slabs, stairs, snow layers, anvils, and walls.
*   **Restitution & Friction**: Applies configurable bounce restitution (`bounciness`) along surface normals and tangential friction (`groundFriction`) when sliding or tumbling across blocks.
*   **Underwater Buoyancy & Drag**: Submerged ragdoll nodes experience upward buoyancy and high viscous water drag (`waterBuoyancy`), drifting and sinking naturally in water.
*   **Settling / Sleep Optimization**: Ragdolls resting motionless on solid ground automatically enter a low-cost settled state and wake up if supporting blocks are broken or modified.

### 2.4 Entity Scope & Seamless Skin/Equipment Rendering
*   **Entity Scope**:
    *   **Players Only** (default): Spawns ragdolls for all players (`AbstractClientPlayer`, `LocalPlayer`, `RemotePlayer`, and `ClientMannequin` / `Avatar`).
    *   **All Humanoids** (configurable): Also enables ragdolls for any entity whose renderer uses a `HumanoidRenderState` (`HumanoidModel` and `IllagerModel`: Zombies, Husks, Drowned, Parched, Skeletons, Strays, Wither Skeletons, Bogged, Piglins, Pillagers, Vindicators, Evokers, Illusioners, etc.).
*   **Persistent RenderState Snapshot**:
    *   Snapshots the entity's `HumanoidRenderState` (`AvatarRenderState` for players) upon death and submits it directly through `EntityRenderDispatcher` in `LevelRenderer.submitEntities`.
    *   Preserves the player's skin texture, slim/wide arm model, outer skin layers (hat, jacket, sleeves, pants), equipped armor (`HumanoidArmorLayer`), and held items (`ItemInHandLayer`).
    *   Continues rendering the ragdoll corpse in the world for the full configured `ragdollLifetimeSeconds` even after the vanilla entity despawns (at `deathTime == 20`) or after a player respawns.
    *   Suppresses the vanilla entity's sideways 90° death tilt and optional death smoke poof (`status == 60`).

### 2.5 In-Game Configuration (ModMenu & NeoForge Config GUI)
*   Provides a clean two-column in-game configuration screen (`RagdollsConfigScreen`) to modify settings on the fly:
    *   **Ragdolls**: Toggle all ragdoll physics ON/OFF.
    *   **Target Entities**: Toggle between **Players Only** (default) and **All Humanoids**.
    *   **Joint Stiffness**: Cycle between **Loose**, **Natural** (default), and **Stiff**.
    *   **Fluid Buoyancy**: Toggle underwater buoyancy & drag ON/OFF.
    *   **Armor & Items**: Toggle rendering equipped armor and held items on ragdolls ON/OFF.
    *   **Death Smoke**: Toggle vanilla death smoke poof particles ON/OFF (default OFF).
    *   **Lifetime**: Slider to adjust ragdoll corpse lifetime (`1s` to `60s`, default `15s`).
    *   **Max Ragdolls**: Slider to adjust maximum active ragdolls (`4` to `100`, default `32`).
    *   **Knockback Force**: Slider to adjust death impact impulse multiplier (`0.2x` to `3.0x`, default `1.0x`).
    *   **Gravity**: Slider to adjust ragdoll gravity scale (`0.2x` to `2.0x`, default `1.0x`).
    *   **Bounciness**: Slider to adjust surface impact restitution (`0.0` to `0.8`, default `0.25`).
    *   **Ground Friction**: Slider to adjust surface sliding friction (`0.1` to `1.0`, default `0.80`).
*   **Storage**: Saves to `config/ragdolls.json`.

---

## 3. Technical Design

### 3.1 Coordinate Space & Euler Reconstruction (`RagdollTransform`)
In `LivingEntityRenderer.submit`, when `setupRotations` is bypassed for a ragdoll `HumanoidRenderState`, the `PoseStack` applies `scale(-1, -1, 1)` ($F = \text{diag}(-1, -1, 1) \in SO(3)$) followed by model scale $s$ (`0.9375` for players, `1.0` for standard humanoids) and `translate(0, -1.501f, 0)`.
*   **Joint Pivot Mapping**: Each `ModelPart` pivot $(x, y, z)$ in model pixels maps from its world-space offset $\Delta \mathbf{p} = \mathbf{p}_{\text{pivot}} - \mathbf{p}_{\text{root}}$ via:
    $$x = -\frac{16}{s}\Delta X, \quad y = 24.016 - \frac{16}{s}\Delta Y, \quad z = +\frac{16}{s}\Delta Z$$
*   **3D Orientation Mapping**: From each limb's right-handed world basis $[\mathbf{e}_x, \mathbf{e}_y, \mathbf{e}_z] \in SO(3)$ (Left, Down, Back), the `ModelPart` rotation matrix $R_{\text{model}} = F [\mathbf{e}_x, \mathbf{e}_y, \mathbf{e}_z] \in SO(3)$ is decomposed into exact ZYX Euler angles `(xRot, yRot, zRot)` for `ModelPart.translateAndRotate`.

### 3.2 Mixins
*   `LivingEntityMixin`: Hooks `tick` and `handleEntityEvent` to spawn ragdolls on death and optionally suppress death smoke (`status == 60`).
*   `EntityRenderDispatcherMixin`: Suppresses duplicate vanilla rendering of an entity while its ragdoll is active (`shouldRender`).
*   `LevelRendererMixin`: Submits all active `RagdollInstance`s during `submitEntities` so ragdolls render at their physics coordinates and persist after entity removal.
*   `LivingEntityRendererMixin`: Bypasses vanilla `setupRotations` for ragdoll render states and applies the ragdoll pose to `this.model` before equipment layers run.
*   `HumanoidModelMixin` & `ModelFeatureRendererMixin`: Applies the 6-limb `RagdollPose` to `HumanoidModel` (and subclasses/armor models) during `setupAnim` and `prepareModel`.

### 3.3 High-Resolution Assets
*   **Mod Menu Icon**: High-resolution 256x256 pixel-art icon (`assets/ragdolls/icon.png`).
