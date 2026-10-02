# Hierarchical Attachments

A RenaissanceLib feature (TaC:Z addon, Forge 1.20.1) that lets an **attachment declare extra mount points**
("rails"). Those mounts appear as extra sub-slots under the host attachment's slot in the refit screen, so a
player can mount canted irons, a piggyback red-dot, a top-rail optic, a laser, etc. — and (for optics) cycle
and aim through them with the normal zoom key.

The host can be **any** attachment slot: a **scope** hosting optics, or a **grip/handguard** (or any other
slot) hosting a laser. Each rail-hosting attachment gets its own row in the refit screen, anchored under its
own slot marker.

> **The one firm rule — optics only nest on scopes.** A rail slot that accepts `scope`/`sight` optics is only
> honoured on a **scope-type** host. A non-scope host (grip, handguard, …) can only accept non-optic mounts
> (lasers, etc.) — it will never offer or accept a scope/sight, even with `allow: "any"`. This keeps sights
> from being hung off grips/lasers while still allowing laser rails on a handguard.

Status: **complete and shipped** for the common cases (one mounted optic; a laser rail). A mounted laser
draws a **functional beam** (and is recolourable). One rendering limitation remains with **two** mounted
magnified optics — see [The Wall](#the-wall-second-optic-in-the-lens).

---

## What it does

- A rail-capable scope declares a `rails` block naming one or more mount **nodes** in its model.
- Those nodes show up as interactive **sub-slots** under the scope slot (install / remove / swap,
  with TaC:Z-matching sounds, cooldown, and tooltips).
- A mounted sight **renders on the gun** at its rail node.
- The zoom key cycles a **combined view cycle**: the scope's views, then each mounted sight's views,
  then back — with smoothed aim alignment, per-optic FOV, and variable (scroll) zoom.
- The gun **barrel is clipped** out of whatever optic you're aiming through (like a normal scope).

---

## Authoring (JSON)

In the host attachment's **data** file (a scope, a grip, a handguard — any slot):

```jsonc
"rails": {
  "aim_self": false,          // does the mount's OWN optic view take part in the zoom cycle?
  "slots": [
    { "node": "canted_1", "type": "canted", "allow": "sight",
      "allow_attachments": ["#tacz:pistol_sight"] },   // optional: only these attachments
    { "node": "laser_pos", "type": "laser_mount", "allow": "laser" }
  ]
}
```

- **`node`** must be a real bone in the attachment's `.geo.json` (an empty locator bone is fine). It's
  where the mount is pinned — and, for an optic, where the camera aims when you look through it. **A wrong
  or missing bone name is the usual reason a mount installs but shows nothing.**
- **`type`** is a **cosmetic label** for the sub-slot in the refit screen (looks up
  `tooltip.renaissance_lib.rail.<type>`, else capitalises the string). It is *not* a TaC:Z type — write
  whatever reads well (`canted`, `top`, `side`, `laser_mount`, …).
- **`allow`** restricts what may be mounted: `"scope"`, `"sight"`, `"laser"`, `"muzzle"`, `"grip"`,
  `"stock"`, `"extended_mag"`, or `"any"` (default). A single string or an array. These *are* real
  attachment types. Remember the firm rule: `scope`/`sight` only work on a **scope host**.
- **`allow_attachments`** (optional) narrows a slot to specific attachments, written like a gun's
  allow list: attachment ids (`"tacz:sight_rmr_dot"`) and `#`-prefixed TaC:Z attachment tags
  (`"#tacz:pistol_sight"`, from `data/<ns>/tacz_tags/attachments/<name>.json`; tags can include other tags).
  A single string or an array. An attachment must pass **both** `allow` and `allow_attachments`. Leave it out
  to accept anything of the allowed type. Checked on the server too, so it can't be bypassed.
- **`aim_self`** (default `false`): a bare optic mount (e.g. a canted rail with no optic of its own) leaves
  this `false` so the cycle walks only the mounted sights. A real scope that *also* carries a rail sets it
  `true` so its own view cycles alongside the mounted sights. (Optic hosts only.)

A mounted item is any normal attachment of an allowed type (and, if set, listed in `allow_attachments`). It
renders its whole model at the rail node.

### Lasers on a rail

A `laser`-slot attachment mounts on any rail whose `allow` admits `laser` (typically a grip/handguard rail).
It **renders its model and projects a working beam** — the beam comes from the laser model's own beam bones,
exactly as when a laser is in the gun's native laser slot. Focus the slot in the refit screen and the
**hue/saturation sliders** (bottom-left) recolour the beam live. Author the mount `node` so the laser points
forward; the beam follows the model.

---

## How it works (build stages)

| Stage | What | Key pieces |
|-------|------|-----------|
| 1–2 | Slots render + interactive install/remove/swap, sounds, cooldown, tooltips | `RailRefitOverlay`, `ClientMessageSetRailSight`, `RailStorage` (gun NBT), `RailsModifier` / `ScopeRails` |
| 3 | Mounted sight renders on the gun at its rail node | `RailRenderRegistrar` (called from `ClientAttachmentIndexMixin` at model load) hangs a `RailSightRenderer` on each rail node; it reads the sight from `RailStorage`, the live gun via `IRailGunItemAccessor`, and draws via TaC:Z `AttachmentRender.renderAttachment` |
| 4 | Combined zoom cycle + aim/FOV/variable-zoom | `ActiveOptic` (the one source of truth for "which optic + which view"), `FirstPersonRenderGunEventMixin`, `VariableZoom` |
| — | Gun barrel clipped inside the active optic's ocular | `BedrockGunModelMixin` re-enables TaC:Z's ocular stencil test before the gun body draws |

### The combined cycle (`ActiveOptic`)

TaC:Z keeps a single `ZoomNumber` on the scope and cycles the scope's views with
`zoomNumber % count`. We widen that into: **scope views → rail-sight-0 views → rail-sight-1 views →
… → back**. `ActiveOptic.resolve(gun)` maps `zoomNumber` onto that combined cycle and reports the
active optic (scope or a specific rail sight) and its local view index. Everything (aim, FOV,
variable zoom) reads from it so they agree.

> Gotcha: each optic's cycle length is `max(zoom, views, viewsFov array lengths)`, **not**
> `views.length` — TaC:Z indexes each of those arrays by its own modulo, so a `zoom: [4, 8]` with a
> single view still has two steps.

### Aim routing (`FirstPersonRenderGunEventMixin`)

- `@Redirect` on `getScopeViewPath` → when a rail sight is active, returns the nested path
  `canted_N (in the scope model) + the sight's own scope_view (in the sight model)`, so the camera
  looks straight down the canted optic. `getPositioningNodeInverse` walks any flat bone list, so the
  nesting "just works."
- `@Redirect` on the aiming `getPositioningNodeInverse` → we **ease the aim matrix ourselves**
  (`RailAim.easedAimMatrix`), because TaC:Z's view-transition smoother keys on a `viewIndex` that is
  constant for a single-view mount, so it would snap.

### FOV + variable zoom (`VariableZoom`)

Generalized to the **active optic**: the scroll-adjustable range, the per-view FOV, and the ADS ramp
all resolve from `ActiveOptic`. A magnified rail sight applies its own zoom/FOV; a plain red dot
doesn't (you look over the gun).

---

## Key files

```
attachment/
  RailsModifier.java        // parses the "rails" JSON block (slots + aim_self)
  ScopeRails.java           // finds the installed scope's rail slots / spec
  RailStorage.java          // mounted optics stored in the scope attachment's own NBT (travels with the rail)
client/
  ActiveOptic.java          // combined-cycle resolver (source of truth)
  RailAim.java              // aim-path building, matrix easing, masksOptic helpers
  RailSightRenderer.java    // draws a mounted sight at its rail node (functional renderer)
  RailRenderRegistrar.java  // hangs RailSightRenderer on the scope model's rail nodes
  VariableZoom.java         // continuous zoom, generalized to the active optic
  gui/RailRefitOverlay.java // the sub-slot UI (render + click handling + tooltips)
mixin/client/
  ClientAttachmentIndexMixin.java   // registers rail renderers at model load
  FirstPersonRenderGunEventMixin.java // aim routing + matrix easing
  BedrockGunModelMixin.java         // gun-barrel ocular clip
  BedrockAttachmentModelMixin.java  // exposes the model's live currentGunItem
network/
  ClientMessageSetRailSight.java    // server-authoritative install/remove
```

Test pack: `run/tacz/gucci_vuitton_attachment` — a `canted_rail` attachment with nodes
`canted_1` / `canted_2`.

---

## The Wall: second optic in the lens

**Symptom:** with **two** mounted optics, when you aim through one, the *other* one (and the mount)
appears **inside** the active optic's ocular circle. We want it clipped out of the lens (gone inside,
visible around) — the way the gun barrel already is.

### Why it's hard

Building this on top of TaC:Z's scope renderer runs into three colliding facts:

1. **Geometry lives in hidden nodes.** A TaC:Z optic keeps its visible body in nodes (`scope_body`,
   `ocular*`, `division`, `scope_view`, …) that are **hidden** (`visible = false`) and drawn *only*
   by its own scope/sight pass via `renderTempPart`. A plain render (`super.render`) of the model
   draws **nothing**.
2. **That pass clears the shared stencil.** Every `renderScope` / `renderSight` does
   `clearStencil` + clear on the shared stencil buffer, wiping whatever ocular was there.
3. **The backdrop is opaque near-depth.** The optic's black surround draws as solid geometry near the
   camera, so whichever optic is drawn *first* depth-occludes the one drawn after it.

To clip optic B out of optic A's lens, B must render **after** A wrote its ocular, **under** A's
stencil, **without** clearing it, and **without** A's backdrop hiding it. Satisfying all four at once
requires re-implementing TaC:Z's private multi-pass scope pipeline for the nested case.

### What we tried (all failed, each differently)

| Attempt | Result |
|--------|--------|
| Masked pre-pass: render active optic full first, then others clipped | Others vanished |
| Reorder: render others normally, active optic last (clean ocular) | The *active* one vanished (others' near-depth backdrops occluded it) |
| Body-only render (force scope/sight flags off → `super.render`) | Others vanished — their body is in hidden nodes, so nothing drew |
| Disable depth test for the others (isolate depth vs stencil) | No change → proved it's **not** depth occlusion |
| `forceAllVisible` (un-hide all nodes via a `modelMap` accessor) | Blanked the **whole scope slot** — cause never runtime-diagnosed |
| `forceAllVisible` but skip lens/reticle nodes (`ocular*`,`division*`,`scope_view*`) | Still blanked everything |
| Delegate-level clip in `RailSightRenderer` (enable stencil `EQUAL 0` against the active ocular already in the buffer, render body-only) | Vanished everything |

The consistent theme: cleanly drawing a *non-active* optic's body (visible, clipped, no side effects)
is defeated by fact #1, and the workaround for #1 (`forceAllVisible`) blanked the slot for reasons
that couldn't be verified without stepping through the render live.

### What DOES work

- **One mounted optic** (the intended canted-sight case): look-through, aim easing, per-optic FOV,
  and barrel clipping all correct.
- **Red-dot / non-magnified sights** never needed lens clipping (you look over the gun).
- Only **two magnified optics on the same gun** hit the overlap — a narrow cosmetic case.

### If it's ever revisited

Do **not** guess blindly again. The realistic path is a **live frame capture** (RenderDoc) or a
debugger stepping through `BedrockAttachmentModel.renderScope` / the delegate drain, to see exactly
what blanks the slot when a second optic's body is forced visible. Only then attempt the stencil-only
ocular pre-pass. Until then, ship the single-optic behavior.
