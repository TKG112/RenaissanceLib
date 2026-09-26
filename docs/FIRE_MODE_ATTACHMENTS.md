# Fire-Mode Attachments — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Lets an attachment change which **fire modes** a gun offers — a giggle switch that adds
full-auto, a burst kit, a trigger group that removes a mode.

TaC:Z ships 18 attachment modifiers (damage, RPM, recoil, ADS…) but none for fire mode.
This adds one.

---

## 1. Quick start — auto-only pistol conversion

`data/<your_namespace>/data/attachments/pistol_switch_data.json`

```json
{
  "weight": 0.05,

  "fire_mode": {
    "set": ["auto"]
  }
}
```

Equip it and the pistol offers only full-auto. Remove it and the gun returns to its normal
modes.

Valid mode names: `semi`, `auto`, `burst`, plus the two extra modes below: `binary`, `manual`.

---

## 1b. Extra modes: `binary` and `manual`

RenaissanceLib adds two fire modes on top of TaC:Z's three. Both fire like `semi` underneath:

| Mode | Behaviour | HUD / radial icon |
|---|---|---|
| `binary` | One shot on trigger **press**, one more on **release** | `renaissance_lib:textures/hud/fire_mode_binary.png` |
| `manual` | Exactly like `semi`, with its own name and icon (for bolt / pump / lever actions) | `renaissance_lib:textures/hud/fire_mode_manual.png` |

Write them anywhere a mode name goes:

- In a **gun's own** `fire_mode` array: `"fire_mode": ["manual"]` or `["semi", "binary", "auto"]`. They cycle in
  the position you wrote them. A gun can list **only** these (e.g. `["manual"]` for a bolt-action, or
  `["binary", "manual"]`): it then offers just those, and a fresh gun starts in the first one.
- In an attachment's `set` / `add` / `remove`, e.g. `"add": ["binary"]` for a binary trigger group.
- In an **underbarrel's** `fire_mode` array (see [Underbarrel Guns §5](UNDERBARREL_GUNS.md)).

They're opt-in: a gun that only lists `semi` doesn't get either one. A mode added by an attachment (with no
authored position) sits right after `semi` in the cycle.

**Icons.** A resource pack can replace either icon by providing a texture at the same path. If the manual icon is
missing, the SEMI icon is shown instead.

**Lua scripts** see these modes as `SEMI`, since they're built on it.

---

## 2. The three operations

```json
"fire_mode": {
  "set":    ["semi", "auto"],
  "add":    ["auto"],
  "remove": ["burst"]
}
```

| Key | Effect |
|---|---|
| `set` | Replaces the gun's mode list outright |
| `add` | Appends modes not already present |
| `remove` | Strips modes if present |

All three are optional — use whichever you need.

### Order of evaluation

Across **all** equipped attachments: **`set` → `add` → `remove`**.

That ordering makes stacking predictable:

- An attachment can establish a base list with `set`
- Others layer deltas on top with `add` / `remove`
- A `remove` always beats an `add`, whichever attachment asked for it

If two attachments both use `set`, the last one evaluated wins.

---

## 3. `set` vs `add`/`remove` — which to use

They often give the same result, and differ when the gun isn't what you assumed.

Say you want "this attachment makes the gun auto-only."

**On a `["semi"]` pistol** — both work:

| Written as | Result |
|---|---|
| `"set": ["auto"]` | `["auto"]` |
| `"add": ["auto"], "remove": ["semi"]` | `["auto"]` |

**On a `["semi", "burst"]` pistol** — they diverge:

| Written as | Result |
|---|---|
| `"set": ["auto"]` | `["auto"]` ✅ auto only |
| `"add": ["auto"], "remove": ["semi"]` | `["burst", "auto"]` ❌ burst survived |

**Rule of thumb:** use `set` to *guarantee* the resulting modes regardless of the gun. Use
`add`/`remove` for a delta that composes with whatever the gun already has.

---

## 4. Behaviour details

**Cycling order.** Modes cycle in list order — the gun's base order, with anything added
appended at the end.

**Removing the attachment resets the mode.** If a gun is sitting in a mode the attachment
granted and the player removes that attachment, the gun snaps to a valid mode immediately.
It won't keep firing full-auto with the switch taken off.

**An empty result falls back.** If your `remove` strips every mode and nothing adds one
back, the gun's own mode list is restored rather than leaving it with none. So
`"remove": ["semi"]` alone on a semi-only pistol is a no-op, not a broken gun.

**Refit GUI.** The resulting modes show as a property row in the gun refit screen, so
players can see what an attachment does before equipping it.

---

## 5. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Nothing changes | Attachment isn't allowed in that slot on that gun (`allow_attachments` tags) |
| Mode list unchanged | Typo in a mode name — only `semi`, `auto`, `burst`, `binary`, `manual` are valid |
| Burst survived when you expected it gone | Used `add`/`remove` on a gun with a mode you didn't account for — use `set` (see §3) |
| Gun stuck in a removed mode | Shouldn't happen; report it with the gun and attachment ids |

**Log check:** on startup you should see
`[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states'`.
If that line is missing, the `fire_mode` block won't be read at all.

---

## 6. Full example — burst kit

Adds burst to a rifle without disturbing its existing modes:

```json
{
  "weight": 0.1,
  "ads_addend": 0.02,

  "fire_mode": {
    "add": ["burst"]
  }
}
```

On a `["semi", "auto"]` rifle this yields `["semi", "auto", "burst"]`.
