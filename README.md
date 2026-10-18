# Laylines

Laylines is a Minecraft Forge 1.20.6 magic mod that maps an invisible, seed-deterministic grid of magical laylines / vains across the world grid. This mod blends technical network graph logic with environmental phenomena, giving players a brand new way to discover, tap into, and utilize world seed-based magical reserves.

## 🗺️ Core Architecture & Mechanics

### 1. The Layline Web (Delaunay Triangulation & World Saved Data)
* **Deterministic Generation:** Laylines are calculated **entirely from the world seed** using a custom 2D network graph algorithm (such as Delaunay Triangulation). This ensures that the web is mathematically unbroken and completely independent of chunk generation order—even if a player teleports or maps chunks randomly.
* **Centralized Storage:** The web's structural node coordinates and connections are maintained in a global World Saved Data file (`LaylinesData`). Edges are permanently calculated paths between node coordinates, allowing fast 2D distance calculations globally, even across unloaded chunks.
* **Nodes & Density:** Nodes generate approximately **200 blocks apart radially**. By default, all layout math is computed in a 2D plane (assumed at `Y=200`).
* **Dead-Ends (Rare Tributaries):** While nodes usually connect to 2–3 others, rare dead-ends (1 connection) can generate. These nodes feature double the base regeneration rate, acting as intense energy volatile hubs.

### 2. Node Typology & Properties
Every single node tracks independent data pools for **all six attributes** (Fire, Water, Earth, Air, Dark, Light). 

* **Per-Attribute Data:** Capacity and Current Value are tracked **individually per attribute** (`fire_value`/`fire_capacity`, `water_value`/`water_capacity`, etc.). This ensures that a node generating intense Fire energy does not choke out or bottleneck Water or Air energy being piped through it from a neighbor.
* **The 50% Generation Rule:** There is a 50% chance that a node is **non-generating** (all attribute generation Rates = 0), acting exclusively as a transmission point or an energy sinkhole.
* **Structure-Driven Node Spawning:** When specific world structures generate (e.g., Desert Temples, Ocean Monuments, Villages), a Layline Node is forcibly anchored to that location. The structure type dictates its single active generating attribute:
  * *Desert Temple:* 90% chance to be a Fire-generating node, 10% chance to be Dark-generating.
* **Biome Mapping (Fallback):** If a node spawns naturally without an anchoring structure, it defaults to a chance-based layout tied to its native biome type (managed via a configuration layout after a global 90% mapping attribute check):
  * *Taiga Forest:* 60% chance to generate Air, 40% chance to generate Water.
* **Energy Flow Direction:** Flows are calculated deterministically across the network map during world creation using graph vector configurations. This creates a logical, non-clashing "river" of flowing attributes instead of a uniform grid direction.

### 3. Attributes & Phenomena (Boundaries: 10m Radius from Edge)
Phenomena occur along the Layline Edges, drawing energy dynamically from connected nodes. A global configuration file establishes base ticking chances, ranges, and scaling logic.

* **Per-Dimension Network Map:** The world seed maps 6 distinct, overlapping global layline vectors—one for each element—per dimension. This means the Overworld network layout remains constant across all biomes within it, providing a stable, unified global grid.
* **Local Biome Filtering:** While the grid map is global to the dimension, any local edge phenomenon checks for the active Biome's elemental affinity to determine its operating cost and efficiency.
  * *Example:* An Earth layline edge passes uniformly through both a Desert and a Plains biome. In the Plains biome, crop growth acceleration bleeds smoothly with maximum efficiency. When that same edge crosses into the Desert biome, the local environment penalizes the effect, making it cost 3x more energy to trigger.

| **Attribute** | **Phenomenon Idea** | **Mechanics & Consumption** |
|---|---|---|
| **Earth** | Faster crop growth | Implemented as Miracle Growth: every 5 seconds, eligible crops in loaded chunks within 10 horizontal blocks of an energized Earth layline edge are bonemealed, consuming fixed Earth units. Y distance is ignored. Pulse interval, horizontal range, energy cost, and random samples per edge are configurable. |
| **Air** | Speed buff / Fall damage immunity | Checked when a player enters the 10m radius. Consumes energy passively per active player. |
| **Fire** | Fast smelting / Block heating | Speeds up furnace ticks or generates rare heat blocks. Minimally destructive (togglable). |
| **Water** | Water Breathing | Grants passive breathing mechanics to players near or in water lines. |
| **Light** | Health Regeneration / Mob burning | Grants passive player regeneration; sets undead mobs on fire at night. |
| **Dark** | Heavily Armored Mob spawns | Increases regional difficulty and triggers custom mob spawns on the surface. |

### 4. Interactions & Sight
* **The Layline Stick:** Right-clicking a basic stick outputs debug chat information regarding the nearest layline edge distance and attributes.
* **The Golden Sight:** Wearing a standard **Gold Helmet** exposes the invisible lines to the naked eye.
* **Particle Systems:** Layline movement is displayed using slow-moving individual firework sparkles.
  * *Color:* Matches the active element's profile.
  * *Density:* Automatically scales thicker as energy density/flow value increases across that edge.

### 5. Admin Commands
Operators (permission level 2) can set the capacity and current value for one attribute pool on a layline node:

```text
/laylines pool set <uuid> <attribute> <capacity> <currentValue>
```

Attributes are `FIRE`, `WATER`, `EARTH`, `AIR`, `DARK`, or `LIGHT`. Both values must be non-negative, and current value cannot exceed capacity. The change is persisted with the world saved data.

---

## 🗺️ Progressive Roadmap

### 📦 Phase 1: Foundations & The Stick (Current Focus)
* Implement seed-deterministic Delaunay Triangulation network math.
* Hook up `LaylinesData` world storage hooks to track multi-attribute nodes.
* Register the basic right-click Stick feedback to map closest edge distance.

### 👁️ Phase 2: Simple Visuals & The Helmet
* Register a custom helmet checker logic.
* Render basic placeholder line particles along the calculated edges when a Gold Helmet is equipped.

### 🌊 Phase 3: Network Flows & Transmission
* Establish flow calculations across nodes using graph vector paths.
* Implement energy capacities and transmission logic (1 value per tick transfer rule).

### 🌌 Phase 4: Fluid Particle Engine
* Upgrade the simple rendering into a fluid system utilizing variable-density firework sparkle particles shifting in the direction of the edge vector.

### 💥 Phase 5: World Phenomena & Ticking
* Introduce the 10m boundary radius checking.
* [x] Implement world-based Earth crop growth (Miracle Growth) in loaded chunks, sampling randomly along energized edges with configurable interval, horizontal radius, energy cost, and sample count; vertical distance is ignored.
* [ ] Implement Air speeds, Light healing, and biome-weighted energy costs.

### 🔌 Phase 6: External Mod Integrations
* Expose API endpoints allowing external magic mods (or systems like Thaumcraft) to tap into Layline vectors for wand/reservoir recharging.
