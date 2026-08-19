import { useEffect, useState } from 'react';

function useCountUp(value, duration = 800) {
  const [display, setDisplay] = useState(0);
  useEffect(() => {
    const target = Number(value) || 0;
    let raf;
    const start = performance.now();
    const step = (now) => {
      const t = Math.min((now - start) / duration, 1);
      setDisplay(Math.round(target * (1 - Math.pow(1 - t, 3))));
      if (t < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [value, duration]);
  return display;
}

/**
 * Premium stat card: gradient icon tile, animated count-up value and a subtle
 * glow that intensifies on hover.
 */
export default function StatCard({ icon: Icon, label, value, sub, gradient }) {
  const display = useCountUp(value);
  return (
    <div className="card group relative overflow-hidden p-5 transition-all duration-200 hover:-translate-y-1 hover:shadow-pop">
      <div className={`absolute -right-8 -top-8 h-28 w-28 rounded-full opacity-10 blur-xl transition-transform duration-300 group-hover:scale-150 ${gradient}`} />
      <div className="flex items-center justify-between">
        <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${gradient} text-white shadow-md shadow-slate-900/10`}>
          <Icon size={20} />
        </span>
        <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
      </div>
      <p className="mt-4 text-3xl font-extrabold tracking-tight text-slate-800 tabular-nums">{display.toLocaleString()}</p>
      <p className="mt-0.5 text-sm font-semibold text-slate-500">{label}</p>
      {sub && <p className="mt-1 text-xs text-slate-400">{sub}</p>}
    </div>
  );
}
