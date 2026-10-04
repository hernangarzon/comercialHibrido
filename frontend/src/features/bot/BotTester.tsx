import { useMutation } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bot, FlaskConical, RotateCcw, SendHorizontal, TriangleAlert } from 'lucide-react'
import { useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import { Button, IconButton, ScoreBadge } from '../../components/ui'
import { api, type BotAnswer, type PreviewMessage } from '../../lib/api'

interface Turn extends PreviewMessage {
  meta?: Pick<BotAnswer, 'leadScore' | 'requiereEscalamiento'>
}

const SUGGESTIONS = ['¿Cuánto cuesta?', 'Necesito 10 unidades, ¿hay descuento?', '¿Cuánto tarda el envío?', 'Quiero hablar con un asesor']

/**
 * Chat de prueba con la configuración guardada: muestra la respuesta, el lead score
 * y si el bot escalaría, sin crear conversaciones ni enviar nada por WhatsApp.
 */
export function BotTester() {
  const [turns, setTurns] = useState<Turn[]>([])
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const scroller = useRef<HTMLDivElement>(null)

  const ask = useMutation({
    mutationFn: (history: Turn[]) => api.previewBot(history.map(({ role, content }) => ({ role, content }))),
    onSuccess: (answer) =>
      setTurns((t) => [...t, { role: 'assistant', content: answer.respuesta, meta: { leadScore: answer.leadScore, requiereEscalamiento: answer.requiereEscalamiento } }]),
    onError: (err) => setError(err.message),
  })

  useLayoutEffect(() => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight })
  }, [turns, ask.isPending])

  function send(content: string) {
    const value = content.trim()
    if (!value || ask.isPending) return
    const history: Turn[] = [...turns, { role: 'user', content: value }]
    setTurns(history)
    setText('')
    setError(null)
    ask.mutate(history)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    send(text)
  }

  return (
    <section className="flex h-[640px] max-h-[calc(100vh-7rem)] flex-col overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
        <div>
          <h2 className="flex items-center gap-1.5 font-semibold">
            <FlaskConical className="size-4 text-brand-600" /> Probar el bot
          </h2>
          <p className="text-xs text-slate-500">Escribe como un cliente. No se envía nada por WhatsApp.</p>
        </div>
        <IconButton label="Reiniciar prueba" onClick={() => { setTurns([]); setError(null) }} disabled={!turns.length}>
          <RotateCcw className="size-4" />
        </IconButton>
      </div>

      <div ref={scroller} className="chat-surface scrollbar-thin flex-1 space-y-2 overflow-y-auto p-4">
        {turns.length === 0 && (
          <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
            <div className="flex size-11 items-center justify-center rounded-2xl bg-brand-600 text-white">
              <Bot className="size-5" />
            </div>
            <p className="max-w-60 text-sm text-slate-600">Prueba preguntas reales de tus clientes y revisa si la respuesta es la que esperas.</p>
            <div className="flex flex-wrap justify-center gap-1.5">
              {SUGGESTIONS.map((s) => (
                <button key={s} onClick={() => send(s)} className="rounded-full bg-white px-3 py-1 text-xs text-slate-700 ring-1 ring-slate-200 hover:ring-brand-300">
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}

        {turns.map((t, i) => (
          <div key={i} className={clsx('flex flex-col', t.role === 'user' ? 'items-end' : 'items-start')}>
            <div
              className={clsx(
                'max-w-[85%] rounded-2xl px-3.5 py-2 text-sm whitespace-pre-wrap shadow-sm',
                t.role === 'user' ? 'rounded-br-md bg-emerald-600 text-white' : 'rounded-bl-md bg-white text-slate-800 ring-1 ring-slate-200',
              )}
            >
              {t.content}
            </div>
            {t.meta && (
              <div className="mt-1 flex items-center gap-1.5 px-1">
                <ScoreBadge score={t.meta.leadScore} />
                {t.meta.requiereEscalamiento && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-orange-50 px-1.5 py-0.5 text-[11px] font-medium text-orange-700 ring-1 ring-orange-200 ring-inset">
                    <TriangleAlert className="size-3" /> Escalaría a un asesor
                  </span>
                )}
              </div>
            )}
          </div>
        ))}

        {ask.isPending && (
          <div className="flex items-center gap-1 px-1 py-2" aria-label="El bot está escribiendo">
            {[0, 1, 2].map((i) => (
              <span key={i} className="size-1.5 animate-bounce rounded-full bg-slate-400" style={{ animationDelay: `${i * 120}ms` }} />
            ))}
          </div>
        )}

        {error && (
          <div role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 ring-1 ring-red-200">
            {error}
          </div>
        )}
      </div>

      <form onSubmit={submit} className="flex items-center gap-2 border-t border-slate-100 p-3">
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={2000}
          placeholder="Escribe un mensaje de prueba…"
          className="h-10 flex-1 rounded-xl bg-slate-100 px-3.5 text-sm outline-none placeholder:text-slate-400 focus:bg-white focus:ring-2 focus:ring-brand-200"
        />
        <Button type="submit" disabled={!text.trim()} loading={ask.isPending} aria-label="Enviar" className="size-10 px-0">
          {!ask.isPending && <SendHorizontal className="size-4" />}
        </Button>
      </form>
    </section>
  )
}
