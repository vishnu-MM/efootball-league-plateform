export type Tone = 'dark' | 'coral' | 'violet' | 'aqua' | 'sage'

export const TONE_CLASS: Record<Tone, string> = {
  dark: 'tone-dark',
  coral: 'tone-coral',
  violet: 'tone-violet',
  aqua: 'tone-aqua',
  sage: 'tone-sage',
}

/**
 * Colour rhythm for a run of tiles. Five steps, so tiles never match their neighbour
 * in a 1-, 2- or 3-column grid (left/right or above/below).
 */
const ROTATION: readonly Tone[] = ['coral', 'dark', 'violet', 'aqua', 'sage']

export function toneAt(index: number): Tone {
  return ROTATION[index % ROTATION.length]
}
