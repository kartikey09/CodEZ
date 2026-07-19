#!/usr/bin/env bash
#
# CodEZ — backup / restore drill (Day 15)
#
# Takes a pg_dump of the live database, restores it into a THROWAWAY database, and compares row
# counts table by table. The point is not the dump — it's proving the dump can be restored. Run it
# before every contest.
#
# The live database is only ever READ. The scratch database is dropped and recreated each run, so
# the drill is safe to repeat and safe to run while the platform is up.
#
#   ./deploy/backup-restore-drill.sh                  # dump + restore + verify, keeps the dump
#   ./deploy/backup-restore-drill.sh --keep-scratch   # leave the restored DB around to poke at
#
set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-contest}"
export PGPASSWORD="${PGPASSWORD:-contest_dev_pw}"

LIVE_DB="${LIVE_DB:-contest}"
SCRATCH_DB="${SCRATCH_DB:-contest_restore_drill}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_SCRATCH=0
[[ "${1:-}" == "--keep-scratch" ]] && KEEP_SCRATCH=1

STAMP="$(date +%Y%m%d-%H%M%S)"
DUMP="${BACKUP_DIR}/codez-${LIVE_DB}-${STAMP}.dump"

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[31mFAIL: %s\033[0m\n' "$*" >&2; exit 1; }

for tool in pg_dump pg_restore psql createdb dropdb; do
  command -v "$tool" >/dev/null 2>&1 || fail "$tool not found on PATH"
done

mkdir -p "$BACKUP_DIR"

# ---------------------------------------------------------------- 1. dump
say "Dumping ${LIVE_DB} -> ${DUMP}"
# -Fc (custom format) so pg_restore can run in parallel and restore selectively.
pg_dump -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$LIVE_DB" -Fc -f "$DUMP"
printf 'dump size: %s\n' "$(du -h "$DUMP" | cut -f1)"

# ---------------------------------------------------------------- 2. restore
say "Recreating scratch database ${SCRATCH_DB}"
dropdb   -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" --if-exists "$SCRATCH_DB"
createdb -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$SCRATCH_DB"

say "Restoring into ${SCRATCH_DB}"
# --exit-on-error so a partial restore is a hard failure, not a warning you scroll past.
pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$SCRATCH_DB" --no-owner --exit-on-error -j 4 "$DUMP"

# ---------------------------------------------------------------- 3. verify
say "Comparing row counts"

# Tables owned by both services' Flyway. Add new tables here as migrations land.
TABLES=(
  users
  auth_events
  contests
  problems
  test_cases
  submissions
  submission_test_results
  announcements
)

count_in() {  # count_in <db> <table>  -> row count, or MISSING
  local db="$1" table="$2"
  psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$db" -tAc \
    "SELECT CASE WHEN to_regclass('public.${table}') IS NULL THEN 'MISSING'
                 ELSE (SELECT count(*)::text FROM ${table}) END" 2>/dev/null | tr -d '[:space:]'
}

printf '\n%-28s %12s %12s   %s\n' "TABLE" "LIVE" "RESTORED" "RESULT"
printf -- '---------------------------------------------------------------------\n'

problems_found=0
for t in "${TABLES[@]}"; do
  live="$(count_in "$LIVE_DB" "$t")"
  restored="$(count_in "$SCRATCH_DB" "$t")"

  if [[ "$live" == "MISSING" ]]; then
    printf '%-28s %12s %12s   \033[33mskipped (not in live)\033[0m\n' "$t" "-" "-"
    continue
  fi
  if [[ "$live" == "$restored" ]]; then
    printf '%-28s %12s %12s   \033[32mok\033[0m\n' "$t" "$live" "$restored"
  else
    printf '%-28s %12s %12s   \033[31mMISMATCH\033[0m\n' "$t" "$live" "$restored"
    problems_found=1
  fi
done

# Flyway's own bookkeeping: a restore that loses migration history will fight the next deploy.
say "Flyway history"
for schema_table in flyway_history_auth flyway_history_contest flyway_schema_history; do
  live="$(count_in "$LIVE_DB" "$schema_table")"
  [[ "$live" == "MISSING" ]] && continue
  restored="$(count_in "$SCRATCH_DB" "$schema_table")"
  if [[ "$live" == "$restored" ]]; then
    printf '%-28s %12s %12s   \033[32mok\033[0m\n' "$schema_table" "$live" "$restored"
  else
    printf '%-28s %12s %12s   \033[31mMISMATCH\033[0m\n' "$schema_table" "$live" "$restored"
    problems_found=1
  fi
done

# ---------------------------------------------------------------- 4. clean up
if [[ "$KEEP_SCRATCH" -eq 0 ]]; then
  say "Dropping scratch database"
  dropdb -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" --if-exists "$SCRATCH_DB"
else
  say "Keeping ${SCRATCH_DB} (--keep-scratch)"
fi

echo
if [[ "$problems_found" -eq 0 ]]; then
  printf '\033[32mDRILL PASSED\033[0m — %s restores cleanly. Dump kept at %s\n' "$LIVE_DB" "$DUMP"
else
  fail "row counts differ between live and restored — investigate before relying on this backup"
fi

cat <<'NOTES'

Reminders:
  * Copy the dump OFF this VM. A backup that lives only on the machine it backs up is not a backup.
  * Redis is NOT dumped, on purpose: sessions and the scoreboard cache are derived state. Losing
    Redis logs everyone out and empties the board cache, both of which rebuild. The submissions
    table is the source of truth.
  * An in-flight submission queue (the `subq` stream) is also not dumped. After a restore, rejudge
    the affected problem to re-derive verdicts.
NOTES
