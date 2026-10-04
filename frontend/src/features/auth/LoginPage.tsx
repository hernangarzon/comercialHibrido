import { Bot, MessageCircle, ShieldCheck, Zap } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Button } from '../../components/ui'
import { useAuth } from '../../lib/auth'

const YEAR = new Date().getFullYear()

const BENEFITS = [
  { icon: Bot, title: 'Bot comercial 24/7', text: 'Responde, califica y cotiza con tu catálogo mientras duermes.' },
  { icon: Zap, title: 'Escala lo que importa', text: 'Los leads calientes llegan a tu equipo con alerta inmediata.' },
  { icon: ShieldCheck, title: 'Tu equipo al mando', text: 'Toma el control de cualquier chat con un clic.' },
]

export function LoginPage() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email.trim(), password)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-full">
      <aside className="relative hidden w-[46%] flex-col justify-between overflow-hidden bg-brand-900 p-12 text-white lg:flex">
        <div className="pointer-events-none absolute -top-32 -right-32 size-96 rounded-full bg-brand-600/40 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-40 -left-20 size-96 rounded-full bg-fuchsia-500/20 blur-3xl" />

        <div className="relative flex items-center gap-2.5 text-lg font-semibold">
          <div className="flex size-9 items-center justify-center rounded-xl bg-white/10 ring-1 ring-white/20">
            <MessageCircle className="size-5" />
          </div>
          Comercial Híbrido
        </div>

        <div className="relative">
          <h1 className="text-4xl leading-tight font-semibold tracking-tight">
            Tu equipo comercial
            <br />
            <span className="text-brand-200">vendiendo por WhatsApp, 24/7.</span>
          </h1>
          <ul className="mt-10 space-y-6">
            {BENEFITS.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex gap-4">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-white/10 ring-1 ring-white/15">
                  <Icon className="size-5 text-brand-200" />
                </div>
                <div>
                  <p className="font-medium">{title}</p>
                  <p className="text-sm text-brand-100/80">{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs text-brand-200/70">© {YEAR} Comercial Híbrido</p>
      </aside>

      <main className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2.5 text-lg font-semibold lg:hidden">
            <div className="flex size-9 items-center justify-center rounded-xl bg-brand-600 text-white">
              <MessageCircle className="size-5" />
            </div>
            Comercial Híbrido
          </div>

          <h2 className="text-2xl font-semibold tracking-tight">Inicia sesión</h2>
          <p className="mt-1 text-sm text-slate-500">Entra con el correo de tu empresa.</p>

          <form onSubmit={onSubmit} className="mt-8 space-y-4">
            <label className="block">
              <span className="text-sm font-medium text-slate-700">Correo electrónico</span>
              <input
                type="email"
                required
                autoFocus
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1.5 h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
                placeholder="tu@empresa.com"
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-700">Contraseña</span>
              <input
                type="password"
                required
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1.5 h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
              />
            </label>

            {error && (
              <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 ring-1 ring-red-200">
                {error}
              </p>
            )}

            <Button type="submit" loading={loading} className="h-11 w-full">
              Entrar
            </Button>
          </form>

          <p className="mt-8 text-center text-xs text-slate-400">
            <a href="/privacidad" className="hover:text-slate-600">Privacidad</a> ·{' '}
            <a href="/terminos" className="hover:text-slate-600">Términos</a>
          </p>
        </div>
      </main>
    </div>
  )
}
