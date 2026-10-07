package com.howlite.cobblemoncards.component;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.item.ItemStack;
import java.util.List;

public class ModDataComponents {

    public static final DataComponentType<CardData> CARD_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "card_data"),
            DataComponentType.<CardData>builder()
                    .persistent(CardData.CODEC) // On remplace "codec" par "persistent"
                    .networkSynchronized(CardData.STREAM_CODEC) // Indispensable pour les visuels !
                    .build()
    );

    public static final DataComponentType<DiskData> DISK_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "disk_data"),
            DataComponentType.<DiskData>builder()
                    .persistent(DiskData.CODEC)
                    .networkSynchronized(DiskData.STREAM_CODEC)
                    .build()
    );

    public static final DataComponentType<List<ItemStack>> CUSTOM_BOOSTER_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "custom_booster_data"),
            DataComponentType.<List<ItemStack>>builder()
                    .persistent(ItemStack.OPTIONAL_CODEC.listOf())
                    .networkSynchronized(ItemStack.OPTIONAL_STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()))
                    .build()
    );

    /**
     * Custom binder contents component — replaces DataComponents.CONTAINER to bypass the
     * vanilla 256-item hard limit in ItemContainerContents. Stores cards as a plain list (index == slot),
     * serialized sparsely by {@link BinderContentsCodecs}.
     */
    public static final DataComponentType<List<ItemStack>> BINDER_CONTENTS = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "binder_contents"),
            DataComponentType.<List<ItemStack>>builder()
                    .persistent(BinderContentsCodecs.CODEC)
                    .networkSynchronized(BinderContentsCodecs.STREAM_CODEC)
                    .build()
    );

    public static void register() {
        // Méthode vide juste pour charger la classe
    }
}