# Ragdolls

Ragdolls is a client-side Minecraft mod that replaces the static sideways death animation of players (and optionally humanoid mobs) with a full 6-limb articulated physics ragdoll simulation. Since it is entirely client-side, it works on vanilla servers.

It is built as a multi-platform project with a shared `:common` codebase supporting both **Fabric** and **NeoForge**.

---

## Mod Features
- **6-Limb Articulated Ragdoll Physics**: Head, torso, left/right arms, and left/right legs connected by anatomical joints with cone angle limits, gravity, surface friction, and bounce restitution.
- **Directional Death Knockback**: Ragdolls inherit the entity's velocity and react dynamically to the direction and weapon force of the killing blow or explosion.
- **Full Skin, Armor & Held Item Rendering**: Preserves player skins (wide and slim arms), outer skin layers, equipped armor, and held items, remaining in the world for a configurable duration even after the vanilla entity despawns or respawns.
- **World Block Collision & Water Buoyancy**: Collides accurately with full blocks, slabs, stairs, snow layers, and walls, and floats/drifts naturally underwater.
- **Multi-Platform Config GUI**: Fully integrated with in-game mod menus (via **ModMenu** on Fabric, and NeoForge's built-in **Mods List Config** button on NeoForge) to customize target entities (Players Only vs. All Humanoids), ragdoll lifetime, knockback force, gravity, bounciness, friction, and joint stiffness on the fly.

For feature specifications and roadmap details, see:
*   [SPEC.md](SPEC.md): Feature specifications and technical design.
*   [TODO.md](TODO.md): Task lists and progress tracking.

---

## Branch & Minecraft Version Mapping

The project maintains different branches to target different major Minecraft and loader versions:

| Branch Name | Built Against | Supported Minecraft | NeoForge | Java |
| :--- | :--- | :--- | :--- | :--- |
| **`mc-26.2`** | **`26.2`** | `26.2` | `26.2.0.81` | 25 |

"Supported Minecraft" is the range declared in `minecraft_dependency`, and is what
`modrinth_game_versions` publishes. Only the "Built Against" version is compiled and
launched during verification.

### Fabric mapping namespaces

| Branch Name | Loom | Loom Plugin | Fabric Production Namespace | Verify Fabric With |
| :--- | :--- | :--- | :--- | :--- |
| **`mc-26.2`** | 1.17 | `fabric-loom` | Mojang (`official`) — no remap step | `:fabric:runClient -PtestJar` |

Fabric does not publish intermediary mappings for Minecraft 26.x, so on `mc-26.2`
the `jar` output *is* the publishable artifact and the dev client runs in the
same namespace as production.

NeoForge runs Mojang mappings on every branch, so its jar is never remapped.

---

## Building the Mod

To compile and package the production `.jar` files for release:

- **Build Fabric Mod:**
  ```bash
  ./gradlew :fabric:build
  ```
- **Build NeoForge Mod:**
  ```bash
  ./gradlew :neoforge:build
  ```
- **Build All Subprojects:**
  ```bash
  ./gradlew build
  ```

Output binaries are placed under the `${project.rootDir}/release/` directory:
- Fabric: `release/ragdolls-<version>-<mc-suffix>-fabric.jar`
- NeoForge: `release/ragdolls-<version>-<mc-suffix>-neoforge.jar`

---

## Running and Testing

### 1. Unit Tests
Run the JUnit 5 physics and geometry test suite in `:common`:
```bash
./gradlew test
```

### 2. Standard Development Run
By default, running the standard client tasks loads Minecraft using loose class directories and resource folders directly from your workspace compilation output (unpackaged classpath):
- **Fabric Dev Client:**
  ```bash
  ./gradlew :fabric:runClient
  ```
- **NeoForge Dev Client:**
  ```bash
  ./gradlew :neoforge:runClient
  ```

### 3. Pre-Publish Verification

**Always run these before publishing.** A dev client is not enough to tell you a jar works.

- **Fabric — packaged jar:**
  ```bash
  ./gradlew :fabric:runClient -PtestJar
  ```
- **NeoForge — packaged jar:**
  ```bash
  ./gradlew :neoforge:runClient -PtestJar
  ```

---

## Branch Layout

| Branch | Role |
| :--- | :--- |
| **`main`** | Version-agnostic content only. Never built or published directly. |
| **`mc-26.2`** | Minecraft 26.2 |

`main` is an ancestor of all version branches, so its changes reach them by
`git merge` rather than by separate cherry-picks. Anything edited there lands
everywhere in one step: docs, release notes, the mod version, assets, and the Java files
that carry no version-specific API.

### What lives where

**Edit on `main`** — merged into every version branch:

- `README.md`, `SPEC.md`, `TODO.md`, `AGENTS.md`, `GEMINI.md`, `LICENSE`
- `gradle/mod.properties` — `mod_version`, `maven_group`, the store project ids
- `release/release-note-*.md`
- `common/src/main/resources/**` (assets, mixin configs)
- `common/src/main/java/com/drinfonty/ragdolls/**` (core ragdoll physics, config, client manager)
- `common/src/test/**` (unit and regression test suites)
- the Fabric and NeoForge platform entry points
- `settings.gradle`, `gradlew`, `gradle/wrapper/**`

**Edit on the version branch** — never merged from `main`:

| File | Why it is per-branch |
| :--- | :--- |
| `gradle.properties` | Every Minecraft, Loom, NeoForge and Java version value |
| `build.gradle`, `common/build.gradle` | The Loom plugin id — `plugins {}` needs a literal, so it cannot be a property |
| `fabric/build.gradle`, `neoforge/build.gradle` | Version-specific loader / dependency configurations |
