import clsx from 'clsx'
import { Building2, Megaphone } from 'lucide-react'
import { navigate } from '../../lib/router'
import { ClientsTab } from './ClientsTab'
import { LeadsTab } from './LeadsPage'

const TABS = [
  { key: 'solicitudes', label: 'Solicitudes', icon: Megaphone },
  { key: 'clientes', label: 'Clientes', icon: Building2 },
] as const

/** Administración de la plataforma (solo dueños): solicitudes de la landing y empresas clientes. */
export function PlatformPage({ tab }: { tab: string | null }) {
  const current = tab === 'clientes' ? 'clientes' : 'solicitudes'
  return (
    <div className="scrollbar-thin h-full overflow-y-auto">
      <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
        <h1 className="text-xl font-semibold tracking-tight">Plataforma</h1>
        <p className="mb-5 text-sm text-slate-500">Tus prospectos y tus clientes. Solo lo ven los dueños de la plataforma.</p>

        <div role="tablist" className="mb-6 flex gap-1 border-b border-slate-200">
          {TABS.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              role="tab"
              aria-selected={current === key}
              onClick={() => navigate('plataforma', { tab: key })}
              className={clsx(
                '-mb-px flex items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm font-medium transition',
                current === key ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800',
              )}
            >
              <Icon className="size-4" />
              {label}
            </button>
          ))}
        </div>

        {current === 'solicitudes' ? <LeadsTab /> : <ClientsTab />}
      </div>
    </div>
  )
}
