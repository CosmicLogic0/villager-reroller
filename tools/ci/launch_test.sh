#!/usr/bin/env bash
# Starts Minecraft like a player's launcher does (real Mojang game, Fabric loader, real Fabric API from Modrinth) with the
# built jar in the mods folder, joins a local vanilla server, and reads the mod's VRTEST log lines.
# usage: launch_test.sh <jar> <game version> <result dir>
set -u
jar="$1"; gv="$2"; out="$3"
mkdir -p "$out"; R="$out/result.txt"
G="$PWD/game"; mkdir -p "$G/mods"
cp "$jar" "$G/mods/"

fail() { { echo "FAIL: $1"; echo; [ -f game.log ] && grep -iE "incompatible|missing|requires|exception|error|crash|villagerreroller|VRTEST" game.log | grep -viE "realms|telemetry|narrator|authlib|YggdrasilUserApi|Failed to fetch user properties|profile" | head -60; echo; echo "--- end of log ---"; tail -n 60 game.log 2>/dev/null; } > "$R"; head -100 "$R"; exit 0; }

# the newest Fabric API Modrinth lists for this exact game version (what a player downloads)
url=$(curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$gv%22%5D&loaders=%5B%22fabric%22%5D" -H "User-Agent: villager-reroller-ci" | python3 -c 'import json,sys; v=json.load(sys.stdin); print(next((f["url"] for f in v[0]["files"] if f["primary"]), v[0]["files"][0]["url"]) if v else "")')
[ -z "$url" ] && fail "no Fabric API on Modrinth for $gv"
curl -sL "$url" -o "$G/mods/$(basename "$url")"; echo "Fabric API: $(basename "$url")"
spec="fabric:$gv"
echo "Launching $spec with $(basename "$jar")"; ls "$G/mods"

export LIBGL_ALWAYS_SOFTWARE=1
# install first (downloads the game, libraries, Java), then start
ok=0; for i in 1 2 3; do portablemc --main-dir "$G" start --dry --jvm-policy mojang-then-system "$spec" > install.log 2>&1 && { ok=1; break; }; sleep 20; done
[ "$ok" = 1 ] || { cp install.log game.log; fail "the launcher could not install $spec"; }

# --- a local vanilla server (offline, creative, flat) for the game to join ---
S="$PWD/server"; mkdir -p "$S"
surl=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | python3 -c "import json,sys,urllib.request; m=json.load(sys.stdin); v=[x for x in m['versions'] if x['id']=='$gv'][0]; print(json.load(urllib.request.urlopen(v['url']))['downloads']['server']['url'])")
curl -sL "$surl" -o "$S/server.jar"
SJ="${JAVA_HOME_25_X64:-$JAVA_HOME}"
echo "eula=true" > "$S/eula.txt"
printf 'online-mode=false\nwhite-list=false\nenforce-whitelist=false\nenforce-secure-profile=false\ngamemode=creative\nlevel-type=minecraft\\:flat\ngenerate-structures=false\ndifficulty=peaceful\nspawn-monsters=false\nspawn-animals=false\nspawn-protection=0\nview-distance=6\nmax-tick-time=-1\nmotd=Villager Reroller test\n' > "$S/server.properties"
(cd "$S" && timeout 1200 "$SJ/bin/java" -Xmx2G -jar server.jar nogui > server.log 2>&1 &)
for i in $(seq 1 90); do sleep 4; grep -q 'Done (' "$S/server.log" && break; done
grep -q 'Done (' "$S/server.log" || { cp "$S/server.log" game.log; fail "the test server for $gv did not start"; }
echo "server up"

# game options: no first-launch screens, keep running without focus
printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\npauseOnLostFocus:false\ntutorialStep:none\nrenderDistance:6\nguiScale:2\nnarrator:0\nsoundCategory_master:0.0\n' > "$G/options.txt"
(timeout 900 xvfb-run -n 99 -s "-screen 0 1280x720x24 +extension GLX +render -noreset -ac" \
   portablemc --main-dir "$G" start --jvm-policy mojang-then-system -u Tester --join-server 127.0.0.1 --join-server-port 25565 "$spec" --jvm-arg=-Xmx3G,-Dvillagerreroller.selftest=true --resolution 1280x720 > game.log 2>&1 &)

loaded=0; done_=0; shot=0
for i in $(seq 1 170); do
  sleep 5
  grep -q "VRTEST loaded" game.log && loaded=1
  if [ "$shot" = 0 ] && grep -q "VRTEST menu shot" game.log; then
    shot=1; sleep 3
    # screenshot 1: mouse parked off the panel; screenshot 2: hovering the first enchant row (tooltip)
    DISPLAY=:99 xdotool mousemove 20 700 || true
    sleep 2; import -display :99 -window root "$out/menu.png" || true
    DISPLAY=:99 xdotool mousemove 340 238 || true
    sleep 2; import -display :99 -window root "$out/menu-hover.png" || true
    for f in menu menu-hover; do
      [ -f "$out/$f.png" ] && convert "$out/$f.png" -quality 82 "$out/$f.jpg" && { echo "SHOT_BEGIN $f"; base64 -w 120 "$out/$f.jpg"; echo "SHOT_END $f"; }
    done
  fi
  grep -q "VRTEST done" game.log && { done_=1; break; }
  grep -qE "VRTEST failed|Incompatible mods found|Missing or unsupported mandatory dependencies|requires .* which is missing|Crash report saved|---- Minecraft Crash Report ----" game.log && break
  pgrep -f "portablemc|java" > /dev/null || break
done
pkill -f java || true; sleep 3
cp "$S/server.log" "$out/server.log" 2>/dev/null || true

if grep -qE "Crash report saved|---- Minecraft Crash Report ----" game.log; then
  cat "$G"/crash-reports/*.txt >> game.log 2>/dev/null; fail "the game crashed"
fi
grep -q "VRTEST failed" game.log && fail "the mod's self-test threw: $(grep -m1 'VRTEST failed' game.log)"
L=$(grep -m1 -oE "(Incompatible mods found|Missing or unsupported mandatory dependencies|requires .* which is missing)[^\r]*" game.log)
[ -n "$L" ] && [ "$loaded" != 1 ] && fail "Fabric refused the mod: $L"
[ "$loaded" = 1 ] || fail "the game did not start with Villager Reroller loaded"
if [ "$done_" != 1 ]; then
  { echo "PASS (boot only): the mod loaded on $gv, but the in-world part did not run (the game never joined the test server)"; grep -E "VRTEST" game.log | head -20; } > "$R"; cat "$R"; exit 0
fi
grep -q "VRTEST menu open=true enchants=[1-9]" game.log || fail "the menu did not open or listed no enchantments: $(grep -m1 'VRTEST menu' game.log)"
grep -q "VRTEST start-without-calibration refused=true" game.log || fail "starting without calibration was not refused"
grep -q "VRTEST calibration step=WAIT_PLACE" game.log || fail "calibration did not start"
{ echo "PASS: Minecraft $gv, mod loaded, joined a world, menu opened, calibration and start-guard work"; grep -E "VRTEST" game.log | head -20; } > "$R"; cat "$R"
