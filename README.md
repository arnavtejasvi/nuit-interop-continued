# Nuit Interop Continued

Converts OptiFine/MCPatcher custom sky resource packs to the Nuit skybox format at runtime, extending compatibility to Minecraft 1.21.5 and 1.21.8.

**Latest release:** v1.0.0 · [Modrinth](https://modrinth.com/mod/nuit-interop-continued)

---

## Requirements

- [Nuit](https://modrinth.com/mod/nuit) — the skybox renderer this mod converts packs for

---

## What it does

OptiFine custom sky packs define skyboxes using `.properties` files in `assets/minecraft/optifine/sky/`. This mod reads those files at runtime and translates them into Nuit-compatible skybox definitions, so existing OptiFine sky resource packs work without any manual conversion.

---

## Compatibility

| Loader | Minecraft |
|--------|-----------|
| Fabric | 1.21.5, 1.21.8 |

Client-side only.
