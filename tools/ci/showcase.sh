#!/usr/bin/env bash
# Records the mod page's screenshots and video: starts Minecraft with the mod (showcase mode), joins a local
# server, builds a small librarian scene from the server console, then lets the mod reroll for real.
# usage: showcase.sh <jar> <game version> <out dir>
jar="$1"; gv="$2"; out="$3"
mkdir -p "$out/frames"
G="$PWD/game"; mkdir -p "$G/mods" "$G/showcase"
cp "$jar" "$G/mods/"
say() { echo "[showcase] $*"; }

url=$(curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$gv%22%5D&loaders=%5B%22fabric%22%5D" -H "User-Agent: villager-reroller-ci" | python3 -c 'import json,sys; v=json.load(sys.stdin); print(next((f["url"] for f in v[0]["files"] if f["primary"]), v[0]["files"][0]["url"]) if v else "")')
curl -sL "$url" -o "$G/mods/$(basename "$url")"
spec="fabric:$gv"
export LIBGL_ALWAYS_SOFTWARE=1
for i in 1 2 3; do portablemc --main-dir "$G" start --dry --jvm-policy mojang-then-system "$spec" > install.log 2>&1 && break; sleep 20; done

# --- local server; its console reads from a pipe so we can send commands
S="$PWD/server"; mkdir -p "$S"
surl=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | python3 -c "import json,sys,urllib.request; m=json.load(sys.stdin); v=[x for x in m['versions'] if x['id']=='$gv'][0]; print(json.load(urllib.request.urlopen(v['url']))['downloads']['server']['url'])")
curl -sL "$surl" -o "$S/server.jar"
SJ="$JAVA_HOME_25_X64"
[ -z "$SJ" ] && SJ="$JAVA_HOME"
echo "eula=true" > "$S/eula.txt"
printf 'online-mode=false\nwhite-list=false\nenforce-secure-profile=false\ngamemode=survival\nlevel-type=minecraft\\:flat\ngenerate-structures=false\ndifficulty=peaceful\nspawn-monsters=false\nspawn-animals=false\nspawn-protection=0\nview-distance=6\nmax-tick-time=-1\nmotd=Villager Reroller\n' > "$S/server.properties"
mkfifo "$S/in"
sleep infinity > "$S/in" &
(cd "$S" && timeout 1700 "$SJ/bin/java" -Xmx2G -jar server.jar nogui < in > server.log 2>&1 &)
for i in $(seq 1 90); do sleep 4; grep -q 'Done (' "$S/server.log" && break; done
srv() { echo "$*" > "$S/in"; sleep 0.4; }
mod() {
  echo "$1" > "$G/showcase/cmd"
  for i in $(seq 1 40); do sleep 0.5; [ -f "$G/showcase/cmd" ] || break; done
  sleep 0.7
}
shot() { import -display :99 -window root "$out/$1.png" && say "shot $1"; }
mouse() { DISPLAY=:99 xdotool mousemove "$1" "$2" || true; }

printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\npauseOnLostFocus:false\ntutorialStep:none\nrenderDistance:8\nguiScale:2\nnarrator:0\nsoundCategory_master:0.0\nfov:0.15\n' > "$G/options.txt"
(timeout 1500 xvfb-run -n 99 -s "-screen 0 1280x720x24 +extension GLX +render -noreset -ac" \
   portablemc --main-dir "$G" start --jvm-policy mojang-then-system -u Steve --join-server 127.0.0.1 --join-server-port 25565 "$spec" --jvm-arg=-Xmx3G,-Dvillagerreroller.showcase=true --resolution 1280x720 > game.log 2>&1 &)

for i in $(seq 1 120); do sleep 5; grep -q "VRSHOW ready" game.log && break; done
grep -q "VRSHOW ready" game.log || { say "game never got into the world"; tail -50 game.log; exit 1; }
say "in world"

# --- the scene: bookshelf wall, a villager in a little fenced cell, its lectern in front of the player
srv "time set 1000"
srv "gamerule doDaylightCycle false"
srv "gamerule advance_time false"
srv "weather clear 1000000"
srv "fill -8 -61 -8 12 -61 10 grass_block"
srv "fill -8 -60 -8 12 -52 10 air"
srv "fill 7 -60 -4 7 -57 5 bookshelf"
srv "fill 8 -60 -4 8 -56 5 spruce_planks"
for z in -1 1; do srv "setblock 5 -60 $z oak_fence"; srv "setblock 5 -59 $z oak_fence"; srv "setblock 5 -58 $z lantern"; done
srv "setblock 6 -60 0 oak_fence"; srv "setblock 6 -59 0 oak_fence"
srv "setblock 6 -60 -2 potted_poppy"; srv "setblock 6 -60 3 potted_cornflower"
srv "setblock 3 -60 3 poppy"; srv "setblock 1 -60 -2 dandelion"; srv "setblock 0 -60 2 cornflower"; srv "setblock 4 -60 -3 oxeye_daisy"
srv "setblock 4 -60 0 lectern[facing=west]"
srv "summon villager 5.5 -60 0.5 {PersistenceRequired:1b}"
srv "gamemode survival Steve"
srv "clear Steve"
srv "give Steve iron_axe 1"
srv "give Steve lectern 3"
srv "give Steve bread 16"
srv "tp Steve 2.7 -60 0.5 -90 16"
sleep 12
srv "tp Steve 2.7 -60 0.5 -90 16"
sleep 4

mod "hud off"; sleep 1; shot scene
mod "hud on"; sleep 1; shot scene-hud

# --- the menu
mod "rules"
mod "menu"; sleep 1.5
mouse 20 700; sleep 1; shot menu
mouse 340 238; sleep 1.5; shot menu-hover
mod "close"; sleep 1
srv "tp Steve 2.7 -60 0.5 -90 16"; sleep 1

# --- calibration chat
mod "calibrate"; sleep 2; shot calibrate

# --- a real run, recorded
mod "rules"
mod "demo"
srv "tp Steve 2.7 -60 0.5 -90 16"; sleep 1
mod "preset 4 -60 0"
mod "start"
n=0; start=$(date +%s)
while :; do
  n=$((n+1)); import -display :99 -window root "$out/frames/f$(printf %04d $n).png" 2>/dev/null
  [ "$n" = 40 ] && cp "$out/frames/f0040.png" "$out/running.png"
  grep -q "VRSHOW stopped" game.log && break
  [ $(( $(date +%s) - start )) -gt 330 ] && { mod "stop"; break; }
  sleep 0.35
done
for k in 1 2 3 4 5 6 7 8; do n=$((n+1)); sleep 0.4; import -display :99 -window root "$out/frames/f$(printf %04d $n).png"; done
shot found
say "frames: $n"
grep -E "VRSHOW|VRTEST" game.log | tail -20
pkill -f java || true
cp game.log "$out/game.log"; cp "$S/server.log" "$out/server.log"
