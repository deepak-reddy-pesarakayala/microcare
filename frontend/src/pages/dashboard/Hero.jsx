import { Logo } from '../../components/ui';

/**
 * Premium gradient hero banner shown at the top of every dashboard.
 * Renders a greeting, subtitle, optional badge and right-aligned action.
 */
export default function Hero({ eyebrow, title, subtitle, badge, action, gradient = 'from-brand-600 via-brand-500 to-cyan-600' }) {
  return (
    <div className={`relative overflow-hidden rounded-3xl bg-gradient-to-br ${gradient} p-6 text-white shadow-pop sm:p-8`}>
      {/* Decorative layers */}
      <div className="pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
      <div className="pointer-events-none absolute -bottom-24 right-32 h-52 w-52 rounded-full bg-cyan-300/20 blur-2xl" />
      <div className="pointer-events-none absolute -left-10 top-1/2 h-40 w-40 rounded-full bg-white/5 blur-xl" />
      <div className="pointer-events-none absolute right-8 top-8 hidden h-24 w-24 opacity-20 lg:block">
        <Logo size={96} />
      </div>

      <div className="relative flex flex-wrap items-end justify-between gap-4">
        <div className="max-w-xl">
          {eyebrow && (
            <p className="mb-2 inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-[11px] font-bold uppercase tracking-widest text-brand-50 ring-1 ring-white/20 backdrop-blur">
              {eyebrow}
            </p>
          )}
          <h1 className="text-2xl font-extrabold tracking-tight sm:text-3xl">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-brand-50/90 sm:text-base">{subtitle}</p>}
        </div>
        <div className="flex items-center gap-3">
          {badge}
          {action}
        </div>
      </div>
    </div>
  );
}
