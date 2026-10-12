package com.howlite.cobblemoncards.neoforge;

import com.howlite.cobblemoncards.util.PlatformHelper;
import com.howlite.cobblemoncards.util.EquippedAccessory;
import com.howlite.cobblemoncards.neoforge.attachment.NeoForgePlayerDataAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

import com.howlite.cobblemoncards.util.BinderLocator;
import net.minecraft.world.inventory.MenuType;
import com.howlite.cobblemoncards.menu.BinderMenu;

@SuppressWarnings("null")
public class NeoForgePlatformHelper implements PlatformHelper {

    @Override
    public String getModVersion(String modId) {
        return net.neoforged.fml.ModList.get()
                .getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("absent");
    }

    @Override
    public MenuType<BinderMenu> createBinderMenuType() {
        return net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                (containerId, playerInventory, buf) -> new BinderMenu(containerId, playerInventory, BinderLocator.read(buf))
        );
    }

    @Override
    public void openBinderMenu(ServerPlayer player, BinderLocator locator, Component title) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, playerInventory, p) -> new BinderMenu(containerId, playerInventory, locator),
                title
        ), buf -> locator.write(buf));
    }

    @Override
    public ItemStack getAccessoryItem(Player player, String slotName, int index) {
        var capability = io.wispforest.accessories.api.AccessoriesCapability.get(player);
        if (capability != null) {
            var container = capability.getContainers().get(slotName);
            if (container != null) {
                var inventory = container.getAccessories();
                if (index >= 0 && index < inventory.getContainerSize()) {
                    return inventory.getItem(index);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public List<EquippedAccessory> getEquippedAccessories(LivingEntity entity) {
        List<EquippedAccessory> list = new ArrayList<>();
        var capability = io.wispforest.accessories.api.AccessoriesCapability.get(entity);
        if (capability != null) {
            capability.getContainers().forEach((slotName, container) -> {
                var inventory = container.getAccessories();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        list.add(new EquippedAccessory(stack, slotName, i));
                    }
                }
            });
        }
        return list;
    }

    @Override
    public boolean equipItem(Player player, ItemStack stack) {
        var capability = io.wispforest.accessories.api.AccessoriesCapability.get(player);
        if (capability != null) {
            var ref = capability.attemptToEquipAccessory(stack);
            if (ref != null && ref.isValid()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasGuaranteedGodPack(Player player) {
        return player.getData(NeoForgePlayerDataAttachments.HAS_GUARANTEED_GOD_PACK.get());
    }

    @Override
    public void setGuaranteedGodPack(Player player, boolean value) {
        player.setData(NeoForgePlayerDataAttachments.HAS_GUARANTEED_GOD_PACK.get(), value);
    }

    @Override
    public int getOpenedBoosters(Player player) {
        return player.getData(NeoForgePlayerDataAttachments.OPENED_BOOSTERS.get());
    }

    @Override
    public void setOpenedBoosters(Player player, int value) {
        player.setData(NeoForgePlayerDataAttachments.OPENED_BOOSTERS.get(), value);
    }

    @Override
    public List<String> getDiscoveredCards(Player player) {
        return player.getData(NeoForgePlayerDataAttachments.DISCOVERED_CARDS.get());
    }

    @Override
    public void setDiscoveredCards(Player player, List<String> cards) {
        player.setData(NeoForgePlayerDataAttachments.DISCOVERED_CARDS.get(), cards);
    }

    @Override
    public void addDiscoveredCard(Player player, String cardId) {
        List<String> list = new ArrayList<>(getDiscoveredCards(player));
        if (!list.contains(cardId)) {
            list.add(cardId);
            setDiscoveredCards(player, list);
        }
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        NeoForgePlatformHelperClient.sendToServer(payload);
    }

    @Override
    public CreativeModeTab createCreativeTab(String name, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator displayItems) {
        CreativeModeTab tab = CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.cobblemon-cards." + name))
                .icon(icon)
                .displayItems(displayItems)
                .build();
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, 
                ResourceLocation.fromNamespaceAndPath("cobblemon-cards", name), tab);
    }

    @Override
    public com.howlite.cobblemoncards.item.custom.BinderItem createBinderItem(com.howlite.cobblemoncards.item.custom.BinderTier tier, net.minecraft.world.item.Item.Properties properties) {
        return new com.howlite.cobblemoncards.neoforge.item.NeoForgeBinderItem(tier, properties);
    }

    @Override
    public com.howlite.cobblemoncards.item.custom.MasterAlbumItem createMasterAlbumItem(net.minecraft.world.item.Item.Properties properties) {
        return new com.howlite.cobblemoncards.neoforge.item.NeoForgeMasterAlbumItem(properties);
    }

    @Override
    public com.howlite.cobblemoncards.item.custom.CardItem createCardItem(net.minecraft.world.item.Item.Properties properties) {
        return new com.howlite.cobblemoncards.neoforge.item.NeoForgeCardItem(properties);
    }

    @Override
    public boolean isNeoForge() {
        return true;
    }

    @Override
    public void refreshEquippedModifiers(LivingEntity entity) {
        var capability = io.wispforest.accessories.api.AccessoriesCapability.get(entity);
        if (capability != null) {
            capability.clearCachedSlotModifiers();
            capability.getContainers().values().forEach(container -> {
                container.markChanged();
                container.update();
            });
        }
    }
}
