package dev.villagerreroller.game;

import dev.villagerreroller.port.V;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
//#if MC >= 1.21
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
//#elif MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.enchantment.Enchantment;
//#else
import net.minecraft.core.Registry;
import net.minecraft.world.item.enchantment.Enchantment;
//#endif

/** Every enchantment a librarian can sell in this world, with what the menu needs to show it. */
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
        //#if MC >= 1.21
        //#if MC >= 1.21.2
        mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(ref -> {
        //#else
        mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().forEach(ref -> {
        //#endif
            if (ref.is(EnchantmentTags.TRADEABLE)) {
                out.add(new Entry(ref.getRegisteredName(), ref.value().description(), ref.value().getMaxLevel(),
                        ref.is(EnchantmentTags.DOUBLE_TRADE_PRICE)));
            }
        });
        //#else
        //#if MC >= 1.19.3
        for (Enchantment e : BuiltInRegistries.ENCHANTMENT) {
            String id = String.valueOf(BuiltInRegistries.ENCHANTMENT.getKey(e));
        //#else
        for (Enchantment e : Registry.ENCHANTMENT) {
            String id = String.valueOf(Registry.ENCHANTMENT.getKey(e));
        //#endif
            //#if MC >= 1.16
            if (!e.isTradeable()) {
                continue;
            }
            //#endif
            out.add(new Entry(id, V.translatable(e.getDescriptionId()), e.getMaxLevel(), e.isTreasureOnly()));
        }
        //#endif
        out.sort(Comparator.comparing((Entry e) -> e.name().getString()));
        return out;
    }
}
