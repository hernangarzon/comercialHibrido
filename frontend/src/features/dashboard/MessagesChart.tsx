import clsx from 'clsx'
import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { DayPoint } from '../../lib/api'

// Paleta categórica validada (CVD y contraste) para 3 series; el color sigue a la
// entidad, nunca al orden. Aqua queda bajo 3:1 de contraste, por eso hay vista de tabla.
export const SERIES = [
  { key: 'client', label: 'Cliente', color: '#eb6834' },
  { key: 'bot', label: 'Bot', color: '#2a78d6' },
  { key: 'agent', label: 'Asesor', color: '#1baf7a' },
] as const

type SeriesKey = (typeof SERIES)[number]['key']

const HEIGHT = 240
const PAD = { top: 12, right: 8, bottom: 28, left: 36 }
const GAP = 2 // separación de superficie entre segmentos apilados
const MAX_BAR = 24

/** Paso redondo (1, 2, 5 × 10ⁿ) para que 4 divisiones cubran el máximo con marcas limpias. */
function niceStep(max: number): number {
  const raw = Math.max(1, max) / 4
  const pow = 10 ** Math.floor(Math.log10(raw))
  return ([1, 2, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow)
}

function shortDate(iso: string, withWeekday: boolean) {
  const d = new Date(`${iso}T12:00:00`)
  return d.toLocaleDateString('es', withWeekday ? { weekday: 'short', day: 'numeric', month: 'short' } : { day: 'numeric', month: 'short' })
}

/** Barra con la punta redondeada (4px) y la base recta, creciendo desde abajo. */
function barPath(x: number, y: number, w: number, h: number, roundTop: boolean) {
  if (h <= 0) return ''
  const r = roundTop ? Math.min(4, w / 2, h) : 0
  return `M${x},${y + h}V${y + r}${r ? `Q${x},${y} ${x + r},${y}` : ''}H${x + w - r}${r ? `Q${x + w},${y} ${x + w},${y + r}` : ''}V${y + h}Z`
}

export function MessagesChart({ series }: { series: DayPoint[] }) {
  const container = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const [hover, setHover] = useState<number | null>(null)
  const [showTable, setShowTable] = useState(false)

  useLayoutEffect(() => {
    const el = container.current
    if (!el) return
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(240, Math.floor(entry!.contentRect.width))))
    observer.observe(el)
    return () => observer.disconnect()
  }, [showTable])

  const step = useMemo(() => niceStep(Math.max(0, ...series.map((d) => d.client + d.bot + d.agent))), [series])
  const max = step * 4
  const ticks = [0, step, step * 2, step * 3, max]
  const plotW = width - PAD.left - PAD.right
  const plotH = HEIGHT - PAD.top - PAD.bottom
  const band = plotW / Math.max(1, series.length)
  const barW = Math.max(3, Math.min(MAX_BAR, band * 0.62))
  const y = (v: number) => PAD.top + plotH - (v / max) * plotH
  const labelEvery = Math.ceil(series.length / Math.max(2, Math.floor(plotW / 64)))
  const total = series.reduce((sum, d) => sum + d.client + d.bot + d.agent, 0)
  const hovered = hover != null ? series[hover] : null

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <ul className="flex flex-wrap items-center gap-4 text-xs text-slate-600" aria-label="Leyenda">
          {SERIES.map((s) => (
            <li key={s.key} className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-sm" style={{ background: s.color }} />
              {s.label}
            </li>
          ))}
        </ul>
        <button onClick={() => setShowTable((v) => !v)} className="text-xs font-medium text-brand-600 hover:underline">
          {showTable ? 'Ver gráfico' : 'Ver tabla'}
        </button>
      </div>

      {showTable ? (
        <div className="scrollbar-thin max-h-64 overflow-auto rounded-lg ring-1 ring-slate-200">
          <table className="w-full text-sm tabular-nums">
            <thead className="sticky top-0 bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="px-3 py-2 text-left font-medium">Día</th>
                {SERIES.map((s) => (
                  <th key={s.key} className="px-3 py-2 text-right font-medium">{s.label}</th>
                ))}
                <th className="px-3 py-2 text-right font-medium">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {[...series].reverse().map((d) => (
                <tr key={d.date}>
                  <td className="px-3 py-1.5 text-slate-600">{shortDate(d.date, true)}</td>
                  <td className="px-3 py-1.5 text-right">{d.client}</td>
                  <td className="px-3 py-1.5 text-right">{d.bot}</td>
                  <td className="px-3 py-1.5 text-right">{d.agent}</td>
                  <td className="px-3 py-1.5 text-right font-medium">{d.client + d.bot + d.agent}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div ref={container} className="relative w-full min-w-0" style={{ height: HEIGHT }} onMouseLeave={() => setHover(null)}>
          {width > 0 && <svg width={width} height={HEIGHT} role="img" aria-label={`Mensajes por día: ${total} en el período`} className="block">
            {ticks.map((t) => (
              <g key={t}>
                <line x1={PAD.left} x2={width - PAD.right} y1={y(t)} y2={y(t)} stroke="#e2e8f0" strokeWidth={1} />
                <text x={PAD.left - 8} y={y(t)} dy="0.32em" textAnchor="end" className="fill-slate-400 text-[10px] tabular-nums">
                  {Math.round(t).toLocaleString('es')}
                </text>
              </g>
            ))}

            {series.map((d, i) => {
              const x = PAD.left + i * band + (band - barW) / 2
              let base = 0
              const stack = SERIES.map((s) => ({ ...s, value: d[s.key as SeriesKey] })).filter((s) => s.value > 0)
              return (
                <g key={d.date} opacity={hover == null || hover === i ? 1 : 0.45}>
                  {stack.map((s, j) => {
                    const top = y(base + s.value)
                    const bottom = y(base)
                    base += s.value
                    // El hueco de 2px separa segmentos sin dibujar bordes.
                    const h = bottom - top - (j > 0 ? GAP : 0)
                    return <path key={s.key} d={barPath(x, top, barW, h, j === stack.length - 1)} fill={s.color} />
                  })}
                  {/* Zona de hover más grande que la barra */}
                  <rect x={PAD.left + i * band} y={PAD.top} width={band} height={plotH} fill="transparent" onMouseEnter={() => setHover(i)} />
                  {i % labelEvery === 0 && (
                    <text x={x + barW / 2} y={HEIGHT - 8} textAnchor="middle" className="fill-slate-400 text-[10px]">
                      {shortDate(d.date, false)}
                    </text>
                  )}
                </g>
              )
            })}
          </svg>}

          {hovered && hover != null && (
            <div
              className={clsx(
                'pointer-events-none absolute top-2 z-10 w-44 rounded-lg bg-white p-3 text-xs shadow-lg ring-1 ring-slate-200',
                PAD.left + hover * band > width / 2 ? '-translate-x-full' : '',
              )}
              style={{ left: PAD.left + hover * band + band / 2 + (PAD.left + hover * band > width / 2 ? -12 : 12) }}
            >
              <p className="mb-1.5 font-semibold text-slate-800 first-letter:uppercase">{shortDate(hovered.date, true)}</p>
              {SERIES.map((s) => (
                <p key={s.key} className="flex items-center justify-between gap-3 py-0.5 text-slate-600">
                  <span className="flex items-center gap-1.5">
                    <span className="size-2 rounded-sm" style={{ background: s.color }} />
                    {s.label}
                  </span>
                  <span className="font-medium text-slate-800 tabular-nums">{hovered[s.key as SeriesKey]}</span>
                </p>
              ))}
              <p className="mt-1 flex justify-between border-t border-slate-100 pt-1 font-medium text-slate-800">
                Total <span className="tabular-nums">{hovered.client + hovered.bot + hovered.agent}</span>
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
