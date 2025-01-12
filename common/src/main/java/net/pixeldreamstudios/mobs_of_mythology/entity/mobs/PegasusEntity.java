package net.pixeldreamstudios.mobs_of_mythology.entity.mobs;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mod.azure.azurelib.common.api.common.animatable.GeoEntity;
import mod.azure.azurelib.core.animatable.instance.AnimatableInstanceCache;
import mod.azure.azurelib.core.animatable.instance.SingletonAnimatableInstanceCache;
import mod.azure.azurelib.core.animation.AnimatableManager;
import mod.azure.azurelib.core.animation.AnimationController;
import mod.azure.azurelib.core.object.PlayState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.pixeldreamstudios.mobs_of_mythology.MobsOfMythology;
import net.pixeldreamstudios.mobs_of_mythology.entity.constant.DefaultMythAnimations;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.SmartBrainProvider;
import net.tslat.smartbrainlib.api.core.behaviour.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.OneRandomBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Idle;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Panic;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomFlyingTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.InvalidateAttackTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetPlayerLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetRandomLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.TargetOrRetaliate;
import net.tslat.smartbrainlib.api.core.navigation.SmoothFlyingPathNavigation;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PegasusEntity extends AbstractChestedHorse implements GeoEntity, SmartBrainOwner<PegasusEntity>, FlyingAnimal {
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(PegasusEntity.class, EntityDataSerializers.INT);
//    private static final String KEY_VARIANT = "Variant";

    private static final int FLYING_INTERVAL = 8;
    protected int flyingTime;

    protected boolean isFlying;
    protected GroundPathNavigation groundNavigation;
    protected FlyingPathNavigation flyingNavigation;

    public PegasusEntity(EntityType<? extends AbstractChestedHorse> entityType, Level level) {
        super(entityType, level);
//        this.navigation = new SmoothFlyingPathNavigation(this, level);
    }

    @Override
    protected void randomizeAttributes(RandomSource randomSource) {

    }
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MobsOfMythology.config.pegasusHealth)
                .add(Attributes.MOVEMENT_SPEED, 0.3f)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75)
                .add(Attributes.FLYING_SPEED, 1.75)
                .add(Attributes.JUMP_STRENGTH, 0.6f);
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(ItemTags.HORSE_FOOD);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack itemStack) {
        boolean bl = false;
        float f = 0.0F;
        if (itemStack.is(ItemTags.HORSE_FOOD)) {
            f = 4.0F;
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f);
            bl = true;
        }

        if (bl) {
            this.eat();
            this.gameEvent(GameEvent.EAT);
        }

        return bl;
    }
    @Override
    protected PathNavigation createNavigation(Level level) {
        groundNavigation = new GroundPathNavigation(this, level);
        flyingNavigation = new FlyingPathNavigation(this, level);
        return groundNavigation;
    }
    @Override
    public PathNavigation getNavigation() {
        if (this.isPassenger() && this.getVehicle() instanceof Mob) {
            Mob mob = (Mob)this.getVehicle();
            return mob.getNavigation();
        } else if(this.isFlying()) {
            return this.flyingNavigation;
        } else {
            return this.groundNavigation;
        }
    }
    @Override
    public void aiStep() {
        super.aiStep();
        // update flying
        if (flyingTime > 0) {
            flyingTime--;
        }
        if (!isVehicle()) {
            isFlying = false;
        }
        // fall slowly when being ridden
        if (isVehicle() && this.getDeltaMovement().y < -0.1D) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.95D, 1.0));
        }

    }

//    @Override
//    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions entityDimensions, float f) {
//        return super.getPassengerAttachmentPoint(entity, entityDimensions, f)
//                .add(new Vec3(0.0, 0.01 * (double) f, -0.1 * (double) f).yRot(-this.getYRot() * (float) (Math.PI / 180.0)));
//    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>(this, "controller", 3, state -> {
            if (!onGround()) {
                return state.setAndContinue(DefaultMythAnimations.FLY);
            }
            if (state.isMoving() && !swinging) {
                if (isAggressive() || hasExactlyOnePlayerPassenger()) {
                    state.getController().setAnimation(DefaultMythAnimations.RUN);
                    return PlayState.CONTINUE;
                }
                state.getController().setAnimation(DefaultMythAnimations.WALK);
                return PlayState.CONTINUE;
            }
            state.getController().setAnimation(DefaultMythAnimations.IDLE);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public List<ExtendedSensor<PegasusEntity>> getSensors() {
        return ObjectArrayList.of(
                new NearbyLivingEntitySensor<>(),
                new HurtBySensor<>()
        );
    }

    @Override
    public BrainActivityGroup<PegasusEntity> getCoreTasks() {
        return BrainActivityGroup.coreTasks(
                new FloatToSurfaceOfFluid<>(),
                new LookAtTarget<>(),
                new MoveToWalkTarget<>());
    }

    @Override
    public BrainActivityGroup<PegasusEntity> getIdleTasks() {
        return BrainActivityGroup.idleTasks(
                new FirstApplicableBehaviour<PegasusEntity>(
                        new TargetOrRetaliate<>()
                                .alertAlliesWhen((mob, entity) -> this.isAggressive()),
                        new SetPlayerLookTarget<>(),
                        new SetRandomLookTarget<>()),
                new OneRandomBehaviour<>(
                        new SetRandomWalkTarget<>(),
                        new SetRandomFlyingTarget<>(),
                        new Idle<>()
                                .runFor(entity -> entity.getRandom().nextInt(30, 60))));
    }

    @Override
    public BrainActivityGroup<PegasusEntity> getFightTasks() {
        return BrainActivityGroup.fightTasks(
                new InvalidateAttackTarget<>()
                        .invalidateIf((target, entity) -> !target.isAlive() || !entity.hasLineOfSight(target) || target.is(getOwner())),
                new Panic<>()
                        .speedMod(mob -> 1.5f)
        );
    }

    @Override
    protected Brain.Provider<?> brainProvider() {
        return new SmartBrainProvider<>(this);
    }

    @Override
    protected void customServerAiStep() {
        tickBrain(this);
    }

    private void eat() {
        if (!this.isSilent()) {
            SoundEvent soundEvent = this.getEatingSound();
            if (soundEvent != null) {
                this.level()
                        .playSound(
                                null, this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundSource(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
                        );
            }
        }
    }

//    @Override
//    protected @NotNull PathNavigation createNavigation(@NotNull Level worldIn) {
//        final var flyingpathnavigator = new SmoothFlyingPathNavigation(this, worldIn);
//        flyingpathnavigator.setCanOpenDoors(false);
//        flyingpathnavigator.setCanFloat(false);
//        flyingpathnavigator.setCanPassDoors(false);
//        return flyingpathnavigator;
//    }

    @Override
    public boolean causeFallDamage(float f, float g, DamageSource damageSource) {
        if (f > 1.0F) {
            this.playSound(SoundEvents.HORSE_LAND, 0.4F, 1.0F);
        }

        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        this.playSound(SoundEvents.HORSE_AMBIENT, 1.0f, 1.25f);
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        this.playSound(SoundEvents.HORSE_HURT, 1.0f, 1.25f);
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        this.playSound(SoundEvents.HORSE_DEATH, 1.0f, 1.25f);
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.HORSE_STEP, 0.25f, 1.25f);
    }

    @Override
    protected void playJumpSound() {
        this.playSound(SoundEvents.HORSE_JUMP, 0.4F, 1.25F);
    }

    @Nullable
    protected SoundEvent getEatingSound() {
        this.playSound(SoundEvents.HORSE_EAT, 1.0f, 1.25f);
        return null;
    }
    @Override
    public void handleStartJump(int jumpPower) {
        if (!this.onGround()) {
            this.setStanding(false);
        }
    }
    @Override
    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }
    @Override
    public @NotNull InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && !this.isVehicle()) {
            player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    @Override
    public boolean canJump() {
        return super.canJump() && flyingTime <= 0;
    }
    @Override
    public void onPlayerJump(int jumpPower) {
        if (this.canJump()) {
            this.setJumping(true);
            this.flyingJump();
        }
    }
    public void flyingJump() {
        if (flyingTime <= 0 && this.canJump()) {
            float jumpMotion = 4.6F;
            this.setDeltaMovement(this.getDeltaMovement().add(0, jumpMotion, 0));
            this.flyingTime = FLYING_INTERVAL;
            this.isFlying = true;
        }
    }
    @Override
    public void handleStopJump() {
        this.setIsJumping(false);
    }
    @Override
    public boolean isFlying() {
        final double flyingMotion = isBaby() ? 0.02D : 0.06D;
        return !this.onGround() || this.getDeltaMovement().lengthSqr() > flyingMotion;
    }
    @Override
    public boolean isJumping() {
        return false;
    }
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();
        return (entity instanceof Player) ? (Player) entity : null;
    }
    @Override
    public void travel(final Vec3 vec) {
        super.travel(vec);

    }
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
    }
}