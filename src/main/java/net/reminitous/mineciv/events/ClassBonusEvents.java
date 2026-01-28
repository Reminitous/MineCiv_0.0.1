package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.enchanting.EnchantmentLevelSetEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivClass;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class ClassBonusEvents {

    private ClassBonusEvents() {}

    /* ---------------- Helpers ---------------- */

    private static Optional<Civilization> civOf(ServerLevel level, ServerPlayer player) {
        return CivilizationManager.findPlayerCiv(level, player.getUUID());
    }

    private static CivClass classOf(ServerLevel level, ServerPlayer player) {
        return civOf(level, player).map(Civilization::classType).orElse(null);
    }

    private static boolean isRedstoneComponent(BlockState state) {
        Block b = state.getBlock();

        // Blocks (no missing piston classes)
        if (b == Blocks.REDSTONE_WIRE) return true;
        if (b == Blocks.REPEATER) return true;
        if (b == Blocks.COMPARATOR) return true;
        if (b == Blocks.OBSERVER) return true;
        if (b == Blocks.LEVER) return true;
        if (b == Blocks.REDSTONE_LAMP) return true;
        if (b == Blocks.DAYLIGHT_DETECTOR) return true;

        if (b == Blocks.PISTON) return true;
        if (b == Blocks.STICKY_PISTON) return true;
        if (b == Blocks.PISTON_HEAD) return true;

        if (b == Blocks.DISPENSER) return true;
        if (b == Blocks.DROPPER) return true;
        if (b == Blocks.HOPPER) return true;
        if (b == Blocks.NOTE_BLOCK) return true;
        if (b == Blocks.TARGET) return true;
        if (b == Blocks.TRIPWIRE_HOOK) return true;

        // Buttons & pressure plates are many different block classes
        return (b instanceof ButtonBlock) || (b instanceof PressurePlateBlock);
    }

    /* ---------------- Agricultural: Crop yield +25% ---------------- */

    @SubscribeEvent
    public static void onCropBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlayer() instanceof ServerPlayer player)) return;

        if (classOf(level, player) != CivClass.AGRICULTURAL) return;

        BlockState state = e.getState();
        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!crop.isMaxAge(state)) return;

        // After vanilla drops, add +25% extra drops by dropping one extra item 25% of the time.
        if (level.random.nextFloat() < 0.25f) {
            var drops = Block.getDrops(
                    state,
                    level,
                    e.getPos(),
                    level.getBlockEntity(e.getPos()),
                    player,
                    player.getMainHandItem()
            );
            if (!drops.isEmpty()) {
                ItemStack extra = drops.get(0).copy();
                extra.setCount(1);
                Block.popResource(level, e.getPos(), extra);
            }
        }
    }

    /* ---------------- Break speed bonuses ---------------- */

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        CivClass type = classOf(level, player);
        if (type == null) return;

        BlockState state = e.getState();

        // Agricultural: logs +15%
        if (type == CivClass.AGRICULTURAL) {
            if (state.is(BlockTags.LOGS)) {
                e.setNewSpeed(e.getNewSpeed() * 1.15f);
            }
        }

        // Technology: mining +15% (broad pickaxe stuff, tweak later)
        if (type == CivClass.TECHNOLOGY) {
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                e.setNewSpeed(e.getNewSpeed() * 1.15f);
            }
        }
    }

    /* ---------------- Agricultural: breeding cooldown -20% ---------------- */

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent e) {
        if (!(e.getChild().level() instanceof ServerLevel level)) return;

        // Attribute to nearest player (1.21.1-safe)
        var nearest = level.getNearestPlayer(
                e.getChild().getX(),
                e.getChild().getY(),
                e.getChild().getZ(),
                8.0,
                false
        );
        if (!(nearest instanceof ServerPlayer player)) return;

        if (classOf(level, player) != CivClass.AGRICULTURAL) return;

        // Reduce cooldown (parents typically have positive age after breeding)
        if (e.getParentA() instanceof Animal a) {
            int age = a.getAge();
            if (age > 0) a.setAge((int) Math.floor(age * 0.8));
        }
        if (e.getParentB() instanceof Animal b) {
            int age = b.getAge();
            if (age > 0) b.setAge((int) Math.floor(age * 0.8));
        }
    }

    /* ---------------- Technology: only Tech can place redstone ---------------- */

    @SubscribeEvent
    public static void onPlaceRedstone(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        BlockState placed = e.getPlacedBlock();
        if (!isRedstoneComponent(placed)) return;

        CivClass type = classOf(level, player);

        if (type != CivClass.TECHNOLOGY) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("Only Technology civilizations can use redstone."));
        }
    }

    /* ---------------- Mystic: only Mystic can use brewing stands ---------------- */

    @SubscribeEvent
    public static void onUseBrewingStand(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        BlockEntity be = level.getBlockEntity(e.getPos());
        if (!(be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity)) return;

        CivClass type = classOf(level, player);
        if (type != CivClass.MYSTIC) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("Only Mystic civilizations can brew potions."));
        }
    }

    /* ---------------- Mystic: enchanting offer levels -20% ---------------- */

    @SubscribeEvent
    public static void onEnchantOffer(EnchantmentLevelSetEvent e) {
        // In 1.21.x, this event does NOT provide a player directly.
        // We attribute by nearest player to the enchanting table position.
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = e.getPos();
        var nearest = level.getNearestPlayer(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                8.0,
                false
        );
        if (!(nearest instanceof ServerPlayer player)) return;

        CivClass type = classOf(level, player);
        if (type != CivClass.MYSTIC) return;

        int old = e.getEnchantLevel();
        int reduced = Math.max(1, (int) Math.floor(old * 0.8));
        e.setEnchantLevel(reduced);
    }

    /* ---------------- Warlike: +15% damage vs hostile mobs ---------------- */

    @SubscribeEvent
    public static void onWarlikeDamage(LivingHurtEvent e) {
        Entity src = e.getSource().getEntity();
        if (!(src instanceof ServerPlayer attacker)) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;

        if (classOf(level, attacker) != CivClass.WARLIKE) return;

        if (e.getEntity() instanceof Monster) {
            e.setAmount(e.getAmount() * 1.15f);
        }
    }
}
