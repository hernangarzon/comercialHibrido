import { useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { BarChart3, Bell, BellOff, Bot, Inbox, KeyRound, LogOut, MessageCircle, Megaphone, Settings } from 'lucide-react'
import { useCallback, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { ChangePasswordDialog } from '../features/account/ChangePassword'
import { useAuth, useSession } from '../lib/auth'
import { displayName } from '../lib/format'
import { canUseBrowserNotifications, playAlertSound, requestNotificationPermission, showBrowserNotification } from '../lib/notify'
import { useRealtime, type RealtimeEvent } from '../lib/realtime'
import { navigate, type Page } from '../lib/router'
import { Avatar, IconButton } from './ui'

const NAV: { page: Page; label: string; icon: typeof Inbox; platformOnly?: boolean }[] = [
  { page: 'bandeja', label: 'Bandeja', icon: Inbox },
  { page: 'resultados', label: 'Resultados', icon: BarChart3 },
  { page: 'bot', label: 'Bot', icon: Bot },
  { page: 'ajustes', label: 'Ajustes', icon: Settings },
  { page: 'solicitudes', label: 'Solicitudes', icon: Megaphone, platformOnly: true },
]

export interface ShellBadges {
  /** Clientes esperando a un asesor. */
  pending: number
  /** Solicitudes nuevas de la landing (solo dueños de la plataforma). */
  newLeads: number
}

/**
 * Marco común de la app: encabezado, navegación y la conexión en vivo.
 * La conexión vive aquí para que las alertas lleguen en cualquier sección.
 */
export function AppShell({ page, badges, logoDataUrl, children }: { page: Page; badges: ShellBadges; logoDataUrl: string | null; children: ReactNode }) {
  const session = useSession()
  const queryClient = useQueryClient()

  const onEvent = useCallback(
    (event: RealtimeEvent) => {
      void queryClient.invalidateQueries({ queryKey: ['conversations'] })
      void queryClient.invalidateQueries({ queryKey: ['messages', event.conversationId] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })

      if (event.type === 'ESCALATION') {
        const who = displayName(event.customerName, event.customerPhone ?? 'Un cliente')
        const open = () => navigate('bandeja', { c: event.conversationId })
        playAlertSound()
        toast.warning(`${who} necesita un asesor`, {
          description: event.summary ?? (event.leadScore != null ? `Lead score ${event.leadScore}/100` : undefined),
          action: { label: 'Ver', onClick: open },
          duration: 10_000,
        })
        showBrowserNotification(`${who} necesita un asesor`, event.summary ?? 'Nueva conversación escalada', open)
      }
    },
    [queryClient],
  )

  const connected = useRealtime(session, onEvent)

  return (
    <div className="flex h-full flex-col">
      <Header page={page} connected={connected} badges={badges} logoDataUrl={logoDataUrl} />
      <div className="min-h-0 flex-1">{children}</div>
    </div>
  )
}

function Header({ page, connected, badges, logoDataUrl }: { page: Page; connected: boolean; badges: ShellBadges; logoDataUrl: string | null }) {
  const session = useSession()
  const { logout } = useAuth()
  const [permission, setPermission] = useState(() => (canUseBrowserNotifications() ? Notification.permission : 'denied'))
  const [menuOpen, setMenuOpen] = useState(false)
  const [changingPassword, setChangingPassword] = useState(false)

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b border-slate-200 bg-white px-3 sm:gap-4 sm:px-4">
      <div className="flex items-center gap-2.5">
        {logoDataUrl ? (
          <img src={logoDataUrl} alt={session.companyName} className="h-8 max-w-24 rounded-md object-contain" />
        ) : (
          <div className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-white">
            <MessageCircle className="size-4" />
          </div>
        )}
        <div className="hidden leading-tight lg:block">
          <p className="text-sm font-semibold">Comercial Híbrido</p>
          <p className="text-[11px] text-slate-500">{session.companyName}</p>
        </div>
      </div>

      <nav className="flex items-center gap-0.5 sm:ml-2">
        {NAV.filter((item) => !item.platformOnly || session.platformAdmin).map(({ page: target, label, icon: Icon }) => {
          const badge = target === 'bandeja' ? badges.pending : target === 'solicitudes' ? badges.newLeads : 0
          return (
          <a
            key={target}
            href={`#/${target}`}
            aria-current={page === target ? 'page' : undefined}
            title={label}
            className={clsx(
              'relative flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm font-medium transition',
              page === target ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
            )}
          >
            <Icon className="size-4" />
            <span className="hidden lg:inline">{label}</span>
            {badge > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-4 rounded-full bg-orange-500 px-1 text-center text-[10px] leading-4 font-semibold text-white lg:static lg:ml-0.5">
                {badge}
              </span>
            )}
          </a>
          )
        })}
      </nav>

      <span
        title={connected ? 'Recibiendo actualizaciones en tiempo real' : 'Sin conexión en vivo; reintentando'}
        className={clsx(
          'hidden items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset md:inline-flex',
          connected ? 'bg-emerald-50 text-emerald-700 ring-emerald-200' : 'bg-amber-50 text-amber-700 ring-amber-200',
        )}
      >
        <span className={clsx('size-1.5 rounded-full', connected ? 'animate-pulse bg-emerald-500' : 'bg-amber-500')} />
        {connected ? 'En vivo' : 'Reconectando…'}
      </span>

      <div className="ml-auto flex items-center gap-1">
        {canUseBrowserNotifications() && permission !== 'granted' && (
          <IconButton
            label={permission === 'denied' ? 'Notificaciones bloqueadas en el navegador' : 'Activar notificaciones'}
            onClick={async () => setPermission(await requestNotificationPermission())}
            disabled={permission === 'denied'}
          >
            {permission === 'denied' ? <BellOff className="size-4" /> : <Bell className="size-4" />}
          </IconButton>
        )}

        <div className="relative">
          <button onClick={() => setMenuOpen((v) => !v)} className="flex items-center gap-2 rounded-lg p-1 pr-2 transition hover:bg-slate-100">
            <Avatar name={session.name} seed={session.email} size="sm" />
            <span className="hidden text-left leading-tight xl:block">
              <span className="block text-xs font-medium">{session.name}</span>
              <span className="block text-[11px] text-slate-500 capitalize">{session.role.toLowerCase()}</span>
            </span>
          </button>
          {menuOpen && (
            <>
              <div className="fixed inset-0 z-40" onClick={() => setMenuOpen(false)} />
              <div className="absolute right-0 z-50 mt-1 w-60 overflow-hidden rounded-xl bg-white py-1 shadow-lg ring-1 ring-slate-200">
                <div className="border-b border-slate-100 px-3 py-2">
                  <p className="truncate text-sm font-medium">{session.name}</p>
                  <p className="truncate text-xs text-slate-500">{session.email}</p>
                  <p className="mt-1 truncate text-xs text-slate-500">{session.companyName}</p>
                </div>
                <button
                  onClick={() => {
                    setMenuOpen(false)
                    setChangingPassword(true)
                  }}
                  className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
                >
                  <KeyRound className="size-4" /> Cambiar contraseña
                </button>
                <button onClick={logout} className="flex w-full items-center gap-2 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50">
                  <LogOut className="size-4" /> Cerrar sesión
                </button>
              </div>
            </>
          )}
        </div>
      </div>
      {changingPassword && <ChangePasswordDialog onClose={() => setChangingPassword(false)} />}
    </header>
  )
}
