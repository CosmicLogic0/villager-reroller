package dev.villagerreroller.game;

import dev.villagerreroller.logic.BookOffer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads the trades out of the villager screen that is currently open. */
public final class VillagerReader {

    private VillagerReader() {
    }

    /**
     * @param books     every enchanted-book-for-emeralds trade found
     * @param signature a stable string describing all trades, used to notice when nothing re-rolled
     */
    public record Scan(List<BookOffer> books, String signature) {
    }

    /** @return null while no villager screen is open or its trades haven't arrived yet. */
    public static Scan scan(Minecraft mc) {
        // 26.x: the open screen lives on mc.gui, not mc.screen
        if (!(mc.gui.screen() instanceof MerchantScreen screen)) {
            return null;
        }
        MerchantOffers offers = screen.getMenu().getOffers();
        if (offers == null || offers.isEmpty()) {
            return null;
        }

        List<BookOffer> books = new ArrayList<>();
        List<String> sig = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            ItemStack costA = offer.getCostA();
            sig.add(result.getItem() + "x" + result.getCount() + "<-" + costA.getItem() + "x" + costA.getCount());

            if (!result.is(Items.ENCHANTED_BOOK) || !costA.is(Items.EMERALD)) {
                continue;
            }
            ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored == null) {
                continue;
            }
            for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
                Holder<Enchantment> holder = e.getKey();
                int level = e.getIntValue();
                books.add(new BookOffer(
                        holder.getRegisteredName(),
                        Enchantment.getFullname(holder, level).getString(),
                        level,
                        holder.value().getMaxLevel(),
                        holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE),
                        costA.getCount()));
                sig.add(holder.getRegisteredName() + ":" + level);
            }
        }
        Collections.sort(sig);
        return new Scan(books, String.join("|", sig));
    }
}
