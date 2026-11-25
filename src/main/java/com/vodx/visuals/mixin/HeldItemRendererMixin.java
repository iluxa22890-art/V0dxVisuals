package com.vodx.visuals.mixin;

import com.vodx.visuals.SwingAnim;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    // Adds our heavy swing on top of the base pose (the matrix stack is pushed/popped by the caller).
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void vodx$swing(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
                            ItemStack item, float equipProgress, MatrixStack matrices,
                            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        SwingAnim.apply(player, hand, swingProgress, item, matrices);
    }

    // Removes the vanilla swing offset while our animation is active (otherwise both would add up).
    @ModifyArg(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FFLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V"),
            index = 0, require = 0)
    private float vodx$noVanillaSwing(float swingProgress) {
        return SwingAnim.overriding ? 0f : swingProgress;
    }
}
