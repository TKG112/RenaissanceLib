# RenaissanceLib — Content Pack Documentation

A TaC:Z addon (Forge 1.20.1) adding capabilities for gun pack authors. Every feature is
driven from the pack files you already write — no new file types.

## Features

| Guide | What it does | Where you configure it |
|---|---|---|
| **[Scope Shaders](SCOPE_SHADERS.md)** | Post-processing effects rendered only inside a scope's glass — night vision, colour grading, pixelation | `display/attachments/<scope>_display.json` |
| **[Toggleable Attachments](TOGGLEABLE_ATTACHMENTS.md)** | Attachments with states the player toggles in-game — folding bipods, flip-aside magnifiers — with their own animations and stat changes | `data/attachments/<name>_data.json` |
| **[Fire-Mode Attachments](FIRE_MODE_ATTACHMENTS.md)** | Attachments that change which fire modes a gun offers | `data/attachments/<name>_data.json` |

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

## Checking it's loaded

On startup:

```
[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states'
```

If that line is missing, none of the attachment JSON keys will be read.
