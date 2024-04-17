package destructionphysics.mixin;

import destructionphysics.entity.AdvancedFallingBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {
    @Redirect(method = "onExploded", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;shouldDropItemsOnExplosion(Lnet/minecraft/world/explosion/Explosion;)Z"))
    private boolean sendFlying(Block instance, Explosion arg0, BlockState state, World world, BlockPos pos, Explosion explosion) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return AdvancedFallingBlockEntity.spawnFromExplosion(world, pos, state, explosion, blockEntity == null ? null : blockEntity.createNbt()) == null;
    }
}
