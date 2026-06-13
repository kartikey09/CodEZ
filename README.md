# IIIT-B Contest Platform

Full-stack timed coding-contest system. Students log in with admin-issued
credentials, solve 5–6 problems in a 2-hour window, submissions are judged by
self-hosted **Judge0 CE**, and a live ICPC-style leaderboard updates in real time.

## Stack
- **Backend:** Java 21 + Spring Boot 3 (services: `auth`, `contest-api`, `orchestrator`, `realtime`)
- **Frontend:** React + Vite + TypeScript (CodeMirror editor)
- **Data:** PostgreSQL 16 (system of record) + Redis 7 (queue, leaderboard, sessions)
- **Judge:** self-hosted Judge0 CE (its own compose stack, added on Day 6–7)
- **CI/CD:** self-hosted GitHub Actions runner on the dev VM + branch protection on `main`

## Dev quickstart (run on the VM)
```bash
# 1. data services
docker compose -f deploy/docker-compose.dev.yml up -d
# 2. backend
cd services/contest-api && ./mvnw spring-boot:run      # http://localhost:8080/api/health
# 3. frontend (after Day-1 scaffold step)
cd ../../frontend && npm run dev                        # http://localhost:5173
```

## Layout
| Path | What |
|------|------|
| `services/contest-api` | Main API (Day 1: health only; problems/submissions later) |
| `services/auth` | Login + sessions (Day 2) |
| `services/orchestrator` | Judge0 queue worker (Day 7) |
| `services/realtime` | WebSocket leaderboard fan-out (Day 10) |
| `frontend` | React SPA (Day 4) |
| `deploy` | docker-compose + (later) k8s manifests |
| `docs` | scoring rules, schema draft, OpenAPI sketch, Judge0 notes |
| `loadtest` | k6 scripts (Day 13) |

See `DAY-01-RUNBOOK.md` for the Day-1 build steps.
