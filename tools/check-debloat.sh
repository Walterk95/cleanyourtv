#!/usr/bin/env bash
# Report which packages from your debloat lists are enabled on the TV again. Some firmwares switch a few
# disabled apps back on at boot or after an update; this lists them so you can disable them once more.
# Read-only: it changes nothing on the TV.
#
# Usage: TV_HOST=192.168.1.50 tools/check-debloat.sh ~/tv-backup/lot*.txt
# Exit status: 0 when none is enabled, 1 when some are, 2 on a usage or connection problem.
set -uo pipefail
[ "$#" -ge 1 ] || { echo "usage: check-debloat.sh <debloat list> [<more lists>...]" >&2; exit 2; }
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 list.txt}"
# Resolve every list against the caller's directory before moving into the repo.
LISTS=()
for f in "$@"; do
  [ -f "$f" ] || { echo "no such list: $f" >&2; exit 2; }
  LISTS+=("$(cd "$(dirname "$f")" && pwd)/$(basename "$f")")
done
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 2; }
export MSYS_NO_PATHCONV=1
# Same list format as disable.sh: one package per line, # comments allowed, BOM and CRLF tolerated.
PKGS=$(for f in "${LISTS[@]}"; do
  LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); print}' "$f"
done | grep -vE '^\s*(#|$)' | sed -E 's/\s+#.*$//' | awk '{print $1}' | grep -E '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' | sort -u)
[ -n "$PKGS" ] || { echo "no package names in the lists" >&2; exit 2; }
ENABLED=$("$PY" -I tools/tv.py 'pm list packages -e') || { echo "could not reach the TV at $TV_HOST" >&2; exit 2; }
ENABLED=$(printf '%s\n' "$ENABLED" | tr -d '\r' | sed -n 's/^package://p')
[ -n "$ENABLED" ] || { echo "the TV returned no package list; nothing was checked" >&2; exit 2; }
BACK=$(printf '%s\n' "$PKGS" | grep -Fx -f <(printf '%s\n' "$ENABLED") || true)
TOTAL=$(printf '%s\n' "$PKGS" | wc -l | tr -d ' ')
if [ -z "$BACK" ]; then
  echo "OK: none of the $TOTAL packages in the lists is enabled."
  exit 0
fi
echo "Enabled again ($(printf '%s\n' "$BACK" | wc -l | tr -d ' ') of $TOTAL):"
printf '%s\n' "$BACK" | sed 's/^/  /'
echo "Put them in a list file and run tools/disable.sh on it, or uninstall them for user 0:"
echo "  tools/venv/bin/python -I tools/tv.py 'pm uninstall -k --user 0 <package>'"
exit 1
