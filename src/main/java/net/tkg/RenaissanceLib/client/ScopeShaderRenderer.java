package net.tkg.RenaissanceLib.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.tkg.RenaissanceLib.RenaissanceLibMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

@OnlyIn(Dist.CLIENT)
public final class ScopeShaderRenderer {

    private static TextureTarget shadedSnapshot = null;
    private static TextureTarget shaderTarget   = null;
    private static boolean       shadedReady    = false;

    private static boolean pendingIrisComposite = false;

    private static int lensMaskTexId  = -1;
    private static int lensMaskWidth  = 0;
    private static int lensMaskHeight = 0;
    private static int lensMaskFbo    = -1;

    private static int     depthSnapTexId = -1;
    private static int     depthSnapFbo   = -1;
    private static int     depthSnapW     = 0;
    private static int     depthSnapH     = 0;
    private static boolean depthSnapValid = false;

    private static int vmMaskTexId = -1;
    private static int vmMaskFbo   = -1;
    private static int vmMaskW     = 0;
    private static int vmMaskH     = 0;

    private static int rawProgramId        = -1;
    private static int maskProgramId       = -1;
    private static int solidProgramId      = -1;
    private static int depthCopyProgramId  = -1;
    private static int vmSubtractProgramId = -1;
    private static int vmMaskProgramId     = -1;
    private static int vmInpaintProgramId  = -1;
    private static int rawVaoId            = -1;
    private static int rawVboId            = -1;

    private static final float VIEWMODEL_DEPTH_EPSILON = 1.0e-6f;
    private static final int   VM_INPAINT_RADIUS       = 16;

    private ScopeShaderRenderer() {}

    public static void prepareForFrame(PostChain postChain, float partialTick) {
        RenderSystem.assertOnRenderThread();
        shadedReady = false;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main.width, h = main.height;
        ensureSnapshots(w, h);
        ensureShaderTarget(w, h);
        ensureRawPrograms();

        // The post-shader processes the normal frame inside the lens.
        int sourceTex = main.getColorTextureId();

        blitColorQuad(sourceTex, shaderTarget, w, h);
        postChain.process(partialTick);
        blitColorQuad(shaderTarget.getColorTextureId(), shadedSnapshot, w, h);
        main.bindWrite(false);

        RenderSystem.clear(256 , Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        shadedReady = true;
    }

    /** True under an active Iris/Oculus shaderpack (world rendering is deferred, so no mid-frame snapshot). */
    public static boolean isIrisShaderpack() {
        return IrisCompat.isShaderPackInUse();
    }

    /**
     * Whether the in-lens composite is deferred to end-of-frame instead of done mid-frame during the scope render.
     * True only under an Iris/Oculus shaderpack (Iris defers world rendering, so the scene isn't on the main target
     * mid-frame); otherwise the effect composites mid-frame during the scope's ocular draw.
     */
    public static boolean useEndOfFramePath() {
        return isIrisShaderpack();
    }

    public static void compositeIntoLens() {
        if (useEndOfFramePath()) return; // deferred to endOfFrameIrisComposite
        compositeIntoLensInternal();
    }

    private static void glCheck(String label) {
        int err = GL11.glGetError();
        if (err != GL11.GL_NO_ERROR)
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] GL error after {}: 0x{}", label, Integer.toHexString(err));
    }

    public static void endOfFrameIrisComposite(PostChain postChain, float partialTick) {
        if (!pendingIrisComposite || postChain == null) {
            depthSnapValid       = false;
            pendingIrisComposite = false;
            return;
        }
        pendingIrisComposite = false;
        RenderSystem.assertOnRenderThread();

        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            int w = main.width, h = main.height;
            ensureShaderTarget(w, h);
            ensureRawPrograms();

            if (vmMaskTexId != -1 && depthSnapValid) {
                inpaintViewmodelIntoTarget(main, shaderTarget, w, h);
            } else {
                blitColorQuad(main.getColorTextureId(), shaderTarget, w, h);
            }
            glCheck("Step1 build PostChain input");

            postChain.process(partialTick);
            main.bindWrite(false);
            glCheck("Step2 postChain");

            int insideTex = shaderTarget.getColorTextureId();
            int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.colorMask(true, true, true, true);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            GL11.glStencilMask(0xFF);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.colorMask(false, false, false, false);
            GL20.glUseProgram(maskProgramId);
            GL20.glUniform1i(GL20.glGetUniformLocation(maskProgramId, "tex"), 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(lensMaskTexId);
            drawRawQuad();
            GlStateManager._bindTexture(0);
            RenderSystem.colorMask(true, true, true, true);
            glCheck("Step4 markLens");

            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glStencilMask(0x00);
            GL20.glUseProgram(rawProgramId);
            GL20.glUniform1i(GL20.glGetUniformLocation(rawProgramId, "tex"), 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(insideTex);
            drawRawQuad();
            GlStateManager._bindTexture(0);
            glCheck("Step6 insideLens");

            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL20.glUseProgram(previousProgram);
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        } catch (Throwable t) {
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Iris scope composite failed; skipping frame", t);
            try {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glStencilMask(0xFF);
                GL20.glUseProgram(0);
                RenderSystem.disableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            } catch (Throwable ignored) {
            }
        } finally {
            depthSnapValid = false;
        }
    }

    public static void destroySnapshot() {
        if (shadedSnapshot != null) { shadedSnapshot.destroyBuffers(); shadedSnapshot = null; }
        shadedReady          = false;
        pendingIrisComposite = false;
    }

    public static void destroyViewmodelMask() {
        if (depthSnapTexId != -1) { GlStateManager._deleteTexture(depthSnapTexId); depthSnapTexId = -1; }
        if (depthSnapFbo   != -1) { GL30.glDeleteFramebuffers(depthSnapFbo);       depthSnapFbo   = -1; }
        if (vmMaskTexId    != -1) { GlStateManager._deleteTexture(vmMaskTexId);    vmMaskTexId    = -1; }
        if (vmMaskFbo      != -1) { GL30.glDeleteFramebuffers(vmMaskFbo);          vmMaskFbo      = -1; }
        depthSnapW = 0;
        depthSnapH = 0;
        vmMaskW    = 0;
        vmMaskH    = 0;
        depthSnapValid = false;
    }

    private static void ensureVmMaskTarget(int w, int h) {
        if (vmMaskTexId != -1 && vmMaskW == w && vmMaskH == h) return;
        if (vmMaskTexId != -1) GlStateManager._deleteTexture(vmMaskTexId);
        vmMaskTexId = GlStateManager._genTexture();
        GlStateManager._bindTexture(vmMaskTexId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, w, h, 0,
                GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._bindTexture(0);
        vmMaskW = w;
        vmMaskH = h;

        if (vmMaskFbo == -1) vmMaskFbo = GL30.glGenFramebuffers();
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, vmMaskFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, vmMaskTexId, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    public static void resize(int width, int height) {
        ensureShaderTarget(width, height);
        destroyViewmodelMask();
        if (shadedSnapshot != null
                && shadedSnapshot.width == width && shadedSnapshot.height == height) return;
        destroySnapshot();
    }

    public static void destroyShaderTarget() {
        if (shaderTarget != null) { shaderTarget.destroyBuffers(); shaderTarget = null; }
    }

    private static void compositeIntoLensInternal() {
        if (!shadedReady || shadedSnapshot == null) return;
        RenderSystem.assertOnRenderThread();
        ensureRawPrograms();

        // Draw with the raw NDC shader (positions already in clip space) instead of a matrix-transformed quad, so
        // we never touch the global projection/modelview. Hijacking + restoring those subtly disturbed the state
        // TaC:Z's following super.render relied on. This matches the Iris composite path.
        //
        // Stencil: draw only into the carved aperture (bit 0x80 set — same selector as the Iris lens-mask capture),
        // NOT the whole ocular. This hook runs after TaC:Z's aperture carve, so the ocular rim/mask region is left
        // untouched — otherwise the snapshot overwrote the scope's own inner ring there and TaC:Z's black mask
        // showed on top of it (a dark ring around the lens).
        // Depth-test (LEQUAL) but don't write depth, and force the fragment depth to the far plane via
        // glDepthRange(1,1). The lens opening is cleared to depth 1.0 in prepareForFrame, so the composite passes
        // only there — the actual see-through background — and is occluded by ALL of the scope's own model geometry
        // (which is nearer than 1.0), whatever depth its inner ring/reticle housing sits at. Without this the
        // snapshot painted over that geometry, carving a see-through hole in the model where the ocular rim sits.
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.colorMask(true, true, true, true);
        GL11.glDepthRange(1.0, 1.0);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        RenderSystem.stencilFunc(GL11.GL_EQUAL, 0x80, 0x80);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        GL20.glUseProgram(rawProgramId);
        GL20.glUniform1i(GL20.glGetUniformLocation(rawProgramId, "tex"), 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(shadedSnapshot.getColorTextureId());
        drawRawQuad();
        GlStateManager._bindTexture(0);

        GL11.glDepthRange(0.0, 1.0);
        GL20.glUseProgram(prevProgram);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        shadedReady = false;
    }

    private static final String VERT_SRC =
            "#version 150 core\n" +
                    "in vec2 Position;\n" +
                    "in vec2 UV;\n" +
                    "out vec2 texCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(Position, 0.0, 1.0);\n" +
                    "    texCoord = UV;\n" +
                    "}\n";

    private static final String FRAG_SRC =
            "#version 150 core\n" +
                    "uniform sampler2D tex;\n" +
                    "in vec2 texCoord;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = texture(tex, texCoord);\n" +
                    "}\n";

    private static final String FRAG_MASK_SRC =
            "#version 150 core\n" +
                    "uniform sampler2D tex;\n" +
                    "in vec2 texCoord;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    if (texture(tex, texCoord).r < 0.5) discard;\n" +
                    "    fragColor = vec4(1.0);\n" +
                    "}\n";

    private static final String FRAG_DEPTHCOPY_SRC =
            "#version 150 core\n" +
                    "uniform sampler2D depthTex;\n" +
                    "in vec2 texCoord;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(texture(depthTex, texCoord).r, 0.0, 0.0, 1.0);\n" +
                    "}\n";

    private static final String FRAG_VMSUBTRACT_SRC =
            "#version 150 core\n" +
                    "uniform sampler2D depthTex;\n" +
                    "uniform sampler2D depthSnap;\n" +
                    "uniform float epsilon;\n" +
                    "in vec2 texCoord;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    float now    = texture(depthTex,  texCoord).r;\n" +
                    "    float before = texture(depthSnap, texCoord).r;\n" +
                    "    if (abs(now - before) <= epsilon) discard;\n" +
                    "    fragColor = vec4(0.0, 0.0, 0.0, 0.0);\n" +
                    "}\n";

    private static final String FRAG_VMMASK_SRC =
            "#version 150 core\n" +
                    "uniform sampler2D apertureTex;\n" +
                    "uniform sampler2D depthTex;\n" +
                    "uniform sampler2D depthSnap;\n" +
                    "uniform float epsilon;\n" +
                    "in vec2 texCoord;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    if (texture(apertureTex, texCoord).r < 0.5) { fragColor = vec4(0.0); return; }\n" +
                    "    float now    = texture(depthTex,  texCoord).r;\n" +
                    "    float before = texture(depthSnap, texCoord).r;\n" +
                    "    fragColor = (abs(now - before) > epsilon) ? vec4(1.0) : vec4(0.0);\n" +
                    "}\n";

    private static final String FRAG_VMINPAINT_SRC =
            "#version 150 core\n" +
                    "uniform sampler2D scene;\n" +
                    "uniform sampler2D vmTex;\n" +
                    "uniform int searchRadius;\n" +
                    "in vec2 texCoord;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    if (texture(vmTex, texCoord).r < 0.5) {\n" +
                    "        fragColor = texture(scene, texCoord);\n" +
                    "        return;\n" +
                    "    }\n" +
                    "    vec2 ts = 1.0 / vec2(textureSize(scene, 0));\n" +
                    "    for (int r = 1; r <= 64; r++) {\n" +
                    "        if (r > searchRadius) break;\n" +
                    "        vec2 ox = vec2(ts.x * float(r), 0.0);\n" +
                    "        vec2 oy = vec2(0.0, ts.y * float(r));\n" +
                    "        if (texture(vmTex, texCoord + ox).r < 0.5) { fragColor = texture(scene, texCoord + ox); return; }\n" +
                    "        if (texture(vmTex, texCoord - ox).r < 0.5) { fragColor = texture(scene, texCoord - ox); return; }\n" +
                    "        if (texture(vmTex, texCoord + oy).r < 0.5) { fragColor = texture(scene, texCoord + oy); return; }\n" +
                    "        if (texture(vmTex, texCoord - oy).r < 0.5) { fragColor = texture(scene, texCoord - oy); return; }\n" +
                    "    }\n" +
                    "    fragColor = texture(scene, texCoord);\n" +
                    "}\n";

    private static final String FRAG_SOLID_SRC =
            "#version 150 core\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = vec4(1.0);\n" +
                    "}\n";

    private static final float[] QUAD_VERTS = {
            -1f, -1f,  0f, 0f,
            1f, -1f,  1f, 0f,
            -1f,  1f,  0f, 1f,
            1f,  1f,  1f, 1f,
    };

    private static void ensureRawPrograms() {
        if (rawProgramId != -1) return;

        int vert = compileShader(GL20.GL_VERTEX_SHADER,   VERT_SRC,      "vert");
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG_SRC,      "frag");
        int mask      = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG_MASK_SRC,       "mask-frag");
        int solid     = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG_SOLID_SRC,      "solid-frag");
        int depthcopy = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG_DEPTHCOPY_SRC,  "depthcopy-frag");
        int vmsub     = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG_VMSUBTRACT_SRC, "vmsubtract-frag");
        int vmmask    = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG_VMMASK_SRC,     "vmmask-frag");
        int vminpaint = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG_VMINPAINT_SRC,  "vminpaint-frag");

        rawProgramId        = linkProgram(vert, frag,      "raw");
        maskProgramId       = linkProgram(vert, mask,      "mask");
        solidProgramId      = linkProgram(vert, solid,     "solid");
        depthCopyProgramId  = linkProgram(vert, depthcopy, "depthcopy");
        vmSubtractProgramId = linkProgram(vert, vmsub,     "vmsubtract");
        vmMaskProgramId     = linkProgram(vert, vmmask,    "vmmask");
        vmInpaintProgramId  = linkProgram(vert, vminpaint, "vminpaint");

        GL20.glDetachShader(rawProgramId,        vert); GL20.glDetachShader(rawProgramId,        frag);
        GL20.glDetachShader(maskProgramId,       vert); GL20.glDetachShader(maskProgramId,       mask);
        GL20.glDetachShader(solidProgramId,      vert); GL20.glDetachShader(solidProgramId,      solid);
        GL20.glDetachShader(depthCopyProgramId,  vert); GL20.glDetachShader(depthCopyProgramId,  depthcopy);
        GL20.glDetachShader(vmSubtractProgramId, vert); GL20.glDetachShader(vmSubtractProgramId, vmsub);
        GL20.glDetachShader(vmMaskProgramId,     vert); GL20.glDetachShader(vmMaskProgramId,     vmmask);
        GL20.glDetachShader(vmInpaintProgramId,  vert); GL20.glDetachShader(vmInpaintProgramId,  vminpaint);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        GL20.glDeleteShader(mask);
        GL20.glDeleteShader(solid);
        GL20.glDeleteShader(depthcopy);
        GL20.glDeleteShader(vmsub);
        GL20.glDeleteShader(vmmask);
        GL20.glDeleteShader(vminpaint);

        rawVaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(rawVaoId);
        rawVboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, rawVboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, QUAD_VERTS, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 16, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 16, 8);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static int compileShader(int type, String src, String label) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Shader compile error ({}): {}",
                    label, GL20.glGetShaderInfoLog(id));
        return id;
    }

    private static int linkProgram(int vert, int frag, String label) {
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vert);
        GL20.glAttachShader(prog, frag);
        GL20.glBindAttribLocation(prog, 0, "Position");
        GL20.glBindAttribLocation(prog, 1, "UV");
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            RenaissanceLibMod.LOGGER.error("[RenaissanceLib] Program link error ({}): {}",
                    label, GL20.glGetProgramInfoLog(prog));
        return prog;
    }

    private static void drawRawQuad() {
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(rawVaoId);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
        GL30.glBindVertexArray(previousVao);
    }

    private static void ensureSnapshots(int w, int h) {
        if (shadedSnapshot == null || shadedSnapshot.width != w || shadedSnapshot.height != h) {
            if (shadedSnapshot != null) shadedSnapshot.destroyBuffers();
            shadedSnapshot = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            shadedSnapshot.setClearColor(0f, 0f, 0f, 1f);
        }
    }

    public static RenderTarget getShaderTarget() {
        Minecraft mc = Minecraft.getInstance();
        ensureShaderTarget(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        return shaderTarget;
    }

    private static void ensureShaderTarget(int w, int h) {
        if (shaderTarget == null) {

            shaderTarget = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            shaderTarget.setClearColor(0f, 0f, 0f, 1f);
        } else if (shaderTarget.width != w || shaderTarget.height != h) {
            shaderTarget.resize(w, h, Minecraft.ON_OSX);
        }
    }

    private static void ensureLensMaskTexture(int w, int h) {
        if (lensMaskTexId != -1 && lensMaskWidth == w && lensMaskHeight == h) return;
        if (lensMaskTexId != -1) GlStateManager._deleteTexture(lensMaskTexId);
        lensMaskTexId = GlStateManager._genTexture();
        GlStateManager._bindTexture(lensMaskTexId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, w, h, 0,
                GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._bindTexture(0);
        lensMaskWidth  = w;
        lensMaskHeight = h;
    }

    private static void ensureLensMaskFbo() {
        if (lensMaskFbo == -1) lensMaskFbo = GL30.glGenFramebuffers();
    }

    public static void snapshotPreViewmodelDepth() {
        if (!IrisCompat.isShaderPackInUse()) return;
        if (depthSnapValid) return;
        if (!ScopeStateTracker.isAimingThroughScope()) return;

        ensureRawPrograms();

        int depthTex = boundDepthTextureId();
        if (depthTex == 0) return;

        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = vp[2], h = vp[3];
        if (w <= 0 || h <= 0) return;

        ensureDepthSnapTarget(w, h);

        int prevProg    = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean prevDepthTest   = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask   = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean prevBlend       = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevStencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);

        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthSnapFbo);
            GL11.glViewport(0, 0, w, h);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            GL11.glDisable(GL11.GL_STENCIL_TEST);

            GL20.glUseProgram(depthCopyProgramId);
            GL20.glUniform1i(GL20.glGetUniformLocation(depthCopyProgramId, "depthTex"), 0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(depthTex);
            drawRawQuad();
            GlStateManager._bindTexture(0);

            depthSnapValid = true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
            GL20.glUseProgram(prevProg);
            RenderSystem.depthMask(prevDepthMask);
            if (prevDepthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (prevBlend)     RenderSystem.enableBlend();     else RenderSystem.disableBlend();
            if (prevStencilTest) GL11.glEnable(GL11.GL_STENCIL_TEST);
            else                 GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    private static int boundDepthTextureId() {
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type == GL11.GL_TEXTURE) {
            return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        }
        type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type == GL11.GL_TEXTURE) {
            return GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        }
        return 0;
    }

    private static int boundDepthTextureId0(int fbo) {
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        int id = 0;
        int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type == GL11.GL_TEXTURE) {
            id = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        } else {
            type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                    GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            if (type == GL11.GL_TEXTURE) {
                id = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                        GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            }
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        return id;
    }

    private static void ensureDepthSnapTarget(int w, int h) {
        if (depthSnapTexId != -1 && depthSnapW == w && depthSnapH == h) return;
        if (depthSnapTexId != -1) GlStateManager._deleteTexture(depthSnapTexId);
        depthSnapTexId = GlStateManager._genTexture();
        GlStateManager._bindTexture(depthSnapTexId);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, w, h, 0,
                GL11.GL_RED, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._bindTexture(0);
        depthSnapW = w;
        depthSnapH = h;

        if (depthSnapFbo == -1) depthSnapFbo = GL30.glGenFramebuffers();
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthSnapFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, depthSnapTexId, 0);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
    }

    public static void captureLensMaskIris() {
        if (!useEndOfFramePath()) return;
        if (!ScopeStateTracker.isAimingThroughScope()) return;
        captureLensMaskGpu();
    }

    private static void captureLensMaskGpu() {
        ensureRawPrograms();

        int gunFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        int sType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int sName = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,
                GL30.GL_STENCIL_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (sType == GL11.GL_NONE || sName == 0) {
            return;
        }

        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

        int w = vp[2], h = vp[3];
        if (w <= 0 || h <= 0) return;

        RenderTarget mainRt = Minecraft.getInstance().getMainRenderTarget();
        if (mainRt != null && mainRt.width > 0 && mainRt.height > 0) {
            double mainAspect = (double) mainRt.width / mainRt.height;
            double vpAspect   = (double) w / h;
            if (Math.abs(vpAspect - mainAspect) > 0.02) {
                return;
            }
        }

        ensureLensMaskTexture(w, h);
        ensureLensMaskFbo();

        int prevProg        = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevReadFbo     = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean prevDepthTest   = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask   = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean prevBlend       = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevStencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        int prevStencilMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int prevSFunc       = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int prevSRef        = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int prevSValueMask  = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int prevSFail       = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int prevSZFail      = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int prevSZPass      = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        float[] prevClearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, prevClearColor);

        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, lensMaskFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, lensMaskTexId, 0);

            boolean ok = tryAttachStencil(sType, sName);
            if (!ok) {

                return;
            }

            GL11.glViewport(0, 0, w, h);

            RenderSystem.colorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.clearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, 0x80, 0x80);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            GL11.glStencilMask(0x00);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();

            GL20.glUseProgram(solidProgramId);
            drawRawQuad();

            if (depthSnapValid && depthSnapTexId != -1) {
                int liveDepth = boundDepthTextureId0(gunFbo);
                if (liveDepth != 0) {

                    ensureVmMaskTarget(w, h);
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, vmMaskFbo);
                    GL11.glViewport(0, 0, w, h);
                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                    RenderSystem.disableBlend();
                    RenderSystem.colorMask(true, true, true, true);
                    GL20.glUseProgram(vmMaskProgramId);
                    GL20.glUniform1i(GL20.glGetUniformLocation(vmMaskProgramId, "apertureTex"), 0);
                    GL20.glUniform1i(GL20.glGetUniformLocation(vmMaskProgramId, "depthTex"),    1);
                    GL20.glUniform1i(GL20.glGetUniformLocation(vmMaskProgramId, "depthSnap"),   2);
                    GL20.glUniform1f(GL20.glGetUniformLocation(vmMaskProgramId, "epsilon"),
                            VIEWMODEL_DEPTH_EPSILON);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE2);
                    GlStateManager._bindTexture(depthSnapTexId);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE1);
                    GlStateManager._bindTexture(liveDepth);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                    GlStateManager._bindTexture(lensMaskTexId);
                    drawRawQuad();
                    GlStateManager._bindTexture(0);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE1);
                    GlStateManager._bindTexture(0);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE2);
                    GlStateManager._bindTexture(0);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0);

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, lensMaskFbo);
                    GL11.glViewport(0, 0, w, h);

                    GL11.glDisable(GL11.GL_STENCIL_TEST);
                    RenderSystem.disableBlend();
                    RenderSystem.colorMask(true, true, true, true);
                    GL20.glUseProgram(vmSubtractProgramId);
                    GL20.glUniform1i(GL20.glGetUniformLocation(vmSubtractProgramId, "depthTex"),  0);
                    GL20.glUniform1i(GL20.glGetUniformLocation(vmSubtractProgramId, "depthSnap"), 1);
                    GL20.glUniform1f(GL20.glGetUniformLocation(vmSubtractProgramId, "epsilon"),
                        VIEWMODEL_DEPTH_EPSILON);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE1);
                    GlStateManager._bindTexture(depthSnapTexId);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                    GlStateManager._bindTexture(liveDepth);
                    drawRawQuad();
                    GlStateManager._bindTexture(0);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE1);
                    GlStateManager._bindTexture(0);
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                }
            }

            pendingIrisComposite = true;
        } finally {

            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, 0, 0);

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, gunFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
            GL20.glUseProgram(prevProg);

            RenderSystem.depthMask(prevDepthMask);
            if (prevDepthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (prevBlend)     RenderSystem.enableBlend();     else RenderSystem.disableBlend();

            RenderSystem.colorMask(true, true, true, true);

            RenderSystem.stencilMask(prevStencilMask);
            RenderSystem.stencilFunc(prevSFunc, prevSRef, prevSValueMask);
            RenderSystem.stencilOp(prevSFail, prevSZFail, prevSZPass);
            if (prevStencilTest) GL11.glEnable(GL11.GL_STENCIL_TEST);
            else                 GL11.glDisable(GL11.GL_STENCIL_TEST);

            RenderSystem.clearColor(prevClearColor[0], prevClearColor[1],
                    prevClearColor[2], prevClearColor[3]);
        }
    }

    private static boolean tryAttachStencil(int sType, int sName) {
        if (sType == GL11.GL_TEXTURE) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, sName, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) return true;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, 0, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, sName, 0);
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } else if (sType == GL30.GL_RENDERBUFFER) {
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, sName);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) return true;
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, sName);
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        }
        return false;
    }

    private static void blitColorQuad(int srcColorTex, RenderTarget dst, int w, int h) {
        dst.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.colorMask(true, true, true, true);
        GL20.glUseProgram(rawProgramId);
        GL20.glUniform1i(GL20.glGetUniformLocation(rawProgramId, "tex"), 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(srcColorTex);
        drawRawQuad();
        GlStateManager._bindTexture(0);
        GL20.glUseProgram(0);
    }

    private static void inpaintViewmodelIntoTarget(RenderTarget main, RenderTarget dst, int w, int h) {
        dst.bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.colorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_STENCIL_TEST);

        GL20.glUseProgram(vmInpaintProgramId);
        GL20.glUniform1i(GL20.glGetUniformLocation(vmInpaintProgramId, "scene"),        0);
        GL20.glUniform1i(GL20.glGetUniformLocation(vmInpaintProgramId, "vmTex"),        1);
        GL20.glUniform1i(GL20.glGetUniformLocation(vmInpaintProgramId, "searchRadius"), VM_INPAINT_RADIUS);

        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(vmMaskTexId);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(main.getColorTextureId());

        drawRawQuad();

        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL20.glUseProgram(0);
        glCheck("Step1b vmInpaint");
    }
}
