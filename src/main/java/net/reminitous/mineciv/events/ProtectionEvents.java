package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class ProtectionEvents {

    private ProtectionEvents() {}

    /* ---------------- Helpers ---------------- */

    private static UUID ownerOf(ServerLevel level, BlockPos pos) {
        return TerritoryManager.getOwnerCivId(level, new ChunkPos(pos));
    }

    private static Optional<Civilization> civOf(ServerLevel level, UUID playerId) {
        return CivilizationManager.findPlayerCiv(level, playerId);
    }

    private static boolean isContainer(BlockEntity be) {
        if (be == null) return false;
        // Covers chests, barrels, furnaces, hoppers, shulker boxes, etc.
        return be instanceof net.minecraft.world.MenuProvider;
    }

    private static boolean areAllies(ServerLevel level, UUID civA, UUID civB) {
        if (civA == null || civB == null) return false;
        if (civA.equals(civB)) return true; // same civ treated as allied for combat rules
        return CivilizationManager.areAllies(level, civA, civB);
    }

    private static boolean isStorageBlock(net.minecraft.world.level.block.state.BlockState state) {
        // Covers most storage and processing blocks
        var block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.ChestBlock
                || block instanceof net.minecraft.world.level.block.BarrelBlock
                || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                || block instanceof net.minecraft.world.level.block.EnderChestBlock
                || block instanceof net.minecraft.world.level.block.FurnaceBlock
                || block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                || block instanceof net.minecraft.world.level.block.HopperBlock
                || block instanceof net.minecraft.world.level.block.DispenserBlock
                || block instanceof net.minecraft.world.level.block.DropperBlock
                || block instanceof net.minecraft.world.level.block.BrewingStandBlock
                || block instanceof net.minecraft.world.level.block.EnchantingTableBlock
                || block instanceof net.minecraft.world.level.block.SmokerBlock
                || block instanceof net.minecraft.world.level.block.BlastFurnaceBlock;
    }

    /* ---------------- Block place/break protection ---------------- */

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        UUID owner = ownerOf(level, e.getPos());
        Optional<Civilization> pc = civOf(level, player.getUUID());

        // If player is in a civilization, restrict storage placement to their own territory only
        if (pc.isPresent() && isStorageBlock(e.getPlacedBlock())) {
            UUID playerCivId = pc.get().id();

            // Must be in player's own claimed land
            if (owner == null || !playerCivId.equals(owner)) {
                e.setCanceled(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "You can only place storage/utility blocks inside your civilization's territory."
                ));
                return;
            }
        }

        // Unclaimed land: allow general placement
        if (owner == null) return;

        // Claimed land: only members of owning civ can place
        if (pc.isEmpty() || !pc.get().id().equals(owner)) {
            e.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You cannot build here."));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlayer() instanceof ServerPlayer player)) return;

        UUID owner = ownerOf(level, e.getPos());
        Optional<Civilization> pc = civOf(level, player.getUUID());

        // Unclaimed land: allow
        if (owner == null) return;

        // Claimed land: only members of owning civ can break
        if (pc.isEmpty() || !pc.get().id().equals(owner)) {
            e.setCanceled(true);
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("You cannot break blocks here.")
            );
        }
    }

    /* ---------------- Container access protection ---------------- */

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        ServerPlayer player = (ServerPlayer) e.getEntity();
        if (player == null) return;

        BlockPos pos = e.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!isContainer(be)) return;

        UUID owner = ownerOf(level, pos);
        Optional<Civilization> pc = civOf(level, player.getUUID());

        // If player is in a civilization, they may only access storage inside their own territory
        if (pc.isPresent()) {
            UUID playerCivId = pc.get().id();

            if (owner == null || !playerCivId.equals(owner)) {
                e.setCanceled(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "You can only access containers inside your civilization's territory."
                ));
                return;
            }
        }

        // If player is NOT in a civilization:
        // - Unclaimed land: allow access
        // - Claimed land: deny access
        if (pc.isEmpty()) {
            if (owner == null) return;
            e.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "This container is locked by a civilization."
            ));
        }
    }

    /* ---------------- Combat rules ---------------- */

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player victim)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;

        // Resolve attacker (player melee + most projectile shooters)
        var srcEntity = e.getSource().getEntity();
        if (!(srcEntity instanceof net.minecraft.world.entity.player.Player attacker)) return;

        Optional<Civilization> civAtt = civOf(level, attacker.getUUID());
        Optional<Civilization> civVic = civOf(level, victim.getUUID());

        UUID attCivId = civAtt.map(Civilization::id).orElse(null);
        UUID vicCivId = civVic.map(Civilization::id).orElse(null);

        // ---- Alliance rule: allies (and same civ) can NEVER hurt each other anywhere ----
        if (attCivId != null && vicCivId != null && areAllies(level, attCivId, vicCivId)) {
            e.setCanceled(true);
            return;
        }

        // Land owner (claimed territory)
        UUID landOwner = ownerOf(level, victim.blockPosition());

        // Unclaimed land: free-for-all (except allies handled above)
        if (landOwner == null) return;

        // Claimed land asymmetric rule:
        // If victim is a defender (member of land owner civ), attacker must be land owner (or ally if you later allow it).
        if (vicCivId != null && vicCivId.equals(landOwner)) {
            // attacker is NOT a member of defending civ -> cannot damage defender in their land
            if (attCivId == null || !attCivId.equals(landOwner)) {
                e.setCanceled(true);
            }
        }

        // If victim is an intruder (not a member of land owner civ), damage is allowed.
    }
}
