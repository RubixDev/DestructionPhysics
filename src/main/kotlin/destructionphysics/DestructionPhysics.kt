package destructionphysics

import destructionphysics.entity.AdvancedFallingBlockEntity
import destructionphysics.registry.ModEntities
import net.fabricmc.api.ModInitializer
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.PistonBlock
import net.minecraft.block.TntBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DestructionPhysics : ModInitializer {
    const val MOD_ID = "destruction-physics"
    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    private const val MAX_CONNECTED_BLOCKS = 1024

    override fun onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        LOGGER.info("Hello Fabric world!")

        ModEntities.register()
    }

    fun BlockState.canFall(world: World, pos: BlockPos): Boolean =
        block !is TntBlock && PistonBlock.isMovable(this, world, pos, Direction.NORTH, true, Direction.NORTH)

    fun causeNeighboringToFall(world: World, origin: BlockPos) {
        outer@ for (startPos in Direction.entries.map { origin.offset(it) }) {
            val fallPositions = mutableSetOf<BlockPos>()
            val ignorePositions = mutableSetOf<BlockPos>()
            val queue = ArrayDeque<BlockPos>().apply { addLast(startPos) }
            while (fallPositions.size <= MAX_CONNECTED_BLOCKS) {
                val pos = queue.removeFirstOrNull() ?: break
                if (fallPositions.contains(pos) || ignorePositions.contains(pos)) continue
                val state = world.getBlockState(pos)
                // TODO: which other blocks should count as air here?
                if (state.isAir || state.isOf(Blocks.FIRE)) continue
                if (state.canFall(world, pos)) {
                    fallPositions.add(pos)
                } else {
                    ignorePositions.add(pos)
                }
                for (direction in Direction.entries) {
                    queue.addLast(pos.offset(direction))
                }
            }
            if (fallPositions.size == MAX_CONNECTED_BLOCKS + 1) continue@outer
            for (pos in fallPositions) {
                AdvancedFallingBlockEntity.spawnFromBlock(world, pos, world.getBlockState(pos))
            }
        }
    }
}
