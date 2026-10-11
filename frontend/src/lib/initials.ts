/** Up to two letters for crests and avatars: "Sunday Night Cup" -> "SN", "Ligue" -> "LI". */
export function initials(name: string) {
  const words = name.trim().split(/\s+/).filter((w) => /[\p{L}\p{N}]/u.test(w))
  const letters = words.length >= 2 ? words[0][0] + words[1][0] : (words[0] ?? '?').slice(0, 2)
  return letters.toUpperCase()
}
