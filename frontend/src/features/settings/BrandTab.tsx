import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bot, ImageUp, MessageCircle, Trash2, TriangleAlert } from 'lucide-react'
import { useEffect, useState, type ChangeEvent } from 'react'
import { toast } from 'sonner'
import { Button, Skeleton } from '../../components/ui'
import { api, type CompanySettings } from '../../lib/api'
import { DEFAULT_BRAND, contrastWithWhite } from '../../lib/branding'

const PRESETS = ['#4f46e5', '#2563eb', '#0f766e', '#15803d', '#b91c1c', '#c2410c', '#9333ea', '#be185d', '#334155']
const MAX_LOGO_BYTES = 200 * 1024

export function BrandTab({ editable }: { editable: boolean }) {
  const { data, isLoading } = useQuery({ queryKey: ['company'], queryFn: api.companySettings })
  if (isLoading || !data) return <Skeleton className="h-96 rounded-xl" />
  return <BrandEditor key={data.updatedAt} settings={data} editable={editable} />
}

function BrandEditor({ settings, editable }: { settings: CompanySettings; editable: boolean }) {
  const queryClient = useQueryClient()
  const [color, setColor] = useState(settings.brandColor ?? DEFAULT_BRAND)
  const [logo, setLogo] = useState<string | null>(settings.logoDataUrl)
  const validHex = /^#[0-9a-f]{6}$/i.test(color)
  const contrast = validHex ? contrastWithWhite(color) : 0
  const readable = contrast >= 4.5
  const dirty = color.toLowerCase() !== (settings.brandColor ?? DEFAULT_BRAND).toLowerCase() || logo !== settings.logoDataUrl

  // Vista previa en vivo: el panel entero adopta el color mientras se edita.
  useEffect(() => {
    if (validHex && readable) document.documentElement.style.setProperty('--brand', color)
    return () => document.documentElement.style.setProperty('--brand', settings.brandColor ?? DEFAULT_BRAND)
  }, [color, validHex, readable, settings.brandColor])

  const save = useMutation({
    mutationFn: () => api.saveBranding(color.toLowerCase() === DEFAULT_BRAND ? null : color, logo),
    onSuccess: (updated) => {
      queryClient.setQueryData(['company'], updated)
      void queryClient.invalidateQueries({ queryKey: ['onboarding'] })
      toast.success('Marca actualizada para todo tu equipo.')
    },
    onError: (err) => toast.error(err.message),
  })

  function onLogo(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) return toast.error('Usa una imagen PNG, JPG o WebP.')
    if (file.size > MAX_LOGO_BYTES) return toast.error('El logo pesa más de 200 KB. Reduce su tamaño.')
    const reader = new FileReader()
    reader.onload = () => setLogo(String(reader.result))
    reader.readAsDataURL(file)
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <section className="space-y-6 rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <div>
          <h2 className="font-semibold">Color de la marca</h2>
          <p className="text-xs text-slate-500">Se usa en botones, pestañas y mensajes del bot en el panel de todo tu equipo.</p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            {PRESETS.map((preset) => (
              <button
                key={preset}
                disabled={!editable}
                onClick={() => setColor(preset)}
                aria-label={`Usar ${preset}`}
                className={clsx(
                  'size-8 rounded-full ring-offset-2 transition disabled:cursor-not-allowed',
                  color.toLowerCase() === preset ? 'ring-2 ring-slate-900' : 'hover:scale-110',
                )}
                style={{ background: preset }}
              />
            ))}
            <label className="ml-2 flex items-center gap-2 rounded-lg bg-slate-50 px-2 py-1 ring-1 ring-slate-200">
              <input
                type="color"
                disabled={!editable}
                value={validHex ? color : DEFAULT_BRAND}
                onChange={(e) => setColor(e.target.value)}
                className="size-6 cursor-pointer rounded border-0 bg-transparent p-0"
                aria-label="Elegir color"
              />
              <input
                value={color}
                readOnly={!editable}
                onChange={(e) => setColor(e.target.value.trim())}
                maxLength={7}
                className="w-20 bg-transparent font-mono text-sm outline-none"
                aria-label="Código del color"
              />
            </label>
          </div>
          {validHex && !readable && (
            <p className="mt-2 flex items-center gap-1.5 text-xs text-red-700">
              <TriangleAlert className="size-3.5" /> Muy claro: el texto blanco de los botones no se leería (contraste {contrast.toFixed(1)}:1, mínimo 4.5:1).
            </p>
          )}
        </div>

        <div>
          <h2 className="font-semibold">Logo</h2>
          <p className="text-xs text-slate-500">PNG, JPG o WebP de hasta 200 KB. Se ve mejor horizontal y con fondo transparente.</p>
          <div className="mt-4 flex items-center gap-4">
            <div className="flex h-16 w-40 items-center justify-center rounded-lg bg-slate-50 ring-1 ring-slate-200">
              {logo ? <img src={logo} alt="Logo" className="max-h-12 max-w-36 object-contain" /> : <span className="text-xs text-slate-400">Sin logo</span>}
            </div>
            {editable && (
              <div className="flex gap-2">
                <label className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg bg-white px-3 text-xs font-medium text-slate-700 shadow-sm ring-1 ring-slate-200 ring-inset hover:bg-slate-50">
                  <ImageUp className="size-3.5" /> Subir
                  <input type="file" accept="image/png,image/jpeg,image/webp" onChange={onLogo} className="sr-only" />
                </label>
                {logo && (
                  <Button variant="ghost" size="sm" onClick={() => setLogo(null)}>
                    <Trash2 className="size-3.5" /> Quitar
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>

        {editable && (
          <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
            <Button
              variant="secondary"
              size="sm"
              disabled={!dirty}
              onClick={() => {
                setColor(settings.brandColor ?? DEFAULT_BRAND)
                setLogo(settings.logoDataUrl)
              }}
            >
              Descartar
            </Button>
            <Button size="sm" disabled={!dirty || !validHex || !readable} loading={save.isPending} onClick={() => save.mutate()}>
              Guardar marca
            </Button>
          </div>
        )}
      </section>

      <aside className="rounded-xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
        <h3 className="text-sm font-semibold">Vista previa</h3>
        <div className="mt-3 overflow-hidden rounded-lg ring-1 ring-slate-200">
          <div className="flex items-center gap-2 border-b border-slate-100 bg-white px-3 py-2">
            {logo ? (
              <img src={logo} alt="" className="h-6 max-w-20 object-contain" />
            ) : (
              <div className="flex size-6 items-center justify-center rounded-md bg-brand-600 text-white">
                <MessageCircle className="size-3.5" />
              </div>
            )}
            <span className="text-xs font-semibold">{settings.name}</span>
          </div>
          <div className="chat-surface space-y-2 p-3">
            <p className="w-fit max-w-[85%] rounded-2xl rounded-bl-md bg-white px-3 py-1.5 text-xs shadow-sm">¿Tienen envíos a mi ciudad?</p>
            <div className="ml-auto w-fit max-w-[85%] rounded-2xl rounded-br-md bg-brand-600 px-3 py-1.5 text-xs text-white">
              <p className="mb-0.5 flex items-center gap-1 text-[10px] font-semibold text-brand-100">
                <Bot className="size-3" /> Bot
              </p>
              ¡Sí! Enviamos a todo el país en 48 horas.
            </div>
          </div>
          <div className="flex justify-end gap-2 bg-white p-3">
            <span className="rounded-md bg-brand-50 px-2 py-1 text-[11px] font-medium text-brand-700">Pestaña activa</span>
            <span className="rounded-md bg-brand-600 px-2 py-1 text-[11px] font-medium text-white">Tomar control</span>
          </div>
        </div>
      </aside>
    </div>
  )
}
