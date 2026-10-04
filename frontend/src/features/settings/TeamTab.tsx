import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { KeyRound, UserPlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { CredentialsDialog, Dialog } from '../../components/dialogs'
import { Avatar, Button, IconButton, Skeleton } from '../../components/ui'
import { api, type Invitation, type Member, type Role } from '../../lib/api'
import { useSession } from '../../lib/auth'

const ROLE_LABEL: Record<Role, string> = { ADMIN: 'Administrador', COMERCIAL: 'Comercial' }

export function TeamTab({ editable }: { editable: boolean }) {
  const session = useSession()
  const queryClient = useQueryClient()
  const { data: members, isLoading } = useQuery({ queryKey: ['team'], queryFn: api.team })
  const [inviting, setInviting] = useState(false)
  const [credentials, setCredentials] = useState<{ title: string; invitation: Invitation } | null>(null)

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['team'] })
    void queryClient.invalidateQueries({ queryKey: ['onboarding'] })
  }

  const update = useMutation({
    mutationFn: ({ member, role, active }: { member: Member; role: Role; active: boolean }) => api.updateMember(member.id, role, active),
    onSuccess: (m) => {
      refresh()
      toast.success(m.active ? `${m.name} ahora es ${ROLE_LABEL[m.role].toLowerCase()}.` : `${m.name} ya no puede entrar al panel.`)
    },
    onError: (err) => toast.error(err.message),
  })

  const reset = useMutation({
    mutationFn: (member: Member) => api.resetPassword(member.id),
    onSuccess: (invitation) => {
      refresh()
      setCredentials({ title: 'Contraseña restablecida', invitation })
    },
    onError: (err) => toast.error(err.message),
  })

  const active = members?.filter((m) => m.active).length ?? 0

  return (
    <section className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-semibold">Equipo</h2>
          <p className="text-xs text-slate-500">
            {members ? `${active} ${active === 1 ? 'persona activa' : 'personas activas'}. ` : ''}Los comerciales atienden; los administradores además configuran.
          </p>
        </div>
        {editable && (
          <Button size="sm" onClick={() => setInviting(true)}>
            <UserPlus className="size-4" /> Invitar
          </Button>
        )}
      </div>

      {isLoading || !members ? (
        <div className="space-y-2 p-5">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      ) : (
        <ul className="divide-y divide-slate-100">
          {members.map((m) => {
            const isMe = m.id === session.userId
            return (
              <li key={m.id} className={clsx('flex flex-wrap items-center gap-3 px-5 py-3', !m.active && 'opacity-60')}>
                <Avatar name={m.name} seed={m.email} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-slate-800">
                    {m.name} {isMe && <span className="text-xs font-normal text-slate-400">(tú)</span>}
                  </p>
                  <p className="truncate text-xs text-slate-500">
                    {m.email}
                    {m.mustChangePassword && m.active && <span className="ml-1.5 text-amber-700">· Pendiente de primer ingreso</span>}
                    {!m.active && <span className="ml-1.5">· Desactivado</span>}
                  </p>
                </div>

                {editable && !isMe ? (
                  <div className="flex items-center gap-1">
                    <select
                      aria-label={`Rol de ${m.name}`}
                      value={m.role}
                      disabled={!m.active || update.isPending}
                      onChange={(e) => update.mutate({ member: m, role: e.target.value as Role, active: m.active })}
                      className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs outline-none focus:border-brand-500"
                    >
                      <option value="COMERCIAL">Comercial</option>
                      <option value="ADMIN">Administrador</option>
                    </select>
                    {m.active && (
                      <IconButton label={`Restablecer contraseña de ${m.name}`} className="size-8" onClick={() => reset.mutate(m)} disabled={reset.isPending}>
                        <KeyRound className="size-3.5" />
                      </IconButton>
                    )}
                    <Button
                      size="sm"
                      variant={m.active ? 'ghost' : 'secondary'}
                      disabled={update.isPending}
                      onClick={() => update.mutate({ member: m, role: m.role, active: !m.active })}
                    >
                      {m.active ? 'Desactivar' : 'Reactivar'}
                    </Button>
                  </div>
                ) : (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">{ROLE_LABEL[m.role]}</span>
                )}
              </li>
            )
          })}
        </ul>
      )}

      {inviting && (
        <InviteDialog
          onClose={() => setInviting(false)}
          onInvited={(invitation) => {
            refresh()
            setInviting(false)
            setCredentials({ title: 'Invitación creada', invitation })
          }}
        />
      )}
      {credentials && <CredentialsDialog {...credentials} onClose={() => setCredentials(null)} />}
    </section>
  )
}

function InviteDialog({ onClose, onInvited }: { onClose: () => void; onInvited: (invitation: Invitation) => void }) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('COMERCIAL')
  const invite = useMutation({
    mutationFn: () => api.invite(name.trim(), email.trim(), role),
    onSuccess: onInvited,
    onError: (err) => toast.error(err.message),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    invite.mutate()
  }

  const input = 'mt-1.5 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100'

  return (
    <Dialog title="Invitar al equipo" onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <label className="block">
          <span className="text-sm font-medium text-slate-700">Nombre</span>
          <input required autoFocus maxLength={100} value={name} onChange={(e) => setName(e.target.value)} className={input} />
        </label>
        <label className="block">
          <span className="text-sm font-medium text-slate-700">Correo</span>
          <input required type="email" maxLength={150} value={email} onChange={(e) => setEmail(e.target.value)} className={input} />
        </label>
        <fieldset>
          <legend className="text-sm font-medium text-slate-700">Rol</legend>
          <div className="mt-1.5 grid grid-cols-2 gap-2">
            {(['COMERCIAL', 'ADMIN'] as Role[]).map((r) => (
              <label
                key={r}
                className={clsx(
                  'cursor-pointer rounded-lg p-3 text-sm ring-1 transition',
                  role === r ? 'bg-brand-50 ring-2 ring-brand-500' : 'ring-slate-200 hover:ring-slate-300',
                )}
              >
                <input type="radio" name="role" value={r} checked={role === r} onChange={() => setRole(r)} className="sr-only" />
                <span className="block font-medium">{ROLE_LABEL[r]}</span>
                <span className="text-xs text-slate-500">{r === 'ADMIN' ? 'Atiende y configura todo' : 'Atiende conversaciones'}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <p className="text-xs text-slate-500">Te mostraremos una contraseña temporal para que se la compartas. Deberá cambiarla al entrar.</p>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" size="sm" loading={invite.isPending}>
            Crear invitación
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
