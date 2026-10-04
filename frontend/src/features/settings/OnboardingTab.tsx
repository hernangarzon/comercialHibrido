import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { ArrowRight, CheckCircle2, Circle, Copy } from 'lucide-react'
import { Skeleton } from '../../components/ui'
import { api } from '../../lib/api'
import { navigate, type Page } from '../../lib/router'

// A dónde lleva cada paso pendiente.
const ACTIONS: Record<string, { label: string; page: Page; tab?: string }> = {
  whatsapp: { label: 'Conectar número', page: 'ajustes', tab: 'whatsapp' },
  mensajes: { label: 'Ver bandeja', page: 'bandeja' },
  conocimiento: { label: 'Escribir conocimiento', page: 'bot' },
  catalogo: { label: 'Agregar productos', page: 'bot' },
  equipo: { label: 'Invitar', page: 'ajustes', tab: 'equipo' },
  marca: { label: 'Personalizar', page: 'ajustes', tab: 'marca' },
}

export function OnboardingTab() {
  const { data, isLoading } = useQuery({ queryKey: ['onboarding'], queryFn: api.onboarding })

  if (isLoading || !data) return <Skeleton className="h-96 rounded-xl" />

  const done = data.steps.filter((s) => s.done).length
  const progress = Math.round((done / data.steps.length) * 100)

  return (
    <div className="space-y-6">
      <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="font-semibold">{done === data.steps.length ? '¡Todo listo para vender!' : 'Tu camino para empezar a vender'}</h2>
          <span className="text-sm font-semibold text-brand-700 tabular-nums">
            {done} de {data.steps.length}
          </span>
        </div>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
          <div className="h-full rounded-full bg-brand-600 transition-all" style={{ width: `${progress}%` }} />
        </div>

        <ol className="mt-5 divide-y divide-slate-100">
          {data.steps.map((step, i) => {
            const action = ACTIONS[step.key]
            return (
              <li key={step.key} className="flex items-center gap-3 py-3.5">
                {step.done ? (
                  <CheckCircle2 className="size-5 shrink-0 text-emerald-500" aria-label="Completado" />
                ) : (
                  <Circle className="size-5 shrink-0 text-slate-300" aria-label="Pendiente" />
                )}
                <div className="min-w-0 flex-1">
                  <p className={clsx('text-sm font-medium', step.done ? 'text-slate-500' : 'text-slate-900')}>
                    <span className="mr-1 text-slate-400 tabular-nums">{i + 1}.</span> {step.title}
                  </p>
                  <p className="truncate text-xs text-slate-500">{step.detail}</p>
                </div>
                {!step.done && action && (
                  <button
                    onClick={() => navigate(action.page, action.tab ? { tab: action.tab } : undefined)}
                    className="flex shrink-0 items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-brand-700 hover:bg-brand-50"
                  >
                    {action.label} <ArrowRight className="size-3.5" />
                  </button>
                )}
              </li>
            )
          })}
        </ol>
      </section>

      {data.platformWebhook && (
        <section className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <h2 className="font-semibold">Webhook de la plataforma</h2>
          <p className="mb-3 text-xs text-slate-500">
            Solo lo ves tú como dueño de la plataforma. Regístralo en tu app de Meta (WhatsApp → Configuración) con el token de verificación
            definido en <code className="rounded bg-slate-100 px-1">WHATSAPP_WEBHOOK_VERIFY_TOKEN</code> y suscríbete al campo{' '}
            <code className="rounded bg-slate-100 px-1">messages</code>.
          </p>
          <CopyField value={data.platformWebhook.callbackUrl} />
          {data.platformWebhook.callbackUrl.startsWith('http://') && (
            <p className="mt-2 text-xs text-amber-700">Meta exige HTTPS: publícalo detrás de un dominio con certificado.</p>
          )}
        </section>
      )}
    </div>
  )
}

function CopyField({ value }: { value: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg bg-slate-50 px-3 py-2 ring-1 ring-slate-200">
      <code className="min-w-0 flex-1 truncate text-sm">{value}</code>
      <button
        onClick={() => navigator.clipboard?.writeText(value)}
        className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-slate-600 hover:bg-white"
      >
        <Copy className="size-3.5" /> Copiar
      </button>
    </div>
  )
}
