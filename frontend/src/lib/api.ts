import type {
  Me,
  ProblemSummary,
  ProblemDetail,
  SubmissionAccepted,
  SubmissionStatusResponse,
  SubmissionSummary,
  TimeResponse,
  StandingsResponse,
  MyStanding,
  Language,
  CreateContestRequest,
  ContestResponse,
  CreateProblemRequest,
  ProblemAdminResponse,
  TestCaseUpload,
  TestCaseUploadResult,
  RebuildResult,
  ContestInfo,
  AdminUser,
  CreatedUser,
  ImportResult,
  ResetResult,
} from './types'

// Both services are reached via same-origin relative paths (the dev server /
// nginx proxies /auth -> auth-service and /api -> contest-api). Every request
// sends the session cookie via credentials: 'include'.

/** Structured error carrying the backend's {code, message} plus HTTP status. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let res: Response
  try {
    res = await fetch(path, { credentials: 'include', ...init, headers })
  } catch {
    // Network / DNS / server-down — never surfaces as an HTTP status.
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server.')
  }

  if (res.status === 204) return undefined as T

  const text = await res.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }

  if (!res.ok) {
    const body = (data ?? {}) as { code?: string; message?: string }
    throw new ApiError(
      res.status,
      body.code ?? `HTTP_${res.status}`,
      body.message ?? res.statusText ?? 'Request failed',
    )
  }

  return data as T
}

export const api = {
  // ----- auth-service (/auth) -----
  login: (loginId: string, password: string) =>
    request<Me>('/auth/login', { method: 'POST', body: JSON.stringify({ loginId, password }) }),

  me: () => request<Me>('/auth/me'),

  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),

  // ----- contest-api (/api) -----
  getProblems: () => request<ProblemSummary[]>('/api/problems'),

  getProblem: (id: number) => request<ProblemDetail>(`/api/problems/${id}`),

  submit: (problemId: number, language: Language, sourceCode: string) =>
    request<SubmissionAccepted>('/api/submissions', {
      method: 'POST',
      body: JSON.stringify({ problemId, language, sourceCode }),
    }),

  getSubmission: (id: number) => request<SubmissionStatusResponse>(`/api/submissions/${id}`),

  getMySubmissions: () => request<SubmissionSummary[]>('/api/submissions/mine'),

  getTime: () => request<TimeResponse>('/api/time'),

  // The running contest, for the countdown + WebSocket channel id. 404 (ApiError) when nothing is running.
  getContest: () => request<ContestInfo>('/api/contest'),

  getStandings: (contestId: number, limit = 200) =>
    request<StandingsResponse>(`/api/contests/${contestId}/standings?limit=${limit}`),

  getMyStanding: (contestId: number) =>
    request<MyStanding>(`/api/contests/${contestId}/standings/me`),

  // ----- admin (/api/admin, role gate in SessionAuthFilter) -----
  adminCreateContest: (req: CreateContestRequest) =>
    request<ContestResponse>('/api/admin/contests', { method: 'POST', body: JSON.stringify(req) }),

  adminCreateProblem: (req: CreateProblemRequest) =>
    request<ProblemAdminResponse>('/api/admin/problems', { method: 'POST', body: JSON.stringify(req) }),

  adminUploadTests: (problemId: number, tests: TestCaseUpload[]) =>
    request<TestCaseUploadResult>(`/api/admin/problems/${problemId}/test-cases`, {
      method: 'POST',
      body: JSON.stringify({ tests }),
    }),

  adminRebuildStandings: (contestId: number) =>
    request<RebuildResult>(`/api/admin/contests/${contestId}/standings/rebuild`, { method: 'POST' }),

  // ----- auth-service admin user management (/auth/admin/users, Day 12) -----
  admin: {
    listUsers: () => request<AdminUser[]>('/auth/admin/users'),

    createUser: (loginId: string, displayName: string, role?: string, password?: string) =>
      request<CreatedUser>('/auth/admin/users', {
        method: 'POST',
        body: JSON.stringify({ loginId, displayName, role, password }),
      }),

    importUsers: (csv: string) =>
      request<ImportResult>('/auth/admin/users/import', {
        method: 'POST',
        body: JSON.stringify({ csv }),
      }),

    resetPassword: (id: number) =>
      request<ResetResult>(`/auth/admin/users/${id}/reset-password`, { method: 'POST' }),

    setActive: (id: number, active: boolean) =>
      request<AdminUser>(`/auth/admin/users/${id}/${active ? 'activate' : 'deactivate'}`, {
        method: 'POST',
      }),

    setRole: (id: number, role: string) =>
      request<AdminUser>(`/auth/admin/users/${id}/role`, {
        method: 'PATCH',
        body: JSON.stringify({ role }),
      }),
  },
}
