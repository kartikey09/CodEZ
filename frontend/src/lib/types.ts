// Mirrors the contest-platform DTOs (services/auth + services/contest-api).
// Field names match the Java records exactly so JSON maps 1:1.

export type Role = 'student' | 'admin' | string

/** /auth/me and /auth/login response (MeResponse). */
export interface Me {
  userId: number
  loginId: string
  displayName: string
  role: Role
  mustChangePassword: boolean
}

/** GET /api/problems row (ProblemSummary). */
export interface ProblemSummary {
  id: number
  label: string // "A", "B", ...
  title: string
}

/** One sample test shown on a problem (SampleTest). */
export interface SampleTest {
  ordinal: number
  input: string
  expectedOutput: string
}

/** GET /api/problems/{id} (ProblemDetail). */
export interface ProblemDetail {
  id: number
  label: string
  title: string
  statementMd: string
  timeLimitMs: number
  memoryLimitMb: number
  samples: SampleTest[]
}

/** The four languages contest-api accepts (app.submission.allowed-languages). */
export type Language = 'c' | 'cpp' | 'java' | 'python'

/** 202 body from POST /api/submissions (SubmissionAccepted). */
export interface SubmissionAccepted {
  submissionId: number
  status: string // "queued"
}

/** submissions.status lifecycle: queued -> running -> done. */
export type SubmissionStatus = 'queued' | 'running' | 'done'

/** submissions.verdict, set once status === "done". */
export type Verdict = 'AC' | 'WA' | 'TLE' | 'MLE' | 'RE' | 'CE' | 'IE'

/** submissions.kind: a real graded attempt vs. a practice Run against sample tests only. */
export type SubmissionKind = 'submit' | 'run'

/**
 * One test's outcome in a Run's breakdown. `index` is a 1..N position, never the real hidden
 * test_cases.ordinal — only ever populated for kind === 'run' rows (samples only).
 */
export interface TestResultDto {
  index: number
  verdict: Verdict
  execTimeMs: number | null
  memoryKb: number | null
}

/**
 * GET /api/submissions/{id} (SubmissionStatusResponse). No raw test ordinal is ever returned.
 * `passedTests`/`totalTests` back the "X of Y passed" display for both kinds; `tests` is a
 * per-test breakdown that is only ever non-empty for kind === 'run'.
 */
export interface SubmissionStatusResponse {
  id: number
  problemId: number
  language: string
  status: SubmissionStatus
  verdict: Verdict | null
  kind: SubmissionKind
  passedTests: number | null
  totalTests: number | null
  execTimeMs: number | null
  memoryKb: number | null
  createdAt: string
  judgedAt: string | null
  tests: TestResultDto[]
}

/** GET /api/submissions/mine row (SubmissionSummary). */
export interface SubmissionSummary {
  id: number
  problemId: number
  language: string
  status: SubmissionStatus
  verdict: Verdict | null
  createdAt: string
}

/** GET /api/time (TimeResponse). */
export interface TimeResponse {
  serverTime: string
  epochMillis: number
}

/** GET /api/contest (ContestInfo). 404 (via ApiError) when no contest is running. */
export interface ContestInfo {
  id: number
  title: string
  startsAt: string
  endsAt: string
  state: ContestState
  serverEpochMillis: number
}

// ----- leaderboard (StandingsResponse) -----

export type CellState = 'solved' | 'attempted' | 'none'

export interface StandingsCell {
  label: string
  state: CellState
  attempts: number
  acMinute: number | null
}

export interface StandingsRow {
  rank: number
  userId: number
  displayName: string
  solved: number
  penalty: number
  cells: StandingsCell[]
}

export interface StandingsResponse {
  contestId: number
  problems: string[]
  rows: StandingsRow[]
  generatedAt: string
}

/** GET /api/contests/{contestId}/standings/me (MyStanding). rank is null until judged. */
export interface MyStanding {
  contestId: number
  userId: number
  rank: number | null
  solved: number
  penalty: number
  cells: StandingsCell[]
}

// ----- admin (/api/admin/**, admin role required) -----

export type ContestState = 'draft' | 'published' | 'running' | 'finished'

/** POST /api/admin/contests body (CreateContestRequest). Instants are ISO-8601 strings. */
export interface CreateContestRequest {
  title: string
  startsAt: string
  endsAt: string
  state: ContestState
}

/** 201 body from POST /api/admin/contests (ContestResponse); also each row of GET /api/contests. */
export interface ContestResponse {
  id: number
  title: string
  startsAt: string
  endsAt: string
  state: ContestState
}

/** GET /api/contests page (ContestPageResponse). Zero-indexed, newest contest first. */
export interface ContestPageResponse {
  items: ContestResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** POST /api/admin/problems body (CreateProblemRequest). Limits <= 0 get server defaults (1000ms / 256MB). */
export interface CreateProblemRequest {
  contestId: number
  label: string
  title: string
  statementMd: string
  timeLimitMs: number
  memoryLimitMb: number
}

/** 201 body from POST /api/admin/problems (ProblemAdminResponse). */
export interface ProblemAdminResponse {
  id: number
  contestId: number
  label: string
  title: string
  timeLimitMs: number
  memoryLimitMb: number
}

/** One test in POST /api/admin/problems/{id}/test-cases (TestCaseUpload). */
export interface TestCaseUpload {
  ordinal: number
  input: string
  expectedOutput: string
  sample: boolean
}

/** POST /api/admin/problems/{id}/test-cases result (TestCaseUploadResult). */
export interface TestCaseUploadResult {
  added: number
}

/** POST /api/admin/contests/{id}/standings/rebuild result. */
export interface RebuildResult {
  rebuilt: boolean
  users: number
}

// ----- admin user management (/auth/admin/users, admin role required) -----

/** GET /auth/admin/users row (AdminUserView). */
export interface AdminUser {
  id: number
  loginId: string
  displayName: string
  role: Role
  active: boolean
  mustChangePassword: boolean
  createdAt: string | null
}

/**
 * Result of creating an account (CreatedUser). `initialPassword` is the one-time
 * plaintext to hand to the student — present only when the server generated it
 * (null when the admin supplied their own password).
 */
export interface CreatedUser {
  id: number
  loginId: string
  displayName: string
  role: Role
  initialPassword: string | null
}

/** A row the roster import refused to create (ImportResult.Skipped). */
export interface SkippedRow {
  line: number
  loginId: string
  reason: string
}

/** POST /auth/admin/users/import response (ImportResult). */
export interface ImportResult {
  created: CreatedUser[]
  skipped: SkippedRow[]
}

/** POST /auth/admin/users/{id}/reset-password response (ResetResult). */
export interface ResetResult {
  id: number
  loginId: string
  initialPassword: string
}

// ----- admin contest control + announcements (contest-api, Day 13) -----

/** A contest row for the admin control page (ContestResponse via GET /api/admin/contests). */
export interface AdminContest {
  id: number
  title: string
  startsAt: string
  endsAt: string
  state: string // draft | published | running | finished
}

/** Partial contest update (UpdateContestRequest) — send only the fields you change. */
export interface UpdateContest {
  startsAt?: string
  endsAt?: string
  state?: string
}

/** An announcement (AnnouncementView) shown in the student banner / admin list. */
export interface Announcement {
  id: number
  contestId: number
  message: string
  active: boolean
  createdAt: string
}
