import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArrowLeft, Bot, Hand, MessagesSquare, PanelRight } from 'lucide-react'
import { Fragment, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Avatar, Button, EmptyState, IconButton, ScoreBadge, Skeleton, StatusBadge } from '../../components/ui'
import { api, type ChatMessage, type Conversation } from '../../lib/api'
import { useSession } from '../../lib/auth'
import { dayLabel, displayName } from '../../lib/format'
import { Composer } from './Composer'
import { MessageBubble } from './MessageBubble'

interface Props {
  conversation: Conversation
  onBack: () => void
  onToggleDetails: () => void
  onSeen: (conversationId: string, at: string | null) => void
  onStatusChange: (status: Conversation['status']) => void
}

export function ChatView({ conversation, onBack, onToggleDetails, onSeen, onStatusChange }: Props) {
  const session = useSession()
  const queryClient = useQueryClient()
  const name = displayName(conversation.customerName, conversation.customerPhone)
  const messagesKey = ['messages', conversation.id]

  const { data: messages, isLoading } = useQuery({
    queryKey: messagesKey,
    queryFn: () => api.messages(conversation.id),
    refetchInterval: 30_000,
  })

  // Marca como leída la conversación abierta cada vez que llegan mensajes nuevos.
  const lastAt = messages?.at(-1)?.createdAt ?? conversation.lastMessageAt
  useEffect(() => onSeen(conversation.id, lastAt), [conversation.id, lastAt, onSeen])

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['conversations'] })
    void queryClient.invalidateQueries({ queryKey: messagesKey })
  }

  const action = useMutation({
    mutationFn: (kind: 'take' | 'release' | 'archive') =>
      kind === 'take' ? api.takeControl(conversation.id) : kind === 'release' ? api.releaseControl(conversation.id) : api.archive(conversation.id),
    onSuccess: (updated, kind) => {
      onStatusChange(updated.status as Conversation['status'])
      refresh()
      toast.success(
        kind === 'take' ? 'Tomaste el control. El bot dejó de responder.' : kind === 'release' ? 'El bot vuelve a atender esta conversación.' : 'Conversación archivada.',
      )
    },
    onError: (err) => toast.error(err.message),
  })

  const send = useMutation({
    mutationFn: (text: string) => api.sendMessage(conversation.id, text),
    // Envío optimista: el mensaje aparece al instante con estado "enviando".
    onMutate: async (text) => {
      await queryClient.cancelQueries({ queryKey: messagesKey })
      const previous = queryClient.getQueryData<ChatMessage[]>(messagesKey)
      const optimistic: ChatMessage = {
        id: `tmp-${Date.now()}`,
        sender: 'COMERCIAL',
        content: text,
        mediaType: 'TEXT',
        mediaId: null,
        mediaFilename: null,
        deliveryStatus: 'PENDING',
        deliveryError: null,
        createdAt: new Date().toISOString(),
      }
      queryClient.setQueryData<ChatMessage[]>(messagesKey, (old) => [...(old ?? []), optimistic])
      return { previous }
    },
    onError: (err, _, context) => {
      queryClient.setQueryData(messagesKey, context?.previous)
      toast.error(`No se envió el mensaje: ${err.message}`)
    },
    onSettled: refresh,
  })

  // ChatView se monta de nuevo por conversación (key), así que este estado arranca limpio.
  const [confirmArchive, setConfirmArchive] = useState(false)

  const canReply = conversation.status === 'HUMANO_CONTROL'
  const busy = action.isPending

  return (
    <div className="flex h-full min-w-0 flex-col">
      <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-3 py-2.5 sm:px-4">
        <IconButton label="Volver" onClick={onBack} className="md:hidden">
          <ArrowLeft className="size-5" />
        </IconButton>
        <Avatar name={name} seed={conversation.customerPhone} />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h2 className="truncate font-semibold text-slate-900">{name}</h2>
            <ScoreBadge score={conversation.leadScore} compact />
          </div>
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span className="truncate">{conversation.customerPhone}</span>
            <StatusBadge status={conversation.status} />
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          {conversation.status !== 'HUMANO_CONTROL' && conversation.status !== 'ARCHIVADO' && (
            <Button size="sm" onClick={() => action.mutate('take')} loading={busy && action.variables === 'take'} disabled={busy}>
              <Hand className="size-3.5" />
              <span className="hidden sm:inline">Tomar control</span>
            </Button>
          )}
          {conversation.status === 'HUMANO_CONTROL' && (
            <Button size="sm" variant="secondary" onClick={() => action.mutate('release')} loading={busy && action.variables === 'release'} disabled={busy}>
              <Bot className="size-3.5" />
              <span className="hidden sm:inline">Devolver al bot</span>
            </Button>
          )}
          {conversation.status !== 'ARCHIVADO' &&
            (confirmArchive ? (
              <Button size="sm" variant="danger" onClick={() => action.mutate('archive')} loading={busy && action.variables === 'archive'} onBlur={() => setConfirmArchive(false)} autoFocus>
                ¿Archivar?
              </Button>
            ) : (
              <IconButton label="Archivar" onClick={() => setConfirmArchive(true)} disabled={busy}>
                <Archive className="size-4" />
              </IconButton>
            ))}
          <IconButton label="Detalles del cliente" onClick={onToggleDetails}>
            <PanelRight className="size-4" />
          </IconButton>
        </div>
      </header>

      <MessageList messages={messages} loading={isLoading} />

      {canReply ? (
        <Composer
          onSend={(text) => send.mutate(text)}
          agentName={session.name}
          companyId={session.companyId}
        />
      ) : (
        <div className="flex flex-col items-center justify-between gap-2 border-t border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 sm:flex-row">
          <p className="flex items-center gap-2">
            <Bot className="size-4 text-brand-600" />
            {conversation.status === 'ARCHIVADO'
              ? 'Conversación archivada. Si el cliente vuelve a escribir, el bot la reabre.'
              : conversation.status === 'ESCALADO_PENDIENTE'
                ? 'Este cliente espera a un asesor. El bot sigue respondiendo hasta que tomes el control.'
                : 'El bot está atendiendo. Toma el control para responder tú.'}
          </p>
          {conversation.status !== 'ARCHIVADO' && (
            <Button size="sm" onClick={() => action.mutate('take')} loading={busy && action.variables === 'take'} disabled={busy}>
              <Hand className="size-3.5" /> Tomar control
            </Button>
          )}
        </div>
      )}
    </div>
  )
}

function MessageList({ messages, loading }: { messages: ChatMessage[] | undefined; loading: boolean }) {
  const container = useRef<HTMLDivElement>(null)
  const stickToBottom = useRef(true)

  // Solo baja automáticamente si el asesor ya estaba al final (no interrumpe si está leyendo arriba).
  useLayoutEffect(() => {
    const el = container.current
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight
  }, [messages])

  return (
    <div
      ref={container}
      onScroll={(e) => {
        const el = e.currentTarget
        stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
      }}
      className="chat-surface scrollbar-thin flex-1 overflow-y-auto px-3 py-4 sm:px-6"
    >
      {loading ? (
        <div className="space-y-4">
          <Skeleton className="h-12 w-2/3" />
          <Skeleton className="ml-auto h-16 w-1/2" />
          <Skeleton className="h-10 w-1/3" />
        </div>
      ) : !messages?.length ? (
        <EmptyState icon={<MessagesSquare className="size-5" />} title="Sin mensajes todavía" />
      ) : (
        <div className="mx-auto max-w-3xl space-y-2">
          {messages.map((m, i) => {
            const prev = messages[i - 1]
            const newDay = !prev || new Date(prev.createdAt).toDateString() !== new Date(m.createdAt).toDateString()
            return (
              <Fragment key={m.id}>
                {newDay && (
                  <div className="sticky top-0 z-10 flex justify-center py-2">
                    <span className="rounded-full bg-white/90 px-3 py-1 text-[11px] font-medium text-slate-500 shadow-sm ring-1 ring-slate-200 backdrop-blur first-letter:uppercase">
                      {dayLabel(m.createdAt)}
                    </span>
                  </div>
                )}
                <MessageBubble message={m} />
              </Fragment>
            )
          })}
        </div>
      )}
    </div>
  )
}
