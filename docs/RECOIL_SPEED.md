# Recoil Speed — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Lets an attachment change **how fast** the gun's camera recoil plays — a heavier stock or a muzzle brake that
makes the kick roll out slower, or a light setup that snaps faster. TaC:Z's own `recoil` modifier only changes
how **strong** the kick is; this changes its **speed**.

---

## 1. Authoring

In the attachment's **data** file (`data/<ns>/data/attachments/<name>_data.json`):

```json
{
  "weight": 0.2,
  "recoil_speed": 0.75
}
```

`recoil_speed` is a multiplier on the speed the gun's `recoil` keyframes (its `pitch` / `yaw` curves) play at:

| Value | Effect |
|---|---|
| `1` | As authored (no change) |
| `0.5` | Half speed — the same kick takes twice as long |
| `0.75` | A bit slower |
| `2` | Twice as fast — the kick is over in half the time |

The kick's **shape and height stay the same**; only its timing stretches or squeezes. Combine it with TaC:Z's
`recoil` modifier to change strength too.

---

## 2. Behaviour

- **Stacks by multiplying.** Two attachments with `0.8` and `0.5` give `0.4`. The result is kept between
  `0.05` and `20`.
- **Taken at the shot.** The speed of a kick is fixed when that round fires, so swapping attachments mid-kick
  doesn't change the one already playing.
- **Camera recoil only.** It times the camera kick from the gun's `recoil` data. The gun model's own `shoot`
  animation is untouched.
- **Underbarrels too.** An underbarrel's kick uses the `recoil_speed` of the attachments on the
  [underbarrel](UNDERBARREL_GUNS.md) itself (e.g. a muzzle device on the launcher).
- **Tooltip.** The attachment's tooltip shows `Recoil speed ×0.75`.

---

## 3. Troubleshooting

| Symptom | Likely cause |
|---|---|
| No change | The attachment isn't installed in a slot that gun allows, or the gun has no `recoil` keyframes |
| Log: "'recoil_speed' must be a positive number" | `0`, a negative number or a string was used |

**Log check:** on startup the modifier must be registered:

```
[RenaissanceLib] Registered attachment modifiers 'fire_mode', 'states', 'shader', 'rails', 'underbarrel_data', 'conversion', 'fire_animation', 'aim_animation', 'recoil_speed'.
```
