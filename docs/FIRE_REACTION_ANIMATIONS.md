# Fire-Reaction Animations — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Make an attachment play its **own animation when the host gun fires** — a reciprocating charging handle,
an ejection-port cover flipping, suppressor baffles shifting, a compensator venting. The clip plays once
per shot (so full-auto cycles it every round).

> **Why this needs the addon (not Lua).** A gun's Lua animation drives the *gun* model's bones. A mounted
> attachment is a **separate model** the gun's Lua can't reach — it can only recoil rigidly with its mount
> point. This feature animates the attachment's *own* bones on the shot.

---

## 1. Authoring

In the attachment's **data** file, add a `fire_animation` block:

`data/<ns>/data/attachments/<name>_data.json`

```json
{
  "weight": 0.3,
  "fire_animation": {
    "animation_file": "mypack:my_attachment",
    "animation": "shoot"
  }
}
```

- **`animation_file`** — the attachment's bedrock animation file
  (`assets/<ns>/animations/<path>.animation.json`), exactly as for a toggleable attachment.
- **`animation`** — the clip in that file to play on each shot.

You author the `shoot` clip by animating the attachment model's bones, the same way you'd author a
toggle clip. That's the whole setup — install the attachment and fire.

---

## 2. Behaviour

- **Per round.** It triggers on TaC:Z's per-shot event, so semi fires it once and full-auto cycles it
  each round. Author the clip's length to suit your fire rate — a clip longer than the interval simply
  restarts on the next round (a continuously reciprocating bolt); a shorter one plays out and rests
  between shots.
- **Returns to rest.** The clip plays once and the attachment settles back to its rest pose, so author
  it to start and end at rest (e.g. handle forward → back → forward).
- **First-person, for now.** It plays for your own gun in first person. (The machinery isn't limited to
  first person — third-person support is a small follow-up if you want it.)
- **Any slot.** Works on whichever slot the attachment occupies — muzzle, grip, a side-rail device, etc.

---

## 3. Combining with toggle states

(ADS animations run on their own track and layer with both — see [ADS Animations](AIM_ANIMATIONS.md).)

A fire animation and **toggleable** states (see [Toggleable Attachments](TOGGLEABLE_ATTACHMENTS.md)) on
the *same* attachment share one animation track — a shot's clip plays over the current state. If you need
both behaviours at once, split them across two attachments. Most fire-reaction parts (charging handles,
covers) aren't toggleable, so this rarely comes up.

If an attachment uses both a `states` block and a `fire_animation` block, point them at the **same**
`animation_file` (one file, all clips) — they share a single controller per attachment.

---

## 4. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Nothing animates on shot | `fire_animation` missing/misspelled, or the `animation`/`animation_file` path is wrong — check the log |
| Log warns "no fire_animation clip …" | The named clip isn't in that animation file |
| Plays in first person but you expected third | Third-person isn't enabled yet (see §2) |
| A toggle state stops working once you add fire | They share a track — split into two attachments (§3) |

**Log check:** on startup the fire-animation modifier must be registered:

```
[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states', 'shader', 'rails', 'underbarrel_data', 'conversion', 'fire_animation', 'aim_animation'.
```

If `fire_animation` isn't in that list, the block in your attachment files won't be read.
