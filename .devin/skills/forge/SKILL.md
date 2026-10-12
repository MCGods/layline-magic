---
name: forge-development
description: "Guidelines and best practices for Minecraft Forge mod development."
trigger: model_decision
---

# Minecraft Forge Development Best Practices

When writing or modification of Minecraft mod code is active, adhere to the following standards:

- **Registry System:** Always use `DeferredRegister` and `RegistryObject` for registering blocks, items, entities, and tile entities. Never use legacy direct registration.
- **Side Separation:** Strictly separate Client-only code (UI, rendering, models) from Server/Common code using `@EventBusSubscriber(Side.CLIENT)` or `DistExecutor` to prevent dedicated server crashes.
- **Event Handling:** Use the correct Event Bus. Lifecycle events (`FMLCommonSetupEvent`) go to the Mod Bus; gameplay events (`PlayerInteractEvent`) go to the Forge Bus.
- **Data Generation:** Prefer utilizing Forge Data Providers (`DataProvider`, `BlockStateProvider`, `ItemModelProvider`) to generate JSON assets rather than writing raw JSON files manually.

## Post-Item Modification Automation

Whenever you finish creating, removing, or editing items or blocks, you must immediately run the Forge data generation gradle command in the terminal to output the required JSON asset files:
`./gradlew runData`

_Note: Ensure the data generation task completes successfully before handing control back to the user._
