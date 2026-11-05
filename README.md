# Villager Reroller

A **client-side Fabric mod for Minecraft 26.3** that rerolls a librarian villager's trades for you. It breaks and replaces the lectern over and over, reads the enchanted book on offer, and stops when it finds the enchantment you want at a price you're happy with.

> **Status: beta (0.2.0-beta.1).** Written against Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.164.0+26.3. See [Known limitations](#known-limitations).

## Features

- **Calibration sequence** – you do the routine once by hand (place lectern, click villager, break lectern, optionally click your bed) and the mod copies it. **Your camera never moves**: positions are remembered, so you can look around and shuffle a little while it works.
- **Night handling** – villagers sleep at night instead of taking jobs, so the mod sleeps in your bed, walks you back to your spot and carries on (or waits for morning if there's no bed).
- **Patient** – waits up to 60 s for the villager to take the job, then re-places the lectern instead of giving up. Spare lecterns in your hotbar are used if the broken one gets stuck.
- **In-game menu** (default key **V**, or `/vr`) listing every enchantment a librarian can sell, **grouped by type** (swords, tools, armor, boots, bow, crossbow, trident, mace, fishing, curses...) with **Want all / Avoid all / Clear** per group and for everything, plus search. For each enchantment choose:
  - **Ignore / Want / Avoid**
  - **Minimum level** (default: max level only)
  - **Price limit as a % of the best possible price** – `100%` means only the cheapest possible roll; `150%` accepts anything up to 1.5× the cheapest. The menu shows the resulting emerald limit next to the percentage.
- **Accept any non-avoided enchant** option with its own price limit.
- Stops on a match with the **trade screen still open and the lectern left in place**, so you can buy the book right away.
- Safety stops: reroll limit, you take damage, you open another screen, villager/lectern out of reach, trades not changing (villager already traded with), lectern not picked up.
- Settings saved to `config/villagerreroller.json`.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3 (Java 25).
2. Install [Fabric API](https://modrinth.com/mod/fabric-api).
3. Drop `villager-reroller-<version>.jar` into your `mods` folder.

Client-side only: nothing needs installing on the server.

## How to use

1. Set up a villager that has **never been traded with**, next to a spot for a lectern, with a bed and a way to stand within reach of both.
2. Put a **lectern in your hotbar** and stand where you'll stay.
3. Press **V**, choose enchantments and prices, press **Calibrate**.
4. Follow the chat prompts: **place** the lectern → **right-click** the villager → **break** the lectern (with the tool you want used) → optionally **right-click a bed** a few blocks away (or `/vr skipbed`). Keep a spare lectern or two in your hotbar.
5. Press **V** → **Start rerolling** (or **B**, or `/vr start`). Press **B** or use `/vr stop` to stop.

Commands: `/vr` (menu), `/vr calibrate`, `/vr start`, `/vr stop`, `/vr status`, `/vr setbed`, `/vr skipbed`.

### How the price percentage works

A librarian's price for a book is `2 + random(5 + 10·level) + 3·level` emeralds, doubled for enchants like Mending, capped at 64. The cheapest possible roll is therefore `2 + 3·level` (×2 for Mending-type enchants). That number is **100%**. Hero of the Village discounts or demand can push the displayed price below 100% or above the roll, and the mod compares what the trade screen actually shows.

If a future game update changes the formula, edit `priceModel` in `config/villagerreroller.json`.

## Fair play

This automates actions. It is fine in singleplayer and on servers that allow it, but many servers ban automation or have anti-cheat that may flag it. Check your server's rules first.

## Known limitations

- **Beta; not yet confirmed in a live 26.3 client.** The Minecraft 26.x API changed a lot; all game calls are isolated in `game/GameActions.java`, `game/VillagerReader.java` and `game/EnchantCatalog.java` so fixes stay small.
- The villager needs a free path to claim the lectern; reroll speed depends on villager AI (roughly one reroll per 1–2 seconds in a tight setup).
- Calibration is per world and per session (positions are not saved).
- English only.

## Building

Requires **JDK 25** and **Gradle 9.6** (or run `gradle wrapper` once to create a wrapper).

```
gradle build
```

The jar appears in `build/libs/`. `gradle runClient` starts a dev client.

## Testing

`.github/workflows/launch-test.yml` starts a real Minecraft 26.3 client in CI (virtual screen), joins a local server, opens the menu and checks the log. Run it from the Actions tab.

## License

All Rights Reserved, see [LICENSE](LICENSE). You can use the official releases; please don't re-upload or copy the code.
