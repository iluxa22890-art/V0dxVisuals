package com.vodx.visuals;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/**
 * First-person swing for swords and the mace: wind-up, fast strike with overshoot, slow recovery.
 * Called from HeldItemRendererMixin at the start of renderFirstPersonItem.
 */
public final class SwingAnim {
    /** true while our animation replaces the vanilla swing for the item currently being drawn. */
    public static boolean overriding;

    private SwingAnim() { }

    public static boolean isHeavy(ItemStack s) {
        return s.isIn(ItemTags.SWORDS) || s.isOf(Items.MACE);
    }

    // pose = {rotX, rotY, rotZ, tx, ty, tz}   (degrees / blocks, mirrored for the left arm where needed)
    private static final double[] REST = {0, 0, 0, 0, 0, 0};

    private static double[][] keys(int style) {
        switch (style) {
            case 2: // Slam: lift high, crush down
                return new double[][] {REST, {62, -6, 8, 0.02, 0.22, 0.12}, {-82, 4, -6, -0.02, -0.22, -0.36}, REST};
            case 3: // Sweep: wide horizontal cut
                return new double[][] {REST, {-8, -44, 12, 0.18, 0.02, 0.10}, {-14, 58, -30, -0.28, -0.06, -0.20}, REST};
            default: // Slash: diagonal cut
                return new double[][] {REST, {34, -16, 26, 0.10, 0.12, 0.10}, {-72, 22, -34, -0.20, -0.14, -0.30}, REST};
        }
    }

    private static double ease(double t, int kind) {
        t = Math.max(0, Math.min(1, t));
        return switch (kind) {
            case 0 -> 1 - Math.pow(1 - t, 3);   // ease-out (wind-up)
            case 1 -> t * t * t;                // ease-in  (strike, accelerating)
            default -> 1 - Math.pow(1 - t, 2);  // recovery
        };
    }

    /** Applies the animation to the matrix stack. Sets {@link #overriding} so the mixin can cancel the vanilla swing. */
    public static void apply(AbstractClientPlayerEntity p, Hand hand, float swing, ItemStack item, MatrixStack m) {
        overriding = false;
        if (!Modules.SWING_ANIM.enabled || swing <= 0f || swing >= 1f || !isHeavy(item) || p.isUsingItem()) return;

        int style = Modules.SWING_STYLE.index;
        if (style == 0) style = item.isOf(Items.MACE) ? 2 : 1;
        double[][] k = keys(style);

        double t = swing;
        double[] a, b;
        double e;
        if (t < 0.30) { a = k[0]; b = k[1]; e = ease(t / 0.30, 0); }
        else if (t < 0.52) { a = k[1]; b = k[2]; e = ease((t - 0.30) / 0.22, 1); }
        else { a = k[2]; b = k[3]; e = ease((t - 0.52) / 0.48, 2); }

        double s = Modules.SWING_STRENGTH.value;
        double[] v = new double[6];
        for (int i = 0; i < 6; i++) v[i] = (a[i] + (b[i] - a[i]) * e) * s;

        Arm arm = hand == Hand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        float side = arm == Arm.RIGHT ? 1f : -1f;

        // pivot = where the hand sits in view space, so the item swings around the wrist, not around the camera
        double px = 0.56 * side, py = -0.52, pz = -0.72;
        m.translate(v[3] * side, v[4], v[5]);
        m.translate(px, py, pz);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (v[1] * side)));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) v[0]));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (v[2] * side)));
        m.translate(-px, -py, -pz);
        overriding = true;
    }
}
