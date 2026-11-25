package com.vodx.visuals.mixin;

import com.vodx.visuals.Modules;
import com.vodx.visuals.Sky;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void vodx$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        ClientWorld self = (ClientWorld) (Object) this;
        if (Sky.active(self)) cir.setReturnValue(Sky.sky(self, tickDelta));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void vodx$clouds(float tickDelta, CallbackInfoReturnable<Integer> cir) {
        ClientWorld self = (ClientWorld) (Object) this;
        if (Sky.active(self) && Modules.SKY_CLOUDS.value) cir.setReturnValue(Sky.clouds(self, tickDelta));
    }
}
