# Scope Shaders — Pack Author Guide

**RenaissanceLib** (TaC:Z addon, Forge 1.20.1)

Attaches a **post-processing shader** to a scope so the effect renders **only inside the
scope's glass** while aiming — night vision, colour grading, pixelation, scan lines.

Works with and without a shaderpack (Iris/Oculus).

---

## 1. Quick start

Add a `shader` key to the scope's **display** file:

`assets/<your_namespace>/display/attachments/<scope_name>_display.json`

```json
{
  "model": "mypack:attachment/scope_pso1",
  "texture": "mypack:attachment/scope_pso1",
  "zoom": [4.0],

  "shader": "mypack:nightvision"
}
```

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

## 6. Troubleshooting

| Symptom | Likely cause |
|---|---|
| No effect at all | `shader` key missing or misspelled in the `_display.json` |
| Effect fills the whole screen | Not possible via this system — check you aren't also applying a resource-pack post effect |
| Solid colour instead of the scene | Your shader's `intarget` isn't `minecraft:main`, so it's processing an empty frame |
| Works in vanilla, not with shaderpack | Report it — the shaderpack path is more fragile; include your Oculus and shaderpack versions |
| Effect looks correct but very dark | Post effects run on the finished frame; under a shaderpack that frame is already tonemapped |

**Log check:** a malformed `_display.json` is skipped silently by design (so one bad file
can't break every scope). If a scope isn't picking up its shader, check the JSON parses.

---

## 7. File layout reference

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
```

The scope id is derived from the display file's name with `_display` stripped — so
`scope_pso1_display.json` maps to the attachment `mypack:scope_pso1`.
