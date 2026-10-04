import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, ExternalLink, RefreshCw, ShieldCheck, TriangleAlert } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Button, Skeleton } from '../../components/ui'
import { api, type WhatsAppStatus } from '../../lib/api'

const STEPS = [
  {
    title: 'Abre tu app en Meta for Developers',
    text: 'En developers.facebook.com entra a tu app → WhatsApp → Configuración de la API.',
  },
  {
    title: 'Copia el identificador del número',
    text: 'Debajo del número que vas a usar aparece «Identificador del número de teléfono» (Phone Number ID): solo números.',
  },
  {
    title: 'Copia el ID de la cuenta de WhatsApp Business',
    text: 'En la misma página aparece «Identificador de la cuenta de WhatsApp Business». Lo usamos para leer tus plantillas aprobadas.',
  },
  {
    title: 'Genera un token permanente',
    text: 'En Business Settings → Usuarios del sistema crea uno con permiso whatsapp_business_messaging y genera su token. Los tokens temporales vencen en 24 horas.',
  },
]

export function WhatsAppTab({ editable }: { editable: boolean }) {
  const queryClient = useQueryClient()
  const { data: settings, isLoading } = useQuery({ queryKey: ['company'], queryFn: api.companySettings })
  const [phoneNumberId, setPhoneNumberId] = useState<string | null>(null)
  const [businessAccountId, setBusinessAccountId] = useState<string | null>(null)
  const [token, setToken] = useState('')
  const [status, setStatus] = useState<WhatsAppStatus | null>(null)

  const test = useMutation({
    mutationFn: api.testWhatsapp,
    onSuccess: setStatus,
    onError: (err) => toast.error(err.message),
  })

  const connect = useMutation({
    mutationFn: () =>
      api.connectWhatsapp(
        (phoneNumberId ?? settings?.whatsappPhoneNumberId ?? '').trim(),
        token.trim(),
        (businessAccountId ?? settings?.whatsappBusinessAccountId ?? '').trim(),
      ),
    onSuccess: (result) => {
      setStatus(result)
      setToken('')
      void queryClient.invalidateQueries({ queryKey: ['company'] })
      void queryClient.invalidateQueries({ queryKey: ['onboarding'] })
      toast.success(`Conectado: ${result.verification.verifiedName ?? result.verification.displayPhoneNumber ?? 'número verificado'}.`)
    },
    onError: (err) => toast.error(err.message),
  })

  if (isLoading || !settings) return <Skeleton className="h-96 rounded-xl" />

  const value = phoneNumberId ?? settings.whatsappPhoneNumberId ?? ''

  function submit(e: FormEvent) {
    e.preventDefault()
    connect.mutate()
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <section className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div>
            <h2 className="font-semibold">Número de WhatsApp</h2>
            <p className="text-xs text-slate-500">El número por el que el bot y tu equipo atienden a los clientes.</p>
          </div>
          <Button variant="secondary" size="sm" onClick={() => test.mutate()} loading={test.isPending}>
            <RefreshCw className="size-3.5" /> Probar conexión
          </Button>
        </div>

        {status && <VerificationBanner status={status} />}

        <form onSubmit={submit} className="space-y-4 p-5">
          <label className="block">
            <span className="text-sm font-medium text-slate-700">Phone Number ID</span>
            <input
              required
              readOnly={!editable}
              inputMode="numeric"
              value={value}
              onChange={(e) => setPhoneNumberId(e.target.value)}
              placeholder="Ej.: 1333592066495703"
              className="mt-1.5 h-10 w-full rounded-lg border border-slate-300 px-3 font-mono text-sm outline-none read-only:bg-slate-50 focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium text-slate-700">
              ID de la cuenta de WhatsApp Business <span className="font-normal text-slate-400">(para plantillas)</span>
            </span>
            <input
              readOnly={!editable}
              inputMode="numeric"
              value={businessAccountId ?? settings.whatsappBusinessAccountId ?? ''}
              onChange={(e) => setBusinessAccountId(e.target.value)}
              placeholder="Ej.: 102290129340398"
              className="mt-1.5 h-10 w-full rounded-lg border border-slate-300 px-3 font-mono text-sm outline-none read-only:bg-slate-50 focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
            />
            <span className="mt-1 block text-xs text-slate-500">
              Sin él no se pueden enviar plantillas, que son la única forma de escribirle a un cliente después de 24 horas sin respuesta.
            </span>
          </label>

          {editable && (
            <label className="block">
              <span className="text-sm font-medium text-slate-700">Token de acceso</span>
              <input
                type="password"
                autoComplete="off"
                value={token}
                onChange={(e) => setToken(e.target.value)}
                placeholder={settings.hasOwnWhatsappToken ? '•••••••• (guardado; escribe uno nuevo para reemplazarlo)' : 'EAAG…'}
                className="mt-1.5 h-10 w-full rounded-lg border border-slate-300 px-3 font-mono text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
              />
              <span className="mt-1 flex items-center gap-1 text-xs text-slate-500">
                <ShieldCheck className="size-3.5" />
                {settings.hasOwnWhatsappToken
                  ? 'Hay un token propio guardado. Nunca se vuelve a mostrar.'
                  : 'Sin token propio: se usa el de la plataforma.'}
              </span>
            </label>
          )}

          {editable && (
            <div className="flex justify-end">
              <Button type="submit" loading={connect.isPending} disabled={!value.trim()}>
                Verificar y guardar
              </Button>
            </div>
          )}
        </form>
      </section>

      <aside className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <h3 className="text-sm font-semibold">¿Dónde encuentro estos datos?</h3>
        <ol className="mt-3 space-y-4">
          {STEPS.map((step, i) => (
            <li key={step.title} className="flex gap-3">
              <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700">{i + 1}</span>
              <div>
                <p className="text-sm font-medium text-slate-800">{step.title}</p>
                <p className="text-xs leading-relaxed text-slate-500">{step.text}</p>
              </div>
            </li>
          ))}
        </ol>
        <a
          href="https://developers.facebook.com/docs/whatsapp/cloud-api/get-started"
          target="_blank"
          rel="noopener noreferrer"
          className="mt-4 inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:underline"
        >
          Guía oficial de Meta <ExternalLink className="size-3" />
        </a>
      </aside>
    </div>
  )
}

function VerificationBanner({ status }: { status: WhatsAppStatus }) {
  const v = status.verification
  return v.ok ? (
    <div className="flex items-start gap-3 border-b border-emerald-100 bg-emerald-50 px-5 py-3 text-sm text-emerald-800">
      <CheckCircle2 className="mt-0.5 size-4 shrink-0" />
      <div>
        <p className="font-medium">Conexión correcta con Meta</p>
        <p className="text-xs">
          {[v.verifiedName, v.displayPhoneNumber, v.qualityRating && `calidad ${v.qualityRating.toLowerCase()}`].filter(Boolean).join(' · ')}
        </p>
      </div>
    </div>
  ) : (
    <div className="flex items-start gap-3 border-b border-red-100 bg-red-50 px-5 py-3 text-sm text-red-800">
      <TriangleAlert className="mt-0.5 size-4 shrink-0" />
      <div>
        <p className="font-medium">No se pudo verificar el número</p>
        <p className="text-xs">{v.error}</p>
      </div>
    </div>
  )
}
