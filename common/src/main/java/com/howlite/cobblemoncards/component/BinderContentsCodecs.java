package com.howlite.cobblemoncards.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sparse serialization for {@link ModDataComponents#BINDER_CONTENTS}.
 * <p>
 * In memory the component stays a dense list (index == slot), but a Master Album has 12,000 slots that are
 * mostly empty. Encoding every empty slot made the item's NBT exceed the vanilla 2 MB network NBT limit as
 * soon as another mod sent it as a {@code CompoundTag} (e.g. the Sophisticated Backpacks contents preview),
 * disconnecting the client. Only non-empty slots are written now, as {@code {slots: [{slot, item}, ...]}}.
 * The legacy dense list format is still accepted when reading and gets rewritten on the next save.
 */
public final class BinderContentsCodecs {
    /** Upper bound on decoded slot indices, so corrupted or hostile data cannot force a huge allocation. */
    private static final int MAX_SLOT = 1 << 20;

    private record Entry(int slot, ItemStack item) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, MAX_SLOT - 1).fieldOf("slot").forGetter(Entry::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(Entry::item)
        ).apply(instance, Entry::new));
    }

    private static final Codec<List<ItemStack>> SPARSE_CODEC = Entry.CODEC.listOf().fieldOf("slots").codec().xmap(BinderContentsCodecs::toDense, BinderContentsCodecs::toSparse);

    /** Writes the sparse format; reads both the sparse and the legacy dense list format. */
    public static final Codec<List<ItemStack>> CODEC = Codec.withAlternative(SPARSE_CODEC, ItemStack.OPTIONAL_CODEC.listOf());

    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> STREAM_CODEC = StreamCodec.of(
            (buf, items) -> {
                List<Entry> entries = toSparse(items);
                ByteBufCodecs.VAR_INT.encode(buf, entries.size());
                for (Entry entry : entries) {
                    ByteBufCodecs.VAR_INT.encode(buf, entry.slot());
                    ItemStack.STREAM_CODEC.encode(buf, entry.item());
                }
            },
            buf -> {
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > MAX_SLOT) {
                    throw new DecoderException("Invalid binder contents size: " + count);
                }
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int slot = ByteBufCodecs.VAR_INT.decode(buf);
                    if (slot < 0 || slot >= MAX_SLOT) {
                        throw new DecoderException("Invalid binder slot index: " + slot);
                    }
                    entries.add(new Entry(slot, ItemStack.STREAM_CODEC.decode(buf)));
                }
                return toDense(entries);
            }
    );

    private BinderContentsCodecs() {
    }

    private static List<Entry> toSparse(List<ItemStack> items) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                entries.add(new Entry(i, stack));
            }
        }
        return entries;
    }

    private static List<ItemStack> toDense(List<Entry> entries) {
        int size = 0;
        for (Entry entry : entries) {
            size = Math.max(size, entry.slot() + 1);
        }
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
        for (Entry entry : entries) {
            items.set(entry.slot(), entry.item());
        }
        return Collections.unmodifiableList(items);
    }
}
