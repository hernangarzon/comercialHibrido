import { MessageCircle } from 'lucide-react'
import type { ReactNode } from 'react'
import { LEGAL } from './company'

/** Marco común de las páginas legales: encabezado, índice y pie con enlaces cruzados. */
export function LegalLayout({ title, intro, children }: { title: string; intro: string; children: ReactNode }) {
  return (
    <div className="min-h-full bg-white">
      <header className="border-b border-slate-200">
        <div className="mx-auto flex h-16 max-w-3xl items-center justify-between px-4 sm:px-6">
          <a href="/" className="flex items-center gap-2 font-semibold">
            <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-white">
              <MessageCircle className="size-4" />
            </span>
            {LEGAL.product}
          </a>
          <a href="/app/" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            Acceso clientes
          </a>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-2 text-sm text-slate-500">Última actualización: {LEGAL.updatedAt}</p>
        <p className="mt-6 leading-relaxed text-slate-700">{intro}</p>
        <div className="legal mt-10 space-y-10">{children}</div>
      </main>

      <footer className="border-t border-slate-200 py-8 text-center text-sm text-slate-500">
        <a href="/privacidad" className="hover:text-slate-800">Política de privacidad</a> ·{' '}
        <a href="/terminos" className="hover:text-slate-800">Términos de uso</a> ·{' '}
        <a href={`mailto:${LEGAL.email}`} className="hover:text-slate-800">{LEGAL.email}</a>
      </footer>
    </div>
  )
}

export function Section({ id, title, children }: { id?: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-6">
      <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
      <div className="mt-3 space-y-3 leading-relaxed text-slate-700 [&_li]:ml-5 [&_li]:list-disc [&_li]:pl-1 [&_ul]:space-y-1.5">
        {children}
      </div>
    </section>
  )
}
