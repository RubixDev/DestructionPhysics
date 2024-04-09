package destructionphysics.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin extends Entity {
    public PersistentProjectileEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "onBlockHit", at = @At("TAIL"))
    private void boom(BlockHitResult blockHitResult, CallbackInfo ci) {
        //noinspection ConstantValue
        if ((Entity) this instanceof ArrowEntity && !this.isOnFire()) {
            TntEntity tnt = new TntEntity(getWorld(), getPos().x, getPos().y, getPos().z, null);
            tnt.setFuse(0);
            getWorld().spawnEntity(tnt);
            this.discard();
        }
    }
}
