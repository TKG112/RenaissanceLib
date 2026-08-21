package net.tkg.RenaissanceLib.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A second-order (position/velocity) smoother matching TaC:Z's {@code SecondOrderDynamics}.
 *
 * <p>Same {@code k1/k2/k3} coefficients from the {@code (f, z, r)} tuning, and crucially the same
 * <em>time base</em>: TaC:Z advances a fixed {@link #SIM_STEP} of simulated time once per
 * {@link #REAL_STEP} of real time on a background thread — so it converges roughly {@code SIM_STEP
 * / REAL_STEP} (~8×) faster than wall-clock. We reproduce that by accumulating the real frame delta
 * and stepping in fixed increments, keeping the feel identical but deterministic and
 * framerate-independent. A naive real-delta integrator (1× time) feels far too slow by comparison.
 */
@OnlyIn(Dist.CLIENT)
public final class SecondOrderDynamics {
    /** Simulated time advanced per step — TaC:Z's fixed timestep {@code t}. */
    private static final float SIM_STEP = 0.05f;
    /** Real time between steps — TaC:Z's {@code Thread.sleep(6)}. */
    private static final float REAL_STEP = 0.006f;

    private final float k1, k2, k3;
    private float px, py, pyd;
    private float acc = 0f;

    public SecondOrderDynamics(float f, float z, float r, float x0) {
        float w = (float) (2 * Math.PI * f);
        this.k1 = z / ((float) Math.PI * f);
        this.k2 = 1f / (w * w);
        this.k3 = r * z / w;
        this.px = x0;
        this.py = x0;
        this.pyd = 0f;
    }

    /** Targets {@code x}, advancing the smoother by {@code realDt} seconds of real time. */
    public float update(float realDt, float x) {
        if (realDt > 0f) {
            acc += Math.min(realDt, 0.25f);
            int guard = 0;
            while (acc >= REAL_STEP && guard++ < 256) {
                step(x);
                acc -= REAL_STEP;
            }
        }
        float out = py + SIM_STEP * pyd;
        if (Float.isNaN(out)) {
            px = py = pyd = 0f;
            return 0f;
        }
        return out;
    }

    private void step(float x) {
        float pxd = (x - px) / SIM_STEP;
        px = x;
        py = py + SIM_STEP * pyd;
        pyd = pyd + SIM_STEP * (x + k3 * pxd - py - k1 * pyd) / k2;
    }

    public float value() {
        return py;
    }
}
