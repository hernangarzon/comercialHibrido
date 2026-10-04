import type { ConversationStatus } from './api'

const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' })

/** "ahora", "hace 5 min", "hace 3 h", "ayer"... para la lista de conversaciones. */
export function timeAgo(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return ''
  const diff = Math.round((new Date(iso).getTime() - now) / 1000)
  const abs = Math.abs(diff)
  if (abs < 45) return 'ahora'
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute')
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour')
  if (abs < 7 * 86400) return rtf.format(Math.round(diff / 86400), 'day')
  return new Date(iso).toLocaleDateString('es', { day: 'numeric', month: 'short' })
}

/** Duración corta desde una fecha: "12 min", "2 h 5 min". */
export function waitingFor(iso: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.floor((now - new Date(iso).getTime()) / 60000))
  if (minutes < 1) return 'menos de 1 min'
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ${minutes % 60} min`
  return `${Math.floor(hours / 24)} d`
}

export function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' })
}

/** Etiqueta de separador de día en el chat: "Hoy", "Ayer" o la fecha. */
export function dayLabel(iso: string): string {
  const date = new Date(iso)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === today.toDateString()) return 'Hoy'
  if (date.toDateString() === yesterday.toDateString()) return 'Ayer'
  return date.toLocaleDateString('es', { weekday: 'long', day: 'numeric', month: 'long' })
}

export function initials(name: string): string {
  const clean = name.replace(/[^\p{L}\p{N}\s]/gu, ' ').trim()
  if (!clean) return '?'
  const parts = clean.split(/\s+/).filter((p) => !/^(dr|dra|sr|sra)$/i.test(p))
  const words = parts.length ? parts : clean.split(/\s+/)
  return words.slice(0, 2).map((w) => w[0]!.toUpperCase()).join('')
}

/** Color estable por cliente para el avatar. */
export function avatarColor(seed: string): string {
  const palette = [
    'bg-rose-100 text-rose-700',
    'bg-amber-100 text-amber-800',
    'bg-emerald-100 text-emerald-700',
    'bg-sky-100 text-sky-700',
    'bg-violet-100 text-violet-700',
    'bg-teal-100 text-teal-700',
    'bg-orange-100 text-orange-700',
    'bg-fuchsia-100 text-fuchsia-700',
  ]
  let hash = 0
  for (const ch of seed) hash = (hash * 31 + ch.charCodeAt(0)) | 0
  return palette[Math.abs(hash) % palette.length]!
}

export function displayName(name: string | null | undefined, phone: string): string {
  return name && name !== 'Sin Nombre' ? name : phone
}

export function whatsappLink(phone: string): string {
  return `https://wa.me/${phone.replace(/[^\d]/g, '')}`
}

export const STATUS_LABEL: Record<ConversationStatus, string> = {
  ESCALADO_PENDIENTE: 'Escalado',
  HUMANO_CONTROL: 'En atención',
  BOT_ACTIVO: 'Bot activo',
  ARCHIVADO: 'Archivado',
}

export function scoreLevel(score: number): { label: string; className: string } {
  if (score >= 70) return { label: 'Caliente', className: 'bg-orange-100 text-orange-700 ring-orange-200' }
  if (score >= 40) return { label: 'Tibio', className: 'bg-amber-50 text-amber-700 ring-amber-200' }
  return { label: 'Frío', className: 'bg-slate-100 text-slate-600 ring-slate-200' }
}

/**
 * Estado de la ventana de 24 h de WhatsApp. Abierta = se puede escribir texto libre;
 * cerrada = solo plantillas aprobadas.
 */
export function windowState(closesAt: string | null, now = Date.now()): { open: boolean; label: string } {
  if (!closesAt) return { open: false, label: 'Sin mensajes del cliente' }
  const ms = new Date(closesAt).getTime() - now
  if (ms <= 0) return { open: false, label: 'Ventana de 24 h cerrada' }
  const minutes = Math.floor(ms / 60000)
  const remaining = minutes < 60 ? `${Math.max(1, minutes)} min` : `${Math.floor(minutes / 60)} h`
  return { open: true, label: `Ventana abierta · quedan ${remaining}` }
}
