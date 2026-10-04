import { useEffect } from 'react'

export const DEFAULT_BRAND = '#4f46e5'

/** Aplica el color de la empresa a todo el panel (variables CSS de la gama de marca). */
export function useBrandColor(color: string | null | undefined) {
  useEffect(() => {
    const value = color && /^#[0-9a-f]{6}$/i.test(color) ? color : DEFAULT_BRAND
    document.documentElement.style.setProperty('--brand', value)
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', value)
  }, [color])
}

/** Contraste WCAG contra blanco: los botones llevan texto blanco sobre el color de marca. */
export function contrastWithWhite(hex: string): number {
  const n = parseInt(hex.slice(1), 16)
  const channel = (v: number) => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }
  const luminance = 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255)
  return 1.05 / (luminance + 0.05)
}
