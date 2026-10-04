import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { StrictMode, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { Toaster } from 'sonner'
import { AppShell } from './components/AppShell'
import { ForcePasswordChange } from './features/account/ChangePassword'
import { LoginPage } from './features/auth/LoginPage'
import { BotSettingsPage } from './features/bot/BotSettingsPage'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { InboxPage } from './features/inbox/InboxPage'
import { LeadsPage } from './features/platform/LeadsPage'
import { SettingsPage } from './features/settings/SettingsPage'
import './index.css'
import { ApiError, api } from './lib/api'
import { AuthProvider, useAuth, useSession } from './lib/auth'
import { useBrandColor } from './lib/branding'
import { setTitleBadge } from './lib/notify'
import { useRoute } from './lib/router'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5_000,
      refetchOnWindowFocus: true,
      // No reintentar errores de permisos: solo fallos de red o del servidor.
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
})

function App() {
  const { session } = useAuth()
  // Al cerrar sesión se descarta la caché para no mostrar datos al siguiente usuario.
  useEffect(() => {
    if (!session) queryClient.clear()
  }, [session])
  if (!session) return <LoginPage />
  // Con contraseña temporal el API solo permite cambiarla: no se carga nada más.
  if (session.mustChangePassword) return <ForcePasswordChange />
  return <AuthenticatedApp key={session.token} />
}

function AuthenticatedApp() {
  const session = useSession()
  const route = useRoute()
  // Misma consulta (y caché) que usa la bandeja: alimenta el contador de casos pendientes.
  const { data: conversations = [] } = useQuery({ queryKey: ['conversations'], queryFn: api.conversations, refetchInterval: 30_000 })
  const { data: company } = useQuery({ queryKey: ['company'], queryFn: api.companySettings })
  const { data: leads = [] } = useQuery({
    queryKey: ['platform-leads'],
    queryFn: api.platformLeads,
    enabled: session.platformAdmin,
    refetchInterval: 60_000,
  })
  const pending = conversations.filter((c) => c.status === 'ESCALADO_PENDIENTE').length
  const newLeads = leads.filter((l) => l.status === 'NUEVA').length
  useEffect(() => setTitleBadge(pending), [pending])
  useBrandColor(company?.brandColor)

  const page = route.page === 'solicitudes' && !session.platformAdmin ? 'bandeja' : route.page

  return (
    <AppShell page={page} badges={{ pending, newLeads }} logoDataUrl={company?.logoDataUrl ?? null}>
      {page === 'bandeja' && <InboxPage selectedId={route.params.get('c')} />}
      {page === 'resultados' && <DashboardPage />}
      {page === 'bot' && <BotSettingsPage />}
      {page === 'ajustes' && <SettingsPage tab={route.params.get('tab')} />}
      {page === 'solicitudes' && <LeadsPage />}
    </AppShell>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <App />
        <Toaster position="bottom-right" richColors closeButton />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
