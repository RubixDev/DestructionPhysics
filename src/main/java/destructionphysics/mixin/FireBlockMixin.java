package destructionphysics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import destructionphysics.entity.AdvancedFallingBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireBlock.class)
public class FireBlockMixin {
    @WrapOperation(method = "trySpreadingFire", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;removeBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"))
    private boolean fallFromFire(World world, BlockPos pos, boolean move, Operation<Boolean> original) {
        BlockState state = world.getBlockState(pos);
        boolean res = original.call(world, pos, move);
        AdvancedFallingBlockEntity.spawnFromFire(world, pos, state, false);
        return res;
    }

    @WrapOperation(method = "trySpreadingFire", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean fallFromFire(World world, BlockPos pos, BlockState fire, int flags, Operation<Boolean> original) {
        BlockState state = world.getBlockState(pos);
        boolean res = original.call(world, pos, fire, flags);
        AdvancedFallingBlockEntity.spawnFromFire(world, pos, state, true);
        return res;
    }
}
