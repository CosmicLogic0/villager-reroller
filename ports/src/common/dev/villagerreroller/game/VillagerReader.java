package dev.villagerreroller.game;

import dev.villagerreroller.logic.BookOffer;
import dev.villagerreroller.port.V;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
//#if MC >= 1.20.5
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
//#else
import java.util.Map;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
//#endif
//#if MC >= 1.21
import net.minecraft.tags.EnchantmentTags;
//#elif MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
import net.minecraft.core.Registry;
//#endif

/** Reads the open trade screen: every enchanted book on offer and what it costs. */
public final class VillagerReader {

    private VillagerReader() {
    }

    /** @param signature a fingerprint of all trades, to notice when they stop changing */
    public record Scan(List<BookOffer> books, String signature) {
    }

    public static Scan scan(Minecraft mc) {
        if (!(V.screen(mc) instanceof MerchantScreen)) {
            return null;
        }
        MerchantScreen screen = (MerchantScreen) V.screen(mc);
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

            if (result.getItem() != Items.ENCHANTED_BOOK || costA.getItem() != Items.EMERALD) {
                continue;
            }
            //#if MC >= 1.20.5
            ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored == null) {
                continue;
            }
            for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
                Holder<Enchantment> holder = e.getKey();
                int level = e.getIntValue();
                String id = holder.getRegisteredName();
                //#if MC >= 1.21
                String name = Enchantment.getFullname(holder, level).getString();
                int max = holder.value().getMaxLevel();
                boolean dbl = holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE);
                //#else
                String name = holder.value().getFullname(level).getString();
                int max = holder.value().getMaxLevel();
                boolean dbl = holder.value().isTreasureOnly();
                //#endif
                books.add(new BookOffer(id, name, level, max, dbl, costA.getCount()));
                sig.add(id + ":" + level);
            }
            //#else
            Map<Enchantment, Integer> stored = EnchantmentHelper.getEnchantments(result);
            for (Map.Entry<Enchantment, Integer> e : stored.entrySet()) {
                Enchantment ench = e.getKey();
                int level = e.getValue();
                //#if MC >= 1.19.3
                String id = String.valueOf(BuiltInRegistries.ENCHANTMENT.getKey(ench));
                //#else
                String id = String.valueOf(Registry.ENCHANTMENT.getKey(ench));
                //#endif
                books.add(new BookOffer(id, ench.getFullname(level).getString(), level, ench.getMaxLevel(),
                        ench.isTreasureOnly(), costA.getCount()));
                sig.add(id + ":" + level);
            }
            //#endif
        }
        Collections.sort(sig);
        return new Scan(books, String.join("|", sig));
    }
}
