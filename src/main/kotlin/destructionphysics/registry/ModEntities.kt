package destructionphysics.registry

import destructionphysics.DestructionPhysics
import destructionphysics.entity.AdvancedFallingBlockEntity
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

object ModEntities {
    fun register() {}

    val ADVANCED_FALLING_BLOCK_ENTITY = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier(DestructionPhysics.MOD_ID, "advanced_falling_block_entity"),
        FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::AdvancedFallingBlockEntity)
            .dimensions(EntityDimensions.fixed(0.98f, 0.98f))
            .trackRangeChunks(10)
            .trackedUpdateRate(20)
            .build(),
    )
}
