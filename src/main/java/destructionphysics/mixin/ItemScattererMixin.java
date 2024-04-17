package destructionphysics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import destructionphysics.entity.AdvancedFallingBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(ItemScatterer.class)
public class ItemScattererMixin {
    @WrapOperation(method = "onStateReplaced", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ItemScatterer;spawn(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/inventory/Inventory;)V"))
    private static void dontScatterForFallingBlocks(World world, BlockPos pos, Inventory inventory, Operation<Void> original) {
        if (Arrays.stream(Thread.currentThread().getStackTrace()).noneMatch(it -> it.getClassName().equals(AdvancedFallingBlockEntity.class.getName()))) {
            original.call(world, pos, inventory);
        }
    }
}
