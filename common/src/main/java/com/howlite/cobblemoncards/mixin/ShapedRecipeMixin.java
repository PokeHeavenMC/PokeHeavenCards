package com.howlite.cobblemoncards.mixin;

import com.howlite.cobblemoncards.item.custom.BinderItem;
import com.howlite.cobblemoncards.block.ModBlocks;
import com.howlite.cobblemoncards.item.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShapedRecipe.class)
public class ShapedRecipeMixin {
    @Inject(method = "assemble", at = @At("RETURN"), cancellable = true)
    private void copyBinderContainer(CraftingInput craftingInput, HolderLookup.Provider provider, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;

        if (result.getItem() instanceof BinderItem binder) {
            if (!com.howlite.cobblemoncards.CobblemonCardsConfig.isBinderTierEnabled(binder.getTier())) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        }

        if (!com.howlite.cobblemoncards.CobblemonCardsConfig.enableInstantDex
                && (result.is(ModItems.INSTANT_DEX) || result.is(ModItems.CARD_STRUCTURE_DISK))) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        boolean isTarget = result.getItem() instanceof BinderItem || result.is(ModBlocks.CARD_CABINET.asItem());

        if (!isTarget) return;

        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack ingredient = craftingInput.getItem(i);
            if (ingredient.isEmpty()) continue;
            if (!(ingredient.getItem() instanceof BinderItem)) continue;

            // Found the source binder — copy its card contents to the result.
            // Both binders and cabinet block items use BINDER_CONTENTS to avoid
            // the vanilla CONTAINER 256-item hard cap.
            if (ingredient.has(com.howlite.cobblemoncards.component.ModDataComponents.BINDER_CONTENTS)) {
                java.util.List<ItemStack> contents = ingredient.get(com.howlite.cobblemoncards.component.ModDataComponents.BINDER_CONTENTS);
                if (contents != null) {
                    result.set(com.howlite.cobblemoncards.component.ModDataComponents.BINDER_CONTENTS, contents);
                }
            }
            // Fallback: legacy saves that still use vanilla CONTAINER
            else if (ingredient.has(DataComponents.CONTAINER)) {
                net.minecraft.world.item.component.ItemContainerContents contents = ingredient.get(DataComponents.CONTAINER);
                if (contents != null) {
                    int totalSlots = (ingredient.getItem() instanceof BinderItem binder) ? binder.getTier().getMaxSlots(12) : 256;
                    net.minecraft.core.NonNullList<ItemStack> legacy = net.minecraft.core.NonNullList.withSize(Math.min(totalSlots, 256), ItemStack.EMPTY);
                    contents.copyInto(legacy);
                    result.set(com.howlite.cobblemoncards.component.ModDataComponents.BINDER_CONTENTS, java.util.Collections.unmodifiableList(legacy));
                }
            }
            break;
        }
    }
}
