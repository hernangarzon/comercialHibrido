import clsx from 'clsx'
import { Lock, Palette, Rocket, Smartphone, Users } from 'lucide-react'
import { useSession } from '../../lib/auth'
import { navigate } from '../../lib/router'
import { BrandTab } from './BrandTab'
import { OnboardingTab } from './OnboardingTab'
import { TeamTab } from './TeamTab'
import { WhatsAppTab } from './WhatsAppTab'

const TABS = [
  { key: 'inicio', label: 'Puesta en marcha', icon: Rocket },
  { key: 'equipo', label: 'Equipo', icon: Users },
  { key: 'whatsapp', label: 'WhatsApp', icon: Smartphone },
  { key: 'marca', label: 'Marca', icon: Palette },
] as const

export type SettingsTab = (typeof TABS)[number]['key']

export function SettingsPage({ tab }: { tab: string | null }) {
  const session = useSession()
  const isAdmin = session.role.toUpperCase() === 'ADMIN'
  const current: SettingsTab = TABS.some((t) => t.key === tab) ? (tab as SettingsTab) : 'inicio'

  return (
    <div className="scrollbar-thin h-full overflow-y-auto">
      <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
        <div className="mb-5">
          <h1 className="text-xl font-semibold tracking-tight">Ajustes</h1>
          <p className="text-sm text-slate-500">Todo lo que necesita {session.companyName} para vender por WhatsApp.</p>
          {!isAdmin && (
            <p className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-3 py-1.5 text-xs text-slate-600">
              <Lock className="size-3.5" /> Solo un administrador puede cambiar estos ajustes.
            </p>
          )}
        </div>

        <div role="tablist" className="scrollbar-thin mb-6 flex gap-1 overflow-x-auto border-b border-slate-200">
          {TABS.map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              role="tab"
              aria-selected={current === key}
              onClick={() => navigate('ajustes', { tab: key })}
              className={clsx(
                '-mb-px flex shrink-0 items-center gap-1.5 border-b-2 px-3 py-2.5 text-sm font-medium transition',
                current === key ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800',
              )}
            >
              <Icon className="size-4" />
              {label}
            </button>
          ))}
        </div>

        {current === 'inicio' && <OnboardingTab />}
        {current === 'equipo' && <TeamTab editable={isAdmin} />}
        {current === 'whatsapp' && <WhatsAppTab editable={isAdmin} />}
        {current === 'marca' && <BrandTab editable={isAdmin} />}
      </div>
    </div>
  )
}
