const KEY = 'signin.next'

/**
 * Google/Discord sign-in leaves the app and the server returns to "/", so the page the
 * user was heading to (e.g. an invite link) is kept here for the trip.
 */
export function rememberNext(path: string) {
  try {
    sessionStorage.setItem(KEY, path)
  } catch {
    // Storage blocked: the user lands on the home page instead.
  }
}

export function takeNext(): string | null {
  try {
    const path = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    return path
  } catch {
    return null
  }
}
