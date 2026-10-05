# EMBERFALL Boss Architecture Research: Vanilla & Display Entity Boss Engineering (1.21)

This research document analyzes non-resource-pack custom boss engineering for Minecraft 1.21.11, covering server implementations, Display Entity capabilities and limits, vanilla boss mechanics, performance benchmarks, and actionable recommendations for EMBERFALL.

---

## 1. High-End Custom Boss Architecture Without Resource Packs

### Example 1: Hypixel SkyBlock — Dungeons Bosses (Necron, Sadan, The Professor)
* **Look & Architecture**: Built using multi-entity rigs consisting of stacked invisible base entities (`ArmorStand`, `Slime`, `Giant`, `WitherSkeleton`). Head slots are fitted with custom player skull textures (`minecraft-heads.com` skin hashes embedded in the NBT skull owner profile), armor slots use custom dyed leather/iron armor, and held slots feature enchanted weapons.
* **Visual FX & Telegraphs**: Heavy reliance on particle trails (`flame`, `dragon_breath`, `witch`, `smoke`), floating text labels via invisible Armor Stand names or `text_display`, and phase transitions where model components swap or change armor color.
* **Sources**:
  * https://www.elkonia.com/wiki/codex/techniques.html
  * https://www.elkonia.com/wiki/codex/plugins/mythics/mythicmobs.html

### Example 2: Hypixel SkyBlock — Slayers (Voidgloom Seraph, Inferno Demonlord)
* **Look & Architecture**: Voidgloom Seraph pairs an Enderman base entity with orbiting `block_display`/`item_display` entities and invisible armor stands representing Null Shields and floating Beacon Beams. Inferno Demonlord utilizes a Blaze/Wither Skeleton base surrounded by orbiting player-head fireball constructs.
* **Mechanics & Visuals**: Hit-shield counters (requiring *N* distinct hits rather than raw damage to break invulnerability), floor particle telegraph runes using colored `dust` particles, and high-frequency sound triggers.
* **Sources**:
  * https://www.elkonia.com/wiki/codex/techniques.html

### Example 3: Wynncraft — Core Non-Resource Pack Boss Design Patterns
* **Look & Architecture**: While modern Wynncraft relies on a client resource pack for custom 3D models, its core boss mechanics framework functions using vanilla primitives: invisible Slimes/Magma Cubes for multi-box hitboxes, particle telegraph arrays (expanding concentric rings of `dust` or `end_rod` particles prior to damage ticks), and distinct audio pitch cues.
* **Lessons for Resource-Pack-Free Builds**: Visual models can be decoupled entirely from hitboxes. Static or animated display entity constructs handle the visual appearance, while invisible vanilla mobs handle AI, damage calculation, and collisions.
* **Sources**:
  * https://www.elkonia.com/wiki/codex/techniques.html
  * https://www.elkonia.com/wiki/codex/plugins/mythics/mythicmobs.html

### Example 4: MythicMobs & MMOItems — Display Entity Mode Rigs
* **Look & Architecture**: MythicMobs boss configurations without custom resource packs mount `block_display`, `item_display`, and `text_display` entities onto invisible base mobs or `interaction` entities.
* **Visual Formatting & Scaling**: Uses `transformation` scale, rotation, and translation matrices, custom skull head components, `Glowing:1b` with `glow_color_override` for boss enrage states, and full `brightness` overrides (`{block:15, sky:15}`) to keep display structures illuminated regardless of arena lighting.
* **Sources**:
  * https://www.elkonia.com/wiki/codex/plugins/mythics/mythicmobs.html
  * https://www.elkonia.com/wiki/codex/display-entities.html

### Example 5: Hypixel Pit & Dungeon Realms — Classic Stacked Vanilla Rigs
* **Look & Architecture**: Constructed using stacked invisible mob bases (e.g. `ArmorStand` riding `Pig` or `Slime`) carrying textured player skulls, custom enchanted equipment, colored leather armor, and continuous particle aura ticks (`mobSpell`, `smoke`, `dragon_breath`).
* **Hitboxes & AI**: Leverages native vanilla pathfinding AI combined with high-level status effects (Speed, Resistance, Strength) and scoreboard-driven skill timers.
* **Sources**:
  * https://www.elkonia.com/wiki/codex/techniques.html

### Example 6: Datapack Showcase Bosses (BDEngine, Animated Java, Solis Legends, Revamped Ender Dragon)
* **Look & Architecture**: Assembles complex multi-joint 3D boss structures using composite `block_display` and `item_display` rigs (utilizing vanilla blocks, items, and custom player skulls) without any resource pack.
* **Animation & Mechanics**: Bone hierarchies are keyframed by setting `interpolation_duration` (e.g. 5–10 ticks) and updating `transformation` matrices on keyframe ticks. The client performs hardware-accelerated linear interpolation at 60–144+ FPS while server command loops update at 20 TPS. Invisible `interaction` entities or `slime` entities serve as hitboxes.
* **Sources**:
  * https://animated-java.github.io/
  * https://skinmc.net/project/revamped-ender-dragon
  * https://www.planetminecraft.com/project/1-21-3-chronicles-of-light-submerged-singularity-custom-minecraft-raid-adventure-map/
  * https://www.elkonia.com/wiki/codex/display-entities.html

---

## 2. Display Entity Capabilities & Technical Limits in Minecraft 1.21

### Entity Types & Capabilities
1. **`minecraft:block_display`**: Renders a block state specified by `block_state:{Name:"...", Properties:{...}}`. Ideal for structural body parts, floating armor pieces, and geometric boss energy nodes.
2. **`minecraft:item_display`**: Renders an item stack stack compound using 1.20.5+/1.21 component SNBT (`item:{id:"...", count:1, components:{...}}`). Supports player skulls (`minecraft:profile`), item models, custom colors, and enchant glints. Model transform modes include `none`, `thirdperson_lefthand`, `thirdperson_righthand`, `firstperson_lefthand`, `firstperson_righthand`, `head`, `gui`, `ground`, and `fixed`.
3. **`minecraft:text_display`**: Renders 3D text components (`text`). Key fields include `line_width` (default 200), `background` (packed ARGB int; set `0` for transparent background), `text_opacity` (0–255), `alignment` (`center`, `left`, `right`), `shadow` (boolean), `see_through` (boolean), and `default_background` (boolean).

### The Transformation Compound (`transformation`)
* Decomposed structure applied in order: **`right_rotation` → `scale` → `left_rotation` → `translation`**:
  ```snbt
  transformation:{
    translation:[0f, 0f, 0f],
    left_rotation:[0f, 0f, 0f, 1f],
    scale:[1f, 1f, 1f],
    right_rotation:[0f, 0f, 0f, 1f]
  }
  ```
* Rotations accept quaternions `[x, y, z, w]` or angle-axis compounds `{angle:<deg>, axis:[x,y,z]}`.
* Alternatively accepts a flat 16-float row-major affine matrix array.

### Interpolation Fields (Client-Side Smoothing)
* **`start_interpolation`**: Tick delay before interpolation begins (set `0` for immediate execution on next tick).
* **`interpolation_duration`**: Duration in ticks over which the client smoothly transitions from current transformation/shadow/background/opacity values to newly set values.
* **`teleport_duration`**: Added in 1.20.2; interpolates position/rotation changes from teleports (clamped 0–59 ticks).
* **Smoothness at 20 TPS**: Server command loops only need to issue NBT updates on keyframes (e.g. every 5–20 ticks with `interpolation_duration:5..20`). The client GPU interpolates linearly between keyframes, producing fluid 60–144+ FPS motion without network packet flooding.

### Visual & Lighting Attributes
* **`brightness`**: Compound `{block:0-15, sky:0-15}` overrides ambient light. Setting `{block:15, sky:15}` ensures full visibility in dark arenas.
* **`Glowing:1b` & `glow_color_override`**: Enables custom outline glowing (packed RGB int, e.g. `16711680` for red).
* **`view_range`**: View-range multiplier (default 1.0).
* **`billboard`**: Billboard facing mode (`fixed`, `vertical`, `horizontal`, `center`).
* **`shadow_radius` & `shadow_strength`**: Controls ground shadow rendering (interpolated).
* **`width` & `height`**: Sets the culling bounding box (defaults to 0 in 1.21.5+; setting realistic dimensions prevents premature frustum culling).

### Technical Limits & Overhead
* **Server Network Packet Cost**: Display entities have zero AI or physics server overhead, BUT modifying display NBT via `/data merge` every single tick generates up to 4x the network packet overhead of standard entities.
* **Client GPU / FPS Limits**: Display models exceeding ~50 entities per active boss or overusing `Glowing:1b` outline shaders cause significant client FPS drops due to post-processing shader passes and matrix computations.
* **Sources**:
  * https://raw.githubusercontent.com/chapmanjw/minecraft-java-fabric-claude-plugin/main/skills/design-monument/reference/display-entities.md
  * https://www.elkonia.com/wiki/codex/display-entities.html
  * https://feedback.minecraft.net/hc/en-us/community/posts/48624215827981-Improve-Display-Entity-Backend
  * https://www.spigotmc.org/resources/lootglow.134648/update?update=646391

---

## 3. Vanilla Boss Mechanics Worth Borrowing & Lessons Learned

1. **Ender Dragon**:
   * *Mechanics*: Multi-part hitbox (`EnderDragonPart`), phase cycle (perching, hovering, charging), environmental healing pillars (Ender Crystals), lingering toxic AOE (dragon breath cloud area denial).
   * *Lesson*: Multi-part hitboxes make giant bosses feel physically real; destroying arena pylons creates essential combat side-objectives.
   * *Sources*: https://minecraft.fandom.com/pt/wiki/Edi%C3%A7%C3%A3o_Java_1.19.4

2. **Wither**:
   * *Mechanics*: Invulnerable spawn charging phase with terminal explosion, dual targeting (main target + random head target), 50% HP armor phase (ranged projectile immunity + minion spawning), destructive projectiles.
   * *Lesson*: Invulnerable charging phases build combat anticipation; conditional projectile immunity forces players to switch tactics from ranged to close-quarters dodging.
   * *Sources*: https://minecraft.fandom.com/pt/wiki/Edi%C3%A7%C3%A3o_Java_1.19.4

3. **Warden**:
   * *Mechanics*: Sound/vibration detection anger meter, wall-piercing shield-bypassing ranged Sonic Boom beam, Darkness status effect pulsation, extreme melee punishment.
   * *Lesson*: Telegraphed ranged beam attacks give players clear dodge windows; environmental darkness pulses create heavy visual atmosphere without requiring resource packs.
   * *Sources*: https://minecraft.fandom.com/pt/wiki/Edi%C3%A7%C3%A3o_Java_1.19.4

4. **Elder Guardian**:
   * *Mechanics*: Range-wide status affliction (Mining Fatigue / visual ghost overlay), lock-on tracking laser beam (charges over time before dealing guaranteed damage unless line-of-sight is broken by cover), defensive counter-spikes.
   * *Lesson*: Lock-on laser beams force players to utilize arena cover; telegraphed charge-up timers allow tactical dodging.
   * *Sources*: https://minecraft.fandom.com/pt/wiki/Edi%C3%A7%C3%A3o_Java_1.19.4

5. **Ravager**:
   * *Mechanics*: Heavy charge attack, shield-stun counter (stuns when blocking its charge), knockback roar AOE, terrain destruction.
   * *Lesson*: Telegraphed charge attacks reward timed dodging/parrying; stun states reward strategic player reactions.
   * *Sources*: https://minecraft.fandom.com/pt/wiki/Edi%C3%A7%C3%A3o_Java_1.19.4

---

## 4. Performance Guidance

### Safe Entity Budget
* **Recommended Budget**: **15 to 30 moving display entities** per active boss for optimal 20 TPS server performance and 60+ FPS client rendering.
* **Upper Limit**: Up to **50 display entities** is manageable IF transformations update infrequently (every 5–10 ticks with matching `interpolation_duration`).
* **Hazard Threshold**: **>100 display entities** per boss causes severe network packet spikes, client interpolation stutter, and FPS drops.

### Performance Bottlenecks & What Is Known to Lag
1. **Per-Tick Server Packet Spam**: Executing `/data merge entity` on display entities every tick (1tps updates) floods client network sockets with packets.
2. **Glow Shader Overuse**: Enabling `Glowing:1b` on many display entities forces multiple post-processing render passes on the client GPU.
3. **High View Range & Missing Culling Boxes**: Setting `view_range` > 2.0 or omitting `width`/`height` culling dimensions breaks client frustum culling, forcing off-screen rendering.
4. **Uncleaned Ghost Displays**: Spawning display entities without cleanup tags (`Tags:["emberfall_boss"]`) causes orphaned display entities to linger indefinitely in chunk storage.

---

## 5. WHAT WE CAN SAFELY BUILD & WHAT TO AVOID

### WHAT WE CAN SAFELY BUILD
1. **Interpolated Composite Display Entity Models**: Multi-joint boss rigs composed of 15–25 `block_display` and `item_display` entities (utilizing custom skull textures from `minecraft-heads.com`), animated via keyframed `transformation` and `interpolation_duration` (https://www.elkonia.com/wiki/codex/display-entities.html).
2. **Invisible Interaction / Slime Hitboxes**: Coupling visual display entities with invisible `interaction` (1.19.4+) or `slime`/`zombie` entities for hit detection, damage processing, and AI pathfinding (https://www.elkonia.com/wiki/codex/techniques.html).
3. **Telegraphed Ground AOE Runes**: Floor-projected `dust` particles or `text_display` rings indicating AOE threat zones prior to damage application (https://www.elkonia.com/wiki/codex/techniques.html).
4. **Wall-Piercing Raycast Beam Attacks**: Wall-piercing charging beam attacks telegraphed via `end_rod`, `sonic_boom`, or `soul_fire_flame` particles (https://www.elkonia.com/wiki/codex/techniques.html).
5. **Dynamic Phase Transitions & Enrage States**: Dynamically modifying `glow_color_override`, swapping `block_state`, or altering scale via single keyframe NBT updates.

### WHAT TO AVOID
1. **Per-Tick Server NBT Updating**: Modifying display transformation NBT every tick without leveraging client-side interpolation (`interpolation_duration`).
2. **High Entity Rig Counts (>50 Displays)**: Assembling overly detailed boss models with more than 50 display entities.
3. **Excessive Glowing Outlines**: Setting `Glowing:1b` on every sub-part of a multi-part boss model.
4. **Missing Cleanup Tags**: Spawning display entities without standard tags (e.g. `Tags:["emberfall_boss"]`), creating persistent ghost entities upon boss despawn or death.
5. **Direct Player Velocity Writing**: Attempting to write `/data modify entity <player> Motion` directly (use data-driven impulse/knockback mechanics or entity mounting).
