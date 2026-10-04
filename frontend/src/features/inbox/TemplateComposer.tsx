import { useMutation, useQuery } from '@tanstack/react-query'
import { Clock, FileText, SendHorizontal, Settings } from 'lucide-react'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '../../components/ui'
import { api, ApiError, type WhatsAppTemplate } from '../../lib/api'
import { navigate } from '../../lib/router'

interface Props {
  conversationId: string
  closedSince: string | null
  onSent: () => void
}

/**
 * Con la ventana de 24 h cerrada, WhatsApp solo permite plantillas aprobadas por Meta.
 * El asesor elige una, completa sus datos y ve el mensaje final antes de enviarlo.
 */
export function TemplateComposer({ conversationId, closedSince, onSent }: Props) {
  const { data: templates, isLoading, error } = useQuery({ queryKey: ['templates'], queryFn: api.templates, staleTime: 5 * 60_000 })
  const [selectedKey, setSelectedKey] = useState('')
  const [params, setParams] = useState<string[]>([])

  const selected = templates?.find((t) => key(t) === selectedKey) ?? null
  const preview = useMemo(() => (selected ? render(selected, params) : ''), [selected, params])
  const complete = !!selected && params.length === selected.paramCount && params.every((p) => p.trim())

  const send = useMutation({
    mutationFn: () => api.sendTemplate(conversationId, selected!.name, selected!.language, params.map((p) => p.trim())),
    onSuccess: () => {
      toast.success('Plantilla enviada. Cuando el cliente responda podrás escribir libremente.')
      setSelectedKey('')
      setParams([])
      onSent()
    },
    onError: (err) => toast.error(err.message),
  })

  const missingAccount = error instanceof ApiError && error.status === 409

  return (
    <div className="border-t border-slate-200 bg-white p-3">
      <p className="mb-2 flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800 ring-1 ring-amber-200">
        <Clock className="mt-0.5 size-3.5 shrink-0" />
        <span>
          Pasaron más de 24 horas desde el último mensaje del cliente{closedSince ? ` (la ventana cerró ${closedSince})` : ''}. WhatsApp
          solo permite escribirle con una <strong>plantilla aprobada</strong>; cuando responda, vuelves a escribir libremente.
        </span>
      </p>

      {isLoading ? (
        <p className="px-1 py-2 text-xs text-slate-500">Cargando plantillas de Meta…</p>
      ) : error ? (
        <div className="flex flex-wrap items-center justify-between gap-2 px-1 py-1 text-xs text-red-700">
          <span>{error.message}</span>
          {missingAccount && (
            <Button size="sm" variant="secondary" onClick={() => navigate('ajustes', { tab: 'whatsapp' })}>
              <Settings className="size-3.5" /> Configurar
            </Button>
          )}
        </div>
      ) : !templates?.length ? (
        <p className="px-1 py-2 text-xs text-slate-500">
          No hay plantillas aprobadas en tu cuenta de WhatsApp Business. Créalas en Meta (Administrador de WhatsApp → Plantillas de mensajes).
        </p>
      ) : (
        <div className="space-y-2">
          <div className="flex flex-wrap gap-2">
            <label className="flex min-w-0 flex-1 items-center gap-2">
              <FileText className="size-4 shrink-0 text-slate-400" />
              <select
                aria-label="Plantilla"
                value={selectedKey}
                onChange={(e) => {
                  setSelectedKey(e.target.value)
                  const t = templates.find((x) => key(x) === e.target.value)
                  setParams(Array.from({ length: t?.paramCount ?? 0 }, () => ''))
                }}
                className="h-9 min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-2 text-sm outline-none focus:border-brand-500"
              >
                <option value="">Elige una plantilla…</option>
                {templates.map((t) => (
                  <option key={key(t)} value={key(t)}>
                    {t.name} · {t.language}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {selected && (
            <>
              {selected.paramCount > 0 && (
                <div className="grid gap-2 sm:grid-cols-2">
                  {params.map((value, i) => (
                    <input
                      key={i}
                      value={value}
                      maxLength={1024}
                      onChange={(e) => setParams((p) => p.map((x, j) => (j === i ? e.target.value : x)))}
                      placeholder={`Dato {{${i + 1}}}`}
                      aria-label={`Dato ${i + 1} de la plantilla`}
                      className="h-9 rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
                    />
                  ))}
                </div>
              )}
              <div className="flex items-end gap-2">
                <p className="min-w-0 flex-1 rounded-xl bg-slate-50 px-3 py-2 text-sm whitespace-pre-wrap text-slate-700 ring-1 ring-slate-200">
                  {preview}
                </p>
                <Button onClick={() => send.mutate()} disabled={!complete} loading={send.isPending} aria-label="Enviar plantilla" className="size-10 px-0">
                  {!send.isPending && <SendHorizontal className="size-4" />}
                </Button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}

function key(t: WhatsAppTemplate) {
  return `${t.name}|${t.language}`
}

function render(t: WhatsAppTemplate, params: string[]) {
  return t.body.replace(/\{\{(\d+)}}/g, (match, n) => params[Number(n) - 1]?.trim() || match)
}
