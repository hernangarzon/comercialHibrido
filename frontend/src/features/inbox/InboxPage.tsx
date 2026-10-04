import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import { MessagesSquare } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { EmptyState } from '../../components/ui'
import { api, type Conversation, type ConversationStatus } from '../../lib/api'
import { useSession } from '../../lib/auth'
import { navigate } from '../../lib/router'
import { useLastSeen, useMediaQuery, useStoredState } from '../../lib/storage'
import { ChatView } from './ChatView'
import { ConversationList, type SortMode } from './ConversationList'
import { CustomerPanel } from './CustomerPanel'

/** La conversación abierta vive en la URL (#/bandeja?c=id) para poder enlazarla desde alertas y el dashboard. */
export function InboxPage({ selectedId }: { selectedId: string | null }) {
  const session = useSession()

  const { data: conversations = [], isLoading } = useQuery({
    queryKey: ['conversations'],
    queryFn: api.conversations,
    // Respaldo por si el WebSocket se cae; en condiciones normales los eventos refrescan al instante.
    refetchInterval: 30_000,
  })

  const [tab, setTab] = useStoredState<ConversationStatus>('ch.tab', 'ESCALADO_PENDIENTE')
  const [sort, setSort] = useStoredState<SortMode>('ch.sort', 'recent')
  // En pantallas grandes el panel de detalles es una columna (preferencia guardada);
  // en el resto flota sobre el chat y solo se abre a pedido.
  const isWide = useMediaQuery('(min-width: 1280px)')
  const [detailsPinned, setDetailsPinned] = useStoredState('ch.details', true)
  const [detailsOverlay, setDetailsOverlay] = useState(false)
  const showDetails = isWide ? detailsPinned : detailsOverlay
  const setShowDetails = (next: boolean | ((v: boolean) => boolean)) => {
    const value = typeof next === 'function' ? next(showDetails) : next
    if (isWide) setDetailsPinned(value)
    else setDetailsOverlay(value)
  }
  const [search, setSearch] = useState('')
  const searchRef = useRef<HTMLInputElement>(null)
  const { seen, markSeen } = useLastSeen(session.userId)

  // Re-render cada minuto para que "hace 5 min" y "esperando 12 min" se mantengan al día.
  const [, setTick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 60_000)
    return () => clearInterval(id)
  }, [])

  const selected = conversations.find((c) => c.id === selectedId) ?? null

  const counts = useMemo(() => {
    const result = { ESCALADO_PENDIENTE: 0, HUMANO_CONTROL: 0, BOT_ACTIVO: 0, ARCHIVADO: 0 } as Record<ConversationStatus, number>
    for (const c of conversations) result[c.status]++
    return result
  }, [conversations])

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase()
    const list = conversations.filter((c) =>
      q
        ? [c.customerName, c.customerPhone, c.lastMessage].some((v) => v?.toLowerCase().includes(q))
        : c.status === tab,
    )
    return list.sort((a, b) =>
      sort === 'score' && b.leadScore !== a.leadScore
        ? b.leadScore - a.leadScore
        : (b.lastMessageAt ?? b.updatedAt).localeCompare(a.lastMessageAt ?? a.updatedAt),
    )
  }, [conversations, search, tab, sort])

  const isUnread = useCallback(
    (c: Conversation) => c.id !== selectedId && c.lastMessageSender === 'CLIENTE' && !!c.lastMessageAt && (!seen[c.id] || c.lastMessageAt > seen[c.id]!),
    [seen, selectedId],
  )

  const open = useCallback((id: string) => navigate('bandeja', { c: id }), [])
  const close = useCallback(() => navigate('bandeja'), [])

  // Al abrir una conversación (también desde una alerta o el dashboard) la lista
  // salta a la pestaña de su estado, para que siempre se vea seleccionada.
  const selectedStatus = selected?.status
  useEffect(() => {
    if (selectedStatus && !search) setTab(selectedStatus)
  }, [selectedId, selectedStatus, search, setTab])

  // Ctrl/Cmd + K enfoca la búsqueda.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        searchRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="flex h-full flex-col">
      <div className="flex min-h-0 flex-1">
        <aside className={clsx('w-full border-r border-slate-200 md:block md:w-[340px] lg:w-[380px]', selected ? 'hidden' : 'block')}>
          <ConversationList
            ref={searchRef}
            conversations={visible}
            loading={isLoading}
            selectedId={selectedId}
            onSelect={open}
            tab={tab}
            onTabChange={(status) => {
              setTab(status)
              setSearch('')
            }}
            counts={counts}
            search={search}
            onSearchChange={setSearch}
            sort={sort}
            onSortChange={setSort}
            isUnread={isUnread}
            currentUserId={session.userId}
          />
        </aside>

        <main className={clsx('min-w-0 flex-1', selected ? 'block' : 'hidden md:block')}>
          {selected ? (
            <ChatView
              key={selected.id}
              conversation={selected}
              onBack={() => {
                close()
                setDetailsOverlay(false)
              }}
              onStatusChange={(status) => !search && setTab(status)}
              onToggleDetails={() => setShowDetails((v) => !v)}
              onSeen={markSeen}
            />
          ) : (
            <div className="chat-surface h-full">
              <EmptyState
                icon={<MessagesSquare className="size-5" />}
                title="Selecciona una conversación"
                description={
                  counts.ESCALADO_PENDIENTE > 0
                    ? `Tienes ${counts.ESCALADO_PENDIENTE} ${counts.ESCALADO_PENDIENTE === 1 ? 'cliente esperando' : 'clientes esperando'} a un asesor.`
                    : 'El bot está atendiendo. Te avisamos cuando un cliente necesite a un asesor.'
                }
              />
            </div>
          )}
        </main>

        {selected && showDetails && (
          <>
            {/* En pantallas medianas el panel flota sobre el chat; en grandes es una columna fija. */}
            <div className="fixed inset-0 z-30 bg-slate-950/30 xl:hidden" onClick={() => setShowDetails(false)} />
            <aside className="fixed inset-y-0 right-0 z-40 w-[320px] max-w-full border-l border-slate-200 shadow-xl xl:static xl:z-auto xl:shadow-none">
              <CustomerPanel conversation={selected} currentUserId={session.userId} onClose={() => setShowDetails(false)} />
            </aside>
          </>
        )}
      </div>
    </div>
  )
}
