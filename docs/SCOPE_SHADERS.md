# Scope Shaders — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Attaches a **post-processing shader** to a scope so the effect renders **only inside the
scope's glass** while aiming — night vision, colour grading, pixelation, scan lines.

Works with and without a shaderpack (Iris/Oculus).

---

## 1. Quick start

Declare the `shader` key in **both** of the scope's files — same value in each.

**1. The display file** (client-side):
`assets/<your_namespace>/display/attachments/<scope_name>_display.json`

```json
{
  "model": "mypack:attachment/scope_pso1",
  "texture": "mypack:attachment/scope_pso1",
  "zoom": [4.0],

  "shader": "mypack:nightvision"
}
```

**2. The data file** (server-side):
`data/<your_namespace>/data/attachments/<scope_name>_data.json`

```json
{
  "shader": "mypack:nightvision"
}
```

> **Why both?** Display files live in the resource pack, so a player can edit their local
> copy. Data files are owned by the server and pushed to clients on join. When connected to
> a server, the **data file wins** — so a player who edits their display file to add or
> change an effect gets the server's value instead. See §7.
>
> Singleplayer, or a server without RenaissanceLib, falls back to the display file, so the
> display declaration is what makes the scope work offline.

Then create the shader itself at:

`assets/mypack/shaders/post/nightvision.json`

This is a **vanilla post-effect file** — the same format Minecraft uses for
`creeper.json`, `invert.json`, and the rest. Nothing RenaissanceLib-specific about it.

That's the whole setup. Aim down sights and the effect fills the glass.

---

## 2. Using vanilla's built-in effects

A shader name with no namespace resolves to `minecraft`, so you can point straight at
vanilla's post effects with no files of your own:

```json
"shader": "invert"
```

Useful ones to try: `invert`, `creeper`, `desaturate`, `blur`, `pencil`, `wobble`.

Good for checking your setup works before writing a shader.

---

## 3. Writing your own effect

A minimal post-effect file that tints the view green:

`assets/mypack/shaders/post/nightvision.json`

```json
{
  "targets": [ "swap" ],
  "passes": [
    {
      "name": "nightvision",
      "intarget": "minecraft:main",
      "outtarget": "swap",
      "uniforms": []
    },
    {
      "name": "blit",
      "intarget": "swap",
      "outtarget": "minecraft:main",
      "uniforms": [ { "name": "ColorModulate", "values": [ 1.0, 1.0, 1.0, 1.0 ] } ]
    }
  ]
}
```

with the fragment/vertex program pair at `assets/mypack/shaders/program/nightvision.fsh`
and `.vsh`. This is all standard vanilla shader authoring — any Minecraft resource-pack
shader tutorial applies.

---

## 4. What it can and cannot do

This is a **post-process filter over the finished frame**. That's the key thing to
understand when planning effects.

**Works well:**

- Night vision (brightening, green tint)
- Colour grading, monochrome, sepia
- Pixelation, scan lines, CRT distortion
- Blur, chromatic aberration, vignettes

**Cannot work:**

- **Thermal vision that reveals entities in the dark.** The shader only sees pixels that
  are already on screen. A mob hidden in an unlit room isn't in the frame, so no filter can
  bring it out. Genuine thermal needs the world re-rendered with different lighting, which
  is a fundamentally different (and much more expensive) technique.
- Anything needing depth, entity positions, or world data — the shader gets colour only.

If you want "brighter in the dark," that works. If you want "see through walls / see
warm bodies," that doesn't.

> **Planned: a double-render scope view.** A separate feature that re-renders the world for the
> lens — enabling true night vision / thermal that reveals entities and geometry the post-shader
> can't (see above) — is planned but **still in development**, so it isn't available yet. When it
> lands it will complement these post-shaders (author the look with a shader on top of the
> re-rendered view), not replace them.

---

## 5. Behaviour details

**Only while aiming.** The effect appears as you bring the sights up and disappears as you
lower them. It scales with the aperture during the ADS transition, so it fills the glass as
the glass grows.

**Confined to the glass.** The scope body, the reticle, and the gun are excluded
automatically — the effect only covers the lens opening.

**Distorting shaders and the reticle.** For effects that move pixels around (pixelate,
blur), the reticle is removed from the shader's input before processing, so you get one
crisp reticle rather than a smeared duplicate beside it. Nothing to configure.

**Shaderpacks.** Works under Iris/Oculus as well as vanilla. Expect a modest frame cost
while scoped under a shaderpack.

---

## 6. Scopes with multiple views (canted sights, variable zoom)

A TaC:Z scope can have several **views** — a main optic plus a canted red dot, or a
variable-power scope. That's declared with the `views` array in the display file, where each
entry maps a zoom step to a view node on the model:

```json
{
  "zoom":  [4.0, 8.0, 1.0],
  "views": [1,   1,   2  ]
}
```

Here zoom steps 0 and 1 (4x, 8x) look through **view node 1** — the main scope — and step 2
(1x) looks through **view node 2**, the canted sight.

### One shader per view

Give `shader` an object keyed by view index instead of a single string. **View indices are
0-based**, so `views: [1, 1, 2]` means view node 1 is index `0` and node 2 is index `1`:

```json
"shader": {
  "0": "mypack:nightvision"
}
```

The main scope gets night vision; the canted sight gets **nothing**, because index `1` isn't
listed. This is how you keep an effect off a canted sight.

Different effects per view work too:

```json
"shader": {
  "0": "mypack:nightvision",
  "1": "mypack:red_tint"
}
```

The single-string form still works and applies to **every** view:

```json
"shader": "mypack:nightvision"
```

Use the object form for any scope with a canted sight — otherwise the effect follows you
onto the canted optic, which is almost never what you want.

As always, put the same value in the data file (§1).

---

## 7. Server authority and what it does (and doesn't) protect

**How it works.** On join, the server pushes its attachment data files to the client. If the
scope's data file declares a `shader`, that value is used and the client's display file is
ignored. If the two disagree, a warning naming both is written to the client log — which
also catches the ordinary case of a typo in one of your two files.

**What this stops:** a player editing their local display file to add an effect to a scope
that shouldn't have one, swap in a stronger effect, or give a shader to a scope the server
never granted one to.

**What it does not stop:** a genuinely modified client. Anything rendered on the player's
machine can be changed by someone determined enough, and no client-side check survives that.

**Worth keeping in perspective:** a scope shader is a post-process filter over the finished
frame. The strongest thing it can do is brighten or recolour — which a plain resource pack
overriding `assets/minecraft/shaders/post/`, a shaderpack's gamma settings, or any
brightness mod can already do. It **cannot** reveal anything not already drawn: no seeing
through walls, no highlighting entities in the dark (see §4). So this is the same class of
issue as gamma abuse, which Minecraft has never solved client-side — the server authority
here raises the bar for casual editing rather than eliminating the problem.

---

## 8. Troubleshooting

| Symptom | Likely cause |
|---|---|
| No effect at all | `shader` key missing or misspelled in the `_display.json` |
| Works in singleplayer, not on a server | `shader` missing from the `_data.json` — the server has no mapping to send |
| Wrong effect on a server | The data file declares a different shader than the display file; the log names both |
| Effect shows on the canted sight too | Using the single-string `shader` form — switch to the per-view object (§6) |
| Effect fills the whole screen | Not possible via this system — check you aren't also applying a resource-pack post effect |
| Solid colour instead of the scene | Your shader's `intarget` isn't `minecraft:main`, so it's processing an empty frame |
| Works in vanilla, not with shaderpack | Report it — the shaderpack path is more fragile; include your Oculus and shaderpack versions |
| Effect looks correct but very dark | Post effects run on the finished frame; under a shaderpack that frame is already tonemapped |

**Log check:** a malformed `_display.json` is skipped silently by design (so one bad file
can't break every scope). If a scope isn't picking up its shader, check the JSON parses.

---

## 9. File layout reference

```
assets/mypack/
├── display/attachments/
│   └── scope_pso1_display.json      ← "shader": "mypack:nightvision"
└── shaders/
    ├── post/
    │   └── nightvision.json          ← post-effect chain
    └── program/
        ├── nightvision.fsh           ← fragment shader
        └── nightvision.vsh           ← vertex shader

data/mypack/
└── data/attachments/
    └── scope_pso1_data.json         ← "shader": "mypack:nightvision"  (same value)
```

The scope id is derived from the display file's name with `_display` stripped — so
`scope_pso1_display.json` maps to the attachment `mypack:scope_pso1`.
