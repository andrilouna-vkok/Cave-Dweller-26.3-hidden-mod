# Better Cave Dweller – Minecraft 26.3 port (Fabric + GeckoLib 5)

**Status: UNCOMPILED DRAFT.** Ported from the decompiled 1.20.1 source without access to Minecraft, Fabric or
GeckoLib jars, so nothing here has been built or run. Expect a few compiler errors on the first build.

## Build
Needs Java 25.
- Local: install Gradle, run `gradle wrapper` once, then `./gradlew build`. Jar: `build/libs/better-cave-dweller-2.0.0.jar`.
- No install: push this folder to a GitHub repo; `.github/workflows/build.yml` builds the jar (Actions -> artifact).

## Server install
Put the jar plus **Fabric API 0.161.0+26.3** and **GeckoLib 5.5.7+ (Fabric, 26.3)** in `mods/` on the server
AND on every client (the entity has a client renderer). Config: `config/cave_dweller.properties`.

## What changed vs 1.20.1
- Mojang names (26.x is unobfuscated), `Identifier`, new registry/EntityType/Item property APIs.
- GeckoLib 5: assets moved to `assets/cave_dweller/geckolib/{models,animations}/entity/cave_dweller.*`,
  texture renamed `cave_dweller.png`; eyes layer replaced by `AutoGlowingGeoLayer` + `cave_dweller_glowmask.png`.
- Both mixins removed: `travel` cancel is a `travel()` override; `canUpdatePath` is an override in the navigation;
  Depth Strider is now the WATER_MOVEMENT_EFFICIENCY attribute (max 1.0, so the 1.5 default is clamped).
- Removed: Mod Menu / Cloth Config screen (edit the .properties file), custom head-tracking model, baby scaling.
- Spelunker light check simplified (raw sky light, 0 at night).

## Most likely compile problems (verify first)
1. GeckoLib 5 import paths in `CaveDwellerEntity` and `CaveDwellerRenderer` (`com.geckolib...`) were guessed.
   `withScale`, `withRenderLayer`, `AutoGlowingGeoLayer<>`, `AnimationTest`, `PlayState`, `thenLoop/thenPlayAndHold` unverified.
2. `isAggressive()` stands in for an unidentified intermediary method (`method_6510`) used in the chase goal/tick.
3. `snapTo`, `doHurtTarget(ServerLevel, Entity)`, `SpawnEggItem` + `Item.Properties#spawnEgg`, `EntityRendererRegistry`.
4. `createPath`, `getNearestPlayer(x,y,z,dist,Predicate)` signatures; `Level#getMinY` not used.
Paste compiler errors back into the chat and they can be fixed one by one.
