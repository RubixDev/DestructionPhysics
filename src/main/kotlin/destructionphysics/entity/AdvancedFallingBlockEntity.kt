package destructionphysics.entity

import destructionphysics.DestructionPhysics
import destructionphysics.DestructionPhysics.LOGGER
import destructionphysics.DestructionPhysics.canFall
import destructionphysics.mixin.accessor.ConcretePowderBlockAccessor
import destructionphysics.registry.ModEntities
import net.minecraft.block.AnvilBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.BrushableBlock
import net.minecraft.block.ConcretePowderBlock
import net.minecraft.block.FallingBlock
import net.minecraft.block.LandingBlock
import net.minecraft.block.PointedDripstoneBlock
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.MovementType
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.fluid.Fluids
import net.minecraft.item.AutomaticItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtHelper
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.predicate.entity.EntityPredicates
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.FluidTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.crash.CrashReportSection
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameRules
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import net.minecraft.world.WorldEvents
import net.minecraft.world.event.GameEvent
import net.minecraft.world.explosion.Explosion
import kotlin.math.min

class AdvancedFallingBlockEntity(type: EntityType<*>?, world: World?) : Entity(type, world) {
    companion object {
        private val SLIDE_POS = DataTracker.registerData(AdvancedFallingBlockEntity::class.java, TrackedDataHandlerRegistry.BLOCK_POS)
        private val SLIDE_DIRECTION = DataTracker.registerData(AdvancedFallingBlockEntity::class.java, TrackedDataHandlerRegistry.BYTE)
        private val SLIDE_PROGRESS = DataTracker.registerData(AdvancedFallingBlockEntity::class.java, TrackedDataHandlerRegistry.BYTE)
        private val SLIDE_COUNT = DataTracker.registerData(AdvancedFallingBlockEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        @JvmStatic
        fun createFromBlock(world: World, pos: BlockPos, state: BlockState): AdvancedFallingBlockEntity {
            LOGGER.debug("creating advanced falling block entity for block state '{}' at '{}'", state, pos)
            val entity = AdvancedFallingBlockEntity(
                world,
                pos.x.toDouble() + 0.5,
                pos.y.toDouble(),
                pos.z.toDouble() + 0.5,
                if (state.contains(Properties.WATERLOGGED)) state.with(Properties.WATERLOGGED, false) else state,
            )

            if (state.block is AnvilBlock) {
                entity.setHurtEntities(2f, 40)
            }

            return entity
        }

        @JvmStatic
        @JvmOverloads
        fun spawnFromBlock(
            world: World,
            pos: BlockPos,
            state: BlockState,
            entityMutator: AdvancedFallingBlockEntity.() -> Unit = {},
        ): AdvancedFallingBlockEntity {
            val entity = createFromBlock(world, pos, state)
            entityMutator(entity)
            world.setBlockState(pos, state.fluidState.blockState, Block.NOTIFY_ALL)
            world.spawnEntity(entity)
            return entity
        }

        @JvmStatic
        fun spawnFromExplosion(world: World, pos: BlockPos, state: BlockState, explosion: Explosion): AdvancedFallingBlockEntity? {
            if (!state.canFall(world, pos)) return null
            return spawnFromBlock(world, pos, state) {
                val flyDirection = this.pos.subtract(explosion.position)
                // TODO: div by 0 check?
                addVelocity(flyDirection.normalize().multiply(3 / flyDirection.length()))
            }.also {
                DestructionPhysics.causeNeighboringToFall(world, pos)
            }
        }
    }

    var block: BlockState = Blocks.SAND.defaultState
        private set
    var slidePos: BlockPos
        get() = dataTracker.get(SLIDE_POS)
        private set(value) = dataTracker.set(SLIDE_POS, value)
    private var slideDirection: Byte
        get() = dataTracker.get(SLIDE_DIRECTION)
        private set(value) = dataTracker.set(SLIDE_DIRECTION, value)
    private var slideProgress: Byte
        get() = dataTracker.get(SLIDE_PROGRESS)
        private set(value) = dataTracker.set(SLIDE_PROGRESS, value)
    private var slideCount: Int
        get() = dataTracker.get(SLIDE_COUNT)
        private set(value) = dataTracker.set(SLIDE_COUNT, value)
    private var timeFalling = 0
    private var dropItem = true
    private var destroyedOnLanding = false
    private var hurtEntities = false
    private var fallHurtAmount = 0f
    private var fallHurtMax = 40

    private constructor(world: World?, x: Double, y: Double, z: Double, block: BlockState) : this(ModEntities.ADVANCED_FALLING_BLOCK_ENTITY, world) {
        this.block = block
        intersectionChecked = true
        setPosition(x, y, z)
        velocity = Vec3d.ZERO
        prevX = x
        prevY = y
        prevZ = z
        slidePos = blockPos
    }

    override fun initDataTracker() {
        dataTracker.startTracking(SLIDE_POS, BlockPos.ORIGIN)
        dataTracker.startTracking(SLIDE_DIRECTION, -1)
        dataTracker.startTracking(SLIDE_PROGRESS, -1)
        dataTracker.startTracking(SLIDE_COUNT, 0)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        block = NbtHelper.toBlockState(world.createCommandRegistryWrapper(RegistryKeys.BLOCK), nbt.getCompound("BlockState"))
        timeFalling = nbt.getInt("Time")
        if (nbt.contains("DropItem", NbtElement.NUMBER_TYPE.toInt())) {
            dropItem = nbt.getBoolean("DropItem")
        }
        destroyedOnLanding = nbt.getBoolean("CancelDrop")
        if (nbt.contains("HurtEntities", NbtElement.NUMBER_TYPE.toInt())) {
            hurtEntities = nbt.getBoolean("HurtEntities")
            fallHurtAmount = nbt.getFloat("FallHurtAmount")
            fallHurtMax = nbt.getInt("FallHurtMax")
        } else if (block.isIn(BlockTags.ANVIL)) {
            hurtEntities = true
        }
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.put("BlockState", NbtHelper.fromBlockState(block))
        nbt.putInt("Time", timeFalling)
        nbt.putBoolean("DropItem", dropItem)
        nbt.putBoolean("CancelDrop", destroyedOnLanding)
        nbt.putBoolean("HurtEntities", hurtEntities)
        nbt.putFloat("FallHurtAmount", fallHurtAmount)
        nbt.putInt("FallHurtMax", fallHurtMax)
    }

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> {
        return EntitySpawnS2CPacket(this, Block.getRawIdFromState(block))
    }

    override fun onSpawnPacket(packet: EntitySpawnS2CPacket) {
        super.onSpawnPacket(packet)
        block = Block.getStateFromRawId(packet.entityData)
        setPosition(packet.x, packet.y, packet.z)
        setVelocity(packet.velocityX, packet.velocityY, packet.velocityZ)
        slidePos = blockPos
    }

    override fun isAttackable(): Boolean = false
    override fun getMoveEffect(): MoveEffect = MoveEffect.NONE
    override fun canHit(): Boolean = !isRemoved
    override fun doesRenderOnFire(): Boolean = false
    override fun getDefaultName(): Text = Text.translatable("entity.minecraft.falling_block_type", block.block.name)
    override fun entityDataRequiresOperator(): Boolean = true

    override fun populateCrashReport(section: CrashReportSection) {
        super.populateCrashReport(section)
        section.add("Immitating BlockState", block.toString())
    }

    override fun tick() {
        if (block.isAir) {
            discard()
            return
        }
        val block = block.block
        timeFalling++
        if (slideDirection.toInt() != -1) {
            slideProgress++
            val dirVec = Direction.fromHorizontal(slideDirection.toInt()).vector
            setPosition(
                pos.x + dirVec.x.toDouble() / 5.0,
                pos.y - (slideProgress.minus(1).coerceAtLeast(0) * 0.1),
                pos.z + dirVec.z.toDouble() / 5.0,
            )

            // TODO: timeFalling = 0?
            if (slideProgress.toInt() >= 4) {
                slideProgress = -1
                slideDirection = -1
                velocity = Vec3d(0.0, -0.3, 0.0)
            }
            return
        }
        if (!hasNoGravity()) {
            velocity = velocity.add(0.0, -0.04, 0.0)
        }
        val yVelocity = velocity.y
        move(MovementType.SELF, velocity)
        if (!world.isClient) {
            var blockPos = blockPos
            val isConcretePowder = block is ConcretePowderBlock
            var shouldConvert = isConcretePowder && world.getFluidState(blockPos).isIn(FluidTags.WATER)
            val blockHitResult = world.raycast(RaycastContext(Vec3d(prevX, prevY, prevZ), pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.SOURCE_ONLY, this))
            if (isConcretePowder && velocity.lengthSquared() > 1.0 && blockHitResult.type != HitResult.Type.MISS && world.getFluidState(blockHitResult.blockPos).isIn(FluidTags.WATER)) {
                blockPos = blockHitResult.blockPos
                shouldConvert = true
            }
            if (isOnGround || shouldConvert) {
                // custom movement
                if (!shouldConvert && (yVelocity < -0.4 || random.nextFloat() < 1f / ((slideCount / 80f) + 1f)) && slide()) {
                    isOnGround = false
                    slideCount++
                    return
                }

                val blockState = world.getBlockState(blockPos)
                velocity = velocity.multiply(0.7, -0.5, 0.7)
                if (!blockState.isOf(Blocks.MOVING_PISTON)) {
                    if (!destroyedOnLanding) {
                        val canReplace = canReplace(blockState, blockPos)
                        val canFallThrough = FallingBlock.canFallThrough(world.getBlockState(blockPos.down())) && (!isConcretePowder || !shouldConvert)
                        val canPlaceAt = this.block.canPlaceAt(world, blockPos) && !canFallThrough
                        if (canReplace && canPlaceAt) {
                            if (this.block.contains(Properties.WATERLOGGED) && world.getFluidState(blockPos).fluid == Fluids.WATER) {
                                this.block = this.block.with(Properties.WATERLOGGED, true)
                            }
                            if (world.setBlockState(blockPos, this.block, Block.NOTIFY_ALL)) {
                                (world as ServerWorld).chunkManager.threadedAnvilChunkStorage.sendToOtherNearbyPlayers(this, BlockUpdateS2CPacket(blockPos, world.getBlockState(blockPos)))
                                discard()
                                this.onLanding(block, blockPos, blockState)
                            } else if (dropItem && world.gameRules.getBoolean(GameRules.DO_ENTITY_DROPS)) {
                                discard()
                                onDestroyedOnLanding(block, blockPos)
                                dropItem(block)
                            }
                        } else {
                            discard()
                            if (dropItem && world.gameRules.getBoolean(GameRules.DO_ENTITY_DROPS)) {
                                onDestroyedOnLanding(block, blockPos)
                                dropItem(block)
                            }
                        }
                    } else {
                        discard()
                        onDestroyedOnLanding(block, blockPos)
                    }
                }
            } else if (!(world.isClient || (timeFalling <= 100 || blockPos.y in world.bottomY + 1 .. world.topY) && timeFalling <= 600)) {
                if (dropItem && world.gameRules.getBoolean(GameRules.DO_ENTITY_DROPS)) {
                    dropItem(block)
                }
                discard()
            }
        }
        velocity = velocity.multiply(0.98)
    }

    private fun canReplace(blockState: BlockState, blockPos: BlockPos, moveDirection: Direction = Direction.DOWN): Boolean =
        blockState.canReplace(AutomaticItemPlacementContext(world, blockPos, moveDirection, ItemStack.EMPTY, moveDirection.opposite))

    private fun slide(): Boolean {
        for (direction in Direction.Type.HORIZONTAL.getShuffled(random)) {
            val fromPos = blockPos
            val midPos = fromPos.offset(direction)
            val midState = world.getBlockState(midPos)
            if (canReplace(midState, midPos, direction)) {
                val endPos = midPos.down()
                val endState = world.getBlockState(endPos)
                if (canReplace(endState, endPos)) {
                    slideDirection = direction.horizontal.toByte()
                    slidePos = blockPos
                    return true
                }
            }
        }
        return false
    }

    fun setHurtEntities(amount: Float, max: Int) {
        hurtEntities = true
        fallHurtAmount = amount
        fallHurtMax = max
    }

    override fun handleFallDamage(fallDistance: Float, damageMultiplier: Float, damageSource: DamageSource?): Boolean {
        if (!hurtEntities) return false
        val i = MathHelper.ceil(fallDistance - 1f)
        if (i < 0) return false
        val predicate = EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.and(EntityPredicates.VALID_LIVING_ENTITY)
        val block = block.block
        val damageSource2 = if (block is LandingBlock) {
            block.getDamageSource(this)
        } else {
            damageSources.fallingBlock(this)
        }
        val amount = min(MathHelper.floor(i.toFloat() * fallHurtAmount), fallHurtMax)
        world.getOtherEntities(this, this.boundingBox, predicate).forEach { it.damage(damageSource2, amount.toFloat()) }
        if (this.block.isIn(BlockTags.ANVIL) && amount > 0 && random.nextFloat() < 0.05f + i.toFloat() * 0.05f) {
            val blockState = AnvilBlock.getLandingState(this.block)
            if (blockState == null) {
                destroyedOnLanding = true
            } else {
                this.block = blockState
            }
        }
        return false
    }

    private fun onDestroyedOnLanding(block: Block, pos: BlockPos) {
        when (block) {
            is AnvilBlock -> if (!isSilent) {
                world.syncWorldEvent(WorldEvents.ANVIL_DESTROYED, pos, 0)
            }
            is BrushableBlock -> {
                val vec3d = boundingBox.center
                world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, BlockPos.ofFloored(vec3d), Block.getRawIdFromState(this.block))
                world.emitGameEvent(this, GameEvent.BLOCK_DESTROY, vec3d)
            }
            is PointedDripstoneBlock -> if (!isSilent) {
                world.syncWorldEvent(WorldEvents.POINTED_DRIPSTONE_LANDS, pos, 0)
            }
            // TODO: break glass on landing
        }
    }

    private fun onLanding(block: Block, pos: BlockPos, currentStateInPos: BlockState) {
        when (block) {
            is AnvilBlock -> if (!isSilent) {
                world.syncWorldEvent(WorldEvents.ANVIL_LANDS, pos, 0)
            }
            is ConcretePowderBlockAccessor -> if (ConcretePowderBlockAccessor.callShouldHarden(world, pos, currentStateInPos)) {
                world.setBlockState(pos, block.hardenedState.defaultState, Block.NOTIFY_ALL)
            }
        }
    }
}
