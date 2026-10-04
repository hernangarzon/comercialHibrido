import { Check, Copy, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import type { Invitation } from '../lib/api'
import { Button, IconButton } from './ui'

/** Muestra la contraseña temporal una única vez, con un texto listo para compartir. */
export function CredentialsDialog({ title, invitation, intro, onClose }: { title: string; invitation: Invitation; intro?: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false)
  const url = `${window.location.origin}/app/`
  const message = `Hola ${invitation.member.name.split(' ')[0]}, te di acceso al panel comercial.\nEntra en ${url}\nCorreo: ${invitation.member.email}\nContraseña temporal: ${invitation.temporaryPassword}\nTe pedirá crear tu propia contraseña al entrar.`

  async function copy() {
    try {
      await navigator.clipboard.writeText(message)
      setCopied(true)
    } catch {
      toast.error('No se pudo copiar; selecciona el texto manualmente.')
    }
  }

  return (
    <Dialog title={title} onClose={onClose}>
      {intro && <p className="mb-3 text-sm text-slate-600">{intro}</p>}
      <p className="text-sm text-slate-600">
        Comparte estos datos con <span className="font-medium text-slate-900">{invitation.member.name}</span>.{' '}
        <span className="font-medium text-amber-700">Esta contraseña no se volverá a mostrar.</span>
      </p>
      <pre className="mt-4 rounded-lg bg-slate-50 p-3 text-xs leading-relaxed whitespace-pre-wrap text-slate-700 ring-1 ring-slate-200">{message}</pre>
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="secondary" size="sm" onClick={copy}>
          {copied ? <Check className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
          {copied ? 'Copiado' : 'Copiar mensaje'}
        </Button>
        <Button size="sm" onClick={onClose}>
          Listo
        </Button>
      </div>
    </Dialog>
  )
}

export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div role="dialog" aria-modal className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/40 p-4 sm:items-center" onClick={onClose}>
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="font-semibold">{title}</h3>
          <IconButton label="Cerrar" onClick={onClose}>
            <X className="size-4" />
          </IconButton>
        </div>
        {children}
      </div>
    </div>
  )
}
