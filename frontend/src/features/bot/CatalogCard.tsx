import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Package, Pencil, Plus, Trash2, X } from 'lucide-react'
import { useState, type FormEvent, type InputHTMLAttributes, type ReactNode } from 'react'
import { toast } from 'sonner'
import { Button, EmptyState, IconButton, Skeleton } from '../../components/ui'
import { api, type Product, type ProductInput } from '../../lib/api'

const EMPTY: ProductInput = {
  name: '',
  category: null,
  material: null,
  description: null,
  price: 0,
  currency: 'USD',
  deliveryTimeDays: null,
  warrantyMonths: 12,
  available: true,
}

function money(price: number, currency: string) {
  try {
    return new Intl.NumberFormat('es', { style: 'currency', currency, maximumFractionDigits: 2 }).format(price)
  } catch {
    return `${price} ${currency}`
  }
}

export function CatalogCard({ editable }: { editable: boolean }) {
  const queryClient = useQueryClient()
  const { data: products, isLoading } = useQuery({ queryKey: ['products'], queryFn: api.products })
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null)

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['products'] })

  const toggle = useMutation({
    mutationFn: (p: Product) => api.saveProduct({ ...p, available: !p.available }, p.id),
    onSuccess: (p) => {
      void refresh()
      toast.success(p.available ? `${p.name} vuelve a ofrecerse.` : `El bot dejará de ofrecer ${p.name}.`)
    },
    onError: (err) => toast.error(err.message),
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.deleteProduct(id),
    onSuccess: () => {
      void refresh()
      toast.success('Producto eliminado.')
    },
    onError: (err) => toast.error(err.message),
    onSettled: () => setConfirmDelete(null),
  })

  const available = products?.filter((p) => p.available).length ?? 0

  return (
    <section className="rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
        <div>
          <h2 className="font-semibold">Catálogo</h2>
          <p className="text-xs text-slate-500">
            {products ? `${available} de ${products.length} disponibles para el bot.` : 'Productos, precios y tiempos que el bot puede ofrecer.'}
          </p>
        </div>
        {editable && (
          <Button size="sm" onClick={() => setEditing('new')}>
            <Plus className="size-4" /> Agregar
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-2 p-5">
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
        </div>
      ) : !products?.length ? (
        <div className="h-56">
          <EmptyState
            icon={<Package className="size-5" />}
            title="Aún no hay productos"
            description="Sin catálogo el bot no puede dar precios y escalará esas preguntas a tu equipo."
            action={editable && <Button size="sm" onClick={() => setEditing('new')}>Agregar el primero</Button>}
          />
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="text-left text-xs text-slate-500">
              <tr className="border-b border-slate-100">
                <th className="px-5 py-2 font-medium">Producto</th>
                <th className="px-3 py-2 text-right font-medium">Precio</th>
                <th className="px-3 py-2 font-medium">Entrega</th>
                <th className="px-3 py-2 font-medium">Disponible</th>
                {editable && <th className="w-24 px-3 py-2" />}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {products.map((p) => (
                <tr key={p.id} className={clsx(!p.available && 'text-slate-400')}>
                  <td className="px-5 py-3">
                    <p className={clsx('font-medium', p.available ? 'text-slate-800' : 'text-slate-500')}>{p.name}</p>
                    <p className="text-xs text-slate-500">{[p.category, p.material].filter(Boolean).join(' · ') || '—'}</p>
                  </td>
                  <td className="px-3 py-3 text-right tabular-nums">{money(p.price, p.currency)}</td>
                  <td className="px-3 py-3 text-slate-600">{p.deliveryTimeDays != null ? `${p.deliveryTimeDays} días` : '—'}</td>
                  <td className="px-3 py-3">
                    <Switch
                      checked={p.available}
                      disabled={!editable || toggle.isPending}
                      label={p.available ? 'El bot lo ofrece' : 'Oculto para el bot'}
                      onChange={() => toggle.mutate(p)}
                    />
                  </td>
                  {editable && (
                    <td className="px-3 py-3">
                      {confirmDelete === p.id ? (
                        <Button size="sm" variant="danger" loading={remove.isPending} onClick={() => remove.mutate(p.id)} onBlur={() => setConfirmDelete(null)} autoFocus>
                          ¿Eliminar?
                        </Button>
                      ) : (
                        <div className="flex justify-end gap-0.5">
                          <IconButton label={`Editar ${p.name}`} className="size-8" onClick={() => setEditing(p)}>
                            <Pencil className="size-3.5" />
                          </IconButton>
                          <IconButton label={`Eliminar ${p.name}`} className="size-8 hover:text-red-600" onClick={() => setConfirmDelete(p.id)}>
                            <Trash2 className="size-3.5" />
                          </IconButton>
                        </div>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <ProductDialog
          product={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            void refresh()
            setEditing(null)
          }}
        />
      )}
    </section>
  )
}

function Switch({ checked, disabled, label, onChange }: { checked: boolean; disabled?: boolean; label: string; onChange: () => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onChange}
      className={clsx(
        'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition disabled:cursor-not-allowed disabled:opacity-60',
        checked ? 'bg-emerald-500' : 'bg-slate-300',
      )}
    >
      <span className={clsx('inline-block size-4 rounded-full bg-white shadow transition', checked ? 'translate-x-4.5' : 'translate-x-0.5')} />
    </button>
  )
}

function ProductDialog({ product, onClose, onSaved }: { product: Product | null; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<ProductInput>(() => (product ? { ...product } : { ...EMPTY }))
  const [priceText, setPriceText] = useState(product ? String(product.price) : '')
  const set = <K extends keyof ProductInput>(key: K, value: ProductInput[K]) => setForm((f) => ({ ...f, [key]: value }))

  const save = useMutation({
    mutationFn: () => api.saveProduct({ ...form, price: Number(priceText.replace(',', '.')) }, product?.id),
    onSuccess: (p) => {
      toast.success(product ? `${p.name} actualizado.` : `${p.name} agregado al catálogo.`)
      onSaved()
    },
    onError: (err) => toast.error(err.message),
  })

  function submit(e: FormEvent) {
    e.preventDefault()
    save.mutate()
  }

  const optionalNumber = (v: string) => (v.trim() === '' ? null : Number(v))

  return (
    <div role="dialog" aria-modal className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/40 p-4 sm:items-center" onClick={onClose}>
      <form onSubmit={submit} onClick={(e) => e.stopPropagation()} className="max-h-full w-full max-w-lg overflow-y-auto rounded-2xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
          <h3 className="font-semibold">{product ? 'Editar producto' : 'Nuevo producto'}</h3>
          <IconButton type="button" label="Cerrar" onClick={onClose}>
            <X className="size-4" />
          </IconButton>
        </div>

        <div className="grid grid-cols-2 gap-4 p-5">
          <Input className="col-span-2" label="Nombre" required value={form.name} onChange={(v) => set('name', v)} maxLength={150} autoFocus />
          <Input label="Categoría" value={form.category ?? ''} onChange={(v) => set('category', v || null)} maxLength={80} />
          <Input label="Material" value={form.material ?? ''} onChange={(v) => set('material', v || null)} maxLength={100} />
          <Input label="Precio" required inputMode="decimal" value={priceText} onChange={setPriceText} placeholder="0.00" />
          <Input label="Moneda" value={form.currency} onChange={(v) => set('currency', v.toUpperCase())} maxLength={10} placeholder="USD" />
          <Input label="Entrega (días hábiles)" type="number" min={0} max={365} value={form.deliveryTimeDays ?? ''} onChange={(v) => set('deliveryTimeDays', optionalNumber(v))} />
          <Input label="Garantía (meses)" type="number" min={0} max={120} value={form.warrantyMonths ?? ''} onChange={(v) => set('warrantyMonths', optionalNumber(v))} />
          <label className="col-span-2 block">
            <span className="text-sm font-medium text-slate-700">Detalles para el bot</span>
            <textarea
              rows={3}
              maxLength={4000}
              value={form.description ?? ''}
              onChange={(e) => set('description', e.target.value || null)}
              placeholder="Indicaciones, para quién es, qué incluye…"
              className="mt-1.5 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
            />
          </label>
          <label className="col-span-2 flex items-center gap-2.5 text-sm text-slate-700">
            <Switch checked={form.available} label="Disponible" onChange={() => set('available', !form.available)} />
            Disponible: el bot puede ofrecerlo
          </label>
        </div>

        <div className="flex justify-end gap-2 border-t border-slate-100 bg-slate-50 px-5 py-3">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" size="sm" loading={save.isPending}>
            {product ? 'Guardar' : 'Agregar'}
          </Button>
        </div>
      </form>
    </div>
  )
}

function Input({
  label,
  className,
  onChange,
  ...props
}: Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'> & { label: ReactNode; onChange: (value: string) => void }) {
  return (
    <label className={clsx('block', className)}>
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <input
        {...props}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1.5 h-10 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none focus:border-brand-500 focus:ring-4 focus:ring-brand-100"
      />
    </label>
  )
}
