// Alertas para que el comercial no se pierda un lead aunque esté en otra pestaña.

let audioContext: AudioContext | null = null

/** Dos tonos cortos generados en el navegador (sin archivos de audio). */
export function playAlertSound() {
  try {
    audioContext ??= new AudioContext()
    const ctx = audioContext
    ;[0, 0.18].forEach((offset, i) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = i === 0 ? 880 : 1175
      gain.gain.setValueAtTime(0.0001, ctx.currentTime + offset)
      gain.gain.exponentialRampToValueAtTime(0.18, ctx.currentTime + offset + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + offset + 0.16)
      osc.connect(gain).connect(ctx.destination)
      osc.start(ctx.currentTime + offset)
      osc.stop(ctx.currentTime + offset + 0.17)
    })
  } catch {
    // El navegador bloqueó el audio (sin interacción previa): no es crítico.
  }
}

export function canUseBrowserNotifications(): boolean {
  return typeof Notification !== 'undefined'
}

export async function requestNotificationPermission(): Promise<NotificationPermission> {
  if (!canUseBrowserNotifications()) return 'denied'
  if (Notification.permission !== 'default') return Notification.permission
  return Notification.requestPermission()
}

/** Notificación del sistema solo si la pestaña no está visible. */
export function showBrowserNotification(title: string, body: string, onClick?: () => void) {
  if (!canUseBrowserNotifications() || Notification.permission !== 'granted' || !document.hidden) return
  const notification = new Notification(title, { body, icon: '/favicon.svg', tag: 'comercial-hibrido' })
  notification.onclick = () => {
    window.focus()
    onClick?.()
    notification.close()
  }
}

/** "(3) Comercial Híbrido" con los casos que esperan atención. */
export function setTitleBadge(pending: number) {
  document.title = `${pending > 0 ? `(${pending}) ` : ''}Comercial Híbrido`
}
