package dev.villagerreroller.port;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
//#if MC >= 1.21.11
import net.minecraft.resources.Identifier;
//#else
import net.minecraft.resources.ResourceLocation;
//#endif
//#if MC >= 1.19.3
import net.minecraft.core.registries.BuiltInRegistries;
//#else
import net.minecraft.core.Registry;
//#endif
//#if MC < 1.19
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
//#endif

/**
 * Everything that is spelled differently between Minecraft 1.15.2 and 1.21.11, in one place.
 * The //#if lines are resolved per Minecraft version by ports/shared.gradle.
 */
public final class V {

    private V() {
    }

    // ------------------------------------------------------------------ text

    /** Plain text with a colour. */
    public static Component text(String s, ChatFormatting color) {
        //#if MC >= 1.19
        return Component.literal(s).withStyle(color);
        //#else
        return new TextComponent(s).withStyle(color);
        //#endif
    }

    public static Component text(String s) {
        //#if MC >= 1.19
        return Component.literal(s);
        //#else
        return new TextComponent(s);
        //#endif
    }

    public static Component translatable(String key) {
        //#if MC >= 1.19
        return Component.translatable(key);
        //#else
        return new TranslatableComponent(key);
        //#endif
    }

    /** a followed by b, as one message. */
    public static Component join(Component a, Component b) {
        return a.copy().append(b);
    }

    public static void chat(Minecraft mc, Component c) {
        if (mc.player != null) {
            mc.player.displayClientMessage(c, false);
        }
    }

    public static void actionBar(Minecraft mc, Component c) {
        if (mc.player != null) {
            mc.player.displayClientMessage(c, true);
        }
    }

    // ------------------------------------------------------------------ screens

    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    public static void setScreen(Minecraft mc, Screen s) {
        mc.setScreen(s);
    }

    // ------------------------------------------------------------------ player

    public static int selectedSlot(Player p) {
        //#if MC >= 1.21.5
        return p.getInventory().getSelectedSlot();
        //#elif MC >= 1.17
        return p.getInventory().selected;
        //#else
        return p.inventory.selected;
        //#endif
    }

    public static void setSelectedSlot(Player p, int slot) {
        //#if MC >= 1.21.5
        p.getInventory().setSelectedSlot(slot);
        //#elif MC >= 1.17
        p.getInventory().selected = slot;
        //#else
        p.inventory.selected = slot;
        //#endif
    }

    public static ItemStack hotbar(Player p, int slot) {
        //#if MC >= 1.17
        return p.getInventory().getItem(slot);
        //#else
        return p.inventory.getItem(slot);
        //#endif
    }

    public static float yaw(Entity e) {
        //#if MC >= 1.17
        return e.getYRot();
        //#else
        return e.yRot;
        //#endif
    }

    public static double blockReach(Minecraft mc, LocalPlayer p) {
        //#if MC >= 1.20.5
        return p.blockInteractionRange();
        //#else
        return mc.gameMode == null ? 4.5 : mc.gameMode.getPickRange();
        //#endif
    }

    public static double entityReach(Minecraft mc, LocalPlayer p) {
        //#if MC >= 1.20.5
        return p.entityInteractionRange();
        //#else
        return mc.gameMode != null && mc.gameMode.getPickRange() > 4.5F ? 5.0 : 3.0;
        //#endif
    }

    // ------------------------------------------------------------------ doing things

    public static void useItemOn(Minecraft mc, BlockHitResult hit) {
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        //#if MC >= 1.19
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        //#else
        mc.gameMode.useItemOn(mc.player, mc.level, InteractionHand.MAIN_HAND, hit);
        //#endif
    }

    public static void interact(Minecraft mc, Entity e) {
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        mc.gameMode.interact(mc.player, e, InteractionHand.MAIN_HAND);
    }

    public static long dayTime(Minecraft mc) {
        return mc.level == null ? 0 : mc.level.getDayTime();
    }

    // ------------------------------------------------------------------ keys

    //#if MC >= 1.21.9
    private static net.minecraft.client.KeyMapping.Category category;
    //#endif

    /** A key binding in the mod's own group of the Controls screen (glfwKey: org.lwjgl.glfw.GLFW.GLFW_KEY_*). */
    public static net.minecraft.client.KeyMapping key(String id, int glfwKey) {
        //#if MC >= 1.21.9
        if (category == null) {
            //#if MC >= 1.21.11
            category = net.minecraft.client.KeyMapping.Category.register(Identifier.fromNamespaceAndPath("villagerreroller", "main"));
            //#else
            category = net.minecraft.client.KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("villagerreroller", "main"));
            //#endif
        }
        return new net.minecraft.client.KeyMapping(id, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, glfwKey, category);
        //#else
        return new net.minecraft.client.KeyMapping(id, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, glfwKey, "key.category.villagerreroller.main");
        //#endif
    }

    // ------------------------------------------------------------------ registries

    /** The item with this id ("minecraft:emerald"), or air. */
    public static Item item(String id) {
        //#if MC >= 1.21.11
        Identifier key = Identifier.tryParse(id);
        //#else
        ResourceLocation key = ResourceLocation.tryParse(id);
        //#endif
        if (key == null) {
            return net.minecraft.world.item.Items.AIR;
        }
        //#if MC >= 1.21.2
        return BuiltInRegistries.ITEM.getValue(key);
        //#elif MC >= 1.19.3
        return BuiltInRegistries.ITEM.get(key);
        //#else
        return Registry.ITEM.get(key);
        //#endif
    }

    // ------------------------------------------------------------------ sounds

    public enum Sound { START, STOP, FOUND, CLICK }

    public static void play(Minecraft mc, Sound s) {
        try {
            SimpleSoundInstance i;
            switch (s) {
                case START:
                    //#if MC >= 1.19.3
                    i = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F);
                    //#else
                    i = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.6F);
                    //#endif
                    break;
                case STOP:
                    //#if MC >= 1.19.3
                    i = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F);
                    //#else
                    i = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.7F);
                    //#endif
                    break;
                case FOUND:
                    i = SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.7F);
                    break;
                default:
                    //#if MC >= 1.19.3
                    i = SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
                    //#else
                    i = SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F);
                    //#endif
            }
            mc.getSoundManager().play(i);
        } catch (RuntimeException ignored) {
            // a sound must never break rerolling
        }
    }
}
