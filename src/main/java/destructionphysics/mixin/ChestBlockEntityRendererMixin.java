package destructionphysics.mixin;

import destructionphysics.entity.AdvancedFallingBlockEntityRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.ChestBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Arrays;

@Mixin(ChestBlockEntityRenderer.class)
public class ChestBlockEntityRendererMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;contains(Lnet/minecraft/state/property/Property;)Z", shift = At.Shift.BEFORE), ordinal = 0)
    private BlockState alwaysUseCachedState(BlockState original, BlockEntity entity) {
        if (Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(it -> it.getClassName().equals(AdvancedFallingBlockEntityRenderer.class.getName()))) {
            return entity.getCachedState();
        }
        return original;
    }
}
