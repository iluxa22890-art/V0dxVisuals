package com.vodx.visuals.mixin;

import com.vodx.visuals.Modules;
import com.vodx.visuals.Sky;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    // Horizon / fog colour, so the sky fades into a matching colour instead of the vanilla blue.
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void vodx$fog(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance,
                                 float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        if (!Sky.active(world) || !Modules.SKY_FOG.value) return;
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;   // keep water / lava fog
        int c = Sky.fog(world, tickDelta);
        cir.setReturnValue(new Vector4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f));
    }
}
