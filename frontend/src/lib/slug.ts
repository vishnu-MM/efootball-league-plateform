/**
 * Mirrors the backend's SlugGenerator.slugify so a crest preview (seeded by slug) matches the saved league.
 * The server may still append "-2", "-3"… if the slug is taken.
 */
export function slugify(name: string) {
  const ascii = name.normalize('NFKD').replace(/\p{M}/gu, '')
  let slug = ascii.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  if (slug.length > 60) slug = slug.slice(0, 60).replace(/-+$/, '')
  return slug || 'league'
}
