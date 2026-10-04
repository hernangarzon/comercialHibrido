import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Building2, Mail, Megaphone, MessageCircle, Phone } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { CredentialsDialog } from '../../components/dialogs'
import { Button, EmptyState, Skeleton } from '../../components/ui'
import { api, type LeadStatus, type NewClient, type SalesLead } from '../../lib/api'
import { timeAgo, whatsappLink } from '../../lib/format'
import { NewClientDialog } from './ClientsTab'

const STATUS: Record<LeadStatus, { label: string; className: string }> = {
  NUEVA: { label: 'Nueva', className: 'bg-orange-50 text-orange-700 ring-orange-200' },
  CONTACTADA: { label: 'Contactada', className: 'bg-sky-50 text-sky-700 ring-sky-200' },
  CONVERTIDA: { label: 'Cliente', className: 'bg-emerald-50 text-emerald-700 ring-emerald-200' },
  DESCARTADA: { label: 'Descartada', className: 'bg-slate-100 text-slate-600 ring-slate-200' },
}

const FILTERS: (LeadStatus | 'TODAS')[] = ['NUEVA', 'CONTACTADA', 'CONVERTIDA', 'DESCARTADA', 'TODAS']

/** Solicitudes de demo que llegan desde la landing. Solo para los dueños de la plataforma. */
export function LeadsTab() {
  const queryClient = useQueryClient()
  const { data: leads, isLoading } = useQuery({ queryKey: ['platform-leads'], queryFn: api.platformLeads, refetchInterval: 60_000 })
  const [filter, setFilter] = useState<LeadStatus | 'TODAS'>('NUEVA')
  const [converting, setConverting] = useState<SalesLead | null>(null)
  const [created, setCreated] = useState<NewClient | null>(null)

  const update = useMutation({
    mutationFn: ({ id, status }: { id: string; status: LeadStatus }) => api.updatePlatformLead(id, status),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['platform-leads'] }),
    onError: (err) => toast.error(err.message),
  })

  const visible = (leads ?? []).filter((l) => filter === 'TODAS' || l.status === filter)
  const count = (s: LeadStatus | 'TODAS') => (leads ?? []).filter((l) => s === 'TODAS' || l.status === s).length

  return (
    <div>
      <div className="mb-4 flex flex-wrap gap-1.5">
        {FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={clsx(
              'rounded-full px-3 py-1 text-sm font-medium transition',
              filter === f ? 'bg-slate-900 text-white' : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50',
            )}
          >
            {f === 'TODAS' ? 'Todas' : STATUS[f].label} <span className="opacity-60 tabular-nums">{count(f)}</span>
          </button>
        ))}
      </div>

      {isLoading ? (
        <Skeleton className="h-64 rounded-xl" />
      ) : visible.length === 0 ? (
        <div className="h-72 rounded-xl bg-white ring-1 ring-slate-200">
          <EmptyState icon={<Megaphone className="size-5" />} title="Sin solicitudes aquí" description="Cuando alguien llene el formulario de la landing, aparecerá en esta lista." />
        </div>
      ) : (
        <ul className="space-y-3">
          {visible.map((lead) => (
            <LeadCard
              key={lead.id}
              lead={lead}
              busy={update.isPending}
              onStatus={(status) => update.mutate({ id: lead.id, status })}
              onConvert={() => setConverting(lead)}
            />
          ))}
        </ul>
      )}

      {converting && (
        <NewClientDialog
          lead={converting}
          onClose={() => setConverting(null)}
          onCreated={(result) => {
            setConverting(null)
            setCreated(result)
            void queryClient.invalidateQueries({ queryKey: ['platform-leads'] })
            void queryClient.invalidateQueries({ queryKey: ['platform-clients'] })
          }}
        />
      )}
      {created && (
        <CredentialsDialog
          title="Cliente creado"
          intro={`${created.company.name} ya tiene su panel. Su administrador debe conectar el número de WhatsApp en Ajustes.`}
          invitation={created.invitation}
          onClose={() => setCreated(null)}
        />
      )}
    </div>
  )
}

function LeadCard({ lead, onStatus, onConvert, busy }: { lead: SalesLead; onStatus: (s: LeadStatus) => void; onConvert: () => void; busy: boolean }) {
  const converted = lead.status === 'CONVERTIDA'
  return (
    <li className="rounded-xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold text-slate-900">{lead.companyName}</p>
          <p className="text-sm text-slate-600">
            {lead.name} · <span className="text-slate-400">{timeAgo(lead.createdAt)}</span>
          </p>
        </div>
        <span className={clsx('rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', STATUS[lead.status].className)}>
          {STATUS[lead.status].label}
        </span>
      </div>

      {lead.message && <p className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm whitespace-pre-wrap text-slate-700">{lead.message}</p>}

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        <a href={whatsappLink(lead.phone)} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 font-medium text-emerald-700 hover:underline">
          <MessageCircle className="size-4" /> WhatsApp
        </a>
        <span className="inline-flex items-center gap-1.5 text-slate-600">
          <Phone className="size-4 text-slate-400" /> {lead.phone}
        </span>
        <span className="inline-flex items-center gap-1.5 text-slate-600">
          <Mail className="size-4 text-slate-400" /> {lead.email}
        </span>
        {!converted && (
          <div className="ml-auto flex flex-wrap gap-1">
            {(['NUEVA', 'CONTACTADA', 'DESCARTADA'] as LeadStatus[])
              .filter((s) => s !== lead.status)
              .map((s) => (
                <button
                  key={s}
                  disabled={busy}
                  onClick={() => onStatus(s)}
                  className="rounded-lg px-2.5 py-1 text-xs font-medium text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50 disabled:opacity-50"
                >
                  Marcar {STATUS[s].label.toLowerCase()}
                </button>
              ))}
            <Button size="sm" onClick={onConvert}>
              <Building2 className="size-3.5" /> Convertir en cliente
            </Button>
          </div>
        )}
      </div>
    </li>
  )
}
