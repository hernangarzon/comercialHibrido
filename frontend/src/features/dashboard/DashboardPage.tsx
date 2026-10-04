import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowDownRight, ArrowUpRight, BarChart3, Bot, Clock, Flame, Info, MessagesSquare, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { Avatar, EmptyState, ScoreBadge, Skeleton, StatusBadge } from '../../components/ui'
import { api, type ConversationStatus, type Dashboard } from '../../lib/api'
import { STATUS_LABEL, displayName, timeAgo } from '../../lib/format'
import { navigate } from '../../lib/router'
import { useStoredState } from '../../lib/storage'
import { MessagesChart } from './MessagesChart'

const PERIODS = [7, 30, 90] as const

export function DashboardPage() {
  const [days, setDays] = useStoredState<number>('ch.dashboard.days', 7)
  const { data, isLoading, isError, error, isFetching } = useQuery({
    queryKey: ['dashboard', days],
    queryFn: () => api.dashboard(days),
    refetchInterval: 60_000,
    placeholderData: (previous) => previous,
  })

  return (
    <div className="scrollbar-thin h-full overflow-y-auto">
      <div className="mx-auto max-w-6xl space-y-6 px-4 py-6 sm:px-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h1 className="text-xl font-semibold tracking-tight">Resultados</h1>
            <p className="text-sm text-slate-500">Cómo está atendiendo y vendiendo tu equipo, con el bot incluido.</p>
          </div>
          <div role="radiogroup" aria-label="Período" className="flex rounded-lg bg-white p-1 shadow-sm ring-1 ring-slate-200">
            {PERIODS.map((p) => (
              <button
                key={p}
                role="radio"
                aria-checked={days === p}
                onClick={() => setDays(p)}
                className={clsx(
                  'rounded-md px-3 py-1 text-sm font-medium transition',
                  days === p ? 'bg-brand-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100',
                )}
              >
                {p} días
              </button>
            ))}
          </div>
        </div>

        {isError ? (
          <EmptyState icon={<TriangleAlert className="size-5" />} title="No se pudieron cargar los resultados" description={error.message} />
        ) : isLoading || !data ? (
          <DashboardSkeleton />
        ) : (
          <div className={clsx('space-y-6 transition-opacity', isFetching && 'opacity-70')}>
            <KpiGrid data={data} />

            <div className="grid gap-6 lg:grid-cols-3">
              <Card className="lg:col-span-2" title="Mensajes por día" subtitle={`Últimos ${data.days} días · ${data.timezone}`}>
                <MessagesChart series={data.series} />
              </Card>
              <Card title="Estado ahora" subtitle="Todas las conversaciones de la empresa">
                <StatusBreakdown status={data.statusNow} />
              </Card>
            </div>

            <Card title="Leads más calientes" subtitle="Conversaciones abiertas con mayor intención de compra">
              <TopLeads leads={data.topLeads} />
            </Card>
          </div>
        )}
      </div>
    </div>
  )
}

function KpiGrid({ data }: { data: Dashboard }) {
  const { kpis, previous } = data
  const period = `vs. ${data.days} días anteriores`
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6">
      <StatTile icon={<MessagesSquare />} label="Conversaciones activas" value={fmt(kpis.activeConversations)}
        delta={delta(kpis.activeConversations, previous.activeConversations)} goodWhenUp period={period}
        hint="Con al menos un mensaje del cliente en el período." />
      <StatTile icon={<Bot />} label="Resueltas por el bot" value={pct(kpis.botResolutionRate)}
        delta={pointsDelta(kpis.botResolutionRate, previous.botResolutionRate)} goodWhenUp period={period}
        hint="Conversaciones activas en las que ningún asesor tuvo que escribir." />
      <StatTile icon={<Flame />} label="Leads calientes" value={fmt(kpis.hotLeads)}
        delta={delta(kpis.hotLeads, previous.hotLeads)} goodWhenUp period={period}
        hint="Conversaciones activas con lead score de 70 o más." />
      <StatTile icon={<TriangleAlert />} label="Escalaciones" value={fmt(kpis.escalations)}
        delta={delta(kpis.escalations, previous.escalations)} period={period}
        hint="Veces que el bot pasó la conversación a un asesor." />
      <StatTile icon={<Clock />} label="Respuesta del asesor" value={duration(kpis.medianAgentResponseSeconds)}
        delta={delta(kpis.medianAgentResponseSeconds, previous.medianAgentResponseSeconds)} goodWhenUp={false} period={period}
        hint={kpis.agentResponsesMeasured > 0
          ? `Mediana desde la escalación hasta la primera respuesta (${kpis.agentResponsesMeasured} casos).`
          : 'Se mide desde la escalación hasta la primera respuesta de un asesor. Aún no hay casos en el período.'} />
      <StatTile icon={<BarChart3 />} label="Mensajes del cliente" value={fmt(kpis.clientMessages)}
        delta={delta(kpis.clientMessages, previous.clientMessages)} goodWhenUp period={period}
        hint={`El bot envió ${fmt(kpis.botMessages)} y los asesores ${fmt(kpis.agentMessages)}.`} />
    </div>
  )
}

function StatTile({
  icon,
  label,
  value,
  delta,
  goodWhenUp,
  period,
  hint,
}: {
  icon: ReactNode
  label: string
  value: string
  delta: { text: string; direction: 'up' | 'down' | 'flat' } | null
  goodWhenUp?: boolean
  period: string
  hint: string
}) {
  // El color del cambio depende de si subir es bueno (más leads) o malo (más tiempo de espera).
  const good = delta && delta.direction !== 'flat' && (delta.direction === 'up') === (goodWhenUp ?? true)
  const neutral = !delta || delta.direction === 'flat' || goodWhenUp === undefined
  return (
    <div className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <div className="flex items-start justify-between gap-2">
        <p className="text-xs font-medium text-slate-500">{label}</p>
        <span className="text-slate-300 [&>svg]:size-4" aria-hidden>
          {icon}
        </span>
      </div>
      <p className="mt-2 text-2xl font-semibold tracking-tight text-slate-900 tabular-nums">{value}</p>
      <div className="mt-1 flex items-center gap-1 text-xs">
        {delta ? (
          <span
            title={period}
            className={clsx(
              'inline-flex items-center gap-0.5 font-medium',
              neutral ? 'text-slate-500' : good ? 'text-emerald-600' : 'text-red-600',
            )}
          >
            {delta.direction === 'up' && <ArrowUpRight className="size-3.5" />}
            {delta.direction === 'down' && <ArrowDownRight className="size-3.5" />}
            {delta.text}
          </span>
        ) : (
          <span className="text-slate-400">Sin comparación</span>
        )}
        <span className="group relative ml-auto">
          <Info className="size-3.5 text-slate-300" aria-label={hint} />
          <span className="pointer-events-none absolute right-0 bottom-full z-10 mb-1 hidden w-56 rounded-lg bg-slate-900 px-2.5 py-1.5 text-[11px] leading-snug text-white shadow-lg group-hover:block">
            {hint}
          </span>
        </span>
      </div>
    </div>
  )
}

const STATUS_ORDER: ConversationStatus[] = ['ESCALADO_PENDIENTE', 'HUMANO_CONTROL', 'BOT_ACTIVO', 'ARCHIVADO']

function StatusBreakdown({ status }: { status: Record<ConversationStatus, number> }) {
  const total = STATUS_ORDER.reduce((sum, s) => sum + (status[s] ?? 0), 0)
  return (
    <ul className="space-y-3">
      {STATUS_ORDER.map((s) => (
        <li key={s}>
          <a href={`#/bandeja`} className="flex items-center justify-between gap-3 rounded-lg px-2 py-1.5 transition hover:bg-slate-50">
            <StatusBadge status={s} />
            <span className="text-sm font-semibold text-slate-800 tabular-nums">
              {status[s] ?? 0}
              <span className="ml-1.5 text-xs font-normal text-slate-400">
                {total ? `${Math.round(((status[s] ?? 0) / total) * 100)}%` : ''}
              </span>
            </span>
          </a>
        </li>
      ))}
      <li className="flex justify-between border-t border-slate-100 px-2 pt-3 text-sm text-slate-500">
        Total <span className="font-semibold text-slate-800 tabular-nums">{total}</span>
      </li>
      {status.ESCALADO_PENDIENTE > 0 && (
        <li>
          <a href="#/bandeja" className="block rounded-lg bg-orange-50 px-3 py-2 text-xs font-medium text-orange-700 ring-1 ring-orange-200 hover:bg-orange-100">
            {status.ESCALADO_PENDIENTE} {status.ESCALADO_PENDIENTE === 1 ? 'cliente espera' : 'clientes esperan'} a un asesor →
          </a>
        </li>
      )}
    </ul>
  )
}

function TopLeads({ leads }: { leads: Dashboard['topLeads'] }) {
  if (!leads.length) {
    return <p className="py-6 text-center text-sm text-slate-500">Aún no hay leads con puntaje en conversaciones abiertas.</p>
  }
  return (
    <ul className="divide-y divide-slate-100">
      {leads.map((lead) => {
        const name = displayName(lead.customerName, lead.customerPhone)
        return (
          <li key={lead.id}>
            <button onClick={() => navigate('bandeja', { c: lead.id })} className="flex w-full items-center gap-3 px-1 py-3 text-left transition hover:bg-slate-50">
              <Avatar name={name} seed={lead.customerPhone} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-800">{name}</p>
                <p className="text-xs text-slate-500">
                  {STATUS_LABEL[lead.status]} · {timeAgo(lead.updatedAt)}
                </p>
              </div>
              <ScoreBadge score={lead.leadScore} />
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function Card({ title, subtitle, className, children }: { title: string; subtitle?: string; className?: string; children: ReactNode }) {
  return (
    <section className={clsx('min-w-0 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200', className)}>
      <h2 className="font-semibold text-slate-900">{title}</h2>
      {subtitle && <p className="mb-4 text-xs text-slate-500">{subtitle}</p>}
      {children}
    </section>
  )
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3 xl:grid-cols-6">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-28 rounded-xl" />
        ))}
      </div>
      <Skeleton className="h-80 rounded-xl" />
    </div>
  )
}

// ---------------------------------------------------------------- formato

function fmt(n: number): string {
  return n.toLocaleString('es')
}

function pct(rate: number | null): string {
  return rate == null ? '—' : `${Math.round(rate * 100)}%`
}

function duration(seconds: number | null): string {
  if (seconds == null) return '—'
  if (seconds < 60) return `${seconds} s`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`
}

function delta(current: number | null, previous: number | null): { text: string; direction: 'up' | 'down' | 'flat' } | null {
  if (current == null || previous == null) return null
  if (previous === 0) return current === 0 ? { text: 'Sin cambios', direction: 'flat' } : null
  const change = Math.round(((current - previous) / previous) * 100)
  if (change === 0) return { text: 'Sin cambios', direction: 'flat' }
  return { text: `${change > 0 ? '+' : ''}${change}%`, direction: change > 0 ? 'up' : 'down' }
}

/** Para tasas, el cambio se expresa en puntos porcentuales, no en % relativo. */
function pointsDelta(current: number | null, previous: number | null) {
  if (current == null || previous == null) return null
  const points = Math.round((current - previous) * 100)
  if (points === 0) return { text: 'Sin cambios', direction: 'flat' as const }
  return { text: `${points > 0 ? '+' : ''}${points} pts`, direction: points > 0 ? ('up' as const) : ('down' as const) }
}

