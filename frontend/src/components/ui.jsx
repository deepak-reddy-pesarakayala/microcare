import { createContext, useContext, useState, useCallback } from 'react';

/* ------------------------------ Icons ------------------------------ */
const I = (path, viewBox = '0 0 24 24') => ({ size = 20, className = '' }) => (
  <svg width={size} height={size} viewBox={viewBox} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    {path}
  </svg>
);

export const IconHome = I(<><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /></>);
export const IconUsers = I(<><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></>);
export const IconCalendar = I(<><rect x="3" y="4" width="18" height="18" rx="2" /><path d="M16 2v4M8 2v4M3 10h18" /></>);
export const IconReceipt = I(<><path d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z" /><path d="M8 7h8M8 11h8M8 15h5" /></>);
export const IconLogout = I(<><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="M16 17l5-5-5-5" /><path d="M21 12H9" /></>);
export const IconPlus = I(<><path d="M12 5v14M5 12h14" /></>);
export const IconSearch = I(<><circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" /></>);
export const IconX = I(<><path d="M18 6 6 18M6 6l12 12" /></>);
export const IconPencil = I(<><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /></>);
export const IconTrash = I(<><path d="M3 6h18" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /></>);
export const IconCheck = I(<><path d="M20 6 9 17l-5-5" /></>);
export const IconClock = I(<><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></>);
export const IconAlert = I(<><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" /><path d="M12 9v4M12 17h.01" /></>);
export const IconStethoscope = I(<><path d="M4.8 2.3A.3.3 0 1 0 5 2H4a2 2 0 0 0-2 2v5a6 6 0 0 0 6 6 6 6 0 0 0 6-6V4a2 2 0 0 0-2-2h-1a.2.2 0 1 0 .3.3" /><path d="M8 15v1a6 6 0 0 0 6 6 6 6 0 0 0 6-6v-4" /><circle cx="20" cy="10" r="2" /></>);
export const IconDollar = I(<><path d="M12 1v22" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" /></>);
export const IconShield = I(<><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1Z" /><path d="m9 12 2 2 4-4" /></>);
export const IconRefresh = I(<><path d="M3 12a9 9 0 0 1 15-6.7L21 8" /><path d="M21 3v5h-5" /><path d="M21 12a9 9 0 0 1-15 6.7L3 16" /><path d="M3 21v-5h5" /></>);

export function Logo({ size = 36, className = '' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" className={className}>
      <rect width="32" height="32" rx="9" fill="url(#mc-grad)" />
      <defs>
        <linearGradient id="mc-grad" x1="0" y1="0" x2="32" y2="32">
          <stop offset="0%" stopColor="#14b8a6" />
          <stop offset="100%" stopColor="#0e7490" />
        </linearGradient>
      </defs>
      <rect x="13" y="7" width="6" height="18" rx="1.5" fill="#fff" />
      <rect x="7" y="13" width="18" height="6" rx="1.5" fill="#fff" />
    </svg>
  );
}

/* ------------------------------ Toasts ------------------------------ */
const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const push = useCallback((type, message) => {
    const id = Math.random().toString(36).slice(2);
    setToasts((t) => [...t, { id, type, message }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200);
  }, []);

  const toast = {
    success: (m) => push('success', m),
    error: (m) => push('error', m),
    info: (m) => push('info', m),
  };

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="pointer-events-none fixed right-4 top-4 z-[100] flex w-80 flex-col gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className={`animate-toast-in pointer-events-auto flex items-start gap-3 rounded-xl px-4 py-3 shadow-pop ring-1 backdrop-blur ${
              t.type === 'success'
                ? 'bg-emerald-50/95 ring-emerald-200'
                : t.type === 'error'
                  ? 'bg-rose-50/95 ring-rose-200'
                  : 'bg-sky-50/95 ring-sky-200'
            }`}
          >
            <span
              className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-white ${
                t.type === 'success' ? 'bg-emerald-500' : t.type === 'error' ? 'bg-rose-500' : 'bg-sky-500'
              }`}
            >
              {t.type === 'success' ? <IconCheck size={14} /> : t.type === 'error' ? <IconX size={14} /> : <IconAlert size={14} />}
            </span>
            <p className="text-sm font-medium text-slate-700">{t.message}</p>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

/* ------------------------------ Badge ------------------------------ */
const badgeStyles = {
  CONFIRMED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 ring-amber-200',
  PAID: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  CANCELLED: 'bg-rose-50 text-rose-700 ring-rose-200',
  REJECTED: 'bg-rose-50 text-rose-700 ring-rose-200',
  RESCHEDULED: 'bg-violet-50 text-violet-700 ring-violet-200',
  AVAILABLE: 'bg-sky-50 text-sky-700 ring-sky-200',
  BOOKED: 'bg-slate-100 text-slate-600 ring-slate-200',
  ADMIN: 'bg-violet-50 text-violet-700 ring-violet-200',
  DOCTOR: 'bg-sky-50 text-sky-700 ring-sky-200',
  PATIENT: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
};

export function Badge({ children, tone }) {
  const cls = badgeStyles[tone] || 'bg-slate-100 text-slate-600 ring-slate-200';
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ${cls}`}>
      {children}
    </span>
  );
}

/* ------------------------------ Modal ------------------------------ */
export function Modal({ open, onClose, title, children, wide }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="animate-fade-in absolute inset-0 bg-slate-900/50 backdrop-blur-sm" onClick={onClose} />
      <div
        className={`animate-slide-up relative max-h-[90vh] w-full overflow-y-auto rounded-2xl bg-white p-6 shadow-pop ${
          wide ? 'max-w-2xl' : 'max-w-md'
        }`}
      >
        <div className="mb-5 flex items-center justify-between">
          <h3 className="text-lg font-bold text-slate-800">{title}</h3>
          <button onClick={onClose} className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-100 hover:text-slate-600">
            <IconX size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

/* ------------------------------ Spinner / states ------------------------------ */
export function Spinner({ className = 'h-5 w-5' }) {
  return (
    <svg className={`animate-spin text-brand-600 ${className}`} viewBox="0 0 24 24" fill="none">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v4a4 4 0 0 0-4 4H4z" />
    </svg>
  );
}

export function PageLoader({ label = 'Loading…' }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-24 text-slate-400">
      <Spinner className="h-8 w-8" />
      <p className="text-sm font-medium">{label}</p>
    </div>
  );
}

export function EmptyState({ title, subtitle, action }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
      <div className="mb-2 flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
        <IconCalendar size={26} />
      </div>
      <h3 className="text-base font-semibold text-slate-700">{title}</h3>
      {subtitle && <p className="max-w-sm text-sm text-slate-400">{subtitle}</p>}
      {action}
    </div>
  );
}

/* ------------------------------ Avatar / patient cell ------------------------------ */
const avatarPalettes = [
  'from-sky-500 to-cyan-600',
  'from-brand-500 to-emerald-600',
  'from-violet-500 to-purple-600',
  'from-amber-500 to-orange-600',
  'from-rose-500 to-pink-600',
];

export function Avatar({ name, size = 'h-9 w-9 text-xs', className = '' }) {
  const initials =
    String(name || '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0].toUpperCase())
      .join('') || '?';
  const idx = [...String(name || '')].reduce((s, c) => s + c.charCodeAt(0), 0) % avatarPalettes.length;
  return (
    <span
      className={`inline-flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br font-bold text-white shadow-sm ${size} ${avatarPalettes[idx]} ${className}`}
      title={name}
    >
      {initials}
    </span>
  );
}

export function PatientCell({ patient, fallbackId }) {
  if (!patient) {
    return <span className="font-medium text-slate-400">#{fallbackId ?? '—'}</span>;
  }
  return (
    <div className="flex items-center gap-3">
      <Avatar name={patient.fullName} />
      <div className="min-w-0">
        <p className="truncate font-semibold text-slate-800">{patient.fullName}</p>
        <p className="truncate text-xs text-slate-400">#{patient.id}{patient.email ? ` · ${patient.email}` : ''}</p>
      </div>
    </div>
  );
}
