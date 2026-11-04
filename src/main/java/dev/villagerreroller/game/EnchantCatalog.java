package dev.villagerreroller.game;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The enchantments a librarian can actually sell, read from the live registry (so modded enchants show up too). */
public final class EnchantCatalog {

    private EnchantCatalog() {
    }

    public record Entry(String id, Component name, int maxLevel, boolean doublePrice) {
    }

    public static List<Entry> tradeable(Minecraft mc) {
        List<Entry> out = new ArrayList<>();
        if (mc.level == null) {
            return out;
        }
        mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(ref -> {
            if (ref.is(EnchantmentTags.TRADEABLE)) {
                out.add(new Entry(
                        ref.getRegisteredName(),
                        ref.value().description(),
                        ref.value().getMaxLevel(),
                        ref.is(EnchantmentTags.DOUBLE_TRADE_PRICE)));
            }
        });
        out.sort(Comparator.comparing((Entry e) -> e.name().getString()));
        return out;
    }
}
