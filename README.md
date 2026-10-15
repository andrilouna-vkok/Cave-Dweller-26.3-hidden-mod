# BetterImprovements – Minecraft 26.3 port (Fabric + GeckoLib 5)

**Status: UNCOMPILED DRAFT.** Ported from the decompiled 1.20.1 source without access to Minecraft, Fabric or
GeckoLib jars, so nothing here has been built or run. Expect a few compiler errors on the first build.

## Build
Needs Java 25.
- Local: install Gradle, run `gradle wrapper` once, then `./gradlew build`. Jar: `build/libs/BetterImprovements-2.2.0.jar`.
- No install: push this folder to a GitHub repo; `.github/workflows/build.yml` builds the jar (Actions -> artifact).

## Server install
Put the jar plus **Fabric API 0.161.0+26.3** and **GeckoLib 5.5.7+ (Fabric, 26.3)** in `mods/` on the server
AND on every client (the entity has a client renderer). Config: `config/cave_dweller.properties`.

## v2.1 additions
- Eyes: glowmask rebuilt (256x256, bright red, matches the base texture); `AutoGlowingGeoLayer` enabled (class path verified against GeckoLib source).
- Spawn egg: self-contained model + textures (no dependency on vanilla template paths).
- Block breaking: new `CaveDwellerBreakBlocksGoal` (config: CAN_BREAK_BLOCKS, BREAK_BLOCK_TIME, BREAK_BLOCK_MAX_HARDNESS).
- Commands (op only): `/BetterImprovements spawn|scare|status|skip|remove|reload`. `status` lists every natural-spawn condition.
  If `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` does not compile, delete that `.requires(...)` line.
- Natural spawn: Overworld only, player Y <= 40, sky light <= 8, not open to sky, survival mode, then a 5-10 min
  timer (40% chance of 20 min) after world load. `/BetterImprovements skip` zeroes the timer for testing.

## v2.2 - creepier
- New dwellers start out stalking (config STALK_CHANCE, default 70%): they creep closer only while unseen and freeze
  the instant they are looked at. When first spotted: 50% keep stalking, 25% chase, 25% flee.
- While stalking unseen, every 5-10 s one of: a cave noise at the player, a nearby torch/lantern snuffed out
  (SNUFF_LIGHTS), a Darkness effect pulse (CREEPY_DARKNESS, only when within 25 blocks).

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
