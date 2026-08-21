# Toggleable Attachments — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Lets an attachment have **states** the player toggles in-game — a bipod that folds down, a
magnifier that flips aside, an adjustable stock. Each state can play an **animation** and
change any **gun property** TaC:Z already supports.

Everything lives in files you already write: the attachment's `data` JSON and your gun's
Lua animation state machine. There's no new file type and no new animation format.

---

## 1. Quick start — a foldable bipod

### The data file

`data/<your_namespace>/data/attachments/bipod_data.json`

```json
{
  "weight": 0.4,

  "states": {
    "cycle": ["stowed", "deployed"],

    "stowed": {
      "animation": "bipod_stow"
    },

    "deployed": {
      "animation": "bipod_deploy",
      "recoil": {
        "yaw":   { "addend": 0.0, "percent": -0.5, "multiplier": 1.0 },
        "pitch": { "addend": 0.0, "percent": -0.7, "multiplier": 1.0 }
      },
      "inaccuracy": { "addend": 0.0, "percent": -0.3, "multiplier": 1.0 }
    }
  }
}
```

That's it for stats. The bipod now:

- starts **stowed** (first entry in `cycle`)
- toggles to **deployed** when the player presses the Grip toggle key
- while deployed, cuts recoil and tightens accuracy
- sends `bipod_deploy` / `bipod_stow` to your animation state machine

### The animation

Model the bipod with its legs as **bones in the attachment's own model**, make a bedrock
animation for it, and point the `states` block at the animation file:

```json
"states": {
  "animation_file": "mypack:bipod",
  "cycle": ["stowed", "deployed"],
  "stowed":   { "animation": "bipod_stow" },
  "deployed": {
    "animation": "bipod_deploy",
    "recoil": { "pitch": { "addend": 0.0, "percent": -0.7, "multiplier": 1.0 } }
  }
}
```

- `animation_file` → `assets/mypack/animations/bipod.animation.json`
- each state's `animation` names a clip **inside that file**

That's the whole animation setup. **No Lua, and nothing to add to any gun.** The animation
lives in the attachment's own model space, so the same file is correct on every gun — the
gun only supplies the mount position.

Animations play once and **hold** on the last frame, since the state persists until toggled
again. Author your clips as one-way transitions (stowed → deployed), not loops.

### Optional: making the gun react too

Everything above works with no gun-side involvement. If you also own the gun, you can
*additionally* have it react — moving the left hand across to fold the bipod, for instance.
The state name is sent to the gun's Lua state machine as an input, so handle it in your
`..._state_machine.lua`. Give it **its own track** so it doesn't interrupt idle or reload:

```lua
-- near the top, alongside the other track declarations
local BIPOD_TRACK = increment(static_track_top)

function idle_state.transition(this, context, input)

    if (input == "bipod_deploy") then
        local track = context:getTrack(STATIC_TRACK_LINE, BIPOD_TRACK)
        context:runAnimation("bipod_deploy", track, false, PLAY_ONCE_STOP, 0.2)
        return this.main_track_states.idle
    end

    if (input == "bipod_stow") then
        local track = context:getTrack(STATIC_TRACK_LINE, BIPOD_TRACK)
        context:runAnimation("bipod_stow", track, false, PLAY_ONCE_STOP, 0.2)
        return this.main_track_states.idle
    end

    -- ... your existing INPUT_RELOAD / INPUT_PUT_AWAY / etc. handling
end
```

This is the same pattern TaC:Z uses for its own fire-mode switch animation (`FIRE_MODE_TRACK`
in the default AK script) — copy that if you want a reference in the base pack.

> **What can and cannot be gun-independent.** The attachment's own parts (bipod legs,
> magnifier hinge) animate from the attachment model, so one animation works everywhere.
> The **player's arms** are positioned by the *gun* model's `lefthand_pos` node, so any
> animation of a hand reaching over to operate the attachment depends on where that gun
> mounts it — and has to be authored per gun. That's why the hand version is the optional
> Lua path and the attachment's own movement is the universal one.

### Binding a key

**The toggle keys ship unbound.** The player sets them in
`Options → Controls → RenaissanceLib`. There is one key per attachment slot:

| Key | Toggles the attachment in |
|---|---|
| Toggle Scope Attachment | scope |
| Toggle Muzzle Attachment | muzzle |
| Toggle Stock Attachment | stock |
| Toggle Grip Attachment | grip |
| Toggle Laser Attachment | laser |
| Toggle Magazine Attachment | extended_mag |

A bipod occupies the **grip** slot, so it's the Grip key. Worth mentioning in your pack
description — nothing happens until the player binds it.

---

## 2. The `states` block

```json
"states": {
  "cycle": ["state_a", "state_b", "state_c"],
  "state_a": { "animation": "...", ...overrides... },
  "state_b": { "animation": "...", ...overrides... },
  "state_c": { "animation": "...", ...overrides... }
}
```

| Field | Meaning |
|---|---|
| `animation_file` | Bedrock animation file with this attachment's own animations, e.g. `"mypack:bipod"` → `assets/mypack/animations/bipod.animation.json`. Optional, but required for the attachment to animate itself. |
| `cycle` | Toggle order. **The first entry is the default state.** Optional — if omitted, declaration order is used. |
| `cooldown` | Seconds before the attachment can be toggled again — a **top-level default** here, overridable per state. Use it to force the toggle animation to finish. Optional; default `0` (no cooldown). See §6. |
| *(state name)* | Any name you like. `stowed`, `deployed`, `folded`, `2x`, `6x` … |
| `animation` | Clip name inside `animation_file` played on entering this state. Also sent to the gun's Lua state machine as an input, for optional gun-side reactions. |
| `zoom_index` | Scope slot only — selects a zoom level by index. Optional. See §4. |
| `cooldown` | Seconds before toggling again after entering **this** state — overrides the top-level `cooldown`. Optional. See §6. |
| *(anything else)* | Property overrides — see below. |

More than two states is fine. A three-position stock just lists three.

---

## 3. What you can put in a state

**Any modifier syntax that already works in an attachment file.** A state body is handed to
every TaC:Z modifier, and each picks out the field it owns. Commonly:

```json
"deployed": {
  "animation": "bipod_deploy",

  "recoil": {
    "yaw":   { "addend": 0.0, "percent": -0.5, "multiplier": 1.0 },
    "pitch": { "addend": 0.0, "percent": -0.7, "multiplier": 1.0 }
  },
  "inaccuracy": { "addend": 0.0, "percent": -0.3, "multiplier": 1.0 },
  "ads":        { "addend": 0.05, "percent": 0.0, "multiplier": 1.0 },
  "weight":     0.2,
  "rpm":        { "addend": -50.0, "percent": 0.0, "multiplier": 1.0 }
}
```

**Each stat is an object with `addend` / `percent` / `multiplier`** (except `weight`, a plain
number) — the same shape a normal attachment file uses. Specify all three; a missing
`multiplier` is read as `0` and would zero the stat. `recoil` is nested into `yaw` and `pitch`.

Also available, same shape: `aim_inaccuracy`, `damage`, `ammo_speed`, `effective_range`,
`head_shot`, `knockback`, `pierce`, `armor_ignore`, `explosion`, `ignite`, `silence`,
`movement_speed`.

### Base stats vs state stats

Modifiers at the **top level** of the file always apply. Modifiers **inside a state** apply
only while in that state, **stacked on top** of the base ones.

```json
{
  "weight": 0.4,                    // always — the bipod's own weight
  "states": {
    "cycle": ["stowed", "deployed"],
    "stowed": { "animation": "bipod_stow" },
    "deployed": {                   // only while deployed
      "animation": "bipod_deploy",
      "recoil": { "pitch": { "addend": 0.0, "percent": -0.7, "multiplier": 1.0 } }
    }
  }
}
```

---

## 4. Second example — sight with a flip-aside magnifier

A state can also select the scope's **zoom level**, so flipping the magnifier changes the
magnification in the same keypress.

### The display file

First declare the zoom levels as usual, in the attachment's **display** JSON:

```json
{
  "zoom": [1.0, 3.0]
}
```

### The data file

Then point each state at an index into that array — `0` is `1.0`, `1` is `3.0`:

```json
{
  "states": {
    "cycle": ["magnifier_aside", "magnifier_up"],

    "magnifier_aside": {
      "animation": "magnifier_flip_aside",
      "zoom_index": 0
    },

    "magnifier_up": {
      "animation": "magnifier_flip_up",
      "zoom_index": 1,
      "ads": { "addend": 0.06, "percent": 0.0, "multiplier": 1.0 }
    }
  }
}
```

Flipping the magnifier now plays the animation, switches to 3x, and slows ADS slightly —
one key, one action.

The magnifier sits in the **scope** slot, so the player uses the Scope toggle key.

### Notes on `zoom_index`

- It's an **index into the `zoom` array**, not a magnification value. `"zoom_index": 3` with
  only two zoom levels declared is a mistake — it wraps around rather than erroring, so you
  get the wrong magnification with no warning in game.
- It only works on the **scope** slot. Nothing else has zoom levels; using it elsewhere is
  ignored and logged.
- Omit it and the state leaves the current zoom alone. Useful if you want a folding
  attachment that changes handling but not optics.
- TaC:Z's own zoom key still works independently and cycles through the same array.

---

## 5. Things to know

**One attachment per keypress.** Each key toggles only its own slot. That's deliberate: the
animation state machine is per-gun, so two attachments animating at once would fight over it
and one animation would be lost. If a gun has both a bipod and a magnifier, they're on
different keys and animate independently.

**States persist.** A deployed bipod stays deployed when the player switches weapons, drops
the gun, or logs out. There is no auto-stow.

**The server decides.** State is stored on the gun item and validated server-side, so the
stat bonuses can't be spoofed by a client.

**Removing a state is safe.** If you edit your pack and delete a state a player's gun is
currently in, that gun quietly falls back to the default state instead of breaking.

**Animations are optional.** Omit `animation` and the state still toggles and still applies
its stat overrides — useful while prototyping, before the model work is done.

---

## 6. Cooldown — make the animation finish first

By default a player can spam the toggle key and re-trigger mid-animation. Add `cooldown`
(in **seconds**) to hold them off until the flip/deploy finishes:

```json
"states": {
  "animation_file": "mypack:bipod",
  "cooldown": 0.5,
  "cycle": ["stowed", "deployed"],
  "stowed":   { "animation": "bipod_stow" },
  "deployed": { "animation": "bipod_deploy" }
}
```

- Set it to your animation's length (the example above matches a `0.5s` clip).
- **Per-state override:** put `cooldown` inside a state to size it to that transition — handy
  when deploy and stow are different lengths:

```json
"stowed":   { "animation": "bipod_stow",   "cooldown": 0.4 },
"deployed": { "animation": "bipod_deploy", "cooldown": 0.6 }
```

  A state's own `cooldown` wins; otherwise the top-level one applies; otherwise there's none.

- The cooldown starts when you enter a state and is sized to **the state you entered**.
- Presses during the window are ignored entirely — nothing is sent to the server, so client
  and server stay in sync.
- Omit it for the old instant-toggle behavior.

---

## 7. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Nothing happens on keypress | Key not bound — check `Options → Controls → RenaissanceLib` |
| Stats change but nothing moves | Missing `animation_file`, or the `animation` name doesn't match a clip inside it — check the log, it names both |
| Attachment animates but the hand doesn't | Expected: hand animation is gun-side and optional (see §1) |
| Animation plays but stats don't change | Typo in the modifier key inside the state body — check spelling against a working attachment file, and remember each stat is an `addend`/`percent`/`multiplier` object |
| A stat drops to zero unexpectedly | A modifier object is missing `multiplier` — it reads as `0`, zeroing the stat. Always include all three fields |
| Deploy animation cuts off idle/reload | Bipod animation is on the main track — give it its own track (see §1) |
| Toggle does nothing on one gun only | The attachment isn't allowed in that slot on that gun (`allow_attachments` tags) |
| Toggle ignored for a moment after use | Expected if you set a `cooldown` — it's waiting out the window (see §6) |
| Wrong magnification after flipping | `zoom_index` is out of range for the `zoom` array in the display JSON — it wraps instead of erroring |
| `zoom_index` does nothing | It's on a non-scope slot (check the log), or the display JSON has no `zoom` array |

**Log check:** on startup you should see
`[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states'`.
If that line is missing, the `states` block won't be read at all.

---

---

## Conditional effects (`require`)

A state's stat overrides can be gated on the player's **stance** — the classic case being a
bipod whose recoil bonus only applies while prone. Add `require` to the state:

```json
"states": {
  "animation_file": "mypack:bipod",
  "cycle": ["stowed", "deployed"],
  "stowed":   { "animation": "bipod_stow" },
  "deployed": {
    "animation": "bipod_deploy",
    "require": "prone",
    "recoil": {
      "yaw":   { "addend": 0.0, "percent": -0.7, "multiplier": 1.0 },
      "pitch": { "addend": 0.0, "percent": -0.7, "multiplier": 1.0 }
    },
    "inaccuracy": { "addend": 0.0, "percent": -0.7, "multiplier": 1.0 }
  }
}
```

Now the bipod still deploys and animates whenever you toggle it, but the recoil/accuracy
bonus only takes effect **while you're prone**. Stand up and it turns off; go prone again
and it returns — live, no re-toggle needed.

**Conditions:**

| Value | Met when |
|---|---|
| `"prone"` | The player is crawling (TaC:Z's crawl key) |
| `"crouch"` | The player is sneaking |

**Any-of:** pass a list to accept more than one — the effect applies if **any** is met:

```json
"require": ["prone", "crouch"]
```

**Notes:**
- Only the **stat overrides** are gated. The `animation` and `zoom_index` still apply
  normally on toggle — the magnifier/bipod always moves.
- A state with no `require` applies its overrides unconditionally (the default).
- Effects gated this way are enforced server-side (spread) and client-side (recoil) alike,
  so they can't be spoofed.

---

## See also

- **[FIRE_MODE_ATTACHMENTS.md](FIRE_MODE_ATTACHMENTS.md)** — attachments that change a gun's
  fire modes. Goes in the same data file, under the `fire_mode` key.
- **[SCOPE_SHADERS.md](SCOPE_SHADERS.md)** — post-processing effects inside a scope's glass.
