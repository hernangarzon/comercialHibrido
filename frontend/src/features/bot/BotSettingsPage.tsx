import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Lock, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button, EmptyState, Skeleton } from '../../components/ui'
import { api, type CompanySettings } from '../../lib/api'
import { useSession } from '../../lib/auth'
import { BotTester } from './BotTester'
import { CatalogCard } from './CatalogCard'

const KB_MAX = 20_000
const PROMPT_MAX = 4_000

export function BotSettingsPage() {
  const session = useSession()
  const isAdmin = session.role.toUpperCase() === 'ADMIN'
  const { data, isLoading, isError, error } = useQuery({ queryKey: ['company'], queryFn: api.companySettings })

  return (
    <div className="scrollbar-thin h-full overflow-y-auto">
      <div className="mx-auto max-w-7xl px-4 py-6 sm:px-6">
        <div className="mb-6">
          <h1 className="text-xl font-semibold tracking-tight">Configuración del bot</h1>
          <p className="text-sm text-slate-500">
            Lo que el bot sabe y cómo vende. Los cambios aplican en la siguiente respuesta; pruébalos a la derecha antes de que lleguen a tus clientes.
          </p>
          {!isAdmin && (
            <p className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-3 py-1.5 text-xs text-slate-600">
              <Lock className="size-3.5" /> Solo lectura: un administrador puede editar esta configuración.
            </p>
          )}
        </div>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_400px]">
          <div className="min-w-0 space-y-6">
            {isError ? (
              <EmptyState icon={<TriangleAlert className="size-5" />} title="No se pudo cargar la configuración" description={error.message} />
            ) : isLoading || !data ? (
              <Skeleton className="h-96 rounded-xl" />
            ) : (
              <KnowledgeCard key={data.updatedAt} settings={data} editable={isAdmin} />
            )}
            <CatalogCard editable={isAdmin} />
          </div>
          <div className="xl:sticky xl:top-6 xl:self-start">
            <BotTester />
          </div>
        </div>
      </div>
    </div>
  )
}

function KnowledgeCard({ settings, editable }: { settings: CompanySettings; editable: boolean }) {
  const queryClient = useQueryClient()
  const [knowledge, setKnowledge] = useState(settings.knowledgeBase ?? '')
  const [prompt, setPrompt] = useState(settings.customPrompt ?? '')
  const dirty = knowledge !== (settings.knowledgeBase ?? '') || prompt !== (settings.customPrompt ?? '')

  const save = useMutation({
    mutationFn: () => api.saveKnowledge(knowledge, prompt),
    onSuccess: (updated) => {
      queryClient.setQueryData(['company'], updated)
      toast.success('Guardado. El bot ya usa esta información.')
    },
    onError: (err) => toast.error(err.message),
  })

  return (
    <section className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="border-b border-slate-100 px-5 py-4">
        <h2 className="font-semibold">Conocimiento y estilo</h2>
        <p className="text-xs text-slate-500">
          El bot solo responde con esta información y el catálogo. Si algo no está aquí, escala la conversación en lugar de inventar.
        </p>
      </div>

      <div className="space-y-5 p-5">
        <Field
          label="Directrices de atención"
          help="Tono, qué priorizar y qué evitar. Ej.: «Trata de usted, ofrece siempre la recogida del modelo, no prometas fechas exactas»."
          value={prompt}
          max={PROMPT_MAX}
          rows={4}
          editable={editable}
          onChange={setPrompt}
        />
        <Field
          label="Base de conocimiento"
          help="Políticas, envíos, medios de pago, horarios, garantías, preguntas frecuentes. Escríbelo como se lo explicarías a un vendedor nuevo."
          value={knowledge}
          max={KB_MAX}
          rows={12}
          editable={editable}
          onChange={setKnowledge}
        />
      </div>

      {editable && (
        <div className="flex items-center justify-end gap-3 rounded-b-xl border-t border-slate-100 bg-slate-50 px-5 py-3">
          {dirty && <span className="text-xs text-amber-700">Cambios sin guardar</span>}
          <Button
            variant="secondary"
            size="sm"
            disabled={!dirty || save.isPending}
            onClick={() => {
              setKnowledge(settings.knowledgeBase ?? '')
              setPrompt(settings.customPrompt ?? '')
            }}
          >
            Descartar
          </Button>
          <Button size="sm" disabled={!dirty} loading={save.isPending} onClick={() => save.mutate()}>
            Guardar cambios
          </Button>
        </div>
      )}
    </section>
  )
}

function Field({
  label,
  help,
  value,
  max,
  rows,
  editable,
  onChange,
}: {
  label: string
  help: string
  value: string
  max: number
  rows: number
  editable: boolean
  onChange: (value: string) => void
}) {
  const over = value.length > max
  return (
    <label className="block">
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-sm font-medium text-slate-800">{label}</span>
        <span className={over ? 'text-xs font-medium text-red-600' : 'text-xs text-slate-400 tabular-nums'}>
          {value.length.toLocaleString('es')} / {max.toLocaleString('es')}
        </span>
      </div>
      <p className="mt-0.5 mb-2 text-xs text-slate-500">{help}</p>
      <textarea
        value={value}
        rows={rows}
        readOnly={!editable}
        onChange={(e) => onChange(e.target.value)}
        className="scrollbar-thin w-full resize-y rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm leading-relaxed shadow-sm outline-none read-only:bg-slate-50 read-only:text-slate-600 focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
      />
    </label>
  )
}
