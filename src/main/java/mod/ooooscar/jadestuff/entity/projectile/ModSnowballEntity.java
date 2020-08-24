package mod.ooooscar.jadestuff.entity.projectile;

import mod.ooooscar.jadestuff.init.ModEffects;
import mod.ooooscar.jadestuff.init.ModEntityTypes;
import mod.ooooscar.jadestuff.init.ModItems;
import mod.ooooscar.jadestuff.init.ModSoundEvents;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.BlazeEntity;
import net.minecraft.entity.monster.MagmaCubeEntity;
import net.minecraft.entity.projectile.ProjectileItemEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Modified {@link SnowballEntity}m which stores a default damage value,
 * deals extra damage to fire based mobs ({@link MagmaCubeEntity} and {@link BlazeEntity}),
 * deals reduced damage to entities in water,
 * extinguishes fire,
 * and has a chance to inflict the {@link ModEffects#CHILLED} debuff.
 *
 * @author Ooooscar
 */
// TODO: Shot ModSnowballs do not render
public class ModSnowballEntity extends ProjectileItemEntity {
    private static float default_damage = 0;
    private static float knockbackStrength = 0;
    private static boolean is_crit = false;

    public ModSnowballEntity(EntityType<? extends ModSnowballEntity> entityTypeIn, World worldIn) {
        super(entityTypeIn, worldIn);
    }
    public ModSnowballEntity(World worldIn, LivingEntity throwerIn) {
        super(ModEntityTypes.STRANGE_SNOWBALL, throwerIn, worldIn);
    }
    public ModSnowballEntity(World worldIn, double x, double y, double z) {
        super(ModEntityTypes.STRANGE_SNOWBALL, x, y, z, worldIn);
    }

    public void setDefaultDamage(float defaultDamageIn) {
        default_damage = defaultDamageIn;
        is_crit = rand.nextInt((int) (2.5 / default_damage) + 2) == 0;
    }
    public void setKnockbackStrength(float knockbackStrengthIn) {
        knockbackStrength = knockbackStrengthIn;
    }

    protected Item getDefaultItem() {
        return ModItems.STRANGE_SNOWBALL;
    }

    // TODO: Prevent spawning snowball particles at the player's head if the speed of snowball is set to a higher value
    @OnlyIn(Dist.CLIENT)
    public IParticleData makeParticle() {
        ItemStack itemstack = this.func_213882_k();
        return (itemstack.isEmpty() ? ParticleTypes.ITEM_SNOWBALL : new ItemParticleData(ParticleTypes.ITEM, itemstack));
    }

    /**
     * Handler for {@link World#setEntityState}
     */
    @OnlyIn(Dist.CLIENT)
    public void handleStatusUpdate(byte id) {
        if (id == 3) {
            IParticleData iparticledata = this.makeParticle();
            for(int i = 0; i < 8; ++i) {
                this.world.addParticle(iparticledata, this.getPosX(), this.getPosY(), this.getPosZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /**
     * Called when the ModSnowball hits an entity
     */
    protected void onEntityHit(EntityRayTraceResult result) {
        float dmg = default_damage;
        float knockback = knockbackStrength;
        Entity entity_hit = result.getEntity();

        // Deal more damage if the hit entity is a blaze or a magma cube, and disables freezing / extinguishing effect
        if (entity_hit instanceof BlazeEntity || entity_hit instanceof MagmaCubeEntity) {
            dmg += 1.5;
            is_crit = false;
        }
        // Deal less damage and knockback if the hit entity is "wet"
        if (entity_hit.isInWaterRainOrBubbleColumn() || entity_hit.isWet()) {
            dmg /= 3;
            knockback /= 3;
        }

        // If it's a critical shot: extinguish burning entity
        if (entity_hit.isBurning() && is_crit) {
            entity_hit.extinguish();
            entity_hit.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    0.5F, 2.6F + (rand.nextFloat() - rand.nextFloat()) * 0.8F);
            is_crit = false;
        }

        // Cause thrown damage to the entity
        Entity entity_attacker = this.func_234616_v_();
        DamageSource damagesource;
        if (entity_attacker == null) {
            damagesource = DamageSource.causeThrownDamage(this, this);
        } else {
            damagesource = DamageSource.causeThrownDamage(this, entity_attacker);
            if (entity_attacker instanceof LivingEntity) {
                ((LivingEntity)entity_attacker).setLastAttackedEntity(entity_hit);
            }
        }

        if (entity_hit instanceof LivingEntity) {
            LivingEntity living_entity_hit = (LivingEntity)entity_hit;
            living_entity_hit.attackEntityFrom(damagesource, dmg);
            // Apply knockback to the entity
            if (knockback > 0) {
                Vector3d vector3d = this.getMotion().mul(1.0D, 0.0D, 1.0D).normalize().scale(knockback * 0.6D);
                if (vector3d.lengthSquared() > 0.0D) {
                    living_entity_hit.addVelocity(vector3d.x, 0.1D, vector3d.z);
                }
            }
            // If it's a critical shot: apply freezing effect to the entity
            if (is_crit) {
                living_entity_hit.playSound(ModSoundEvents.EFFECT_FREEZE, 0.5F, 2.6F + (rand.nextFloat() - rand.nextFloat()) * 0.8F);
                living_entity_hit.addPotionEffect(new EffectInstance(ModEffects.CHILLED, (int)(120 * dmg), 5, true, true));
            }
        }

    }

    /**
     * Called when the ModSnowball hits a block
     */
    // TODO: Fire on other sides of the block (i.e. not on top of the hit block)?
    // If it's a critical shot: Extinguish fire
    protected void func_230299_a_(BlockRayTraceResult result) {
        super.func_230299_a_(result);
        if (is_crit) {
            BlockPos pos_up = result.getPos().up();
            BlockState ibs_up = world.getBlockState(pos_up);
            Block block_up = ibs_up.getBlock();
            if (block_up instanceof FireBlock) {
                playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.5F, 2.6F + (rand.nextFloat() - rand.nextFloat()) * 0.8F);
                this.world.removeBlock(pos_up, false);
            }
        }
    }

    /**
     * Called when the ModSnowball hits a block or an entity
     */
    @Override
    protected void onImpact(RayTraceResult result) {
        super.onImpact(result);
        if (!this.world.isRemote) {
            this.world.setEntityState(this, (byte)3);
            this.remove();
        }
    }

    @Override
    public ItemStack getItem() {
        return null;
    }
}
