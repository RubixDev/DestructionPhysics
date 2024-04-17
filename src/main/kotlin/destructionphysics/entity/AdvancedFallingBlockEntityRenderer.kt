package destructionphysics.entity

import destructionphysics.mixin.accessor.BlockRenderManagerAccessor
import destructionphysics.mixin.accessor.BuiltinModelItemRendererAccessor
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockRenderType
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random

@Environment(EnvType.CLIENT)
class AdvancedFallingBlockEntityRenderer(ctx: EntityRendererFactory.Context) :
    EntityRenderer<AdvancedFallingBlockEntity>(ctx) {
    private val blockRenderManager = ctx.blockRenderManager

    init {
        shadowRadius = 0.5f
    }

    override fun getTexture(entity: AdvancedFallingBlockEntity?): Identifier {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE
    }

    override fun render(
        entity: AdvancedFallingBlockEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        val blockState = entity.block
        if (blockState.renderType == BlockRenderType.INVISIBLE) return
        val world = entity.world

        matrices.push()
        val blockPos = BlockPos.ofFloored(entity.x, entity.boundingBox.maxY, entity.z)
        matrices.translate(-0.5, 0.0, -0.5)
        blockRenderManager.modelRenderer.render(
            world,
            blockRenderManager.getModel(blockState),
            blockState,
            blockPos,
            matrices,
            vertexConsumers.getBuffer(RenderLayers.getMovingBlockLayer(blockState)),
            false,
            Random.create(),
            blockState.getRenderingSeed(entity.slidePos),
            OverlayTexture.DEFAULT_UV,
        )
        if (blockState.renderType == BlockRenderType.ENTITYBLOCK_ANIMATED) {
            (blockState.block as? BlockEntityProvider)?.let { provider ->
                val blockEntity = provider.createBlockEntity(BlockPos.ORIGIN, blockState) ?: return@let
                try {
                    blockEntity.readNbt(entity.blockEntityData)
                } catch (_: Exception) {}
                ((blockRenderManager as BlockRenderManagerAccessor).builtinModelItemRenderer as BuiltinModelItemRendererAccessor).blockEntityRenderDispatcher.renderEntity(
                    blockEntity,
                    matrices,
                    vertexConsumers,
                    light,
                    OverlayTexture.DEFAULT_UV,
                )
            }
        }
        matrices.pop()
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }
}
