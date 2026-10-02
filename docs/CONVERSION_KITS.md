# Conversion Kits — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

A **conversion kit** is an attachment that swaps a weapon into a *different gun entirely*. Install it and
the whole weapon — stats, model, animations, ammo, fire modes, and which attachments it accepts — becomes
another, fully-defined TaC:Z gun that you already ship. Remove it and the original gun comes back, untouched.

Think caliber conversions (5.56 → .300 BLK), platform kits (carbine → SBR), or a "restricted" variant that
locks out most attachments. The kit rides its own slot in the refit screen, **under the mag column**.

---

## 1. How it works

The kit **references an existing gun** by id. While the kit is installed, the weapon *resolves as* that
referenced gun in every respect — there's no partial stat patching. That one redirect is the whole feature:

- **Stats, model, display, animations, ammo** — all come from the referenced ("converted") gun.
- **Attachment locking is automatic.** The converted gun brings its *own* `allow_attachments`, so whatever
  it does or doesn't permit is exactly what the weapon now permits. A locked-down conversion is just a
  converted gun with a short `allow_attachments` list.

Because it's a reference (not a copy), the base gun's identity is preserved underneath — removing the kit
restores it completely.

---

## 2. The two things you author

### a) The kit's `conversion` block

A conversion kit is an ordinary TaC:Z attachment. In its **data** file, add a `conversion` block naming the
gun to convert into:

`data/<ns>/data/attachments/<kit_name>_data.json`

```json
{
  "weight": 0.5,
  "conversion": {
    "converted_gun": "mypack:ak12_300blk"
  }
}
```

- **`converted_gun`** is the id of any existing, fully-defined gun (yours or another pack's). If the id
  doesn't resolve to a loaded gun, the kit simply does nothing (the weapon stays as-is) — no crash.

Give the kit a normal **display** file too (model, texture, icon) so it looks like an item in the world and
the refit slot. Its attachment *type* doesn't matter — the kit rides a dedicated virtual slot, and it's
blocked from being dropped into any native slot.

### b) Let the base gun accept the kit

A gun opts into a kit through the **same `allow_attachments` gate as any attachment** — list the kit (by id
or a tag) in the base gun's data file:

`data/<ns>/data/guns/<base_gun>_data.json`

```json
{
  "allow_attachments": [
    "mypack:ak12_300blk_kit",
    "#mypack:conversions"
  ]
}
```

Only guns that list the kit will **show the conversion slot** and accept it. A gun that never lists any kit
never grows the slot — nothing changes for weapons you don't set up.

> The **converted** gun (`mypack:ak12_300blk`) is a completely normal gun with its own data/display/model.
> If you want the *look* to stay the same and only the numbers to change, point `converted_gun` at a gun
> that reuses the base model but ships different stats.

---

## 3. In-game

| Action | How |
|---|---|
| Open the slot | Refit screen (default **Z**). A gold-outlined slot sits **under the mag column**, shown only when a compatible kit is installed or in your inventory. |
| Install | Click the slot → pick a kit from the list that appears. The weapon becomes the converted gun. |
| Remove | Click the slot (now showing the kit) → click the unload button. The base gun returns. |

Installing and removing use TaC:Z's own refit sounds and a short cooldown, like the other slots.

---

## 4. What happens on install

Because the weapon fully becomes the converted gun, the addon tidies up so it starts in a clean state:

- **Incompatible attachments are returned to you.** Any attachment in a native slot that the *converted*
  gun no longer allows — by slot type or by its `allow_attachments` tags — is popped back into your
  inventory. (Attachments the converted gun still allows stay put.)
- **The magazine is emptied.** Ammo is cleared on both install and removal, so a caliber change never leaves
  rounds of the wrong type loaded.
- **The kit travels with the weapon.** It's stored on the gun; removing it hands the kit item back.

---

## 5. Compatibility & authority

- **Which guns accept which kit** is entirely the base gun's `allow_attachments` (§2b) — the idiomatic TaC:Z
  mechanism. Tag your kits and list the tag on every compatible gun to manage families of conversions.
- **Server-authoritative.** Installing/removing is validated and applied on the server; a client can't force
  a conversion the base gun's `allow_attachments` doesn't permit.

---

## 6. Troubleshooting

| Symptom | Likely cause |
|---|---|
| No conversion slot appears | The base gun doesn't list the kit in `allow_attachments` (by id or tag), or you aren't carrying a compatible kit |
| Kit installs but nothing changes | `converted_gun` doesn't resolve to a loaded gun — check the id and that the target gun is loaded |
| Kit shows in a normal slot's picker | It shouldn't install there (blocked); give the kit an attachment type the base gun doesn't use natively to keep it out of that picker entirely |
| Attachments vanished after converting | Expected — the converted gun disallows them; they were returned to your inventory |
| Magazine empty after converting | Expected — ammo is cleared so a caliber change starts clean |

**Log check:** on startup the conversion modifier must be registered:

```
[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states', 'shader', 'rails', 'underbarrel_data', 'conversion', 'fire_animation', 'aim_animation', 'recoil_speed'.
```

If `conversion` isn't in that list, the `conversion` block in your kit files won't be read.
