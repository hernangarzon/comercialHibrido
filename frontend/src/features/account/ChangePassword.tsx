import { KeyRound, LogOut, X } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Button, IconButton } from '../../components/ui'
import { api } from '../../lib/api'
import { useAuth, useSession } from '../../lib/auth'

const MIN_LENGTH = 8

function ChangePasswordForm({ onDone, submitLabel }: { onDone: () => void; submitLabel: string }) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (next.length < MIN_LENGTH) return setError(`La nueva contraseña debe tener al menos ${MIN_LENGTH} caracteres.`)
    if (next !== confirm) return setError('Las contraseñas nuevas no coinciden.')
    setSaving(true)
    try {
      await api.changePassword(current, next)
      onDone()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo cambiar la contraseña')
    } finally {
      setSaving(false)
    }
  }

  const input =
    'mt-1.5 h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100'

  return (
    <form onSubmit={submit} className="space-y-4">
      <label className="block">
        <span className="text-sm font-medium text-slate-700">Contraseña actual</span>
        <input type="password" required autoFocus autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} className={input} />
      </label>
      <div>
        <label className="block">
          <span className="text-sm font-medium text-slate-700">Nueva contraseña</span>
          <input
            type="password"
            required
            autoComplete="new-password"
            minLength={MIN_LENGTH}
            aria-describedby="new-password-help"
            value={next}
            onChange={(e) => setNext(e.target.value)}
            className={input}
          />
        </label>
        <p id="new-password-help" className="mt-1 text-xs text-slate-500">Mínimo {MIN_LENGTH} caracteres.</p>
      </div>
      <label className="block">
        <span className="text-sm font-medium text-slate-700">Repite la nueva contraseña</span>
        <input type="password" required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} className={input} />
      </label>
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 ring-1 ring-red-200">
          {error}
        </p>
      )}
      <Button type="submit" loading={saving} className="h-11 w-full">
        {submitLabel}
      </Button>
    </form>
  )
}

/** Pantalla obligatoria tras entrar con una contraseña temporal (invitación o restablecimiento). */
export function ForcePasswordChange() {
  const session = useSession()
  const { updateSession, logout } = useAuth()
  return (
    <div className="flex min-h-full items-center justify-center px-6 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex size-11 items-center justify-center rounded-xl bg-brand-600 text-white">
          <KeyRound className="size-5" />
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">Crea tu contraseña</h1>
        <p className="mt-1 mb-8 text-sm text-slate-500">
          Hola {session.name.split(' ')[0]}, entraste con una contraseña temporal. Elige una propia para continuar a {session.companyName}.
        </p>
        <ChangePasswordForm
          submitLabel="Guardar y continuar"
          onDone={() => {
            updateSession({ mustChangePassword: false })
            toast.success('¡Listo! Ya puedes usar el panel.')
          }}
        />
        <button onClick={logout} className="mx-auto mt-6 flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-800">
          <LogOut className="size-4" /> Salir
        </button>
      </div>
    </div>
  )
}

export function ChangePasswordDialog({ onClose }: { onClose: () => void }) {
  return (
    <div role="dialog" aria-modal className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/40 p-4 sm:items-center" onClick={onClose}>
      <div className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-5 flex items-center justify-between">
          <h2 className="font-semibold">Cambiar contraseña</h2>
          <IconButton label="Cerrar" onClick={onClose}>
            <X className="size-4" />
          </IconButton>
        </div>
        <ChangePasswordForm
          submitLabel="Cambiar contraseña"
          onDone={() => {
            toast.success('Contraseña actualizada.')
            onClose()
          }}
        />
      </div>
    </div>
  )
}
