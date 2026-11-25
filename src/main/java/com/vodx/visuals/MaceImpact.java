package com.vodx.visuals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Visual only: when you hit something with the mace, the ground cracks outward (dark glowing cracks drawn by {@link Vfx}
 * plus a wave of block particles) and chunks of the block under the target fly up. Client-side only.
 * Registered from {@link Effects#init()}.
 */
public final class MaceImpact {
    private static final class Wave {
        final Vec3d origin;
        final BlockState state;
        final float[] angles;
        final double maxRadius;
        int age;

        Wave(Vec3d o, BlockState s, float[] a, double maxRadius) {
            origin = o;
            state = s;
            angles = a;
            this.maxRadius = maxRadius;
        }
    }

    private static final List<Wave> waves = new ArrayList<>();
    private static final Random RNG = new Random();
    private static final int LIFE = 9;           // ticks the crack wave takes to spread
    private static final double BASE_RADIUS = 3.2;

    private MaceImpact() { }

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && Modules.MACE_IMPACT.enabled && player.getMainHandStack().isOf(Items.MACE)) {
                start(entity, player.fallDistance);
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(MaceImpact::tick);
    }

    private static void start(Entity target, float fall) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        BlockPos below = BlockPos.ofFloored(target.getX(), target.getY() - 0.1, target.getZ());
        BlockState st = w.getBlockState(below);
        if (st.isAir() || !st.getFluidState().isEmpty()) st = Blocks.DIRT.getDefaultState();

        float strength = (float) Modules.MACE_SIZE.value;
        if (Modules.MACE_FALL.value) strength *= fall > 1.5f ? 1.0f + Math.min(fall, 10f) * 0.07f : 0.8f;

        int rays = 9 + RNG.nextInt(4);
        float[] a = new float[rays];
        for (int i = 0; i < rays; i++) a[i] = (float) ((i + RNG.nextFloat() * 0.7) * Math.PI * 2 / rays);
        Vec3d o = new Vec3d(target.getX(), below.getY() + 1.02, target.getZ());
        waves.add(new Wave(o, st, a, BASE_RADIUS * strength));

        if (Modules.MACE_CRACKS.value) Vfx.spawnCracks(o.x, below.getY() + 1.0, o.z, strength);

        if (Modules.MACE_DEBRIS.value) {
            BlockStateParticleEffect fx = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
            int n = (int) (40 * strength);
            for (int i = 0; i < n; i++) {            // debris burst
                double ang = RNG.nextDouble() * Math.PI * 2, sp = (0.08 + RNG.nextDouble() * 0.25) * Math.min(1.5, strength);
                w.addParticle(fx, o.x, o.y + 0.1, o.z,
                        Math.cos(ang) * sp, (0.25 + RNG.nextDouble() * 0.4) * Math.min(1.5, strength), Math.sin(ang) * sp);
            }
        }
        w.playSound(o.x, o.y, o.z, SoundEvents.ITEM_MACE_SMASH_GROUND,
                SoundCategory.PLAYERS, 0.8f * Math.min(1.3f, strength), 0.9f + RNG.nextFloat() * 0.2f, false);
    }

    private static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null) {
            waves.clear();
            return;
        }
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave v = it.next();
            double r0 = v.maxRadius * v.age / LIFE, r1 = v.maxRadius * (v.age + 1) / LIFE;
            BlockStateParticleEffect fx = new BlockStateParticleEffect(ParticleTypes.BLOCK, v.state);
            for (float a : v.angles) {
                // jagged crack: slight angle wobble along the ray
                for (double r = r0; r < r1; r += 0.25) {
                    double wob = Math.sin(r * 3.1 + a * 7) * 0.12;
                    double x = v.origin.x + Math.cos(a + wob) * r;
                    double z = v.origin.z + Math.sin(a + wob) * r;
                    w.addParticle(fx, x, v.origin.y, z, 0, 0.05 + RNG.nextDouble() * 0.12, 0);
                    if (RNG.nextInt(3) == 0) {
                        w.addParticle(ParticleTypes.SMOKE, x, v.origin.y + 0.05, z, 0, 0.02, 0);
                    }
                }
            }
            if (++v.age >= LIFE) it.remove();
        }
    }
}
