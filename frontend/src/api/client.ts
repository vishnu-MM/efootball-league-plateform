export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function toError(response: Response): Promise<ApiError> {
  // Spring returns RFC 7807 problem details: { title, detail, status, ... }
  let message = `${response.status} ${response.statusText}`
  try {
    const problem = (await response.json()) as { detail?: string; title?: string }
    message = problem.detail ?? problem.title ?? message
  } catch {
    // Not JSON; keep the status text.
  }
  return new ApiError(response.status, message)
}

function csrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith('XSRF-TOKEN='))
    ?.split('=')[1]
}

export async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, { credentials: 'include', headers: { Accept: 'application/json' } })
  if (!response.ok) throw await toError(response)
  return (await response.json()) as T
}

/** POST/PUT/DELETE with the CSRF token the backend expects. Returns undefined for 204 responses. */
export async function send<T = void>(method: 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  const token = csrfToken()
  const response = await fetch(path, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
      ...(token && { 'X-XSRF-TOKEN': decodeURIComponent(token) }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw await toError(response)
  return (response.status === 204 ? undefined : await response.json()) as T
}
