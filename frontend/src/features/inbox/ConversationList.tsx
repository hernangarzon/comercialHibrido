import clsx from 'clsx'
import { Bot, Clock, Inbox, Search, UserRound } from 'lucide-react'
import { forwardRef } from 'react'
import { Avatar, EmptyState, ScoreBadge, Skeleton } from '../../components/ui'
import type { Conversation, ConversationStatus } from '../../lib/api'
import { displayName, timeAgo, waitingFor } from '../../lib/format'

export type SortMode = 'recent' | 'score'

export const TABS: { status: ConversationStatus; label: string; empty: string }[] = [
  { status: 'ESCALADO_PENDIENTE', label: 'Escalados', empty: 'Ningún cliente espera a un asesor. ¡Todo al día!' },
  { status: 'HUMANO_CONTROL', label: 'Atención', empty: 'No estás atendiendo ninguna conversación ahora.' },
  { status: 'BOT_ACTIVO', label: 'Bot', empty: 'El bot no tiene conversaciones activas.' },
  { status: 'ARCHIVADO', label: 'Archivo', empty: 'Aún no hay conversaciones archivadas.' },
]

interface Props {
  conversations: Conversation[]
  loading: boolean
  selectedId: string | null
  onSelect: (id: string) => void
  tab: ConversationStatus
  onTabChange: (status: ConversationStatus) => void
  counts: Record<ConversationStatus, number>
  search: string
  onSearchChange: (value: string) => void
  sort: SortMode
  onSortChange: (sort: SortMode) => void
  isUnread: (conversation: Conversation) => boolean
  currentUserId: string
}

export const ConversationList = forwardRef<HTMLInputElement, Props>(function ConversationList(
  { conversations, loading, selectedId, onSelect, tab, onTabChange, counts, search, onSearchChange, sort, onSortChange, isUnread, currentUserId },
  searchRef,
) {
  const tabInfo = TABS.find((t) => t.status === tab)!

  return (
    <div className="flex h-full flex-col bg-white">
      <div className="space-y-3 border-b border-slate-200 p-3">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-slate-400" />
          <input
            ref={searchRef}
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Buscar por nombre, teléfono o mensaje"
            className="h-9 w-full rounded-lg bg-slate-100 pr-14 pl-9 text-sm outline-none placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:ring-brand-200"
          />
          <kbd className="pointer-events-none absolute top-1/2 right-2.5 hidden -translate-y-1/2 rounded border border-slate-200 bg-white px-1.5 text-[10px] text-slate-400 sm:block">
            Ctrl K
          </kbd>
        </div>

        <div role="tablist" className="grid grid-cols-4 gap-1 rounded-lg bg-slate-100 p-1">
          {TABS.map(({ status, label }) => {
            const active = status === tab
            const count = counts[status]
            return (
              <button
                key={status}
                role="tab"
                aria-selected={active}
                onClick={() => onTabChange(status)}
                className={clsx(
                  'flex items-center justify-center gap-1 rounded-md px-1 py-1.5 text-xs font-medium transition',
                  active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800',
                )}
              >
                <span className="truncate">{label}</span>
                {count > 0 && status !== 'ARCHIVADO' && (
                  <span
                    className={clsx(
                      'min-w-4 rounded-full px-1 text-[10px] leading-4 font-semibold tabular-nums',
                      status === 'ESCALADO_PENDIENTE' ? 'bg-orange-500 text-white' : 'bg-slate-200 text-slate-600',
                    )}
                  >
                    {count}
                  </span>
                )}
              </button>
            )
          })}
        </div>

        <div className="flex items-center justify-between text-xs text-slate-500">
          <span>
            {conversations.length} {conversations.length === 1 ? 'conversación' : 'conversaciones'}
          </span>
          <label className="flex items-center gap-1.5">
            Ordenar
            <select
              value={sort}
              onChange={(e) => onSortChange(e.target.value as SortMode)}
              className="rounded-md bg-transparent py-0.5 font-medium text-slate-700 outline-none hover:bg-slate-100"
            >
              <option value="recent">Más recientes</option>
              <option value="score">Mayor score</option>
            </select>
          </label>
        </div>
      </div>

      <div className="scrollbar-thin flex-1 overflow-y-auto">
        {loading ? (
          <ListSkeleton />
        ) : conversations.length === 0 ? (
          <EmptyState
            icon={search ? <Search className="size-5" /> : <Inbox className="size-5" />}
            title={search ? 'Sin resultados' : 'Nada por aquí'}
            description={search ? `Ninguna conversación coincide con “${search}”.` : tabInfo.empty}
          />
        ) : (
          <ul className="divide-y divide-slate-100">
            {conversations.map((c) => (
              <ConversationItem
                key={c.id}
                conversation={c}
                selected={c.id === selectedId}
                unread={isUnread(c)}
                mine={c.assignedSalespersonId === currentUserId}
                onSelect={onSelect}
              />
            ))}
          </ul>
        )}
      </div>
    </div>
  )
})

function ConversationItem({
  conversation: c,
  selected,
  unread,
  mine,
  onSelect,
}: {
  conversation: Conversation
  selected: boolean
  unread: boolean
  mine: boolean
  onSelect: (id: string) => void
}) {
  const name = displayName(c.customerName, c.customerPhone)
  const prefix = c.lastMessageSender === 'BOT' ? 'Bot: ' : c.lastMessageSender === 'COMERCIAL' ? 'Asesor: ' : ''

  return (
    <li>
      <button
        onClick={() => onSelect(c.id)}
        className={clsx(
          'relative flex w-full gap-3 px-3 py-3 text-left transition',
          selected ? 'bg-brand-50' : 'hover:bg-slate-50',
        )}
      >
        {selected && <span className="absolute inset-y-2 left-0 w-1 rounded-r bg-brand-600" />}
        <Avatar name={name} seed={c.customerPhone} />
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline justify-between gap-2">
            <span className={clsx('truncate text-sm', unread ? 'font-semibold text-slate-900' : 'font-medium text-slate-800')}>
              {name}
            </span>
            <span className={clsx('shrink-0 text-[11px]', unread ? 'font-semibold text-brand-600' : 'text-slate-400')}>
              {timeAgo(c.lastMessageAt ?? c.updatedAt)}
            </span>
          </div>
          <div className="mt-0.5 flex items-center gap-2">
            <p className={clsx('min-w-0 flex-1 truncate text-xs', unread ? 'text-slate-700' : 'text-slate-500')}>
              {c.lastMessage ? (
                <>
                  {prefix && <span className="text-slate-400">{prefix}</span>}
                  {c.lastMessage}
                </>
              ) : (
                <span className="italic">Sin mensajes</span>
              )}
            </p>
            {unread && <span className="size-2 shrink-0 rounded-full bg-brand-600" aria-label="Sin leer" />}
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            <ScoreBadge score={c.leadScore} compact />
            {c.status === 'ESCALADO_PENDIENTE' && (
              <span className="inline-flex items-center gap-1 text-[11px] font-medium text-orange-600">
                <Clock className="size-3" />
                Esperando {waitingFor(c.updatedAt)}
              </span>
            )}
            {c.status === 'HUMANO_CONTROL' && (
              <span className="inline-flex items-center gap-1 text-[11px] text-emerald-700">
                <UserRound className="size-3" />
                {mine ? 'Atiendes tú' : 'Otro asesor'}
              </span>
            )}
            {c.status === 'BOT_ACTIVO' && (
              <span className="inline-flex items-center gap-1 text-[11px] text-slate-400">
                <Bot className="size-3" />
                Bot
              </span>
            )}
          </div>
        </div>
      </button>
    </li>
  )
}

function ListSkeleton() {
  return (
    <div className="space-y-1 p-3">
      {Array.from({ length: 7 }, (_, i) => (
        <div key={i} className="flex gap-3 py-2">
          <Skeleton className="size-10 rounded-full" />
          <div className="flex-1 space-y-2 py-1">
            <Skeleton className="h-3 w-2/3" />
            <Skeleton className="h-3 w-full" />
          </div>
        </div>
      ))}
    </div>
  )
}
