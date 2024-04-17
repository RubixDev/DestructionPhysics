package destructionphysics

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import destructionphysics.entity.AdvancedFallingBlockEntity
import destructionphysics.registry.ModEntities
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

object DestructionPhysics : ModInitializer {
    const val MOD_ID = "destruction-physics"
    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
    private var MAX_CONNECTED_BLOCKS = 1024
    private val CONFIG_PATH = FabricLoader.getInstance().configDir.resolve("DestructionPhysics/maxConnectedBlocks")

    override fun onInitialize() {
        LOGGER.info("Initializing Destruction Physics!")
        CONFIG_PATH.parent.createDirectories()
        loadConfig()

        ModEntities.register()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val builder = literal<ServerCommandSource>(MOD_ID).requires { it.hasPermissionLevel(4) }
            builder.then(literal<ServerCommandSource>("maxConnectedBlocks").executes { ctx ->
                ctx.source.sendFeedback({ Text.literal("Option 'maxConnectedBlocks' is currently set to $MAX_CONNECTED_BLOCKS") }, false)
                1
            }.then(argument<ServerCommandSource?, Int?>("value", IntegerArgumentType.integer(0)).executes { ctx ->
                MAX_CONNECTED_BLOCKS = IntegerArgumentType.getInteger(ctx, "value")
                writeConfig()
                ctx.source.sendFeedback({ Text.literal("Option 'maxConnectedBlocks' is now set to $MAX_CONNECTED_BLOCKS") }, true)
                1
            }))
            dispatcher.register(builder)
        }
    }

    private fun loadConfig() {
        try {
            MAX_CONNECTED_BLOCKS = CONFIG_PATH.readText().toInt()
        } catch (err: Exception) {
            LOGGER.warn("Unable to read config, using default setting: $err")
        }
    }

    private fun writeConfig() {
        try {
            CONFIG_PATH.writeText("$MAX_CONNECTED_BLOCKS")
        } catch (err: Exception) {
            LOGGER.error("Failed to write config: $err")
        }
    }

    fun BlockState.canFall(world: World, pos: BlockPos): Boolean =
        block !is TntBlock && PistonBlock.isMovable(this, world, pos, Direction.NORTH, true, Direction.NORTH)

    val BlockState.isBreakable: Boolean get() = block is LeavesBlock
        || isIn(ConventionalBlockTags.GLASS_BLOCKS)
        || isIn(ConventionalBlockTags.GLASS_PANES)

    fun causeNeighboringToFall(world: World, origin: BlockPos) {
        for (direction in Direction.entries) {
            causeTouchingToFall(world, origin.offset(direction))
        }
    }

    private fun causeTouchingToFall(
        world: World,
        startPos: BlockPos,
        blockPredicate: (BlockState) -> Boolean = { true },
        finalPredicate: (Set<BlockPos>) -> Boolean = { true },
    ): Boolean {
        val fallPositions = mutableSetOf<BlockPos>()
        val ignorePositions = mutableSetOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>().apply { addLast(startPos) }
        while (fallPositions.size <= MAX_CONNECTED_BLOCKS) {
            val pos = queue.removeFirstOrNull() ?: break
            if (fallPositions.contains(pos) || ignorePositions.contains(pos)) continue
            val state = world.getBlockState(pos)
            // TODO: which other blocks should count as air here?
            if (state.isAir || state.isOf(Blocks.FIRE) || !blockPredicate(state)) continue
            if (state.canFall(world, pos)) {
                fallPositions.add(pos)
            } else {
                ignorePositions.add(pos)
            }
            for (direction in Direction.entries) {
                queue.addLast(pos.offset(direction))
            }
        }
        if (fallPositions.size == MAX_CONNECTED_BLOCKS + 1 || !finalPredicate(fallPositions)) return false
        for (pos in fallPositions.sortedBy { it.y }) {
            AdvancedFallingBlockEntity.spawnFromBlock(world, pos, world.getBlockState(pos))
        }
        return true
    }

    @JvmStatic
    fun makeTreeFall(world: World, origin: BlockPos, logType: Block) {
        // never recurse
        if (Thread.currentThread().stackTrace.let { trace ->
            val current = trace[1]
            trace.slice(2..trace.lastIndex).any {
                it.className == current.className
                    && it.methodName == current.methodName
            }
        }) {
            return
        }

        var leavesType: LeavesBlock? = null
        var shouldDrop = false

        for (direction in Direction.entries) {
            causeTouchingToFall(world, origin.offset(direction), { state ->
                if (leavesType == null) {
                    leavesType = state.block as? LeavesBlock
                }
                val isCorrectLog = state.block == logType
                val isCorrectLeaves = leavesType != null && (state.block == leavesType && !state.get(LeavesBlock.PERSISTENT))
                if (isCorrectLeaves) shouldDrop = true
                isCorrectLog || isCorrectLeaves
            }, { shouldDrop })
        }
    }
}
