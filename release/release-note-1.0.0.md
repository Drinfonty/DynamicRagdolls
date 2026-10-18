# Ragdolls 1.0.0

Initial release of **Ragdolls** for Minecraft 26.2 (**Fabric** & **NeoForge**).

## Highlights
- **6-Limb Articulated Ragdoll Physics**: Replaces the vanilla sideways death flip with a 12-node Verlet physics ragdoll simulation featuring anatomical joint-cone limits for head, torso, arms, and legs.
- **Directional Death Knockback**: Transfers entity momentum and applies weapon-scaled directional knockback impulses from melee attacks, projectiles, and explosions.
- **World Block Collision & Fluid Buoyancy**: Resolves collisions against block `VoxelShape` geometry (slabs, stairs, walls, snow layers) with configurable bounce restitution and surface friction, plus underwater buoyancy and drag.
- **Full Skin & Equipment Rendering**: Preserves player skins (slim and wide arms), outer skin layers, equipped armor, and held items for the full ragdoll lifetime even after entity despawn or respawn.
- **In-Game Configuration**: Integrated with ModMenu on Fabric and the Mods Config screen on NeoForge (`config/ragdolls.json`).
