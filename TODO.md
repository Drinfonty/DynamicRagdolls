# TODO: Ragdolls Mod

## Version 1.0.0
### Phase 1: Project Setup & Multi-Platform Toolchain
- [x] Create project documentation (`README.md`, `TODO.md`, `SPEC.md`, `AGENTS.md`, `GEMINI.md`)
- [x] Configure multi-loader Gradle build (`:common`, `:fabric`, `:neoforge`) for Minecraft 26.2 (Java 25)
- [x] Set up ModMenu integration on Fabric and built-in Config Screen factory on NeoForge
- [x] Create 256x256 pixel-art mod icon (`assets/ragdolls/icon.png`)

### Phase 2: 6-Limb Articulated Ragdoll Physics Engine
- [x] Implement 12-node Verlet ragdoll skeleton (`RagdollSkeleton`, `RagdollParticle`, `DistanceConstraint`)
- [x] Implement tetrahedral 3D torso truss with chirality preservation (`NECK`, `PELVIS`, `LEFT_SHOULDER`, `RIGHT_SHOULDER`, `CHEST_FRONT`)
- [x] Implement anatomical joint angle cone constraints (`JointAngleConstraint`) for neck, shoulders, and hips
- [x] Implement shortest-arc quaternion parallel transport for twist-free limb orientations
- [x] Add JUnit 5 unit tests verifying skeleton constraints, joint limits, collisions, and Euler matrix reconstruction

### Phase 3: World Collision, Knockback & Fluid Physics
- [x] Implement `RagdollWorld` collision abstraction and `ClientRagdollWorld` wrapping `ClientLevel` block `VoxelShape`s
- [x] Add sphere-vs-AABB collision resolution with configurable bounciness and ground friction
- [x] Add directional death knockback impulse scaled by killing weapon and damage source
- [x] Add underwater buoyancy and viscous drag when ragdoll limbs are submerged
- [x] Implement settling/sleep optimization for resting ragdolls

### Phase 4: Rendering Pipeline & In-Game Verification
- [x] Implement `HumanoidRenderState` / `AvatarRenderState` snapshotting and `RagdollStateAttachment`
- [x] Implement `LivingEntityMixin`, `EntityRenderDispatcherMixin`, `LevelRendererMixin`, `LivingEntityRendererMixin`, `HumanoidModelMixin`, and `ModelFeatureRendererMixin`
- [x] Implement `RagdollsConfig` (`config/ragdolls.json`) and `RagdollsConfigScreen`
- [x] Implement `InGameSmokeTest` automated test runner to verify live combat ragdoll spawning, physics settling, and rendering

---

## Future Features
- [ ] **Local First-Person Death Camera**: Optional camera attachment to the ragdoll head when the local player dies.
- [ ] **Quadruped & Custom Mob Skeletons**: Extend articulated ragdoll skeletons to four-legged animals, spiders, and iron golems.
