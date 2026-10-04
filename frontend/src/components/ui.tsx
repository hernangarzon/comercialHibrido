import clsx from 'clsx'
import { Flame } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import type { ConversationStatus } from '../lib/api'
import { STATUS_LABEL, avatarColor, initials, scoreLevel } from '../lib/format'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 shadow-sm',
  secondary: 'bg-white text-slate-700 ring-1 ring-inset ring-slate-200 hover:bg-slate-50 shadow-sm',
  ghost: 'text-slate-600 hover:bg-slate-100',
  danger: 'bg-white text-red-600 ring-1 ring-inset ring-red-200 hover:bg-red-50',
  success: 'bg-emerald-600 text-white hover:bg-emerald-700 shadow-sm',
}

export function Button({
  variant = 'primary',
  size = 'md',
  className,
  loading,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: 'sm' | 'md'; loading?: boolean }) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={clsx(
        'inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500',
        'disabled:cursor-not-allowed disabled:opacity-60',
        size === 'sm' ? 'h-8 px-3 text-xs' : 'h-10 px-4 text-sm',
        VARIANTS[variant],
        className,
      )}
    >
      {loading && <span className="size-3.5 animate-spin rounded-full border-2 border-current border-r-transparent" />}
      {children}
    </button>
  )
}

export function IconButton({
  label,
  className,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      {...props}
      aria-label={label}
      title={label}
      className={clsx(
        'inline-flex size-9 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-800',
        'focus-visible:outline-2 focus-visible:outline-brand-500',
        className,
      )}
    >
      {children}
    </button>
  )
}

export function Avatar({ name, seed, size = 'md' }: { name: string; seed: string; size?: 'sm' | 'md' | 'lg' }) {
  return (
    <div
      aria-hidden
      className={clsx(
        'flex shrink-0 items-center justify-center rounded-full font-semibold',
        avatarColor(seed),
        size === 'sm' && 'size-8 text-xs',
        size === 'md' && 'size-10 text-sm',
        size === 'lg' && 'size-16 text-xl',
      )}
    >
      {initials(name)}
    </div>
  )
}

const STATUS_STYLE: Record<ConversationStatus, string> = {
  ESCALADO_PENDIENTE: 'bg-orange-50 text-orange-700 ring-orange-200',
  HUMANO_CONTROL: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  BOT_ACTIVO: 'bg-brand-50 text-brand-700 ring-brand-200',
  ARCHIVADO: 'bg-slate-100 text-slate-600 ring-slate-200',
}

export function StatusBadge({ status }: { status: ConversationStatus }) {
  return (
    <span className={clsx('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', STATUS_STYLE[status])}>
      {STATUS_LABEL[status]}
    </span>
  )
}

export function ScoreBadge({ score, compact }: { score: number; compact?: boolean }) {
  const level = scoreLevel(score)
  return (
    <span
      title={`Lead score ${score}/100 · ${level.label}`}
      className={clsx('inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-[11px] font-semibold ring-1 ring-inset tabular-nums', level.className)}
    >
      {score >= 70 && <Flame className="size-3" />}
      {score}
      {!compact && <span className="font-medium">· {level.label}</span>}
    </span>
  )
}

export function EmptyState({ icon, title, description, action }: { icon: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
      <div className="flex size-12 items-center justify-center rounded-2xl bg-white text-slate-400 shadow-sm ring-1 ring-slate-200">{icon}</div>
      <div>
        <p className="font-semibold text-slate-800">{title}</p>
        {description && <p className="mt-1 max-w-xs text-sm text-slate-500">{description}</p>}
      </div>
      {action}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded-md bg-slate-200/70', className)} />
}
