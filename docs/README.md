# RenaissanceLib — Content Pack Documentation

A TaC:Z addon (Forge 1.20.1) adding capabilities for gun pack authors. Every feature is
driven from the pack files you already write — no new file types.

## Features

| Guide | What it does | Where you configure it |
|---|---|---|
| **[Scope Shaders](SCOPE_SHADERS.md)** | Post-processing effects rendered only inside a scope's glass — night vision, colour grading, pixelation | `display/attachments/<scope>_display.json` |
| **[Toggleable Attachments](TOGGLEABLE_ATTACHMENTS.md)** | Attachments with states the player toggles in-game — folding bipods, flip-aside magnifiers — with their own animations and stat changes | `data/attachments/<name>_data.json` |
| **[Fire-Mode Attachments](FIRE_MODE_ATTACHMENTS.md)** | Attachments that change which fire modes a gun offers | `data/attachments/<name>_data.json` |
| **[Hierarchical Attachments](HIERARCHICAL_ATTACHMENTS.md)** | Attachments with their own mount points — canted irons or a piggyback red-dot on a scope, a laser on a handguard — mounted from sub-slots in the refit screen, with the zoom key cycling through every mounted optic | `data/attachments/<name>_data.json` (`rails` block) |
| **[Underbarrel Guns](UNDERBARREL_GUNS.md)** | A grip attachment that's a working secondary weapon — underbarrel launcher/shotgun with its own ammo, fire modes, reload and animations, switched with a key or the fire-mode radial. Optional own iron sights (ADS) and an `item_link` so owning the standalone gun lets you mount it | `data/attachments/<name>_data.json` + `display/attachments/<name>_display.json` + the underbarrel's `index` file |
| **[Combination Scope Modeling](COMBINATION_SCOPE_MODELING.md)** | Model a sight + magnifier so the sight is 1× see-through and the magnifier is a magnified porthole | model groups + display JSON |
| **[Conversion Kits](CONVERSION_KITS.md)** | An attachment that swaps the whole weapon into a different gun you already ship — caliber/platform conversions, restricted variants — locking attachments via the converted gun's own rules | `data/attachments/<name>_data.json` + the base gun's `allow_attachments` |
| **[Fire-Reaction Animations](FIRE_REACTION_ANIMATIONS.md)** | An attachment plays its own animation when the host gun fires — a reciprocating charging handle, an ejection-port cover, suppressor baffles | `data/attachments/<name>_data.json` |
| **[ADS Animations](AIM_ANIMATIONS.md)** | An attachment plays its own animation when you aim — a flip-up sight rising, a magnifier swinging in, a lens cover opening; follows the aim or plays aim-in/out clips | `data/attachments/<name>_data.json` |
| **[Recoil Speed](RECOIL_SPEED.md)** | An attachment changes how fast the gun's camera recoil plays — a slower, rolling kick or a faster snap — without changing its strength | `data/attachments/<name>_data.json` |

*Planned (in development):* a **double-render scope view** that re-renders the world for the lens —
enabling true night vision / thermal that reveals unlit entities and geometry the post-shaders can't.
Not available yet; see [Scope Shaders](SCOPE_SHADERS.md).

## Quick orientation

**Toggleable and fire-mode attachments** share the same file — an attachment can do both.
They're documented separately because they're independent features:

```json
{
  "weight": 0.4,
  "fire_mode": { "add": ["auto"] },
  "states": {
    "animation_file": "mypack:bipod",
    "cycle": ["stowed", "deployed"],
    "stowed":   { "animation": "bipod_stow" },
    "deployed": { "animation": "bipod_deploy", "recoil_modifier": { "pitch": -0.7 } }
  }
}
```

**Scope shaders** are configured in the *display* file instead, since they're purely visual.

## Two things worth knowing up front

**Toggle keys ship unbound.** Players set them in `Options → Controls → RenaissanceLib`,
one key per attachment slot. Worth mentioning in your pack description — otherwise a
toggleable attachment looks broken.

**Attachment animations are gun-independent; hand animations are not.** An attachment's own
moving parts animate from the attachment's model, so one animation is correct on every gun.
Animating the player's *hand* reaching over to operate it depends on where that specific gun
mounts the attachment, so it has to be authored per gun. See
[Toggleable Attachments §1](TOGGLEABLE_ATTACHMENTS.md).

**Fire-select is now tap-or-hold, on every gun.** A quick tap of the fire-select key cycles fire
modes exactly like vanilla TaC:Z; holding it (~¼ second) opens a **fire-mode radial** to pick a mode
directly — point and release. It lists the gun's modes (plus binary / manual when offered). When an
[underbarrel](UNDERBARREL_GUNS.md) is installed the radial shows **both** weapons' modes at once (the
underbarrel's tinted amber) and doubles as a weapon selector — picking an underbarrel mode switches to
the underbarrel, picking a host mode switches back. Nothing to configure — it works from the gun's
existing `fire_mode` list.

## Checking it's loaded

On startup:

```
[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states', 'shader', 'rails', 'underbarrel_data', 'conversion', 'fire_animation', 'aim_animation', 'recoil_speed'.
```

If that line is missing, none of the attachment JSON keys will be read.
