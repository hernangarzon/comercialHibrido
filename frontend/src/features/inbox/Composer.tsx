import clsx from 'clsx'
import { Plus, SendHorizontal, Trash2, Zap } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Button, IconButton } from '../../components/ui'
import { DEFAULT_QUICK_REPLIES, useStoredState, type QuickReply } from '../../lib/storage'

interface Props {
  onSend: (text: string) => void
  agentName: string
  companyId: string
}

/**
 * Caja de respuesta del asesor. Enter envía, Shift+Enter hace salto de línea
 * y escribir "/" abre las respuestas rápidas.
 */
export function Composer({ onSend, agentName, companyId }: Props) {
  const [text, setText] = useState('')
  const [highlight, setHighlight] = useState(0)
  const [managing, setManaging] = useState(false)
  const [replies, setReplies] = useStoredState<QuickReply[]>(`ch.replies.${companyId}`, DEFAULT_QUICK_REPLIES)
  const textarea = useRef<HTMLTextAreaElement>(null)

  // Autoajuste de altura hasta ~6 líneas.
  useEffect(() => {
    const el = textarea.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`
  }, [text])

  const query = text.startsWith('/') && !text.includes('\n') ? text.slice(1).toLowerCase() : null
  const matches = useMemo(
    () =>
      query === null
        ? []
        : replies.filter((r) => r.shortcut.toLowerCase().includes(query) || r.text.toLowerCase().includes(query)).slice(0, 6),
    [query, replies],
  )

  function applyReply(reply: QuickReply) {
    setText(reply.text.replaceAll('{nombre}', agentName.split(' ')[0] ?? agentName))
    setHighlight(0)
    textarea.current?.focus()
  }

  function submit() {
    const value = text.trim()
    if (!value) return
    onSend(value)
    setText('')
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (matches.length > 0) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault()
        setHighlight((h) => (h + (e.key === 'ArrowDown' ? 1 : -1) + matches.length) % matches.length)
        return
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        applyReply(matches[Math.min(highlight, matches.length - 1)]!)
        return
      }
      if (e.key === 'Escape') {
        setText('')
        return
      }
    }
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="relative border-t border-slate-200 bg-white p-3">
      {matches.length > 0 && (
        <div className="absolute right-3 bottom-full left-3 mb-2 overflow-hidden rounded-xl bg-white shadow-lg ring-1 ring-slate-200">
          <p className="border-b border-slate-100 px-3 py-1.5 text-[11px] font-medium text-slate-500">Respuestas rápidas · ↑↓ para elegir, Enter para usar</p>
          <ul>
            {matches.map((r, i) => (
              <li key={r.id}>
                <button
                  onMouseDown={(e) => {
                    e.preventDefault()
                    applyReply(r)
                  }}
                  onMouseEnter={() => setHighlight(i)}
                  className={clsx('flex w-full gap-2 px-3 py-2 text-left text-sm', i === highlight && 'bg-brand-50')}
                >
                  <span className="shrink-0 font-mono text-xs text-brand-600">/{r.shortcut}</span>
                  <span className="truncate text-slate-600">{r.text}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="flex items-end gap-2">
        <IconButton label="Respuestas rápidas" onClick={() => setManaging(true)} className="mb-0.5">
          <Zap className="size-4" />
        </IconButton>
        <textarea
          ref={textarea}
          rows={1}
          autoFocus
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            setHighlight(0)
          }}
          onKeyDown={onKeyDown}
          placeholder="Escribe un mensaje…  ( / para respuestas rápidas)"
          className="scrollbar-thin max-h-40 min-h-10 flex-1 resize-none rounded-xl bg-slate-100 px-3.5 py-2.5 text-sm outline-none placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:ring-brand-200"
        />
        <Button onClick={submit} disabled={!text.trim()} aria-label="Enviar" className="mb-0.5 size-10 px-0">
          <SendHorizontal className="size-4" />
        </Button>
      </div>

      {managing && <QuickRepliesDialog replies={replies} onChange={setReplies} onClose={() => setManaging(false)} />}
    </div>
  )
}

function QuickRepliesDialog({
  replies,
  onChange,
  onClose,
}: {
  replies: QuickReply[]
  onChange: (replies: QuickReply[]) => void
  onClose: () => void
}) {
  const [shortcut, setShortcut] = useState('')
  const [text, setText] = useState('')

  function add() {
    const key = shortcut.trim().replace(/^\//, '').replace(/\s+/g, '-').toLowerCase()
    if (!key || !text.trim()) return
    onChange([...replies.filter((r) => r.shortcut !== key), { id: crypto.randomUUID(), shortcut: key, text: text.trim() }])
    setShortcut('')
    setText('')
  }

  return (
    <div role="dialog" aria-modal className="fixed inset-0 z-40 flex items-end justify-center bg-slate-950/40 p-4 sm:items-center" onClick={onClose}>
      <div className="w-full max-w-lg rounded-2xl bg-white shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="border-b border-slate-100 px-5 py-4">
          <h3 className="font-semibold">Respuestas rápidas</h3>
          <p className="text-sm text-slate-500">
            Escribe <span className="font-mono text-brand-600">/atajo</span> en el chat para usarlas. <span className="font-mono">{'{nombre}'}</span> se
            reemplaza por tu nombre.
          </p>
        </div>
        <ul className="scrollbar-thin max-h-72 divide-y divide-slate-100 overflow-y-auto">
          {replies.map((r) => (
            <li key={r.id} className="flex items-start gap-3 px-5 py-3">
              <span className="shrink-0 font-mono text-xs text-brand-600">/{r.shortcut}</span>
              <p className="flex-1 text-sm text-slate-600">{r.text}</p>
              <IconButton label="Eliminar" className="size-7" onClick={() => onChange(replies.filter((x) => x.id !== r.id))}>
                <Trash2 className="size-3.5" />
              </IconButton>
            </li>
          ))}
        </ul>
        <div className="space-y-2 border-t border-slate-100 bg-slate-50 px-5 py-4">
          <div className="flex gap-2">
            <input
              value={shortcut}
              onChange={(e) => setShortcut(e.target.value)}
              placeholder="atajo"
              className="h-9 w-32 rounded-lg border border-slate-300 bg-white px-2.5 font-mono text-sm outline-none focus:border-brand-500"
            />
            <input
              value={text}
              onChange={(e) => setText(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && add()}
              placeholder="Texto de la respuesta"
              className="h-9 flex-1 rounded-lg border border-slate-300 bg-white px-2.5 text-sm outline-none focus:border-brand-500"
            />
            <Button size="sm" className="h-9" onClick={add} aria-label="Agregar">
              <Plus className="size-4" />
            </Button>
          </div>
          <div className="flex justify-end">
            <Button variant="secondary" size="sm" onClick={onClose}>
              Listo
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
