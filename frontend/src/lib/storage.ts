import { useCallback, useEffect, useState } from 'react'

// Preferencias locales del comercial (por navegador). Si el almacenamiento
// no está disponible, el panel funciona igual sin recordarlas.

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

function write(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // Ignorado: modo privado o almacenamiento lleno.
  }
}

export function useStoredState<T>(key: string, fallback: T) {
  const [value, setValue] = useState<T>(() => read(key, fallback))
  useEffect(() => write(key, value), [key, value])
  return [value, setValue] as const
}

/**
 * Última vez que el comercial vio cada conversación, para marcar las no leídas.
 * Una conversación está sin leer si el último mensaje es del cliente y es posterior.
 */
export function useLastSeen(userId: string) {
  const [seen, setSeen] = useStoredState<Record<string, string>>(`ch.seen.${userId}`, {})

  const markSeen = useCallback(
    (conversationId: string, at: string | null) => {
      if (!at) return
      setSeen((prev) => (prev[conversationId] && prev[conversationId] >= at ? prev : { ...prev, [conversationId]: at }))
    },
    [setSeen],
  )

  return { seen, markSeen }
}

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches)
  useEffect(() => {
    const media = window.matchMedia(query)
    const onChange = () => setMatches(media.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [query])
  return matches
}

export interface QuickReply {
  id: string
  shortcut: string
  text: string
}

export const DEFAULT_QUICK_REPLIES: QuickReply[] = [
  { id: 'saludo', shortcut: 'saludo', text: '¡Hola! Soy {nombre}, asesor comercial. Ya tengo tu caso, ¿en qué te puedo ayudar?' },
  { id: 'cotizacion', shortcut: 'cotizacion', text: 'Con gusto te preparo una cotización. ¿Me confirmas cantidades y fecha en la que lo necesitas?' },
  { id: 'pago', shortcut: 'pago', text: 'Puedes pagar por transferencia o tarjeta. Te envío los datos para el pago en un momento.' },
  { id: 'gracias', shortcut: 'gracias', text: '¡Gracias por tu compra! Cualquier duda, aquí estoy.' },
]
