import clsx from 'clsx'
import { CalendarDays, Check, Copy, ExternalLink, Sparkles, UserRound, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Avatar, IconButton, StatusBadge } from '../../components/ui'
import type { Conversation } from '../../lib/api'
import { displayName, scoreLevel, whatsappLink } from '../../lib/format'

export function CustomerPanel({ conversation, currentUserId, onClose }: { conversation: Conversation; currentUserId: string; onClose: () => void }) {
  const name = displayName(conversation.customerName, conversation.customerPhone)
  const level = scoreLevel(conversation.leadScore)
  const [copied, setCopied] = useState(false)

  async function copyPhone() {
    try {
      await navigator.clipboard.writeText(conversation.customerPhone)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // Portapapeles no disponible.
    }
  }

  const assigned =
    conversation.assignedSalespersonId == null ? 'Sin asignar' : conversation.assignedSalespersonId === currentUserId ? 'Tú' : 'Otro asesor'

  return (
    <div className="flex h-full flex-col bg-white">
      <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <h3 className="text-sm font-semibold">Detalles del cliente</h3>
        <IconButton label="Cerrar detalles" onClick={onClose}>
          <X className="size-4" />
        </IconButton>
      </div>

      <div className="scrollbar-thin flex-1 space-y-6 overflow-y-auto p-5">
        <div className="flex flex-col items-center text-center">
          <Avatar name={name} seed={conversation.customerPhone} size="lg" />
          <p className="mt-3 font-semibold text-slate-900">{name}</p>
          <div className="mt-1 flex items-center gap-1 text-sm text-slate-500">
            {conversation.customerPhone}
            <IconButton label={copied ? 'Copiado' : 'Copiar teléfono'} className="size-7" onClick={copyPhone}>
              {copied ? <Check className="size-3.5 text-emerald-600" /> : <Copy className="size-3.5" />}
            </IconButton>
          </div>
          <a
            href={whatsappLink(conversation.customerPhone)}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:underline"
          >
            Abrir en WhatsApp <ExternalLink className="size-3" />
          </a>
        </div>

        <section>
          <div className="flex items-baseline justify-between">
            <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">Intención de compra</p>
            <p className="text-sm font-semibold tabular-nums">{conversation.leadScore}/100</p>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
            <div
              className={clsx(
                'h-full rounded-full transition-all',
                conversation.leadScore >= 70 ? 'bg-orange-500' : conversation.leadScore >= 40 ? 'bg-amber-400' : 'bg-slate-400',
              )}
              style={{ width: `${Math.min(100, Math.max(0, conversation.leadScore))}%` }}
            />
          </div>
          <p className="mt-1.5 text-xs text-slate-500">
            Lead <span className="font-medium text-slate-700">{level.label.toLowerCase()}</span> según el análisis del bot.
          </p>
        </section>

        {conversation.summary && (
          <section className="rounded-xl bg-brand-50 p-3.5 ring-1 ring-brand-100">
            <p className="flex items-center gap-1.5 text-xs font-semibold text-brand-700">
              <Sparkles className="size-3.5" /> Resumen del bot
            </p>
            <p className="mt-1.5 text-sm leading-relaxed text-slate-700">{conversation.summary}</p>
          </section>
        )}

        <dl className="space-y-3 text-sm">
          <Row label="Estado">
            <StatusBadge status={conversation.status} />
          </Row>
          <Row label="Asesor" icon={<UserRound className="size-3.5" />}>
            {assigned}
          </Row>
          <Row label="Cliente desde" icon={<CalendarDays className="size-3.5" />}>
            {new Date(conversation.createdAt).toLocaleDateString('es', { day: 'numeric', month: 'short', year: 'numeric' })}
          </Row>
        </dl>
      </div>
    </div>
  )
}

function Row({ label, icon, children }: { label: string; icon?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="flex items-center gap-1.5 text-slate-500">
        {icon}
        {label}
      </dt>
      <dd className="font-medium text-slate-800">{children}</dd>
    </div>
  )
}
