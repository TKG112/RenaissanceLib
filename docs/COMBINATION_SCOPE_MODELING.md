# Combination Scopes (Sight + Magnifier) — Modeling Guide

**For TaC:Z gun packs (Forge 1.20.1).** How to model an optic that is a **1× sight you can
see the barrel through** *and* a **magnified view that hides the barrel** — e.g. an EOTech
with a G33/G45 flip magnifier.

This is a **native TaC:Z feature** (it calls them "combination scopes"). No mod code is
needed — it's entirely modeling + the display/data JSON. This guide is written for someone
who doesn't model much: it tells you exactly which groups to make and what to name them.

> This pairs with two other guides:
> - **[TOGGLEABLE_ATTACHMENTS.md](TOGGLEABLE_ATTACHMENTS.md)** — the flip-aside animation.
> - **[SCOPE_SHADERS.md](SCOPE_SHADERS.md)** — putting a night-vision/thermal effect in the
>   magnified view.

---

## 1. The idea in one picture

TaC:Z builds a scope view by naming special **groups** (called "bones" in Blockbench) in the
model. Two kinds matter here:

- A **sight ocular** = a 1× see-through window. You see your gun, hand, and the world
  through it, with a reticle on top. This is your EOTech.
- A **scope ocular** = a magnified porthole. As you aim in, a circle grows and shows a
  zoomed view with a black eyepiece surround — and crucially the **barrel is hidden**. This
  is your magnifier.

You put **both** on one attachment. TaC:Z renders the EOTech as the see-through sight and the
magnifier as the magnified scope, at the same time.

---

## 2. The naming reference

These are the group names TaC:Z looks for. **Names are exact and case-sensitive.** The
*shape* and *position* of a group is up to you; only the name makes it special.

| Group name | What it is | Notes |
|---|---|---|
| `scope_view` | An empty locator marking where your eye lines up (the 1× / sight view) | Position only; geometry optional |
| `scope_view_2` | Eye locator for the **magnified** view | Add for the magnifier |
| `ocular` | The **sight** glass (1× see-through) | Your EOTech window |
| `ocular_scope_2` | The **scope** glass (magnified, hides barrel) | Your magnifier lens |
| `division` | The reticle drawn inside `ocular` | Your EOTech dot/holo |
| `division_2` | The reticle drawn inside `ocular_scope_2` | The magnified view's dot (optional) |
| `scope_body` | The opaque body of the optic | Everything solid |
| `ocular_ring` | An outer ring around the eyepiece | Optional trim |

Other valid ocular names, if you need them: `ocular_sight` (same as `ocular`, just explicit)
and higher numbers `ocular_3`, `ocular_scope_4`, etc.

### ⚠️ The one rule people get wrong

**All ocular groups share a single numbering, no matter their type.** A plain `ocular` counts
as number **1**. So your magnifier cannot be `ocular_scope` (that's *also* number 1 and would
collide with the EOTech) — it must be **`ocular_scope_2`**.

The reticles follow the same order: `division` pairs with ocular #1 (the EOTech),
`division_2` pairs with ocular #2 (the magnifier).

---

## 3. Applied to your EOTech + G33 model

### What you have now

```
scope_view          ← eye locator (sight)
division            ← EOTech reticle
  crosshair_illuminated
ocular              ← EOTech glass  (sight, #1)   ✅ already correct
scope_body          ← solid body
  EOTech_EXPS3      ← EOTech housing
  EOTech_STS        ← magnifier mount
    STS_base
    STS_flip        ← the flip hinge
      EOTech_G45    ← magnifier body  ← solid, no glass yet
        tube
```

The EOTech is already a proper sight ocular. The magnifier is just solid geometry. You add a
scope ocular to it.

### What to add

**1. `ocular_scope_2` — the magnifier glass.**
Make a thin, flat group (a plane, or a very thin box) sitting on the **rear lens of the
magnifier**, facing your eye — roughly filling the round glass area.

Now it has to **flip with the magnifier**, or the glass floats in mid-air while the magnifier
swings away. Getting this right is the fiddliest part of the whole build, and it hinges on
one fact that isn't obvious:

> ### 🔑 The see-through hole is drawn at the ocular's *pivot*, not at its glass
>
> TaC:Z centres the growing aperture circle on the ocular group's **pivot point**. So the
> pivot has to sit **in the middle of the glass**, or the hole is carved somewhere off to the
> side and the lens renders **solid black**. (This is why a normal scope like Vudu puts its
> ocular pivot dead-centre on the lens.)
>
> That collides with how you'd normally flip a part. A bone rotates around **its own pivot**,
> so to swing the glass on the magnifier's hinge you'd want the pivot **at the hinge joint** —
> but that's the wrong place for the aperture. **One bone can't have its pivot in two places.**

The way out is to let the flip come from a **parent** while the ocular keeps its own pivot on
the glass:

> ### ✅ Do this for the scope ocular
>
> 1. **Parent** `ocular_scope_2` under the magnifier body (`EOTech_G45`, which is under the
>    hinge `STS_flip`). It inherits the flip for free.
> 2. Give it **no rotation of its own** (`[0,0,0]`). The parent supplies the `-90`; adding
>    another here would stack to `-180` and point the glass backwards.
> 3. Put its **pivot at the centre of the glass** — this is now free to be wherever you want,
>    because a bone with no rotation ignores its pivot for geometry; the pivot *only* places
>    the aperture.
> 4. **Do not** put `ocular_scope_2` in the animation file. It flips by inheritance now;
>    animating it too would double-rotate.
>
> Because the geometry doesn't move when you re-parent (the parent's `-90` around the same
> joint reproduces the rotation you removed), the glass stays exactly where it was — only the
> aperture snaps to the pivot you set.

> ### ❌ What doesn't work (and looks like it should)
>
> Giving `ocular_scope_2` the hinge's pivot and animating it in parallel with `STS_flip` *does*
> make it swing correctly — but it forces the pivot onto the joint, so the aperture is drawn at
> the joint and the lens is **black when deployed**. This is the trap; it's why the scope
> ocular specifically must use the parent method above.
>
> (Parallel animation is still fine for **decorative** parts that have no aperture — e.g. an
> `ocular_ring` trim can swing along that way.)

**2. `scope_view_2` — the magnified eye position (recommended).**
Duplicate `scope_view`, rename the copy to `scope_view_2`, and move it back to where your eye
would sit when looking through the magnifier. You'll fine-tune this in-game.

**3. `division_2` — the magnified reticle (optional).**
If you want a dot in the magnified view, make a small reticle group named `division_2`. Skip
it and the magnified porthole just shows the world with no dot.

That's the whole model change. You do **not** rename or move the existing `ocular`,
`division`, or `scope_view`.

---

## 4. The display and data files

Two flags flip it from "sight" to "combination scope". In the **display** file
(`assets/<ns>/display/attachments/<name>_display.json`):

```json
{
  "scope": true,
  "sight": true,

  "zoom":  [1.0, 3.0],
  "views": [1,   2  ]
}
```

- `scope:true` + `sight:true` together is what selects the combination-scope renderer.
- `zoom` / `views` map each zoom step to a view locator. Here step 0 is 1× through view node
  1 (the EOTech), step 1 is 3× through view node 2 (the magnifier). `views` numbers are
  **1-based** and point at `scope_view` (1) and `scope_view_2` (2).

Everything else in the display file (model, texture, lod) stays as it is.

The data file (`data/<ns>/data/attachments/<name>_data.json`) doesn't need changes for the
optic itself — that's where the flip animation and stats live (see the other guides).

### The flip animation targets the *hinge*, not the glass

Once `ocular_scope_2` is parented under the magnifier (§3), the **only** bone the flip
animation drives is the hinge, `STS_flip` — the glass, tube, and porthole all come along by
inheritance. Decorative trim that isn't parented (e.g. an `ocular_ring`) can be added to the
same clip alongside `STS_flip` with identical keyframes.

The flip axis is model-specific and you'll dial it in visually; on this EOTech it turned out
to be **Z** (`[0, 0, 90]`), not the Y you might first reach for. Whatever axis and sign you
land on, keep every bone in the clip on the **same** values so nothing drifts apart.

### If you're converting a static two-item magnifier

Many packs ship the magnifier as **two separate attachment items** — one "magnifier off"
(1×) and one "magnifier on" (magnified) — with crafting recipes to swap between them. That's
the old workaround for having no animated magnifier.

With this feature you only need **one** item. Point it at the functional (magnified) model,
add the combination-scope display flags and the `states` block, and it does both — flip in
for magnified, flip out for 1×. Then you can delete the second item, its display, and the
swap recipes, and drop the stray entry from the attachment tags. (If the two items shared one
model, editing that model changes both, so consolidate onto one item to avoid surprises.)

---

## 5. How the pieces fit together

Once it's a combination scope:

- **Aim through the EOTech (1×):** you see barrel, hand, world, and the EOTech dot. The
  `ocular` sight window is a plain see-through hole.
- **Aim through the magnifier (magnified):** a circle grows as you ADS, showing the zoomed
  view with a black surround; the **barrel is hidden**. That's the `ocular_scope_2` porthole.
- **Flip the magnifier aside** (RenaissanceLib toggle key): the magnifier and its
  `ocular_scope_2` swing away together, leaving just the 1× EOTech.

### Linking the flip and the zoom (both keys, always in sync)

You want the magnifier flip and the 1×/magnified switch to be the same thing, no matter
which key the player presses. Give each toggle state a `zoom_index` in the data file's
`states` block — it points at an index in the display file's `zoom` array:

```json
"states": {
  "animation_file": "asos:sight_eotech_hhs8",
  "cycle": ["aligned", "aside"],
  "aligned": { "animation": "flip_in",  "zoom_index": 0 },
  "aside":   { "animation": "flip_out", "zoom_index": 1 }
}
```

This links them **both directions**:
- **The flip key** sets the state, which sets the zoom.
- **TaC:Z's zoom key** changes the zoom, and RenaissanceLib reacts by flipping the magnifier
  to match. So the two controls can never drift apart.

**Don't want the zoom key to flip the magnifier?** Add `"zoom_key_toggle": false` to the
`states` block. Then only the dedicated flip key controls the magnifier; TaC:Z's zoom key is
left alone. Defaults to `true`.

```json
"states": {
  "animation_file": "asos:sight_eotech_hhs8",
  "zoom_key_toggle": false,
  "cycle": ["stowed", "deployed"],
  "stowed":   { "animation": "stow",   "zoom_index": 0 },
  "deployed": { "animation": "deploy", "zoom_index": 1 }
}
```

> **Ordering rule — put your model's rest pose at zoom index 0.** A scope's zoom starts at
> index 0 when equipped, and your model's *rest pose* (all flip bones at rotation 0) is
> whatever you modelled. Those two must agree, or the optic looks one way and zooms the
> other on first aim.
>
> This magnifier is modelled **in-line** (rest = aligned), so index 0 is the magnified view
> and `aligned` uses `zoom_index: 0`:
>
> ```json
> "zoom":  [3.6, 1.2],
> "views": [2,   1  ]
> ```
>
> If you instead modelled the magnifier resting **flipped aside**, you'd put the 1× view at
> index 0 and give `aside` the `zoom_index: 0`.

### If you want a shader (night vision etc.) in the magnified view

This is where it gets nice: RenaissanceLib's shader applies to the **scope** ocular's
magnified porthole automatically, and **not** to the 1× EOTech view — because the effect
keys off the magnified aperture specifically. So a shader "just works" on the magnifier
without leaking onto the barrel you see through the EOTech.

Declare it per view so it only lands on the magnified index (see
[SCOPE_SHADERS.md §6](SCOPE_SHADERS.md)):

```json
"shader": {
  "1": "mypack:nightvision"
}
```

(View index `1` = the magnified view. Index `0`, the 1× EOTech, is left unshaded.)

---

## 6. Test checklist

1. In-game, install the optic and ADS. Do you get the EOTech at 1× (barrel visible)?
2. Cycle zoom (TaC:Z's zoom key). Does it switch to the magnified porthole with the barrel
   hidden?
3. Does the magnified circle grow smoothly as you aim in, and sit centered on the magnifier
   glass? If it's off-center, move `ocular_scope_2` so its center is the lens center.
4. Flip the magnifier aside — does its porthole go with it?
5. If using a shader, does it appear only in the magnified view?

### Common mistakes

| Symptom | Cause |
|---|---|
| Magnifier isn't see-through | The glass group isn't named `ocular_scope_2`, or is named `ocular_scope` (collides with the EOTech as #1) |
| Both views look 1× / no magnified porthole | Display is missing `scope:true`, or `views` doesn't point at a `scope_view_2` |
| **Magnifier lens is solid black when deployed** | `ocular_scope_2`'s **pivot** is at the hinge joint, not on the glass. The aperture is drawn at the pivot — move it to the centre of the lens (see §3) |
| Magnified circle is off to the side | Same cause — `ocular_scope_2`'s pivot isn't centred on the lens |
| Glass doesn't move when flipped | `ocular_scope_2` isn't parented under the magnifier (`STS_flip` → `EOTech_G45`). Parent it, no own rotation |
| Glass points the wrong way / double-rotates | `ocular_scope_2` is parented under the hinge **and** still has its own `-90` rotation (or is also in the animation). It inherits the flip — remove its own rotation and its animation channel |
| Reticle missing in magnified view | No `division_2` group (it's optional) |
