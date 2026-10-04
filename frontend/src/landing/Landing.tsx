import clsx from 'clsx'
import {
  ArrowRight,
  BarChart3,
  Bot,
  Check,
  CheckCircle2,
  ChevronDown,
  Flame,
  Hand,
  Inbox,
  MessageCircle,
  ShieldCheck,
  Smartphone,
  Users,
} from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'

const YEAR = new Date().getFullYear()

const FEATURES = [
  {
    icon: Bot,
    title: 'Un bot que vende con tu información',
    text: 'Responde precios, tiempos y políticas usando solo tu catálogo y tu base de conocimiento. Si no sabe algo, no lo inventa: lo pasa a tu equipo.',
  },
  {
    icon: Flame,
    title: 'Detecta a quién vale la pena llamar',
    text: 'Califica la intención de compra de cada cliente y te avisa al instante cuando aparece un lead caliente.',
  },
  {
    icon: Hand,
    title: 'Tu equipo toma el control con un clic',
    text: 'Cualquier asesor puede entrar a la conversación; el bot se calla y la retoma cuando se la devuelves.',
  },
  {
    icon: Inbox,
    title: 'Una bandeja compartida y en vivo',
    text: 'Todo el equipo ve las conversaciones en tiempo real, con alertas, respuestas rápidas y estados de entrega.',
  },
  {
    icon: BarChart3,
    title: 'Resultados que puedes medir',
    text: 'Conversaciones atendidas, % resuelto por el bot, leads calientes y tiempo de respuesta de tu equipo, semana a semana.',
  },
  {
    icon: Smartphone,
    title: 'Desde el computador o el celular',
    text: 'El panel se adapta a cualquier pantalla para que tu equipo responda esté donde esté.',
  },
]

const STEPS = [
  { title: 'Conectamos tu WhatsApp', text: 'Usamos la API oficial de WhatsApp de Meta con tu número de empresa.' },
  { title: 'Cargas tu catálogo', text: 'Productos, precios, envíos y políticas. El bot aprende de eso y de nada más.' },
  { title: 'Empiezas a vender 24/7', text: 'El bot atiende a toda hora y tu equipo cierra las ventas que él calienta.' },
]

const INCLUDED = [
  'Bot comercial con tu catálogo y políticas',
  'Bandeja compartida en vivo para tu equipo',
  'Alertas de leads calientes y escalaciones',
  'Dashboard de resultados',
  'Usuarios ilimitados con roles',
  'Panel con tu logo y tus colores',
  'Acompañamiento para conectar tu número',
]

const FAQ = [
  {
    q: '¿Necesito un número especial de WhatsApp?',
    a: 'Se usa la API oficial de WhatsApp Business (Cloud API de Meta). Te acompañamos a conectar tu número durante la puesta en marcha.',
  },
  {
    q: '¿El bot puede inventar precios o prometer cosas?',
    a: 'No. Solo responde con el catálogo y la información que tú cargas. Si una pregunta no está cubierta, escala la conversación a tu equipo en lugar de improvisar.',
  },
  {
    q: '¿Qué pasa cuando un cliente quiere hablar con una persona?',
    a: 'El bot lo detecta, avisa a tu equipo en el panel y cualquier asesor puede tomar la conversación. Mientras un asesor la atiende, el bot no interviene.',
  },
  {
    q: '¿Puedo probar cómo responde antes de activarlo?',
    a: 'Sí. El panel incluye un probador donde escribes como un cliente y ves la respuesta del bot sin enviar nada por WhatsApp.',
  },
  {
    q: '¿Cuánto cuesta?',
    a: 'Depende del volumen de conversaciones y del tamaño de tu equipo. Déjanos tus datos y te enviamos una propuesta a la medida.',
  },
]

export function Landing() {
  const [salesWhatsapp, setSalesWhatsapp] = useState('')

  useEffect(() => {
    fetch('/api/public/config')
      .then((r) => (r.ok ? r.json() : null))
      .then((c) => setSalesWhatsapp(c?.salesWhatsapp ?? ''))
      .catch(() => {})
  }, [])

  return (
    <div className="bg-white text-slate-900">
      <Nav />
      <Hero salesWhatsapp={salesWhatsapp} />
      <HowItWorks />
      <Features />
      <Pricing />
      <Faq />
      <Contact salesWhatsapp={salesWhatsapp} />
      <footer className="border-t border-slate-200 py-8 text-center text-sm text-slate-500">
        © {YEAR} Comercial Híbrido ·{' '}
        <a href="/app/" className="hover:text-slate-800">
          Acceso clientes
        </a>
      </footer>
    </div>
  )
}

function Logo({ light }: { light?: boolean }) {
  return (
    <span className={clsx('flex items-center gap-2 font-semibold', light && 'text-white')}>
      <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-white">
        <MessageCircle className="size-4" />
      </span>
      Comercial Híbrido
    </span>
  )
}

function Nav() {
  return (
    <header className="sticky top-0 z-40 border-b border-slate-200/70 bg-white/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <a href="#inicio">
          <Logo />
        </a>
        <nav className="hidden items-center gap-6 text-sm text-slate-600 md:flex">
          <a href="#como-funciona" className="hover:text-slate-900">Cómo funciona</a>
          <a href="#funciones" className="hover:text-slate-900">Funciones</a>
          <a href="#planes" className="hover:text-slate-900">Planes</a>
          <a href="#preguntas" className="hover:text-slate-900">Preguntas</a>
        </nav>
        <div className="flex items-center gap-2">
          <a href="/app/" className="hidden rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 sm:block">
            Iniciar sesión
          </a>
          <a href="#demo" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-brand-700">
            Pide una demo
          </a>
        </div>
      </div>
    </header>
  )
}

function Hero({ salesWhatsapp }: { salesWhatsapp: string }) {
  return (
    <section id="inicio" className="relative overflow-hidden">
      <div className="pointer-events-none absolute -top-40 left-1/2 size-[720px] -translate-x-1/2 rounded-full bg-brand-100/60 blur-3xl" />
      <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 pt-16 pb-20 sm:px-6 lg:grid-cols-2 lg:pt-24">
        <div>
          <p className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700 ring-1 ring-brand-100">
            <Bot className="size-3.5" /> Bot comercial + tu equipo, en WhatsApp
          </p>
          <h1 className="mt-5 text-4xl leading-[1.1] font-semibold tracking-tight sm:text-5xl">
            Vende por WhatsApp las 24 horas,{' '}
            <span className="text-brand-600">sin perder el toque humano.</span>
          </h1>
          <p className="mt-5 max-w-lg text-lg text-slate-600">
            Un bot que responde al instante con tu catálogo, califica a cada cliente y le pasa a tu equipo los que están listos para comprar.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <a href="#demo" className="inline-flex items-center gap-2 rounded-xl bg-brand-600 px-5 py-3 font-semibold text-white shadow-sm hover:bg-brand-700">
              Pide una demo <ArrowRight className="size-4" />
            </a>
            {salesWhatsapp && (
              <a
                href={`https://wa.me/${salesWhatsapp}?text=${encodeURIComponent('Hola, quiero conocer Comercial Híbrido')}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 rounded-xl bg-white px-5 py-3 font-semibold text-slate-800 ring-1 ring-slate-200 hover:bg-slate-50"
              >
                <MessageCircle className="size-4 text-emerald-600" /> Escríbenos por WhatsApp
              </a>
            )}
          </div>
          <ul className="mt-8 flex flex-wrap gap-x-6 gap-y-2 text-sm text-slate-600">
            {['API oficial de WhatsApp', 'Responde en segundos', 'Tu equipo decide cuándo entrar'].map((t) => (
              <li key={t} className="flex items-center gap-1.5">
                <CheckCircle2 className="size-4 text-emerald-500" /> {t}
              </li>
            ))}
          </ul>
        </div>
        <ChatMock />
      </div>
    </section>
  )
}

/** Ilustración del producto: una conversación real del flujo bot → escalación. */
function ChatMock() {
  return (
    <div className="relative mx-auto w-full max-w-md" aria-hidden>
      <div className="rounded-3xl bg-white p-2 shadow-2xl ring-1 ring-slate-200">
        <div className="flex items-center gap-3 border-b border-slate-100 px-4 py-3">
          <div className="flex size-9 items-center justify-center rounded-full bg-amber-100 text-sm font-semibold text-amber-800">LG</div>
          <div className="flex-1">
            <p className="text-sm font-semibold">Dr. Luis Gómez</p>
            <p className="text-xs text-slate-500">+57 300 111 0002</p>
          </div>
          <span className="inline-flex items-center gap-0.5 rounded-full bg-orange-100 px-2 py-0.5 text-[11px] font-semibold text-orange-700">
            <Flame className="size-3" /> 85
          </span>
        </div>
        <div className="chat-surface space-y-2.5 rounded-b-2xl p-4">
          <Bubble>Hola, ¿cuánto cuesta una corona de zirconio?</Bubble>
          <Bubble bot>La corona de zirconio cuesta $180 USD y está lista en 5 días hábiles. ¿Le agendo la recogida del modelo?</Bubble>
          <Bubble>Necesito 12 para un caso completo, ¿hay precio especial?</Bubble>
          <Bubble bot>¡Excelente caso! Un asesor le prepara una cotización por volumen en unos minutos.</Bubble>
          <div className="flex justify-center pt-1">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-orange-50 px-3 py-1 text-[11px] font-medium text-orange-700 ring-1 ring-orange-200">
              <Users className="size-3" /> Escalado a tu equipo · lead caliente
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

function Bubble({ bot, children }: { bot?: boolean; children: ReactNode }) {
  return (
    <div className={clsx('flex', bot ? 'justify-end' : 'justify-start')}>
      <p
        className={clsx(
          'max-w-[85%] rounded-2xl px-3.5 py-2 text-[13px] leading-relaxed shadow-sm',
          bot ? 'rounded-br-md bg-brand-600 text-white' : 'rounded-bl-md bg-white text-slate-800 ring-1 ring-slate-200/70',
        )}
      >
        {bot && (
          <span className="mb-0.5 flex items-center gap-1 text-[10px] font-semibold text-brand-100">
            <Bot className="size-3" /> Bot
          </span>
        )}
        {children}
      </p>
    </div>
  )
}

function Section({ id, eyebrow, title, subtitle, children, muted }: { id: string; eyebrow: string; title: string; subtitle?: string; children: ReactNode; muted?: boolean }) {
  return (
    <section id={id} className={clsx('scroll-mt-16 py-20', muted && 'bg-slate-50')}>
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <div className="mx-auto max-w-2xl text-center">
          <p className="text-sm font-semibold text-brand-600">{eyebrow}</p>
          <h2 className="mt-2 text-3xl font-semibold tracking-tight">{title}</h2>
          {subtitle && <p className="mt-3 text-slate-600">{subtitle}</p>}
        </div>
        <div className="mt-12">{children}</div>
      </div>
    </section>
  )
}

function HowItWorks() {
  return (
    <Section id="como-funciona" eyebrow="Cómo funciona" title="En marcha en tres pasos" muted>
      <ol className="grid gap-6 md:grid-cols-3">
        {STEPS.map((step, i) => (
          <li key={step.title} className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <span className="flex size-9 items-center justify-center rounded-full bg-brand-600 text-sm font-semibold text-white">{i + 1}</span>
            <h3 className="mt-4 font-semibold">{step.title}</h3>
            <p className="mt-1.5 text-sm text-slate-600">{step.text}</p>
          </li>
        ))}
      </ol>
    </Section>
  )
}

function Features() {
  return (
    <Section id="funciones" eyebrow="Funciones" title="Lo mejor del bot y lo mejor de tu equipo" subtitle="El bot se encarga de lo repetitivo y de la madrugada. Tu equipo, de cerrar.">
      <div className="grid gap-x-8 gap-y-10 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title}>
            <div className="flex size-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600 ring-1 ring-brand-100">
              <Icon className="size-5" />
            </div>
            <h3 className="mt-4 font-semibold">{title}</h3>
            <p className="mt-1.5 text-sm leading-relaxed text-slate-600">{text}</p>
          </div>
        ))}
      </div>
    </Section>
  )
}

function Pricing() {
  return (
    <Section id="planes" eyebrow="Planes" title="Un plan a la medida de tu operación" muted>
      <div className="mx-auto grid max-w-4xl overflow-hidden rounded-3xl bg-white shadow-sm ring-1 ring-slate-200 md:grid-cols-2">
        <div className="p-8">
          <h3 className="text-lg font-semibold">Todo incluido</h3>
          <p className="mt-1 text-sm text-slate-600">Sin módulos extra ni sorpresas.</p>
          <ul className="mt-6 space-y-3">
            {INCLUDED.map((item) => (
              <li key={item} className="flex gap-2.5 text-sm text-slate-700">
                <Check className="mt-0.5 size-4 shrink-0 text-brand-600" /> {item}
              </li>
            ))}
          </ul>
        </div>
        <div className="flex flex-col justify-center bg-brand-900 p-8 text-white">
          <p className="text-sm font-medium text-brand-100">Precio</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight">Cotiza tu plan</p>
          <p className="mt-3 text-sm text-brand-100">
            Según el volumen de conversaciones y el tamaño de tu equipo. Te enviamos una propuesta después de conocer tu operación.
          </p>
          <a href="#demo" className="mt-8 inline-flex items-center justify-center gap-2 rounded-xl bg-white px-5 py-3 font-semibold text-brand-900 hover:bg-brand-50">
            Pedir propuesta <ArrowRight className="size-4" />
          </a>
        </div>
      </div>
    </Section>
  )
}

function Faq() {
  return (
    <Section id="preguntas" eyebrow="Preguntas frecuentes" title="Lo que suelen preguntarnos">
      <div className="mx-auto max-w-3xl divide-y divide-slate-200 rounded-2xl ring-1 ring-slate-200">
        {FAQ.map(({ q, a }) => (
          <details key={q} className="group px-6 py-4 [&_summary::-webkit-details-marker]:hidden">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-4 font-medium">
              {q}
              <ChevronDown className="size-4 shrink-0 text-slate-400 transition group-open:rotate-180" />
            </summary>
            <p className="mt-2 text-sm leading-relaxed text-slate-600">{a}</p>
          </details>
        ))}
      </div>
    </Section>
  )
}

function Contact({ salesWhatsapp }: { salesWhatsapp: string }) {
  const [form, setForm] = useState({ name: '', companyName: '', email: '', phone: '', message: '', website: '' })
  const [state, setState] = useState<'idle' | 'sending' | 'sent'>('idle')
  const [error, setError] = useState<string | null>(null)
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setState('sending')
    try {
      const res = await fetch('/api/public/leads', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      if (!res.ok) {
        const body = await res.json().catch(() => null)
        throw new Error(body?.error ?? 'No pudimos enviar tu solicitud. Intenta de nuevo.')
      }
      setState('sent')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No pudimos enviar tu solicitud.')
      setState('idle')
    }
  }

  const input =
    'mt-1.5 h-11 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100'

  return (
    <section id="demo" className="scroll-mt-16 bg-brand-900 py-20 text-white">
      <div className="mx-auto grid max-w-6xl gap-12 px-4 sm:px-6 lg:grid-cols-2">
        <div>
          <h2 className="text-3xl font-semibold tracking-tight">Mira cómo vendería tu negocio</h2>
          <p className="mt-4 text-brand-100">
            Te mostramos el bot respondiendo con tus propios productos y armamos una propuesta para tu equipo. Sin compromiso.
          </p>
          <ul className="mt-8 space-y-3 text-sm text-brand-100">
            {['Demo de 30 minutos', 'Con tu catálogo real', 'Propuesta a la medida'].map((t) => (
              <li key={t} className="flex items-center gap-2">
                <CheckCircle2 className="size-4 text-emerald-300" /> {t}
              </li>
            ))}
          </ul>
          {salesWhatsapp && (
            <a
              href={`https://wa.me/${salesWhatsapp}?text=${encodeURIComponent('Hola, quiero una demo de Comercial Híbrido')}`}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-8 inline-flex items-center gap-2 text-sm font-semibold text-white underline-offset-4 hover:underline"
            >
              <MessageCircle className="size-4" /> ¿Prefieres WhatsApp? Escríbenos
            </a>
          )}
        </div>

        <div className="rounded-2xl bg-white p-6 text-slate-900 shadow-xl sm:p-8">
          {state === 'sent' ? (
            <div className="flex h-full flex-col items-center justify-center py-10 text-center">
              <CheckCircle2 className="size-12 text-emerald-500" />
              <h3 className="mt-4 text-xl font-semibold">¡Gracias, {form.name.split(' ')[0]}!</h3>
              <p className="mt-2 max-w-xs text-sm text-slate-600">Recibimos tu solicitud. Te contactaremos muy pronto para agendar la demo.</p>
            </div>
          ) : (
            <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
              <label className="block">
                <span className="text-sm font-medium text-slate-700">Nombre</span>
                <input required maxLength={120} autoComplete="name" value={form.name} onChange={set('name')} className={input} />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-slate-700">Empresa</span>
                <input required maxLength={150} autoComplete="organization" value={form.companyName} onChange={set('companyName')} className={input} />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-slate-700">Correo</span>
                <input required type="email" maxLength={150} autoComplete="email" value={form.email} onChange={set('email')} className={input} />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-slate-700">WhatsApp o teléfono</span>
                <input required type="tel" maxLength={40} autoComplete="tel" value={form.phone} onChange={set('phone')} placeholder="+57 300 000 0000" className={input} />
              </label>
              <label className="block sm:col-span-2">
                <span className="text-sm font-medium text-slate-700">¿Qué vendes y cuántos mensajes recibes? (opcional)</span>
                <textarea rows={3} maxLength={2000} value={form.message} onChange={set('message')} className="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100" />
              </label>
              {/* Campo trampa para bots: oculto para personas y lectores de pantalla. */}
              <input type="text" name="website" tabIndex={-1} autoComplete="off" value={form.website} onChange={set('website')} className="hidden" aria-hidden />
              {error && (
                <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 ring-1 ring-red-200 sm:col-span-2">
                  {error}
                </p>
              )}
              <button
                type="submit"
                disabled={state === 'sending'}
                className="flex h-12 items-center justify-center gap-2 rounded-xl bg-brand-600 font-semibold text-white shadow-sm hover:bg-brand-700 disabled:opacity-60 sm:col-span-2"
              >
                {state === 'sending' ? 'Enviando…' : 'Quiero mi demo'}
              </button>
              <p className="flex items-center gap-1.5 text-xs text-slate-500 sm:col-span-2">
                <ShieldCheck className="size-3.5" /> Solo usamos tus datos para contactarte sobre la demo.
              </p>
            </form>
          )}
        </div>
      </div>
    </section>
  )
}
