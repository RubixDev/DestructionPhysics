package destructionphysics

import destructionphysics.entity.AdvancedFallingBlockEntityRenderer
import destructionphysics.registry.ModEntities
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

object DestructionPhysicsClient : ClientModInitializer {
    override fun onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ADVANCED_FALLING_BLOCK_ENTITY, ::AdvancedFallingBlockEntityRenderer)
    }
}
