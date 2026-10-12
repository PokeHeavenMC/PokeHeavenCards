package com.howlite.cobblemoncards.fabric;

import com.howlite.cobblemoncards.util.PlatformHelper;
import com.howlite.cobblemoncards.util.EquippedAccessory;
import com.howlite.cobblemoncards.attachment.PlayerDataAttachments;
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

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

import com.howlite.cobblemoncards.util.BinderLocator;
import net.minecraft.world.inventory.MenuType;
import com.howlite.cobblemoncards.menu.BinderMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

@SuppressWarnings("null")
public class FabricPlatformHelper implements PlatformHelper {

    @Override
    public String getModVersion(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("absent");
    }

    @Override
    public MenuType<BinderMenu> createBinderMenuType() {
        return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                (containerId, playerInventory, locator) -> new BinderMenu(containerId, playerInventory, locator),
                BinderLocator.STREAM_CODEC
        );
    }

    @Override
    public void openBinderMenu(ServerPlayer player, BinderLocator locator, Component title) {
        player.openMenu(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<BinderLocator>() {
            @Override
            public BinderLocator getScreenOpeningData(ServerPlayer p) {
                return locator;
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player p) {
                return new BinderMenu(containerId, playerInventory, locator);
            }

            @Override
            public Component getDisplayName() {
                return title;
            }
        });
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
    public void refreshEquippedModifiers(LivingEntity entity) {
        com.howlite.cobblemoncards.CobblemonCards.LOGGER.info("CobblemonCards: FabricPlatformHelper.refreshEquippedModifiers called for " + entity.getName().getString());
        var capability = io.wispforest.accessories.api.AccessoriesCapability.get(entity);
        if (capability != null) {
            capability.clearCachedSlotModifiers();
            capability.getContainers().values().forEach(container -> {
                container.markChanged();
                container.update();
            });
        }
    }

    @Override
    public boolean hasGuaranteedGodPack(Player player) {
        return player.getAttachedOrCreate(PlayerDataAttachments.HAS_GUARANTEED_GOD_PACK, () -> false);
    }

    @Override
    public void setGuaranteedGodPack(Player player, boolean value) {
        player.setAttached(PlayerDataAttachments.HAS_GUARANTEED_GOD_PACK, value);
    }

    @Override
    public int getOpenedBoosters(Player player) {
        return player.getAttachedOrCreate(PlayerDataAttachments.OPENED_BOOSTERS, () -> 0);
    }

    @Override
    public void setOpenedBoosters(Player player, int value) {
        player.setAttached(PlayerDataAttachments.OPENED_BOOSTERS, value);
    }

    @Override
    public List<String> getDiscoveredCards(Player player) {
        return player.getAttachedOrCreate(PlayerDataAttachments.DISCOVERED_CARDS, ArrayList::new);
    }

    @Override
    public void setDiscoveredCards(Player player, List<String> cards) {
        player.setAttached(PlayerDataAttachments.DISCOVERED_CARDS, cards);
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
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        try {
            Class<?> clientHelper = Class.forName("com.howlite.cobblemoncards.fabric.FabricPlatformHelperClient");
            java.lang.reflect.Method method = clientHelper.getMethod("sendToServer", CustomPacketPayload.class);
            method.invoke(null, payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public CreativeModeTab createCreativeTab(String name, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator displayItems) {
        CreativeModeTab tab = net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.cobblemon-cards." + name))
                .icon(icon)
                .displayItems(displayItems)
                .build();
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, 
                ResourceLocation.fromNamespaceAndPath("cobblemon-cards", name), tab);
    }

    @Override
    public com.howlite.cobblemoncards.item.custom.BinderItem createBinderItem(com.howlite.cobblemoncards.item.custom.BinderTier tier, net.minecraft.world.item.Item.Properties properties) {
        return new com.howlite.cobblemoncards.fabric.item.FabricBinderItem(tier, properties);
    }

    @Override
    public com.howlite.cobblemoncards.item.custom.MasterAlbumItem createMasterAlbumItem(net.minecraft.world.item.Item.Properties properties) {
        return new com.howlite.cobblemoncards.fabric.item.FabricMasterAlbumItem(properties);
    }

    @Override
    public com.howlite.cobblemoncards.item.custom.CardItem createCardItem(net.minecraft.world.item.Item.Properties properties) {
        return new com.howlite.cobblemoncards.item.custom.CardItem(properties);
    }
}
