import clsx from 'clsx'
import { AlertCircle, Bot, Check, CheckCheck, Clock, Download, FileText, ImageOff, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api, type ChatMessage, type DeliveryStatus } from '../../lib/api'
import { clockTime } from '../../lib/format'

export function MessageBubble({ message }: { message: ChatMessage }) {
  const fromClient = message.sender === 'CLIENTE'
  const fromBot = message.sender === 'BOT'
  const isPlaceholder = message.mediaType !== 'TEXT' && /^\[.*\]$/.test(message.content)

  return (
    <div className={clsx('flex', fromClient ? 'justify-start' : 'justify-end')}>
      <div
        className={clsx(
          'max-w-[85%] rounded-2xl px-3.5 py-2 text-sm shadow-sm sm:max-w-[70%]',
          fromClient && 'rounded-bl-md bg-white text-slate-800 ring-1 ring-slate-200/70',
          fromBot && 'rounded-br-md bg-brand-600 text-white',
          message.sender === 'COMERCIAL' && 'rounded-br-md bg-emerald-600 text-white',
        )}
      >
        {!fromClient && (
          <p className={clsx('mb-0.5 flex items-center gap-1 text-[11px] font-semibold', fromBot ? 'text-brand-100' : 'text-emerald-100')}>
            {fromBot && <Bot className="size-3" />}
            {fromBot ? 'Bot' : 'Asesor'}
          </p>
        )}

        {message.mediaId && <Attachment message={message} />}

        {!isPlaceholder && <p className="leading-relaxed break-words whitespace-pre-wrap">{message.content}</p>}

        <div className={clsx('mt-1 flex items-center justify-end gap-1 text-[10px]', fromClient ? 'text-slate-400' : 'text-white/75')}>
          {clockTime(message.createdAt)}
          {!fromClient && <DeliveryTicks status={message.deliveryStatus} error={message.deliveryError} />}
        </div>
      </div>
    </div>
  )
}

function DeliveryTicks({ status, error }: { status: DeliveryStatus | null; error: string | null }) {
  switch (status) {
    case 'READ':
      return <CheckCheck className="size-3.5 text-sky-200" aria-label="Leído" />
    case 'DELIVERED':
      return <CheckCheck className="size-3.5" aria-label="Entregado" />
    case 'SENT':
      return <Check className="size-3.5" aria-label="Enviado" />
    case 'FAILED':
      return (
        <span title={error ?? 'No se pudo entregar'} className="inline-flex items-center gap-0.5 font-semibold text-red-100">
          <AlertCircle className="size-3.5" /> No entregado
        </span>
      )
    default:
      return <Clock className="size-3" aria-label="Enviando" />
  }
}

// Las URLs de blob se reutilizan entre renders para no descargar dos veces el mismo archivo.
const blobCache = new Map<string, Promise<string>>()

function useMediaUrl(mediaId: string) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    let pending = blobCache.get(mediaId)
    if (!pending) {
      pending = api.media(mediaId).then((blob) => URL.createObjectURL(blob))
      blobCache.set(mediaId, pending)
      pending.catch(() => blobCache.delete(mediaId))
    }
    pending.then((u) => active && setUrl(u)).catch(() => active && setFailed(true))
    return () => {
      active = false
    }
  }, [mediaId])

  return { url, failed }
}

function Attachment({ message }: { message: ChatMessage }) {
  const { url, failed } = useMediaUrl(message.mediaId!)
  const [zoom, setZoom] = useState(false)

  if (failed) {
    return (
      <p className="my-1 flex items-center gap-1.5 text-xs opacity-80">
        <ImageOff className="size-4" /> No se pudo cargar el archivo
      </p>
    )
  }

  if (message.mediaType === 'IMAGE') {
    return (
      <>
        <button onClick={() => url && setZoom(true)} className="my-1 block overflow-hidden rounded-lg">
          {url ? (
            <img src={url} alt="Imagen enviada por el cliente" className="max-h-64 w-auto object-cover transition hover:opacity-90" />
          ) : (
            <div className="h-40 w-56 animate-pulse bg-slate-200" />
          )}
        </button>
        {zoom && url && <Lightbox src={url} onClose={() => setZoom(false)} />}
      </>
    )
  }

  const filename = message.mediaFilename || 'documento.pdf'
  return (
    <a
      href={url ?? undefined}
      download={filename}
      className={clsx(
        'my-1 flex items-center gap-3 rounded-xl bg-slate-50 p-2.5 text-slate-700 ring-1 ring-slate-200 transition hover:bg-slate-100',
        !url && 'pointer-events-none opacity-60',
      )}
    >
      <div className="flex size-9 items-center justify-center rounded-lg bg-red-100 text-red-600">
        <FileText className="size-5" />
      </div>
      <span className="min-w-0 flex-1 truncate text-xs font-medium">{filename}</span>
      <Download className="size-4 text-slate-400" />
    </a>
  )
}

function Lightbox({ src, onClose }: { src: string; onClose: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div role="dialog" aria-modal className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/85 p-6" onClick={onClose}>
      <button aria-label="Cerrar" className="absolute top-4 right-4 rounded-full bg-white/10 p-2 text-white hover:bg-white/20">
        <X className="size-5" />
      </button>
      <img src={src} alt="Imagen ampliada" className="max-h-full max-w-full rounded-lg shadow-2xl" onClick={(e) => e.stopPropagation()} />
    </div>
  )
}
