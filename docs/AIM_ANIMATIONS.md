# ADS Animations — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Make an attachment play its **own animation when the player aims down sights** — a flip-up sight rising, a
magnifier swinging in, a scope's lens cover opening, a bipod folding.

> **Why this needs the addon (not Lua).** A gun's Lua animation drives the *gun* model's bones. A mounted
> attachment is a **separate model** the gun's Lua can't reach. This feature animates the attachment's *own*
> bones when you aim.

---

## 1. Two styles

Pick one per attachment, in its **data** file (`data/<ns>/data/attachments/<name>_data.json`).

### Follow the aim

```json
{
  "aim_animation": {
    "animation_file": "mypack:my_sight",
    "follow": "flip_up"
  }
}
```

The clip is tied to **how far the gun has aimed in**. At half-aimed it sits halfway through the clip, and at
fully aimed it sits on the last frame. Letting go of ADS plays it back from wherever it got to. It always
matches the gun's ADS speed and never snaps, whatever the gun or attachments do to aim time. Its length in
seconds doesn't matter: only the start and end poses do.

**Use it for** anything that should move *with* the sight coming up: flip-ups, magnifiers, covers.

### Aim-in / aim-out clips

```json
{
  "aim_animation": {
    "animation_file": "mypack:my_sight",
    "aim_in": "deploy",
    "aim_out": "stow"
  }
}
```

`aim_in` plays when ADS starts and `aim_out` when it ends, each at its **own** speed, like a normal clip. Both
hold on their last frame.

- **`aim_out` is optional.** Leave it out and releasing ADS plays `aim_in` **in reverse** from wherever it got
  to (aiming again mid-way carries on forward).
- **Use it for** motions with their own timing, or a different way back: a cover that snaps open but slides
  closed, a part with a bounce.

If both `follow` and `aim_in` are given, `follow` wins.

**Common fields:**

- **`animation_file`** — the attachment's bedrock animation file
  (`assets/<ns>/animations/<path>.animation.json`), exactly as for a toggleable attachment.
- The clip names must exist in that file.

---

## 2. Behaviour

- **Rest pose.** Author the clip's **first frame as the un-aimed pose**. With `follow`, 0% aimed is the first
  frame, so that's how the attachment looks when you're not aiming.
- **Rail-mounted too.** Works on an attachment in any slot, including one mounted on a
  [rail](CANTED_RAIL_SYSTEM.md) (the classic flip-up sight on a side rail).
- **Any ADS.** It plays whenever the gun aims, whichever optic you're looking through.
- **First-person.** Like [fire-reaction animations](FIRE_REACTION_ANIMATIONS.md), it plays for your own gun in
  first person.

---

## 3. Combining with toggle states and fire animations

ADS animations run on their **own track**, so they layer with [toggle states](TOGGLEABLE_ATTACHMENTS.md) and
[fire-reaction animations](FIRE_REACTION_ANIMATIONS.md) on the same attachment, as long as the clips move
**different bones**. If two clips animate the same bone, the ADS clip wins for that bone.

When combining, point every block at the **same** `animation_file` (one file, all clips): there's a single
animation controller per attachment.

> The model is shared by every copy of that attachment, same as toggle states: two guns carrying the same
> attachment show the same pose.

---

## 4. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Nothing animates on ADS | `aim_animation` missing or misspelled, or a wrong `animation_file`; check the log |
| Log: "'aim_animation' needs an animation_file and either 'follow' or 'aim_in'" | The block is missing one of those fields |
| Log: "no aim_animation clip …" | The named clip isn't in that animation file |
| The part jumps at the start of ADS | The clip's first frame isn't the un-aimed pose (§2) |
| A fire or state animation looks overridden while aiming | Both clips animate the same bone (§3) |

**Log check:** on startup the modifier must be registered:

```
[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states', 'shader', 'rails', 'underbarrel_data', 'conversion', 'fire_animation', 'aim_animation'.
```

If `aim_animation` isn't in that list, the block in your attachment files won't be read.
