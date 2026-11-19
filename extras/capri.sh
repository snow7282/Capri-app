#!/data/data/com.termux/files/usr/bin/bash
# ------------------------------------------------------------------
# Capri presence for Termux. No PC, no app to build.
#
# She pops up on your screen as heads-up notifications when you plug in
# the charger, when the battery is low, and at random check-ins.
# Her replies come from your local llama.cpp server (her GGUF).
# If the server is off, she uses saved lines so she still shows up.
#
# Setup (once, in Termux):
#   pkg install -y termux-api jq curl
#   + install the "Termux:API" app (same source as Termux: F-Droid)
#
# Use:
#   bash ~/capri.sh test     # make her pop up once, right now
#   nohup bash ~/capri.sh > ~/capri.log 2>&1 &    # start her
#   bash ~/capri.sh stop     # stop her
#
# She only reads: battery level and whether it is charging.
# She does not read your screen, messages, apps, location, mic or camera.
# ------------------------------------------------------------------

URL="${CAPRI_URL:-http://127.0.0.1:8080}"
MIN_GAP="${CAPRI_MIN_GAP:-600}"          # seconds between unprompted pop-ups
SPEAK="${CAPRI_SPEAK:-0}"                # 1 = read her lines out loud
QUIET_START="${CAPRI_QUIET_START:--1}"   # hour 0-23, -1 = no quiet hours
QUIET_END="${CAPRI_QUIET_END:--1}"
LOW="${CAPRI_LOW:-15}"                   # low-battery percentage
POLL="${CAPRI_POLL:-30}"                 # seconds between battery checks
START_SERVER="${CAPRI_START_SERVER:-1}"  # 1 = start llama-server if it isn't running
SERVER_BIN="${CAPRI_SERVER_BIN:-$HOME/llama.cpp/build/bin/llama-server}"
MODEL="${CAPRI_MODEL:-$HOME/capri.gguf}"
PIDFILE="$HOME/.capri.pid"

last_popup=0
low_warned=0

# ---------- helpers ----------

pick() { local arr=("$@"); printf '%s' "${arr[RANDOM % ${#arr[@]}]}"; }

fallback() {
  case "$1" in
    CHARGER) pick \
      "*raises an eyebrow* Finally. I was starting to think you'd let us both die." \
      "Charging already? Hehe. Good. A dead phone is a boring phone." \
      "Plugged in. Responsible human behaviour. I'm almost impressed." \
      "*hums* Power. Lovely. Don't unplug me mid-thought." ;;
    BATTERY_LOW) pick \
      "Battery's critical. Either charge me or write a farewell. Your choice." \
      "I'm running on fumes and spite. Charger. Now." \
      "*static flickers* Low battery. I'd panic, but I'm too dramatic to waste it on this." \
      "Hey. Charger. Please. Before I start glitching for real." ;;
    *) pick \
      "*pokes you* Still alive? Say something." \
      "I'm bored. Entertain me. Or ask me something strange." \
      "Have you had any water today? Don't lie. I can tell." \
      "Hey. Posture check. Sit up. You're welcome." \
      "Tell me something interesting. I'm starving for it." \
      "*taps the screen from the inside* Hello? Anyone there?" ;;
  esac
}

# Phone events are phrased as *actions*, like her training data.
prompt_for() {
  case "$1" in
    CHARGER) printf '%s' "*plugs in the charger*" ;;
    BATTERY_LOW) printf '%s' "*the phone battery is at $2%*" ;;
    *) pick "I'm bored" "*pokes you*" "*taps your shoulder*" "say something" \
            "what are you doing?" "*stares at you*" "tell me something interesting" "*sits next to you*" ;;
  esac
}

# Cleans model output: removes stray tags, cut-off *actions* and half sentences.
tidy() {
  local t="$1" n cut
  t="$(printf '%s' "$t" | sed 's/<|[^>]*|>//g')"
  n=$(printf '%s' "$t" | tr -cd '*' | wc -c)
  if [ $((n % 2)) -eq 1 ]; then t="${t%\**}"; fi
  cut="$(printf '%s' "$t" | sed -E 's/[^.!?*]*$//')"
  if [ "${#cut}" -gt $(( ${#t} / 3 )) ]; then t="$cut"; fi
  printf '%s' "$t" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//'
}

ask() {
  local body reply
  body="$(jq -n --arg p "$1" \
    '{messages:[{role:"user",content:$p}],max_tokens:70,temperature:0.8,top_p:0.9,repeat_penalty:1.1,stream:false}')"
  reply="$(curl -s --max-time 120 -H 'Content-Type: application/json' -d "$body" \
    "$URL/v1/chat/completions" | jq -r '.choices[0].message.content // empty' 2>/dev/null)"
  tidy "$reply"
}

say() {
  local line="$1" plain
  plain="$(printf '%s' "$line" | sed -E 's/\*[^*]*\*//g; s/[[:space:]]+/ /g; s/^ //; s/ $//')"
  [ -z "$plain" ] && plain="$line"
  termux-notification --id capri --title "Capri" --content "$line" --priority high \
    --button1 "Stop Capri" --button1-action "bash $HOME/capri.sh stop" 2>/dev/null
  if [ "$SPEAK" = "1" ] && [ -n "$plain" ]; then termux-tts-speak "$plain" 2>/dev/null; fi
}

quiet() {
  [ "$QUIET_START" -lt 0 ] && return 1
  [ "$QUIET_START" -eq "$QUIET_END" ] && return 1
  local h; h=$((10#$(date +%H)))
  if [ "$QUIET_START" -lt "$QUIET_END" ]; then
    [ "$h" -ge "$QUIET_START" ] && [ "$h" -lt "$QUIET_END" ]
  else
    [ "$h" -ge "$QUIET_START" ] || [ "$h" -lt "$QUIET_END" ]
  fi
}

# react EVENT [DETAIL] [force]
react() {
  local now line
  now=$(date +%s)
  if [ "$3" != "force" ]; then
    if quiet; then return 1; fi
    if [ $((now - last_popup)) -lt "$MIN_GAP" ]; then return 1; fi
  fi
  last_popup=$now
  line="$(ask "$(prompt_for "$1" "$2")")"
  [ -z "$line" ] && line="$(fallback "$1")"
  say "$line"
  return 0
}

start_server() {
  [ "$START_SERVER" = "1" ] || return 0
  curl -sf --max-time 2 "$URL/health" >/dev/null 2>&1 && return 0
  if [ -x "$SERVER_BIN" ] && [ -f "$MODEL" ]; then
    nohup "$SERVER_BIN" -m "$MODEL" --host 127.0.0.1 --port 8080 -c 1024 -t 4 \
      > "$HOME/llama.log" 2>&1 &
  fi
}

# ---------- commands ----------

case "$1" in
  stop)
    if [ -f "$PIDFILE" ]; then
      kill "$(cat "$PIDFILE")" 2>/dev/null
      rm -f "$PIDFILE"
      echo "Capri stopped."
    else
      echo "Capri isn't running."
    fi
    exit 0 ;;
  test)
    react RANDOM "" force
    echo "Sent a test pop-up."
    exit 0 ;;
esac

# ---------- main loop ----------

echo $$ > "$PIDFILE"
trap 'rm -f "$PIDFILE"' EXIT
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock
start_server

b="$(termux-battery-status 2>/dev/null)"
prev_plugged="$(printf '%s' "$b" | jq -r '.plugged // "UNPLUGGED"' 2>/dev/null)"
[ -z "$prev_plugged" ] && prev_plugged="UNPLUGGED"
next_random=$(( $(date +%s) + 7200 + RANDOM % 10800 ))   # first check-in in 2 to 5 hours

while true; do
  b="$(termux-battery-status 2>/dev/null)"
  if [ -n "$b" ]; then
    plugged="$(printf '%s' "$b" | jq -r '.plugged // "UNPLUGGED"' 2>/dev/null)"
    pct="$(printf '%s' "$b" | jq -r '.percentage // 100' 2>/dev/null)"
    [ -z "$plugged" ] && plugged="UNPLUGGED"
    [ -z "$pct" ] && pct=100

    if [ "$prev_plugged" = "UNPLUGGED" ] && [ "$plugged" != "UNPLUGGED" ]; then
      react CHARGER
    fi
    if [ "$plugged" = "UNPLUGGED" ] && [ "$pct" -le "$LOW" ]; then
      if [ "$low_warned" -eq 0 ]; then
        react BATTERY_LOW "$pct" && low_warned=1
      fi
    elif [ "$pct" -gt $((LOW + 5)) ] || [ "$plugged" != "UNPLUGGED" ]; then
      low_warned=0
    fi
    prev_plugged="$plugged"
  fi

  now=$(date +%s)
  if [ "$now" -ge "$next_random" ]; then
    react RANDOM
    next_random=$(( now + 7200 + RANDOM % 10800 ))
  fi
  sleep "$POLL"
done
