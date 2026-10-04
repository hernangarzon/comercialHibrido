import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Building2, CheckCircle2, CircleSlash, Plus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { CredentialsDialog, Dialog } from '../../components/dialogs'
import { Button, EmptyState, Skeleton } from '../../components/ui'
import { api, type ClientCompany, type NewClient, type SalesLead } from '../../lib/api'
import { timeAgo } from '../../lib/format'

/** Empresas clientes de la plataforma: alta manual y suspensión (p. ej. por falta de pago). */
export function ClientsTab() {
  const queryClient = useQueryClient()
  const { data: clients, isLoading } = useQuery({ queryKey: ['platform-clients'], queryFn: api.clients })
  const [creating, setCreating] = useState(false)
  const [created, setCreated] = useState<NewClient | null>(null)
  const [confirm, setConfirm] = useState<string | null>(null)

  const toggle = useMutation({
    mutationFn: (c: ClientCompany) => api.setClientActive(c.id, !c.active),
    onSuccess: (c) => {
      void queryClient.invalidateQueries({ queryKey: ['platform-clients'] })
      toast.success(c.active ? `${c.name} reactivado.` : `${c.name} suspendido: su equipo ya no puede entrar y el bot deja de atender.`)
    },
    onError: (err) => toast.error(err.message),
    onSettled: () => setConfirm(null),
  })

  const active = clients?.filter((c) => c.active).length ?? 0

  return (
    <section className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-semibold">Clientes</h2>
          <p className="text-xs text-slate-500">{clients ? `${active} activos de ${clients.length}.` : ''} Cada empresa tiene su propio panel, equipo y número.</p>
        </div>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus className="size-4" /> Nuevo cliente
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2 p-5">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      ) : !clients?.length ? (
        <div className="h-56">
          <EmptyState icon={<Building2 className="size-5" />} title="Aún no hay clientes" />
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-sm">
            <thead className="text-left text-xs text-slate-500">
              <tr className="border-b border-slate-100">
                <th className="px-5 py-2 font-medium">Empresa</th>
                <th className="px-3 py-2 font-medium">WhatsApp</th>
                <th className="px-3 py-2 text-right font-medium">Usuarios</th>
                <th className="px-3 py-2 text-right font-medium">Conversaciones</th>
                <th className="px-3 py-2 font-medium">Estado</th>
                <th className="w-32 px-3 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {clients.map((c) => (
                <tr key={c.id} className={clsx(!c.active && 'bg-slate-50/60')}>
                  <td className="px-5 py-3">
                    <p className="font-medium text-slate-800">
                      {c.name} {c.yours && <span className="text-xs font-normal text-slate-400">(la tuya)</span>}
                    </p>
                    <p className="text-xs text-slate-500">Alta {timeAgo(c.createdAt)}</p>
                  </td>
                  <td className="px-3 py-3">
                    {c.whatsappConnected ? (
                      <span className="inline-flex items-center gap-1 text-xs text-emerald-700">
                        <CheckCircle2 className="size-3.5" /> Conectado
                      </span>
                    ) : (
                      <span className="text-xs text-amber-700">Pendiente</span>
                    )}
                  </td>
                  <td className="px-3 py-3 text-right tabular-nums">{c.users}</td>
                  <td className="px-3 py-3 text-right tabular-nums">{c.conversations}</td>
                  <td className="px-3 py-3">
                    <span
                      className={clsx(
                        'rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
                        c.active ? 'bg-emerald-50 text-emerald-700 ring-emerald-200' : 'bg-red-50 text-red-700 ring-red-200',
                      )}
                    >
                      {c.active ? 'Activo' : 'Suspendido'}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-right">
                    {!c.yours &&
                      (confirm === c.id ? (
                        <Button size="sm" variant={c.active ? 'danger' : 'success'} loading={toggle.isPending} onClick={() => toggle.mutate(c)} onBlur={() => setConfirm(null)} autoFocus>
                          {c.active ? '¿Suspender?' : '¿Reactivar?'}
                        </Button>
                      ) : (
                        <Button size="sm" variant="ghost" onClick={() => setConfirm(c.id)}>
                          {c.active ? (
                            <>
                              <CircleSlash className="size-3.5" /> Suspender
                            </>
                          ) : (
                            'Reactivar'
                          )}
                        </Button>
                      ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {creating && (
        <NewClientDialog
          onClose={() => setCreating(false)}
          onCreated={(result) => {
            setCreating(false)
            setCreated(result)
            void queryClient.invalidateQueries({ queryKey: ['platform-clients'] })
          }}
        />
      )}
      {created && (
        <CredentialsDialog
          title="Cliente creado"
          intro={`${created.company.name} ya tiene su panel. Su administrador debe conectar el número de WhatsApp en Ajustes.`}
          invitation={created.invitation}
          onClose={() => setCreated(null)}
        />
      )}
    </section>
  )
}

/** Alta de una empresa cliente con su primer administrador; puede partir de una solicitud de la landing. */
export function NewClientDialog({ lead, onClose, onCreated }: { lead?: SalesLead; onClose: () => void; onCreated: (result: NewClient) => void }) {
  const [companyName, setCompanyName] = useState(lead?.companyName ?? '')
  const [adminName, setAdminName] = useState(lead?.name ?? '')
  const [adminEmail, setAdminEmail] = useState(lead?.email ?? '')

  const create = useMutation({
    mutationFn: () => api.createClient(companyName.trim(), adminName.trim(), adminEmail.trim(), lead?.id),
    onSuccess: onCreated,
    onError: (err) => toast.error(err.message),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate()
  }

  const input = 'mt-1.5 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100'

  return (
    <Dialog title={lead ? 'Convertir en cliente' : 'Nuevo cliente'} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <label className="block">
          <span className="text-sm font-medium text-slate-700">Empresa</span>
          <input required autoFocus maxLength={100} value={companyName} onChange={(e) => setCompanyName(e.target.value)} className={input} />
        </label>
        <label className="block">
          <span className="text-sm font-medium text-slate-700">Nombre del administrador</span>
          <input required maxLength={100} value={adminName} onChange={(e) => setAdminName(e.target.value)} className={input} />
        </label>
        <label className="block">
          <span className="text-sm font-medium text-slate-700">Correo del administrador</span>
          <input required type="email" maxLength={150} value={adminEmail} onChange={(e) => setAdminEmail(e.target.value)} className={input} />
        </label>
        <p className="text-xs text-slate-500">
          Se crea la empresa con un administrador y una contraseña temporal para compartirle. Luego él conecta su WhatsApp e invita a su equipo.
        </p>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" size="sm" loading={create.isPending}>
            Crear cliente
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
