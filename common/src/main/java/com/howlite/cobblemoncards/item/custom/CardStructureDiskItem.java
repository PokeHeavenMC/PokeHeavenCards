package com.howlite.cobblemoncards.item.custom;

import com.howlite.cobblemoncards.CobblemonCardsConfig;
import com.howlite.cobblemoncards.component.DiskData;
import com.howlite.cobblemoncards.component.ModDataComponents;
import com.howlite.cobblemoncards.item.ModItems;
import com.howlite.cobblemoncards.block.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class CardStructureDiskItem extends Item {

    private static final int MAX_DUST = 1000;

    public CardStructureDiskItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack heldStack = player.getItemInHand(usedHand);
        DiskData data = heldStack.getOrDefault(ModDataComponents.DISK_DATA, DiskData.empty());

        if (!level.isClientSide) {
            if (!CobblemonCardsConfig.enableInstantDex) {
                player.displayClientMessage(Component.translatable("message.cobblemon-cards.instant_dex.disabled").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(heldStack);
            }

            if (data.dust() >= MAX_DUST) {
                player.displayClientMessage(Component.translatable("message.cobblemon-cards.structure_disk.disk_full").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(heldStack);
            }

            int spaceLeft = MAX_DUST - data.dust();
            int totalAdded = 0;

            if (!player.isShiftKeyDown()) {
                // Single action: prioritize 1 single dust, then 1 pouch (9), then 1 sack (81).
                int singleDustSlot = findItemSlot(player, ModItems.CARD_DUST);
                if (singleDustSlot != -1) {
                    player.getInventory().getItem(singleDustSlot).shrink(1);
                    totalAdded = 1;
                } else {
                    int pouchSlot = findItemSlot(player, ModItems.CARD_DUST_POUCH);
                    if (pouchSlot != -1) {
                        player.getInventory().getItem(pouchSlot).shrink(1);
                        totalAdded = 9;
                    } else {
                        int sackSlot = findItemSlot(player, ModBlocks.CARD_DUST_SACK.asItem());
                        if (sackSlot != -1) {
                            player.getInventory().getItem(sackSlot).shrink(1);
                            totalAdded = 81;
                        }
                    }
                }
            } else {
                // Sneaking: consume as much as possible up to spaceLeft
                // First sacks (81)
                int sackSlot;
                while (spaceLeft >= 81 && (sackSlot = findItemSlot(player, ModBlocks.CARD_DUST_SACK.asItem())) != -1) {
                    ItemStack stack = player.getInventory().getItem(sackSlot);
                    int countToConsume = Math.min(stack.getCount(), spaceLeft / 81);
                    if (countToConsume == 0) break;
                    stack.shrink(countToConsume);
                    int added = countToConsume * 81;
                    totalAdded += added;
                    spaceLeft -= added;
                }
                
                // Then pouches (9)
                int pouchSlot;
                while (spaceLeft >= 9 && (pouchSlot = findItemSlot(player, ModItems.CARD_DUST_POUCH)) != -1) {
                    ItemStack stack = player.getInventory().getItem(pouchSlot);
                    int countToConsume = Math.min(stack.getCount(), spaceLeft / 9);
                    if (countToConsume == 0) break;
                    stack.shrink(countToConsume);
                    int added = countToConsume * 9;
                    totalAdded += added;
                    spaceLeft -= added;
                }
                
                // Then single dust (1)
                int dustSlot;
                while (spaceLeft >= 1 && (dustSlot = findItemSlot(player, ModItems.CARD_DUST)) != -1) {
                    ItemStack stack = player.getInventory().getItem(dustSlot);
                    int countToConsume = Math.min(stack.getCount(), spaceLeft);
                    if (countToConsume == 0) break;
                    stack.shrink(countToConsume);
                    int added = countToConsume;
                    totalAdded += added;
                    spaceLeft -= added;
                }

                // Friendly overfill/top-up logic if they have no smaller units
                if (spaceLeft > 0 && totalAdded == 0) {
                    int pouchSlotIdx = findItemSlot(player, ModItems.CARD_DUST_POUCH);
                    if (pouchSlotIdx != -1) {
                        player.getInventory().getItem(pouchSlotIdx).shrink(1);
                        totalAdded = spaceLeft;
                    } else {
                        int sackSlotIdx = findItemSlot(player, ModBlocks.CARD_DUST_SACK.asItem());
                        if (sackSlotIdx != -1) {
                            player.getInventory().getItem(sackSlotIdx).shrink(1);
                            totalAdded = spaceLeft;
                        }
                    }
                }
            }

            if (totalAdded > 0) {
                int newDust = Math.min(data.dust() + totalAdded, MAX_DUST);
                heldStack.set(ModDataComponents.DISK_DATA, new DiskData(
                        newDust,
                        data.scanCount(),
                        data.scannedPokemon(),
                        data.targetSpecies()
                ));

                level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
                return InteractionResultHolder.success(heldStack);
            } else {
                player.displayClientMessage(Component.translatable("message.cobblemon-cards.structure_disk.no_dust").withStyle(ChatFormatting.RED), true);
            }
        }

        return InteractionResultHolder.pass(heldStack);
    }

    private int findItemSlot(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        DiskData data = stack.getOrDefault(ModDataComponents.DISK_DATA, DiskData.empty());
        return data.dust() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        DiskData data = stack.getOrDefault(ModDataComponents.DISK_DATA, DiskData.empty());
        return Math.round(13.0f * ((float) data.dust() / MAX_DUST));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Couleur violette
        return Mth.color(160, 32, 240);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        DiskData data = stack.getOrDefault(ModDataComponents.DISK_DATA, DiskData.empty());
        
        int dust = data.dust();

        if (!CobblemonCardsConfig.enableInstantDex) {
            tooltipComponents.add(Component.translatable("message.cobblemon-cards.instant_dex.disabled").withStyle(ChatFormatting.RED));
            super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
            return;
        }

        tooltipComponents.add(Component.translatable("tooltip.cobblemon-cards.structure_disk.charge_instruction").withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable("tooltip.cobblemon-cards.structure_disk.charge_all_instruction").withStyle(ChatFormatting.YELLOW));
        
        tooltipComponents.add(Component.empty());
        
        tooltipComponents.add(Component.translatable("tooltip.cobblemon-cards.structure_disk.accumulated_dust").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(dust + " / " + MAX_DUST).withStyle(ChatFormatting.AQUA)));
                
        String potentialRarity = "Common";
        ChatFormatting rarityColor = ChatFormatting.WHITE;
        
        if (dust >= 1000) {
            potentialRarity = "Legendary";
            rarityColor = ChatFormatting.LIGHT_PURPLE;
        } else if (dust >= 500) {
            potentialRarity = "Epic";
            rarityColor = ChatFormatting.GOLD;
        } else if (dust >= 200) {
            potentialRarity = "Rare";
            rarityColor = ChatFormatting.DARK_PURPLE;
        } else if (dust >= 50) {
            potentialRarity = "Uncommon";
            rarityColor = ChatFormatting.BLUE;
        }
        
        tooltipComponents.add(Component.translatable("tooltip.cobblemon-cards.structure_disk.potential_rarity").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(potentialRarity).withStyle(rarityColor)));

        if (data.scanCount() > 0) {
            tooltipComponents.add(Component.empty());

            data.targetSpecies().ifPresent(species -> {
                tooltipComponents.add(Component.translatable("tooltip.cobblemon-cards.structure_disk.target_species").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(species).withStyle(ChatFormatting.YELLOW)));
            });

            int count = data.scanCount();
            StringBuilder progress = new StringBuilder(" [");
            for (int i = 0; i < 5; i++) {
                if (i < count) {
                    progress.append("■");
                } else {
                    progress.append("□");
                }
            }
            progress.append("] ").append(count).append("/5");

            tooltipComponents.add(Component.translatable("tooltip.cobblemon-cards.structure_disk.scan_progress").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(progress.toString()).withStyle(ChatFormatting.AQUA)));
        }
                
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}