# Judge0 integration notes (fill in while reading the docs of your pinned release)

Judge0 CE runs as its **own docker-compose stack** (API + workers + its own internal
Postgres/Redis, with the `isolate` sandbox inside). We stand it up on Day 6 and wire
the orchestrator to it on Day 7. Until then this file is a living crib sheet.

## Pin the release
- Release pinned: `judge0-vX.Y.Z`  ← FILL IN (don't track `latest`)
- Source / docs: https://github.com/judge0/judge0  (and its `judge0.conf`)

## ⚠ cgroup requirement — VERIFY THIS ON DAY 1
Several Judge0 CE releases require the host to boot with **cgroup v1**
(`systemd.unified_cgroup_hierarchy=0` via GRUB) for `isolate` to work.
- On our setup this change happens **inside the Ubuntu VM**, never on the CachyOS host.
- Confirm whether YOUR pinned release needs it: ____  (yes / no)
- If yes, the GRUB edit + VM reboot is scheduled as the first action when Judge0 goes up.

## Auth
- Judge0 is bound to the **internal network only** and protected by a token.
- Header on every call: `X-Auth-Token: <JUDGE0_AUTH_TOKEN>`  (also `X-Auth-User` for some setups)
- Nothing but the orchestrator may reach Judge0. Students only ever hit contest-api.

## Endpoints we will use
- `GET  /languages`                 → list language ids (FILL the table below from the live instance)
- `POST /submissions?base64_encoded=true&wait=false`        → enqueue one run, returns `{ token }`
- `POST /submissions/batch?base64_encoded=true`             → enqueue many (our hot path)
- `GET  /submissions/{token}?base64_encoded=true&fields=*`  → poll one
- `GET  /submissions/batch?tokens=...&base64_encoded=true`  → poll many

Submission fields we set: `source_code`, `language_id`, `stdin`, `expected_output`,
`cpu_time_limit`, `wall_time_limit`, `memory_limit`. (base64-encode source/stdin/expected.)

## Language ids (FILL from `GET /languages` on your instance — they are version-specific)
| our key | language               | language_id |
|---------|------------------------|-------------|
| c       | C (GCC)                | ____        |
| cpp     | C++ (GCC)              | ____        |
| java    | Java (OpenJDK)         | ____        |
| python  | Python 3               | ____        |

## Status → our verdict mapping (Judge0 status ids are STABLE across releases)
| Judge0 id | Judge0 description        | our verdict |
|-----------|---------------------------|-------------|
| 1         | In Queue                  | (running)   |
| 2         | Processing                | (running)   |
| 3         | Accepted                  | AC          |
| 4         | Wrong Answer              | WA          |
| 5         | Time Limit Exceeded       | TLE         |
| 6         | Compilation Error         | CE          |
| 7         | Runtime Error (SIGSEGV)   | RE          |
| 8         | Runtime Error (SIGXFSZ)   | RE          |
| 9         | Runtime Error (SIGFPE)    | RE          |
| 10        | Runtime Error (SIGABRT)   | RE          |
| 11        | Runtime Error (NZEC)      | RE          |
| 12        | Runtime Error (Other)     | RE          |
| 13        | Internal Error            | IE          |
| 14        | Exec Format Error         | IE          |

MLE: Judge0 reports it as a runtime error; detect by comparing reported `memory`
against the limit (at/over the cap → MLE), else RE. Decide once, encode in the mapping.

## Per-language limit multipliers (applied to the problem's base time_limit_ms)
- C / C++  → ×1
- Java     → ×2  (+1000 ms flat for JVM startup)
- Python   → ×3
