## 0.2.0-beta.1
- **Fixed: the lectern never broke.** Breaking is now a real held break (it used to restart every tick, like spam-clicking).
- **Fixed: rerolling stopped after ~14 seconds** when the villager was slow to take the job. It now keeps waiting (60 s by default, `claimTimeoutSeconds` in the config), and if the villager still hasn't taken the job it breaks and re-places the lectern instead of stopping.
- **Your camera never moves.** The lectern spot, the villager and the bed are remembered, so the mod places, breaks and clicks without turning your view. You can look around and shuffle a little while it works.
- **Night handling.** Villagers go to bed at night instead of taking jobs. The mod now pauses, sleeps in your bed (new optional calibration step 4, or "Set bed" in the menu / `/vr setbed`), then walks you back to the spot you were standing on and carries on. No bed, or can't sleep? It waits for morning and resumes by itself. Toggle with "Night" in the menu.
- **Spare lecterns.** If the broken lectern gets stuck and can't be picked up, the mod just carries on with the other lecterns in your hotbar.
- **New menu.** Enchantments are grouped by type (Swords & Axes, Tools, Armor, Helmet, Boots, Bow, Crossbow, Trident, Mace, Fishing Rod, Universal, Curses...), each group has Want all / Avoid all / Clear, plus a search box, a Wanted/Avoided filter and Want all / Avoid all / Clear for everything listed.
- New commands: `/vr setbed`, `/vr skipbed`.

## 0.1.0-beta.1
- First beta for Minecraft 26.3 (Fabric).
- **Calibration:** place the lectern, click the villager, break the lectern once by hand and the mod repeats it.
- **Menu** (V, or /vr): pick the enchants you Want or Avoid, a minimum level for each, and the highest price you accept as a percentage of the best possible price (100% = cheapest possible roll, 150% = up to 1.5x).
- **Accept any non-avoided enchant** with its own price limit.
- Stops with the trade screen open and the lectern left in place. Stops by itself after a reroll limit, when you take damage, when you open another screen, or when the villager's trades stop changing.
- Commands: /vr, /vr calibrate, /vr start, /vr stop, /vr status. Keys: V (menu), B (start / stop).
