package com.vodx.visuals;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Colour logic of the custom sky. Used by the mixins in com.vodx.visuals.mixin. All colours are 0xRRGGBB. */
public final class Sky {
    private Sky() { }

    public static boolean active(ClientWorld w) {
        return Modules.SKY.enabled && w != null && w.getRegistryKey() == World.OVERWORLD;
    }

    /** hsv triple for the zenith (fog=false) or the horizon (fog=true). */
    private static float[] hsv(boolean fog) {
        double t = System.currentTimeMillis() / 1000.0 * Modules.SKY_SPEED.value;
        switch (Modules.SKY_PRESET.index) {
            case 1:  // Pastel: powder blue -> pink horizon
                return fog ? new float[] {0.93f, 0.25f, 1.00f} : new float[] {0.57f, 0.38f, 0.97f};
            case 2:  // Sunset: violet -> orange horizon
                return fog ? new float[] {0.05f, 0.62f, 1.00f} : new float[] {0.76f, 0.55f, 0.72f};
            case 3:  // Ocean: deep teal -> mint horizon
                return fog ? new float[] {0.46f, 0.30f, 1.00f} : new float[] {0.54f, 0.72f, 0.84f};
            case 4: { // Aurora: slowly drifting green / teal / violet
                float h = (float) (0.50 + 0.22 * Math.sin(t * 0.25));
                return fog ? new float[] {h + 0.12f, 0.45f, 0.95f} : new float[] {h, 0.70f, 0.68f};
            }
            case 5: { // Rainbow
                float h = (float) ((t * 0.05) % 1.0);
                return fog ? new float[] {h + 0.08f, 0.35f, 1f} : new float[] {h, 0.55f, 1f};
            }
            default: { // Custom
                float h = Modules.SKY_HUE.f() / 360f, s = Modules.SKY_SAT.f() / 100f, v = Modules.SKY_BRIGHT.f() / 100f;
                return fog ? new float[] {h + 0.04f, s * 0.55f, Math.min(1f, v + 0.12f)} : new float[] {h, s, v};
            }
        }
    }

    private static int toRgb(float[] hsv, float dim) {
        float h = hsv[0] - (float) Math.floor(hsv[0]);
        return MathHelper.hsvToRgb(h, MathHelper.clamp(hsv[1], 0f, 1f), MathHelper.clamp(hsv[2] * dim, 0f, 1f));
    }

    /** Darkening with the time of day (1.0 = full day). */
    private static float dim(ClientWorld w, float tickDelta) {
        if (Modules.SKY_DAY.value) return 1f;
        return MathHelper.clamp(w.getSkyBrightness(tickDelta), 0.12f, 1f);
    }

    public static int sky(ClientWorld w, float tickDelta) {
        return 0xFF000000 | toRgb(hsv(false), dim(w, tickDelta));
    }

    public static int fog(ClientWorld w, float tickDelta) {
        return 0xFF000000 | toRgb(hsv(true), dim(w, tickDelta));
    }

    public static int clouds(ClientWorld w, float tickDelta) {
        float[] f = hsv(true);
        return 0xFF000000 | toRgb(new float[] {f[0], f[1] * 0.45f, 1f}, 0.35f + 0.65f * dim(w, tickDelta));
    }
}
