# Underbarrel Guns — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Turns a **grip attachment** into a fully functional secondary weapon — an underbarrel grenade
launcher, a masterkey shotgun, a flare gun. The player switches to it with the switch-weapon key and
it fires, reloads, animates, and tracks its own ammo independently of the host gun.

An underbarrel is an ordinary grip-slot attachment that embeds a **whole sub-gun**: a gun *data*
block (`underbarrel_data`) and a gun *display* block (`underbarrel_display`). You author those two
blocks exactly like a normal TaC:Z gun's `data`/`display` files — the addon runs them as a weapon
mounted on the host.

---

## 1. The two blocks

An underbarrel rides the **`grip`** slot. It needs a block in each of its attachment files:

**`data/<ns>/data/attachments/<name>_data.json`** — the sub-gun's stats:

```json
{
  "weight": 1.2,

  "underbarrel_data": {
    "ammo": "tacz:40mm",
    "ammo_amount": 1,
    "rpm": 60,
    "fire_mode": ["semi"],
    "bullet": {
      "damage": 4,
      "explosion": { "damage": 50, "radius": 3.0 }
    },
    "reload": {
      "type": "MAGAZINE",
      "feed": { "empty": 2.5 }
    }
  }
}
```

**`assets/<ns>/display/attachments/<name>_display.json`** — the sub-gun's model & animation:

```json
{
  "model": "<ns>:item/attachment/my_gl",

  "underbarrel_display": {
    "model": "<ns>:geo_models/attachment/my_gl.json",
    "texture": "<ns>:textures/attachment/my_gl.png",
    "animation": "<ns>:my_gl",
    "muzzle_flash": { "texture": "tacz:textures/...", "scale": 0.3 }
  },

  "hide_tactical_handguard": false,
  "mount_offset": { "position": [0, 0, 0], "rotation": [0, 0, 0] }
}
```

`underbarrel_data` is a full gun **data** object and `underbarrel_display` is a full gun
**display** object — every key you'd put in a normal gun's files works here (bolt, recoil,
sounds, LOD model, and so on). Only the two extra display keys below are ours.

| Extra display key | Purpose |
|---|---|
| `hide_tactical_handguard` | `true` hides the host gun's tactical handguard (TaC:Z force-shows it whenever a grip is installed), so it won't clash with the underbarrel's own handguard geometry. |
| `mount_offset` | Fallback placement nudge (`position` in pixels, `rotation` in degrees) if you can't position the model in Blockbench. Usually leave at zero and place the model directly. |

---

## 2. Model nodes

The underbarrel model is a normal bedrock gun model with a few named locator bones the addon
looks for:

| Node | Required? | What it does |
|---|---|---|
| `root` | Recommended | The whole-gun bone. Its animation is applied to the **whole weapon** (host + underbarrel together), so animating it for shoot/reload recoil swings the whole assembly in view — and the underbarrel stays on the host rail. Animate `root` (not the model body) for recoil; it's applied 1:1 to the weapon anchor, *not* to the underbarrel model, so it never rotates the launcher off its mount. |
| `lefthand_pos` | Recommended | Support-hand locator. The addon draws the **vanilla player arm** here (your host gun keeps its right hand). Model a placeholder arm cube to position it — the cube is hidden in-game and the real arm is drawn at the bone. |
| `muzzle_flash_underbarrel` | Optional | Where the muzzle flash draws (only if `muzzle_flash` is set in the display). A grenade launcher usually omits it. |
| `shell` | Optional | Shell-ejection origin, for underbarrels whose ammo has a shell model (e.g. a 12-gauge masterkey). No effect for shell-less ammo like 40 mm. |
| `iron_view` | Optional | The aim node for the underbarrel's **own iron sights** (§10). Add it (and set `iron_zoom`) to give the underbarrel its own ADS; omit it and aiming keeps using the host gun's sights. Same bone TaC:Z uses for a gun's iron sight. |

> **`lefthand_pos` is placed for the vanilla arm, not your cube.** The arm's origin differs from
> a placeholder cube, so position the bone by checking the real arm **in-game**, the same way
> TaC:Z's own guns are authored. Its rotation orients the forearm.

---

## 3. Animations

The underbarrel plays its own animations from the `animation` file in `underbarrel_display`:

| Clip | When |
|---|---|
| `static_idle` (or `idle`) | Loops while the underbarrel is the active weapon |
| `shoot` | Once per shot |
| `reload_tactical` / `reload_empty` (or `reload`) | Once per reload (magazine-style) — `reload_empty` plays when reloading from empty, `reload_tactical` otherwise |
| `inspect` | Once when the inspect key is pressed while the underbarrel is active |

> Clip names follow TaC:Z's **gun** convention, so you can name the underbarrel's animations exactly like a
> gun's: `static_idle`, `reload_tactical`, `reload_empty`, `shoot`. The older simple names (`idle`, `reload`)
> still work as fallbacks if a gun-convention clip isn't present.

### Shell-by-shell (shotgun) reloads

For an underbarrel whose `reload.type` is **`MANUAL`**, author the tube-shotgun clip set instead
of a single `reload`:

| Clip | Role |
|---|---|
| `reload_intro` / `reload_intro_empty` | Opening (the `_empty` variant plays when starting from 0 rounds) |
| `reload_loop` | One shell inserted — **repeated** to fill the reload |
| `reload_end` | Closing |

The addon plays **intro → loop (repeated) → end** and scales the reload time to the rounds being
loaded (`feed` time is treated as *per shell*, matching TaC:Z). Author `reload_loop` to match your
per-shell `feed` time so the loops line up with the shells. If any of the three clips is missing,
it falls back to the single `reload` clip.

> This is our own animator, not TaC:Z's Lua state machine — the intro/loop/end convention above is
> supported; arbitrary Lua-scripted animation logic is not.

---

## 4. In-game controls

Everything routes to whichever weapon is **active** (host gun vs underbarrel):

| Action | Key | Behaviour |
|---|---|---|
| Switch weapon | Switch-weapon key (bind in `Options → Controls → RenaissanceLib`) | **Toggles** between the main gun and the underbarrel. Since a gun has at most one underbarrel there's no menu — each press just flips. You can **also** switch straight from the fire-mode radial (below). |
| Fire | Normal shoot key | Fires the active weapon |
| Reload | Normal reload key | Reloads the active weapon (host reload is cancelled while the underbarrel is active) |
| Fire select | Normal fire-select key | **Tap** cycles the active weapon's own fire modes; **hold** (~¼ s) opens the fire-mode radial, which lists **both** weapons' modes and doubles as a weapon selector (see §5) |
| Inspect | Normal inspect key | While the underbarrel is active, hands control back to the **host gun**, plays the host's own inspect in full, then re-arms the underbarrel when it finishes — so the support arm animates properly (the underbarrel's rig owns that arm otherwise). With the host already active it inspects normally. |

> **Reload has no post-reload cooldown.** The underbarrel can fire the instant loading finishes — its reload
> lockout is the `feed` time only, not `feed + cooldown` (put any extra delay in the feed time itself).

The underbarrel keeps its **own** selected fire mode and **own** ammo count, both stored on the
gun and synced — they don't touch the host gun's.

---

## 5. Fire modes, binary & manual

The underbarrel cycles the modes in its `fire_mode` list (`semi` / `auto` / `burst`). Binary and manual are **opt-in**,
exactly like the host gun: add `"binary"` to the underbarrel's `fire_mode` array
(e.g. `"fire_mode": ["semi", "binary"]`) — it's never auto-added just because the underbarrel can fire semi.
When present, binary is added to the underbarrel's cycle and fires one shot on trigger **press** and one on
**release**. `"manual"` works the same way: plain semi with its own name and icon (see
[Fire-Mode Attachments §1b](FIRE_MODE_ATTACHMENTS.md)).

**Fire-mode radial.** Holding the fire-select key (~¼ second) opens a radial; point and release to pick a mode
directly (a quick tap still just cycles). This works on any gun, not only underbarrels.

When a gun has an underbarrel installed, the radial shows **both** weapons' modes in one ring — the host gun's
(including its binary / manual) and the underbarrel's — so it doubles as a **weapon selector**. The underbarrel's
segments are tinted a distinct amber, and the header names the weapon the highlighted segment belongs to
(`Main Gun` / `Underbarrel`). Picking a host segment switches to the host and sets that mode; picking an
underbarrel segment switches to (and arms) the underbarrel and sets that mode. So one gesture both selects the
weapon and its fire mode.

---

## 6. Ammo, HUD, and the tooltip

- **Ammo** is separate from the host gun: its own magazine, reload, and spare-ammo draw
  (loose rounds, ammo boxes, and creative unlimited all work). Reload duration comes from the
  underbarrel's `reload` data.
- **HUD** shows the underbarrel's ammo and fire mode while it's the active weapon; switching back
  restores the host gun's HUD. If `underbarrel_display` declares a `hud` (and optional `hud_empty`)
  texture, the HUD gun image swaps to it while the underbarrel is active — otherwise the host gun's
  image stays. Give the full texture path (e.g. `"hud": "mypack:textures/hud/my_gl.png"`), like the
  `muzzle_flash` texture, since the underbarrel display isn't path-converted.
- **Tooltip** — hovering the underbarrel attachment shows a gun-style stat block (ammo icon,
  ammo count, damage/explosion, fire rate, fire modes) alongside TaC:Z's normal attachment
  modifier tooltip. In the refit screen the ammo count reflects the installed underbarrel's live
  rounds (e.g. `0/1`).
- **Sounds** — the underbarrel plays its **own** authored fire sounds from `underbarrel_display.sounds`:
  `shoot` (what the shooter hears) and `shoot_3p` (what nearby players hear), plus `silence`/`silence_3p`
  for a silencer (§7). Give raw sound ids (e.g. `"shoot": "mypack:my_gl/my_gl_shoot"`), like the
  `muzzle_flash` texture, since the underbarrel display isn't path-converted.

---

## 7. The underbarrel's own attachments

An underbarrel is a sub-gun, so it can carry its **own** attachments — a muzzle, an extended mag, etc. — that
change *its* stats, separately from the host gun's attachments.

**Author it in two places:**

1. **`underbarrel_data`** — declare the slots the underbarrel accepts (its `allow_attachment_types`), plus the
   usual gun fields the effects read (e.g. `extended_mag_ammo_amount` for a mag):

   ```json
   "underbarrel_data": {
     "ammo_amount": 1,
     "extended_mag_ammo_amount": [2, 3],
     "allow_attachment_types": ["muzzle", "extended_mag"],
     ...
   }
   ```

2. **The underbarrel model** — add a `<type>_pos` locator bone for each slot you want the attachment to render
   at (`muzzle_pos`, `extended_mag_pos`, …), exactly like a gun model names its attachment bones.

**In-game:** the underbarrel's slots appear as a **row just below the gun's own attachment row** (anchored at
the grip column, where the underbarrel rides) — the same layout as [hierarchical attachments](HIERARCHICAL_ATTACHMENTS.md)
sub-slots. Click an empty slot for a picker of matching attachments; click a filled one to unload. The
attachment renders on the underbarrel at its `<type>_pos` bone and travels with the underbarrel between guns.

**What the attachments change:** their modifiers apply to the underbarrel's own fire — **inaccuracy**,
**fire rate (rpm)**, **projectile velocity**, **magazine size** (via an extended mag), **recoil** (see §8), and
**silencing** (a silencer muzzle — see below). More stat types (e.g. damage) will follow as the underbarrel
gains the systems they hook into.

**Silencer muzzle.** A muzzle whose modifiers set TaC:Z's silence flag makes the underbarrel fire quietly: it
plays the underbarrel's **silenced** sound instead of its shoot sound and **hides the muzzle flash**. Author
the silenced sound in `underbarrel_display.sounds` as `silence` (first-person) and `silence_3p` (what others
hear); if omitted, a silenced shot falls back to the normal shoot sound. This is the same silence modifier a
normal TaC:Z silencer uses, so any silencer the underbarrel `allow`s works.

---

## 8. Recoil

The underbarrel kicks the camera using **TaC:Z's own data-driven recoil** — the exact curve system the
host gun uses. Author a `recoil` block inside `underbarrel_data` (pitch/yaw keyframes) just like a normal
gun, and the underbarrel plays that curve on each shot: the kick eases in and settles instead of a flat jump.

```json
"underbarrel_data": {
  "recoil": {
    "pitch": [ { "time": 0, "value": 0 }, { "time": 0.1, "value": 3.5 }, { "time": 0.3, "value": 0 } ],
    "yaw":   [ { "time": 0, "value": 0 }, { "time": 0.1, "value": 0.6 }, { "time": 0.3, "value": 0 } ]
  }
}
```

Recoil-modifying attachments installed on the underbarrel (§7) scale this curve, the same as on a gun.
Since the underbarrel doesn't aim down sights, the full (non-aiming) recoil factor always applies. No
`recoil` block → no camera kick.

---

## 9. Unlocking via a linked gun (`item_link`)

An underbarrel can be made installable by **owning the standalone gun it represents**, instead of a
separate attachment item. Declare an `item_link` in the underbarrel's **index** file
(`data/<ns>/index/attachments/<name>.json`):

```json
{
  "name": "...",
  "display": "<ns>:my_gl_display",
  "data": "<ns>:my_gl_data",
  "type": "grip",
  "item_link": "tacz:modern_kinetic_gun{GunId:\"tacz:m320\"}"
}
```

The value is an item id with optional SNBT. For a TaC:Z gun the item is always
`tacz:modern_kinetic_gun`, so the **`GunId`** tag is what actually identifies which gun unlocks it.

**How it behaves (round-trip):**

- While you hold the linked gun anywhere in your inventory, the underbarrel appears as an installable
  candidate in the host gun's **grip** slot in the gun-smith refit screen — its inventory button shows
  the linked gun.
- **Installing consumes one** of the linked gun and mounts the underbarrel. The consumed gun is
  remembered on the attachment.
- **Uninstalling gives the linked gun back** (not an attachment item) — so mounting/unmounting never
  loses your weapon. An underbarrel installed the normal way (as an attachment item) is unaffected and
  returns its attachment item as usual.

`item_link` is additive: it only *adds* the ownership path. The underbarrel still installs normally
from its own attachment item if the pack provides one, and the host gun must still allow the grip via
its `allow_attachments` tags.

---

## 10. Iron sights (ADS)

The underbarrel can have its **own** aim-down-sights — **optional**. If you don't set it up, aiming while the
underbarrel is up simply keeps the host gun's aim, exactly as before.

To enable it:

1. Add an **`iron_view`** locator bone to the underbarrel model, positioned/oriented so the camera looks
   straight down the underbarrel's sights (the same bone TaC:Z uses for a gun's iron sight). Its presence is
   the opt-in — no `iron_view`, no underbarrel ADS.
2. Set **`iron_zoom`** in the `underbarrel_display` (the aim magnification, e.g. `1.33` for a slight zoom).

```json
"underbarrel_display": {
  "iron_zoom": 1.33
}
```

When the underbarrel is the active weapon and you aim, the camera aligns to `iron_view` and the world zooms to
`iron_zoom`. The gun-model FOV (`zoom_model_fov`) stays the host's — iron sights don't need it.

> **`iron_view` is placed for the camera, not a cube — tune it in-game.** Like `lefthand_pos`, its exact
> position/rotation is best dialed in by aiming in-game, since it sets where the camera sits relative to the
> sights.

---

## 11. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Underbarrel doesn't appear / can't install | It must be a **grip**-slot attachment, allowed on the gun via `allow_attachments` tags |
| `item_link` gun doesn't show in the refit picker | Wrong `GunId` in `item_link`, the gun isn't in your inventory, or the host gun's grip slot doesn't allow the underbarrel |
| Aiming the underbarrel still uses the host sights | No `iron_view` node in the underbarrel model — that node is the ADS opt-in (§10) |
| Underbarrel ADS aims at the wrong spot | Reposition/rotate the `iron_view` node in-game (§10) |
| Installs but does nothing when selected | `underbarrel_data` missing or malformed — the addon only treats a grip as an underbarrel when that block parses |
| Model shows but no animation / effects | `underbarrel_display` didn't parse — check the `animation`/`model` paths inside that block |
| Support arm off-screen or wrong spot | Reposition/rotate `lefthand_pos` for the **vanilla arm**, checked in-game (§2) |
| Whole weapon doesn't move on shoot/reload | Animate the `root` node — that's what drives whole-weapon motion (applied 1:1). Animating the model body bones instead moves only the launcher, swinging it off the rail |
| Shotgun reload plays as one clip | `reload.type` isn't `MANUAL`, or the `reload_intro`/`reload_loop`/`reload_end` clips are missing |
| Host handguard clips through the underbarrel | Set `"hide_tactical_handguard": true` in the display |

**Log check:** on startup you should see the underbarrel modifier registered:

```
[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states', 'shader', 'rails', 'underbarrel_data', 'conversion', 'fire_animation', 'aim_animation', 'recoil_speed'.
```

If `underbarrel_data` isn't in that list, none of the underbarrel keys will be read.
