package com.vodx.visuals;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Second glow renderer (same style as {@link Fx}): kill effects (Souls / Shatter / Lightning), animated cosmetics
 * (Wings / Halo / Orbits / Helix), the slash arc of sword swings and the ground cracks of {@link MaceImpact}.
 */
public final class Vfx {
    private static final class P {
        double x, y, z, px, py, pz, vx, vy, vz, grav, drag;
        int life, max, color, shape;
        float size;
        boolean trail;
        // spiral motion (souls)
        boolean spiral;
        double cx, cz, ang, angVel, rad, radVel;
    }

    private static final class Ring {
        double x, y, z;
        int age, max, off;
        float r0, r1, width;
    }

    private static final class Pillar {
        double x, y, z, rad, height;
        int age, max, off;
    }

    private static final class Bolt {
        List<Vec3d> pts = new ArrayList<>();
        int age, max, off;
        double width;
    }

    private static final class Arc {
        List<Vec3d> pts = new ArrayList<>();
        int age, max, off;
    }

    private static final class Crack {
        List<List<Vec3d>> branches = new ArrayList<>();
        int age, max;
        double width;
    }

    private static final List<P> particles = new ArrayList<>();
    private static final List<Ring> rings = new ArrayList<>();
    private static final List<Pillar> pillars = new ArrayList<>();
    private static final List<Bolt> bolts = new ArrayList<>();
    private static final List<Arc> arcs = new ArrayList<>();
    private static final List<Crack> cracks = new ArrayList<>();
    private static int ticks;
    private static float lastSwing;
    private static boolean arcFlip;
    private static double camX, camY, camZ;

    private Vfx() { }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(Vfx::tick);
        WorldRenderEvents.LAST.register(Vfx::render);
    }

    // ================================================================== spawning
    private static P part(double x, double y, double z, double vx, double vy, double vz,
                          int life, float size, int shape, int color, double grav, double drag) {
        P p = new P();
        p.x = p.px = x;
        p.y = p.py = y;
        p.z = p.pz = z;
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
        p.life = p.max = life;
        p.size = size;
        p.shape = shape;
        p.color = color;
        p.grav = grav;
        p.drag = drag;
        particles.add(p);
        return p;
    }

    private static void ring(double x, double y, double z, float r0, float r1, int max, float width, int delay, int off) {
        Ring r = new Ring();
        r.x = x;
        r.y = y;
        r.z = z;
        r.r0 = r0;
        r.r1 = r1;
        r.max = max;
        r.width = width;
        r.age = -delay;
        r.off = off;
        rings.add(r);
    }

    private static void pillar(double x, double y, double z, double rad, double h, int max, int off) {
        Pillar p = new Pillar();
        p.x = x;
        p.y = y;
        p.z = z;
        p.rad = rad;
        p.height = h;
        p.max = max;
        p.off = off;
        pillars.add(p);
    }

    public static void spawnKill(LivingEntity e) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float s = Modules.KILL_SCALE.f();
        double x = e.getX(), y = e.getY(), z = e.getZ(), h = e.getHeight();
        int style = Modules.KILL_STYLE.index;

        if (style == 0) {                                   // ---- Souls: light pillar, converging helix, rising soul orb
            pillar(x, y, z, 0.26 * s, 4.8 * s, 34, r.nextInt(360));
            ring(x, y + 0.05, z, 0.2f, 2.3f * s, 18, 0.14f, 0, 0);
            ring(x, y + 0.05, z, 0.2f, 1.6f * s, 16, 0.10f, 5, 120);
            for (int i = 0; i < 32 && particles.size() < 2200; i++) {
                P p = part(x, y + (i % 16) * 0.05, z, 0, 0.085 + r.nextDouble() * 0.03, 0,
                        34 + r.nextInt(10), 0.06f + r.nextFloat() * 0.04f, 0, Colors.accent(i * 11), 0, 1.0);
                p.spiral = true;
                p.cx = x;
                p.cz = z;
                p.ang = i * Math.PI / 8;
                p.angVel = 0.30;
                p.rad = (0.95 + (i % 3) * 0.12) * s;
                p.radVel = -0.016 * s;
            }
            P orb = part(x, y + h * 0.6, z, 0, 0.06, 0, 46, 0.2f * s, 0, 0xFFFFFFFF, 0, 1.0);
            orb.trail = true;
            for (int i = 0; i < 14; i++) {
                double a = r.nextDouble() * Math.PI * 2, sp = 0.04 + r.nextDouble() * 0.06;
                part(x, y + h * 0.5, z, Math.cos(a) * sp, 0.03 + r.nextDouble() * 0.05, Math.sin(a) * sp,
                        26 + r.nextInt(16), 0.05f + r.nextFloat() * 0.05f, 1, Colors.accent(i * 25), -0.002, 0.95);
            }
            sound(w, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
        } else if (style == 1) {                            // ---- Shatter: star shards blow outward
            ring(x, y + h * 0.5, z, 0.1f, 2.6f * s, 12, 0.12f, 0, 0);
            ring(x, y + 0.05, z, 0.3f, 3.0f * s, 16, 0.16f, 2, 80);
            part(x, y + h * 0.5, z, 0, 0, 0, 7, 0.9f * s, 0, 0xFFFFFFFF, 0, 1.0);
            for (int i = 0; i < 64 && particles.size() < 2200; i++) {
                double dx = r.nextGaussian(), dy = r.nextGaussian() * 0.7 + 0.2, dz = r.nextGaussian();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-6;
                double sp = (0.14 + r.nextDouble() * 0.32) * s;
                part(x, y + h * 0.5, z, dx / len * sp, dy / len * sp, dz / len * sp,
                        26 + r.nextInt(18), 0.07f + r.nextFloat() * 0.08f, 1, Colors.accent(i * 7), 0.025, 0.92);
            }
            sound(w, x, y, z, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 0.8f);
        } else {                                            // ---- Lightning: bolt from the sky + shards
            for (int k = 0; k < 2; k++) {
                Bolt b = new Bolt();
                b.max = 11 + k * 3;
                b.off = k * 70;
                b.width = k == 0 ? 0.34 : 0.2;
                Vec3d top = new Vec3d(x + r.nextGaussian() * (k * 1.4), y + 15, z + r.nextGaussian() * (k * 1.4));
                int segs = 16;
                for (int i = 0; i <= segs; i++) {
                    double t = i / (double) segs;
                    double jit = (i == 0 || i == segs) ? 0 : 0.75 * (1.0 - t * 0.55);
                    b.pts.add(new Vec3d(top.x + (x - top.x) * t + r.nextGaussian() * jit,
                            top.y + (y + 0.1 - top.y) * t,
                            top.z + (z - top.z) * t + r.nextGaussian() * jit));
                }
                bolts.add(b);
                if (k == 0) {                                // two short side branches
                    for (int br = 0; br < 2; br++) {
                        Bolt side = new Bolt();
                        side.max = 8;
                        side.off = 40 + br * 50;
                        side.width = 0.14;
                        Vec3d from = b.pts.get(4 + r.nextInt(8));
                        Vec3d cur = from;
                        double dirx = r.nextGaussian(), dirz = r.nextGaussian();
                        side.pts.add(cur);
                        for (int i = 0; i < 5; i++) {
                            cur = cur.add(dirx * 0.45 + r.nextGaussian() * 0.35, -0.7 - r.nextDouble() * 0.5, dirz * 0.45 + r.nextGaussian() * 0.35);
                            side.pts.add(cur);
                        }
                        bolts.add(side);
                    }
                }
            }
            ring(x, y + 0.05, z, 0.2f, 2.8f * s, 10, 0.2f, 0, 0);
            part(x, y + 0.4, z, 0, 0, 0, 9, 1.1f * s, 0, 0xFFFFFFFF, 0, 1.0);
            for (int i = 0; i < 26 && particles.size() < 2200; i++) {
                double a = r.nextDouble() * Math.PI * 2, sp = (0.06 + r.nextDouble() * 0.22) * s;
                part(x, y + 0.2, z, Math.cos(a) * sp, 0.12 + r.nextDouble() * 0.3, Math.sin(a) * sp,
                        16 + r.nextInt(12), 0.05f + r.nextFloat() * 0.06f, 1, Colors.accent(i * 14), 0.03, 0.92);
            }
            sound(w, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
        }
    }

    private static void sound(ClientWorld w, double x, double y, double z, net.minecraft.sound.SoundEvent ev, float vol, float pitch) {
        if (Modules.KILL_SOUND.value) w.playSound(x, y, z, ev, SoundCategory.PLAYERS, vol, pitch, false);
    }

    /** Ground cracks used by the mace impact. (x, y, z) is the top of the block that was hit. */
    public static void spawnCracks(double x, double y, double z, float scale) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Crack c = new Crack();
        c.max = 110;
        c.width = 0.085 * Math.max(0.7, scale);
        int rays = 8 + r.nextInt(4);
        for (int i = 0; i < rays; i++) {
            double a = (i + r.nextDouble() * 0.7) * Math.PI * 2 / rays;
            double len = (2.4 + r.nextDouble() * 1.3) * scale;
            List<Vec3d> main = new ArrayList<>();
            double cx = x, cz = z;
            main.add(new Vec3d(x, groundY(w, x, z, y) + 0.02, z));
            for (double d = 0.35; d <= len; d += 0.35) {
                a += (r.nextDouble() - 0.5) * 0.55;
                cx += Math.cos(a) * 0.35;
                cz += Math.sin(a) * 0.35;
                main.add(new Vec3d(cx, groundY(w, cx, cz, y) + 0.02, cz));
                if (d > 0.7 && r.nextInt(100) < 38) {        // side branch
                    double ba = a + (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.5);
                    List<Vec3d> br = new ArrayList<>();
                    br.add(main.get(main.size() - 1));
                    double bx = cx, bz = cz;
                    int steps = 2 + r.nextInt(3);
                    for (int k = 0; k < steps; k++) {
                        ba += (r.nextDouble() - 0.5) * 0.5;
                        bx += Math.cos(ba) * 0.3;
                        bz += Math.sin(ba) * 0.3;
                        br.add(new Vec3d(bx, groundY(w, bx, bz, y) + 0.02, bz));
                    }
                    c.branches.add(br);
                }
            }
            c.branches.add(main);
        }
        cracks.add(c);
        ring(x, y + 0.05, z, 0.3f, 3.0f * scale, 12, 0.16f, 0, 25);
    }

    /** Height of the walkable surface near (x, z), starting the search at the block row of y0. */
    private static double groundY(ClientWorld w, double x, double z, double y0) {
        int base = MathHelper.floor(y0 - 0.02);
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos pos = new BlockPos(MathHelper.floor(x), base + dy, MathHelper.floor(z));
            BlockState st = w.getBlockState(pos);
            VoxelShape shape = st.getCollisionShape(w, pos);
            if (shape.isEmpty()) continue;
            BlockPos above = pos.up();
            if (!w.getBlockState(above).getCollisionShape(w, above).isEmpty()) continue;
            return pos.getY() + shape.getMax(Direction.Axis.Y);
        }
        return y0;
    }

    private static void spawnArc(ClientPlayerEntity pl) {
        Vec3d eye = pl.getEyePos();
        Vec3d fwd = pl.getRotationVec(1f).normalize();
        Vec3d right = fwd.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-6) return;
        right = right.normalize();
        Vec3d up = right.crossProduct(fwd).normalize();
        arcFlip = !arcFlip;
        double roll = Math.toRadians(arcFlip ? 28 : -28) + ThreadLocalRandom.current().nextGaussian() * 0.1;
        Vec3d axis = right.multiply(Math.cos(roll)).add(up.multiply(Math.sin(roll)));
        Arc a = new Arc();
        a.max = 8;
        a.off = ThreadLocalRandom.current().nextInt(360);
        int n = 16;
        for (int i = 0; i < n; i++) {
            double u = (i / (double) (n - 1)) * 2 - 1;         // -1..1
            if (arcFlip) u = -u;
            double th = u * Math.toRadians(68);
            Vec3d pt = eye.add(fwd.multiply(1.55 * Math.cos(th))).add(axis.multiply(1.55 * Math.sin(th))).add(0, -0.3, 0);
            a.pts.add(pt);
        }
        arcs.add(a);
    }

    // ================================================================== tick
    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity pl = mc.player;
        if (pl == null || mc.world == null) {
            particles.clear();
            rings.clear();
            pillars.clear();
            bolts.clear();
            arcs.clear();
            cracks.clear();
            return;
        }
        ticks++;

        // swing detection (rising edge of the hand swing progress) -> slash arc
        float sp = pl.getHandSwingProgress(1f);
        if (sp > 0f && lastSwing <= 0.001f && Modules.SWING_ANIM.enabled && Modules.SWING_TRAIL.value
                && pl.getMainHandStack().isIn(ItemTags.SWORDS)) {
            spawnArc(pl);
        }
        lastSwing = sp;

        List<P> born = null;
        for (P p : particles) {
            p.px = p.x;
            p.py = p.y;
            p.pz = p.z;
            if (p.spiral) {
                p.ang += p.angVel;
                p.rad = Math.max(0.04, p.rad + p.radVel);
                p.y += p.vy;
                p.x = p.cx + Math.cos(p.ang) * p.rad;
                p.z = p.cz + Math.sin(p.ang) * p.rad;
            } else {
                p.x += p.vx;
                p.y += p.vy;
                p.z += p.vz;
                p.vx *= p.drag;
                p.vz *= p.drag;
                p.vy = (p.vy - p.grav) * p.drag;
            }
            p.life--;
            if (p.trail && particles.size() < 2200) {
                if (born == null) born = new ArrayList<>();
                P t = new P();
                t.x = t.px = p.x;
                t.y = t.py = p.y;
                t.z = t.pz = p.z;
                t.vx = (Math.random() - 0.5) * 0.02;
                t.vy = -0.004;
                t.vz = (Math.random() - 0.5) * 0.02;
                t.life = t.max = 14;
                t.size = 0.05f;
                t.shape = 1;
                t.drag = 0.95;
                t.color = Colors.accent(ticks * 9);
                born.add(t);
            }
        }
        if (born != null) particles.addAll(born);
        particles.removeIf(p -> p.life <= 0);
        for (Ring r : rings) r.age++;
        rings.removeIf(r -> r.age >= r.max);
        for (Pillar p : pillars) p.age++;
        pillars.removeIf(p -> p.age >= p.max);
        for (Bolt b : bolts) b.age++;
        bolts.removeIf(b -> b.age >= b.max);
        for (Arc a : arcs) a.age++;
        arcs.removeIf(a -> a.age >= a.max);
        for (Crack c : cracks) c.age++;
        cracks.removeIf(c -> c.age >= c.max);
    }

    // ================================================================== render
    private static int cosColor(int i) {
        if (Modules.COS_THEME.value) return Colors.accent(i);
        float h = (Modules.COS_HUE.f() + i * 0.6f) % 360f / 360f;
        return 0xFF000000 | MathHelper.hsvToRgb(h, 0.62f, 1f);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity pl = mc.player;
        MatrixStack ms = ctx.matrixStack();
        if (pl == null || ms == null) return;

        boolean cosmetics = Modules.COSMETICS.enabled && !mc.options.getPerspective().isFirstPerson();
        if (particles.isEmpty() && rings.isEmpty() && pillars.isEmpty() && bolts.isEmpty()
                && arcs.isEmpty() && cracks.isEmpty() && !cosmetics) return;

        float pt = ctx.tickCounter().getTickDelta(false);
        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        camX = cp.x;
        camY = cp.y;
        camZ = cp.z;
        Matrix4f m = ms.peek().getPositionMatrix();
        Vector3f right = new Vector3f(1, 0, 0).rotate(cam.getRotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(cam.getRotation());
        long now = System.currentTimeMillis();

        // ---------- pass 1: normal alpha blend (dark ground cracks)
        if (!cracks.isEmpty()) {
            BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Crack c : cracks) {
                float grow = Math.min(1f, (c.age + pt) / 7f);
                float fade = c.age > c.max - 35 ? Math.max(0f, (c.max - c.age - pt) / 35f) : 1f;
                int a = (int) (235 * fade);
                if (a < 4) continue;
                for (List<Vec3d> br : c.branches) groundRibbon(b, m, br, grow, c.width, Colors.alpha(0x140D09, a));
            }
            BuiltBuffer built = b.endNullable();
            if (built != null) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
                BufferRenderer.drawWithGlobalProgram(built);
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
        }

        // ---------- pass 2: additive glow
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // glowing core of the cracks
        for (Crack c : cracks) {
            if (!Modules.MACE_GLOW.value) break;
            float grow = Math.min(1f, (c.age + pt) / 7f);
            float fade = c.age > c.max - 60 ? Math.max(0f, (c.max - c.age - pt) / 60f) : 1f;
            float flick = 0.75f + 0.25f * (float) Math.sin(now / 140.0);
            int a = (int) (150 * fade * flick);
            if (a < 4) continue;
            for (List<Vec3d> br : c.branches) groundRibbon(b, m, br, grow, c.width * 0.38, Colors.alpha(0xFF7A1E, a));
        }

        // particles
        for (P p : particles) {
            double x = p.px + (p.x - p.px) * pt, y = p.py + (p.y - p.py) * pt, z = p.pz + (p.z - p.pz) * pt;
            float f = Math.max(0f, (p.life - pt) / p.max);
            int a = (int) (235 * Math.pow(f, 0.65));
            if (a < 4) continue;
            double s = p.size * (0.55 + 0.45 * f);
            if (p.shape == 1) {
                star(b, m, x, y, z, s * 3.4, Colors.alpha(p.color, a), right, up);
                glow(b, m, x, y, z, s * 1.5, Colors.alpha(p.color, a / 3), right, up);
            } else {
                glow(b, m, x, y, z, s * 2.8, Colors.alpha(p.color, a), right, up);
                glow(b, m, x, y, z, s * 0.9, Colors.alpha(0xFFFFFF, a), right, up);
            }
        }

        // rings
        for (Ring r : rings) {
            if (r.age < 0) continue;
            float t = Math.min(1f, (r.age + pt) / r.max);
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            double rad = r.r0 + (r.r1 - r.r0) * e;
            double w = r.width * (1.0 - t * 0.6);
            int a = (int) (220 * Math.pow(1.0 - t, 1.4));
            if (a < 4) continue;
            int segs = 56;
            for (int i = 0; i < segs; i++) {
                double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
                int col = Colors.alpha(Colors.accent(r.off + i * 6), a);
                ringBand(b, m, r.x, r.y, r.z, rad - w * 2.2, rad, a0, a1, Colors.alpha(col, 0), col);
                ringBand(b, m, r.x, r.y, r.z, rad, rad + w * 0.6, a0, a1, col, Colors.alpha(col, 0));
            }
        }

        // pillars
        for (Pillar p : pillars) {
            float t = Math.min(1f, (p.age + pt) / p.max);
            double h = p.height * Math.min(1f, t * 3.5f);
            double rad = p.rad * (1.0 - t * 0.85);
            int a = (int) (190 * (1.0 - t));
            if (a < 4) continue;
            cylinder(b, m, p.x, p.y, p.z, rad, h, Colors.alpha(Colors.accent(p.off), a));
            cylinder(b, m, p.x, p.y, p.z, rad * 0.35, h, Colors.alpha(0xFFFFFF, a));
        }

        // lightning bolts
        for (Bolt bt : bolts) {
            float t = (bt.age + pt) / bt.max;
            if (t >= 1f) continue;
            float flick = (bt.age % 2 == 0) ? 1f : 0.7f;
            int a = (int) (255 * (1f - t) * flick);
            lineRibbon(b, m, bt.pts, bt.width * (1.0 - t * 0.5), Colors.alpha(Colors.accent(bt.off), a));
            lineRibbon(b, m, bt.pts, bt.width * 0.3 * (1.0 - t * 0.5), Colors.alpha(0xFFFFFF, a));
        }

        // slash arcs
        for (Arc ar : arcs) {
            float g = Math.min(1f, (ar.age + pt) / 3f);
            float fade = ar.age > 3 ? Math.max(0f, 1f - (ar.age + pt - 3f) / (ar.max - 3f)) : 1f;
            int n = ar.pts.size();
            double head = g * (n - 1), tail = Math.max(0, g * 1.4 - 0.4) * (n - 1);
            for (int i = 0; i < n - 1; i++) {
                if (i + 1 < tail || i > head) continue;
                double mid = (i + 0.5) / (n - 1);
                double wdt = 0.20 * Math.sin(mid * Math.PI) + 0.02;
                int a = (int) (230 * fade * Math.min(1.0, (i + 1 - tail) * 0.5));
                if (a < 4) continue;
                int col = Colors.alpha(Colors.accent(ar.off + i * 12), a);
                lineRibbon(b, m, ar.pts.subList(i, i + 2), wdt, col);
                lineRibbon(b, m, ar.pts.subList(i, i + 2), wdt * 0.35, Colors.alpha(0xFFFFFF, a));
            }
        }

        // cosmetics
        if (cosmetics) {
            Vec3d pos = pl.getLerpedPos(pt);
            float sc = Modules.COS_SCALE.f();
            double tt = now / 1000.0 * Modules.COS_SPEED.value;
            int style = Modules.COS_STYLE.index;
            if (style == 0 || style == 4) wings(b, m, pl, pos, pt, tt, sc);
            if (style == 1 || style == 4) halo(b, m, pl, pos, tt, sc, right, up);
            if (style == 2) orbits(b, m, pl, pos, tt, sc, right, up);
            if (style == 3) helix(b, m, pl, pos, tt, sc, right, up);
        }

        BuiltBuffer built = b.endNullable();
        if (built == null) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ================================================================== cosmetics
    private static void wings(BufferBuilder b, Matrix4f m, ClientPlayerEntity pl, Vec3d pos, float pt, double tt, float sc) {
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(pt, pl.prevBodyYaw, pl.bodyYaw));
        Vec3d f = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d r = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d back = f.multiply(-1);
        double hgt = pl.isSneaking() ? 1.05 : 1.3;
        Vec3d base = pos.add(0, hgt, 0).add(back.multiply(pl.isSneaking() ? 0.28 : 0.2));
        double flap = Math.sin(tt * 5.2) * 0.36 + 0.12;
        double[] len = {0.85, 1.15, 1.45, 1.55, 1.25, 0.9};
        int n = len.length;
        for (int side = -1; side <= 1; side += 2) {
            Vec3d[] tips = new Vec3d[n];
            for (int i = 0; i < n; i++) {
                double a = 0.18 + i * 0.30 + flap;
                Vec3d d = r.multiply(side * Math.sin(a)).add(0, Math.cos(a), 0).add(back.multiply(0.5)).normalize();
                Vec3d wdir = f.crossProduct(d).normalize();
                double L = len[i] * sc, wd = 0.085 * sc * (1 + (i == 2 || i == 3 ? 0.4 : 0));
                Vec3d b0 = base.add(d.multiply(0.12 * sc));
                Vec3d tip = base.add(d.multiply(L));
                tips[i] = tip;
                int c0 = cosColor(i * 18 + (side > 0 ? 0 : 7));
                tri(b, m, b0.x - wdir.x * wd, b0.y - wdir.y * wd, b0.z - wdir.z * wd, Colors.alpha(c0, 0xC8),
                        b0.x + wdir.x * wd, b0.y + wdir.y * wd, b0.z + wdir.z * wd, Colors.alpha(c0, 0xC8),
                        tip.x, tip.y, tip.z, Colors.alpha(c0, 0x00));
                glow(b, m, tip.x, tip.y, tip.z, 0.06 * sc, Colors.alpha(c0, 0x90), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0));
            }
            for (int i = 0; i < n - 1; i++) {                       // soft membrane between feathers
                int c = cosColor(i * 18);
                tri(b, m, base.x, base.y, base.z, Colors.alpha(c, 0x38),
                        tips[i].x, tips[i].y, tips[i].z, Colors.alpha(c, 0x00),
                        tips[i + 1].x, tips[i + 1].y, tips[i + 1].z, Colors.alpha(c, 0x00));
            }
        }
    }

    private static void halo(BufferBuilder b, Matrix4f m, ClientPlayerEntity pl, Vec3d pos, double tt, float sc,
                             Vector3f right, Vector3f up) {
        double cy = pos.y + pl.getHeight() + 0.28 * sc + Math.sin(tt * 2.2) * 0.04;
        double rad = 0.34 * sc, tilt = 0.12 * Math.sin(tt * 1.3);
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            int col = cosColor(i * 9 + (int) (tt * 60));
            double y0 = cy + Math.sin(a0) * rad * tilt, y1 = cy + Math.sin(a1) * rad * tilt;
            quad(b, m, pos.x + Math.cos(a0) * (rad - 0.045 * sc), y0, pos.z + Math.sin(a0) * (rad - 0.045 * sc), Colors.alpha(col, 0),
                    pos.x + Math.cos(a0) * rad, y0, pos.z + Math.sin(a0) * rad, Colors.alpha(col, 0xF0),
                    pos.x + Math.cos(a1) * rad, y1, pos.z + Math.sin(a1) * rad, Colors.alpha(col, 0xF0),
                    pos.x + Math.cos(a1) * (rad - 0.045 * sc), y1, pos.z + Math.sin(a1) * (rad - 0.045 * sc), Colors.alpha(col, 0));
            quad(b, m, pos.x + Math.cos(a0) * rad, y0, pos.z + Math.sin(a0) * rad, Colors.alpha(col, 0xF0),
                    pos.x + Math.cos(a0) * (rad + 0.05 * sc), y0, pos.z + Math.sin(a0) * (rad + 0.05 * sc), Colors.alpha(col, 0),
                    pos.x + Math.cos(a1) * (rad + 0.05 * sc), y1, pos.z + Math.sin(a1) * (rad + 0.05 * sc), Colors.alpha(col, 0),
                    pos.x + Math.cos(a1) * rad, y1, pos.z + Math.sin(a1) * rad, Colors.alpha(col, 0xF0));
        }
        for (int k = 0; k < 4; k++) {                               // sparkles circling the halo
            double a = tt * 2.0 + k * Math.PI / 2;
            double tw = 0.5 + 0.5 * Math.sin(tt * 6 + k * 1.7);
            double sx = pos.x + Math.cos(a) * rad, sz = pos.z + Math.sin(a) * rad, sy = cy + Math.sin(a) * rad * tilt;
            star(b, m, sx, sy, sz, 0.09 * sc * (0.5 + tw), Colors.alpha(cosColor(k * 40), (int) (120 + 135 * tw)), right, up);
        }
    }

    private static void orbits(BufferBuilder b, Matrix4f m, ClientPlayerEntity pl, Vec3d pos, double tt, float sc,
                               Vector3f right, Vector3f up) {
        int orbs = 4;
        for (int k = 0; k < orbs; k++) {
            for (int j = 0; j < 9; j++) {                           // short comet tail
                double a = tt * 2.4 + k * Math.PI * 2 / orbs - j * 0.11;
                double y = pos.y + pl.getHeight() * 0.55 + Math.sin(tt * 1.6 + k * 1.6 - j * 0.05) * 0.38 * sc;
                double rr = 0.95 * sc;
                double f = 1.0 - j / 9.0;
                int col = Colors.alpha(cosColor(k * 60 + j * 4), (int) (230 * f * f));
                glow(b, m, pos.x + Math.cos(a) * rr, y, pos.z + Math.sin(a) * rr, 0.13 * sc * (0.35 + 0.65 * f), col, right, up);
                if (j == 0) glow(b, m, pos.x + Math.cos(a) * rr, y, pos.z + Math.sin(a) * rr, 0.05 * sc, Colors.alpha(0xFFFFFF, 255), right, up);
            }
        }
    }

    private static void helix(BufferBuilder b, Matrix4f m, ClientPlayerEntity pl, Vec3d pos, double tt, float sc,
                              Vector3f right, Vector3f up) {
        int pts = 34;
        double h = pl.getHeight() + 0.25;
        for (int strand = 0; strand < 2; strand++) {
            for (int i = 0; i < pts; i++) {
                double t = i / (double) pts;
                double a = tt * 2.0 + t * Math.PI * 4 + strand * Math.PI;
                double rr = (0.52 + 0.1 * Math.sin(t * 9 + tt * 2)) * sc;
                double y = pos.y + t * h;
                int col = Colors.alpha(cosColor(i * 7 + strand * 90), (int) (220 * Math.sin(t * Math.PI)));
                glow(b, m, pos.x + Math.cos(a) * rr, y, pos.z + Math.sin(a) * rr, 0.075 * sc, col, right, up);
            }
        }
    }

    // ================================================================== primitives
    private static void vtx(BufferBuilder b, Matrix4f m, double x, double y, double z, int c) {
        b.vertex(m, (float) (x - camX), (float) (y - camY), (float) (z - camZ)).color(c);
    }

    private static void tri(BufferBuilder b, Matrix4f m,
                            double x1, double y1, double z1, int c1,
                            double x2, double y2, double z2, int c2,
                            double x3, double y3, double z3, int c3) {
        vtx(b, m, x1, y1, z1, c1);
        vtx(b, m, x2, y2, z2, c2);
        vtx(b, m, x3, y3, z3, c3);
        vtx(b, m, x3, y3, z3, c3);
    }

    private static void quad(BufferBuilder b, Matrix4f m,
                             double x1, double y1, double z1, int c1,
                             double x2, double y2, double z2, int c2,
                             double x3, double y3, double z3, int c3,
                             double x4, double y4, double z4, int c4) {
        vtx(b, m, x1, y1, z1, c1);
        vtx(b, m, x2, y2, z2, c2);
        vtx(b, m, x3, y3, z3, c3);
        vtx(b, m, x4, y4, z4, c4);
    }

    private static void glow(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, double s,
                             int color, Vector3f r, Vector3f u) {
        int rim = Colors.alpha(color, 0);
        int seg = 8;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            double c0 = Math.cos(a0) * s, s0 = Math.sin(a0) * s, c1 = Math.cos(a1) * s, s1 = Math.sin(a1) * s;
            tri(b, m, cx, cy, cz, color,
                    cx + r.x * c0 + u.x * s0, cy + r.y * c0 + u.y * s0, cz + r.z * c0 + u.z * s0, rim,
                    cx + r.x * c1 + u.x * s1, cy + r.y * c1 + u.y * s1, cz + r.z * c1 + u.z * s1, rim);
        }
    }

    private static void star(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, double s,
                             int color, Vector3f r, Vector3f u) {
        int rim = Colors.alpha(color, 0);
        double t = s * 0.16;
        quad(b, m,
                cx + r.x * s, cy + r.y * s, cz + r.z * s, rim,
                cx + u.x * t, cy + u.y * t, cz + u.z * t, color,
                cx - r.x * s, cy - r.y * s, cz - r.z * s, rim,
                cx - u.x * t, cy - u.y * t, cz - u.z * t, color);
        quad(b, m,
                cx + u.x * s, cy + u.y * s, cz + u.z * s, rim,
                cx + r.x * t, cy + r.y * t, cz + r.z * t, color,
                cx - u.x * s, cy - u.y * s, cz - u.z * s, rim,
                cx - r.x * t, cy - r.y * t, cz - r.z * t, color);
    }

    private static void ringBand(BufferBuilder b, Matrix4f m, double cx, double cy, double cz,
                                 double rIn, double rOut, double a0, double a1, int cIn, int cOut) {
        double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
        quad(b, m,
                cx + c0 * rIn, cy, cz + s0 * rIn, cIn,
                cx + c0 * rOut, cy, cz + s0 * rOut, cOut,
                cx + c1 * rOut, cy, cz + s1 * rOut, cOut,
                cx + c1 * rIn, cy, cz + s1 * rIn, cIn);
    }

    private static void cylinder(BufferBuilder b, Matrix4f m, double x, double y, double z,
                                 double rad, double h, int color) {
        int top = Colors.alpha(color, 0);
        int sides = 10;
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI * 2 * i / sides, a1 = Math.PI * 2 * (i + 1) / sides;
            quad(b, m,
                    x + Math.cos(a0) * rad, y, z + Math.sin(a0) * rad, color,
                    x + Math.cos(a1) * rad, y, z + Math.sin(a1) * rad, color,
                    x + Math.cos(a1) * rad, y + h, z + Math.sin(a1) * rad, top,
                    x + Math.cos(a0) * rad, y + h, z + Math.sin(a0) * rad, top);
        }
    }

    /** Camera-facing glowing line: opaque centre, transparent edges. */
    private static void lineRibbon(BufferBuilder b, Matrix4f m, List<Vec3d> pts, double width, int color) {
        Vec3d cam = new Vec3d(camX, camY, camZ);
        int edge = Colors.alpha(color, 0);
        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3d a = pts.get(i), c = pts.get(i + 1);
            Vec3d dir = c.subtract(a);
            if (dir.lengthSquared() < 1e-8) continue;
            Vec3d mid = a.add(c).multiply(0.5);
            Vec3d side = dir.crossProduct(cam.subtract(mid));
            if (side.lengthSquared() < 1e-10) continue;
            side = side.normalize().multiply(width);
            quad(b, m,
                    a.x - side.x, a.y - side.y, a.z - side.z, edge,
                    a.x, a.y, a.z, color,
                    c.x, c.y, c.z, color,
                    c.x - side.x, c.y - side.y, c.z - side.z, edge);
            quad(b, m,
                    a.x, a.y, a.z, color,
                    a.x + side.x, a.y + side.y, a.z + side.z, edge,
                    c.x + side.x, c.y + side.y, c.z + side.z, edge,
                    c.x, c.y, c.z, color);
        }
    }

    /** Flat ribbon lying on the ground along a polyline, revealed up to 'grow' (0..1) of its length. */
    private static void groundRibbon(BufferBuilder b, Matrix4f m, List<Vec3d> pts, float grow, double width, int color) {
        int n = pts.size();
        double reach = grow * (n - 1);
        for (int i = 0; i < n - 1 && i < reach; i++) {
            Vec3d a = pts.get(i);
            Vec3d c = pts.get(i + 1);
            if (reach - i < 1) c = a.add(c.subtract(a).multiply(reach - i));
            double dx = c.x - a.x, dz = c.z - a.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-6) continue;
            double px = -dz / len, pz = dx / len;
            double w0 = width * (1.0 - i / (double) n * 0.65), w1 = width * (1.0 - (i + 1) / (double) n * 0.65);
            quad(b, m,
                    a.x - px * w0, a.y, a.z - pz * w0, color,
                    a.x + px * w0, a.y, a.z + pz * w0, color,
                    c.x + px * w1, c.y, c.z + pz * w1, color,
                    c.x - px * w1, c.y, c.z - pz * w1, color);
        }
    }
}
