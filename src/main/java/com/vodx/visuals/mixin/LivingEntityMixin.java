package com.vodx.visuals.mixin;

import com.vodx.visuals.Modules;
import com.vodx.visuals.SwingAnim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    // Visual only: makes your own sword / mace swing last longer so the animation reads as "heavy".
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void vodx$longSwing(CallbackInfoReturnable<Integer> cir) {
        if (!Modules.SWING_ANIM.enabled) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != MinecraftClient.getInstance().player || !SwingAnim.isHeavy(self.getMainHandStack())) return;
        cir.setReturnValue(Math.round(cir.getReturnValue() * (float) Modules.SWING_LENGTH.value));
    }
}
