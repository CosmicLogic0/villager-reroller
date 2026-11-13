## 1.0.1
- **Logic Survival:** the mod adds Logic Survival (`play.logicsurvival.fyi`), the survival server run by the maker of this mod, to the end of your Multiplayer server list once, the first time it starts. If you don't want it, delete it there - it won't come back by itself. The new **Server** button in the menu (V) adds it again.
- Your other servers and their settings are kept exactly as they were (the old list is saved as `servers.dat_old`, like the game does).

## 1.0.0
- **Every version.** Villager Reroller now runs on Fabric (Minecraft 1.15.2 and 1.16.1 - 26.3), Forge (1.15.2 - 26.3) and NeoForge (1.20.2 - 26.3). Pick the file for your loader and Minecraft version.
- **Full release.** Each version is started in CI like a player would: the real game and loader, joining a world, opening the menu and starting calibration.
- On Minecraft 1.15 the `/vr` commands aren't available (that version has no client commands); everything works from the menu (V) and the start/stop key (B). On Forge, `/vr` needs 1.18.2 or newer.

## 0.3.0-beta.1
- **New look for the menu.** One clean dark panel with the status at the top, a toolbar (Calibrate, Bed, Night, Sounds, Accept any) and a big Start / Stop button.
- **Drop-down sections.** Enchantments are sorted into sections (Swords & Axes, Tools, Armor, Boots, Bow...) that open and close with a click. Each section has its own icon and shows how many enchants you want or avoid. "Expand all" and "Collapse all" are at the bottom, and the list scrolls with the mouse wheel.
- **Icons and hover details.** Every enchantment has an icon. Hover over it to see what it does, what it works on, its max level and the cheapest possible price.
- **Emerald price display.** Prices show an emerald icon with the number instead of "(<=34)".
- **Want all / Avoid all / Clear only at the top.** They apply to everything listed (they follow the search and Show filter).
- **Sounds.** A chime when rerolling starts, a low note when it stops, and a fanfare when your book is found. Turn them off with "Sounds" in the menu.
- **Removed:** the "Max rerolls" limit and the separate "Max %" for Accept any. Accept any now uses each enchant's own price limit (150% unless you change it).
- Right-click the Ignore/Want/Avoid and level buttons to go back a step.

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
