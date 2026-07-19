# CodEZ — Security Audit (Day 15)

A route-by-route authorisation audit of every HTTP and WebSocket surface, plus the fixes applied.
Written to be re-runnable: §5 is the checklist to repeat whenever a new endpoint is added.

Audited at the Day-14 commit. Services: `auth` (:8081), `contest-api` (:8080),
`orchestrator` (worker, no HTTP), `realtime` (:8083).

---

## 1. Findings

| # | Severity | Finding | Status |
|---|---|---|---|
| F1 | **High** | Session filters authorised on the raw URI, which differs from the path Spring routes on — admin routes reachable without the admin check | **Fixed** |
| F2 | **Medium** | No limit on failed logins: unlimited offline-speed password guessing against admin-issued accounts | **Fixed** |
| F3 | **Medium** | Deactivating or demoting a user left their live session working with old privileges until TTL (up to 8h) | **Fixed** |
| F4 | **Medium** | No audit trail: no way to answer "who deactivated this account", or to see a password-spray in progress | **Fixed** |
| F5 | **Low** | Unknown login id skipped the bcrypt compare, so response time revealed which accounts exist | **Fixed** |
| F6 | **Low** | contest-api returned `code: "UNAUTHORIZED"` with HTTP 403 for the admin refusal; auth returned `FORBIDDEN` | **Fixed** |
| F7 | Info | `services/auth/.../web/dto/ApiExceptionHandler.java` declares `package …auth.web` but sits in `…/web/dto/` | Left alone — compiles, cosmetic |
| F8 | **Verified OK** | Stored XSS via problem statements / announcements | No fix needed — see §3 |
| F9 | **Verified OK** | Submission + Run rate limiting, realtime handshake, submission ownership scoping | No fix needed — see §4 |

### F1 — authorisation bypass via path confusion (the important one)

Both filters did:

```java
String path = req.getRequestURI();          // RAW: not decoded, not normalised
... if (AuthPaths.isAdminOnly(path)) ...    // startsWith("/api/admin/")
```

`getRequestURI()` returns the path exactly as sent. Spring maps the request to a controller using
the **decoded, normalised** path. Every string below routes to an admin handler while failing the
`startsWith` test, so the admin check was skipped:

```
/api/%61dmin/contests          percent-encoded 'a'
/api/%2561dmin/contests        double-encoded
/api//admin/contests           duplicate slash
/api/./admin/contests          dot segment
/api/problems/../admin/contests parent segment
/api/admin/contests;jsessionid=x path parameter
```

**Impact:** any authenticated student could read and write admin endpoints — list/patch contests,
post announcements, edit problems, trigger rejudges, list users (auth-service had the same flaw on
`/auth/admin/**`, so: create accounts, reset passwords, grant themselves `admin`).

**Fix:** `RequestPaths.canonical()` is applied before any authorisation decision — query and path
parameters stripped, percent-decoded until stable (bounded at 3 passes so double-encoding can't
slip through), duplicate slashes collapsed, `.`/`..` resolved. Additionally
`RequestPaths.isSuspicious()` rejects encoded path separators (`%2f`, `%5c`), encoded `%` and null
bytes with a **400** — these have no legitimate use here. Regression test:
`contest-api/src/test/java/in/ac/iiitb/contest/session/RequestPathsTest.java`, plus section B of
`http/regression/09-security.http`, which replays every vector as a student and asserts none
returns 200.

### F2 — login throttle

`LoginThrottle` (auth-service) keeps two counters in Redis: per account (default **8** failures)
and per source IP (default **30**), each over a **300s** window, tripping a **900s** lockout.
Counting keys on the *submitted* login id whether or not it resolves, so the throttle can't be used
to enumerate accounts. A lockout returns **429** with `Retry-After` and code `LOGIN_THROTTLED`.

It **fails open** on a Redis outage — deliberately. On contest day 200-300 students log in within a
few minutes; a Redis blip that locked all of them out of an exam is a worse failure than briefly
losing the throttle.

Knobs (all `@Value` defaults, so no config change is required to deploy):

```yaml
app:
  login:
    max-account-failures: 8
    max-ip-failures: 30
    window-seconds: 300
    lockout-seconds: 900
```

> **Note on the IP counter behind a proxy.** `X-Forwarded-For` is attacker-controllable unless a
> proxy you control overwrites it. Only the first hop is read and it's length-capped, so the worst
> case is an attacker throttling themselves. It is recorded for forensics and used for throttling,
> and **never** for an access-control decision.

### F3 — live session revocation

Sessions cache `role` and `mustChange` at login and are never re-read from Postgres, so
`deactivate` and role changes only took effect at next login. `SessionService` now maintains a
reverse index `sessions:user:{userId}` → set of sids, and `destroyAllForUser()` kills them all.
`AdminUserController` calls it on **deactivate**, **role change**, and **password reset**.

Trade-off kept as-is: `resolve()` still doesn't re-read Postgres per request. Revocation is now
push-based (the admin action clears sessions) rather than pull-based (every request re-checks),
which keeps the hot path a single Redis hash read.

### F5 — login timing side-channel

An unknown login id returned before any bcrypt comparison; a known one paid ~100ms of bcrypt. That
difference is trivially measurable and turns login into an account-enumeration oracle. Login now
always performs one comparison — against a fixed dummy hash when the account doesn't exist.

---

## 2. Route authorisation matrix (post-fix)

`P` = public (no session), `S` = any authenticated session, `A` = admin only.
Every non-public route additionally rejects a user whose password change is still pending.

### auth-service (:8081)

| Route | Gate |
|---|---|
| `POST /auth/login` | P (throttled) |
| `GET /actuator/health`, `/error` | P |
| `GET /auth/me`, `POST /auth/logout`, `POST /auth/change-password` | S — and reachable *during* must-change, by design |
| `GET/POST /auth/admin/users`, `POST /auth/admin/users/import`, `POST /auth/admin/users/{id}/reset-password\|activate\|deactivate`, `PATCH /auth/admin/users/{id}/role` | A |
| `GET /auth/admin/events` | A *(new)* |

### contest-api (:8080)

| Route | Gate |
|---|---|
| `GET /api/health`, `/actuator/*`, `/error` | P |
| `GET /api/problems`, `/api/problems/{id}`, `POST /api/problems/{id}/run` | S |
| `GET /api/contest`, `/api/contests`, `/api/time`, `/api/announcements` | S |
| `POST /api/submissions`, `GET /api/submissions/mine`, `GET /api/submissions/{id}` | S — `{id}` is owner-scoped, a non-owner gets 404 |
| `GET /api/contests/{id}/standings`, `/standings/me` | S |
| everything under `/api/admin/**` (contests, problems, announcements, rejudge, standings rebuild) | A |

### realtime (:8083)

| Surface | Gate |
|---|---|
| WebSocket upgrade | S — session resolved from the `sid` cookie; must-change → 403; missing/non-numeric `contestId` → 400 |
| Subscription scope | Fixed at handshake from server-side attributes; the client cannot re-subscribe to another contest or another user's channel afterwards |

---

## 3. F8 — stored XSS: verified safe, and the invariant to keep

Problem statements are admin-authored Markdown (Day 14 made that a routine action) and are rendered
to every student. Checked:

- `frontend/src/components/problem-panel.tsx` renders with `<ReactMarkdown>{problem.statementMd}</ReactMarkdown>`.
- `react-markdown` **v10** does not render raw HTML unless `rehype-raw` is added. It isn't — the only
  markdown dependency in `package.json` is `react-markdown` itself.
- Its default `urlTransform` strips dangerous URL schemes, so `[x](javascript:alert(1))` is inert.
- No `dangerouslySetInnerHTML` anywhere in `frontend/src`.
- Announcement text renders as `{a.message}` in JSX, which React escapes.

**Conclusion: no stored-XSS vector today.** The invariants that keep it that way:

1. **Never add `rehype-raw`** (or `rehype-katex` with `trust: true`, or any `allowDangerousHtml`) to
   the statement renderer. That single dependency turns every admin into an XSS vector against the
   whole cohort.
2. **Never introduce `dangerouslySetInnerHTML`** for statements, announcements, or display names.
3. If HTML in statements is ever genuinely needed, add `rehype-sanitize` **in the same change**.

---

## 4. F9 — verified-OK controls (no change made)

- **Submission rate limiting** already existed and is sound: a per-user in-flight lock
  (`inflight:{userId}`, 120s TTL) plus a submit cooldown (`cooldown:{userId}`, 10s), and Run has its
  own separate 3-per-12s limiter that never touches the submit cooldown.
- **Source size** is capped at 64 KB before anything reaches the judge.
- **Submission ownership**: `GET /api/submissions/{id}` is scoped by user id, and a non-owner gets
  **404** rather than 403 — correct, since 403 would confirm the row exists.
- **Hidden test data** never leaves the server: the Day-14 authoring endpoint returns full data for
  samples only and byte sizes for hidden tests, and the Run path structurally filters to sample
  tests before judging.
- **Password storage**: BCrypt cost 12; generated passwords come from an unambiguous alphabet via
  `SecureRandom`; plaintext is returned exactly once at creation/reset and never stored.
- **Login response shape**: unknown id and wrong password are indistinguishable (same 401, same
  code, and now the same timing).

---

## 5. Re-run checklist (do this whenever a route is added)

1. Does the new path fall under an existing gate prefix (`/api/admin/**`, `/auth/admin/**`)? If it
   needs admin rights but sits elsewhere, the filter will **not** protect it — extend `AuthPaths`.
2. Is the route in the §2 matrix? If not, add it.
3. If it returns anything derived from hidden test data, or another user's rows, scope the query by
   the session user id and return **404** (not 403) on a miss.
4. If it renders user- or admin-supplied text in the frontend, check §3's invariants.
5. Run `09-security.http`, and add the new admin path to section B's bypass replay.

---

## 6. Backup / restore drill

`deploy/backup-restore-drill.sh` takes a `pg_dump` of the live database, restores it into a scratch
database, and verifies row counts match. Run it **before every contest**; a backup nobody has
restored is a hypothesis, not a backup. See the Day-15 runbook §7 for the walkthrough.
