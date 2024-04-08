package destructionphysics.mixin;

import destructionphysics.entity.AdvancedFallingBlockEntity;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlock.class)
public class FallingBlockMixin {
    @Inject(method = "scheduledTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/FallingBlockEntity;spawnFromBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Lnet/minecraft/entity/FallingBlockEntity;"), cancellable = true)
    private void spawnAdvancedFromBlock(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
        AdvancedFallingBlockEntity.spawnFromBlock(world, pos, state);
        ci.cancel();
    }
}
