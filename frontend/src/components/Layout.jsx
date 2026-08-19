import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { Logo, IconHome, IconUsers, IconCalendar, IconReceipt, IconStethoscope, IconLogout, Badge } from './ui';

const roleLabels = { ADMIN: 'Administrator', DOCTOR: 'Doctor', PATIENT: 'Patient' };
const roleBadgeTone = { ADMIN: 'ADMIN', DOCTOR: 'DOCTOR', PATIENT: 'PATIENT' };

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const isStaff = user?.role === 'ADMIN' || user?.role === 'DOCTOR';

  const nav = [
    { to: '/', label: 'Dashboard', icon: IconHome, end: true },
    ...(isStaff ? [{ to: '/patients', label: 'Patients', icon: IconUsers }] : []),
    ...(user?.role === 'ADMIN' ? [{ to: '/doctors', label: 'Doctors', icon: IconStethoscope }] : []),
    { to: '/appointments', label: 'Appointments', icon: IconCalendar },
    { to: '/invoices', label: 'Invoices', icon: IconReceipt },
  ];

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const sidebar = (
    <div className="flex h-full flex-col">
      {/* Premium brand header */}
      <div className="relative overflow-hidden bg-gradient-to-br from-brand-600 via-brand-500 to-cyan-600 px-5 py-6">
        <div className="pointer-events-none absolute -right-8 -top-10 h-32 w-32 rounded-full bg-white/10 blur-2xl" />
        <div className="pointer-events-none absolute -bottom-12 -left-6 h-28 w-28 rounded-full bg-cyan-300/20 blur-xl" />
        <div className="relative flex items-center gap-3">
          <Logo size={38} />
          <div>
            <p className="text-lg font-extrabold tracking-tight text-white">MicroCare</p>
            <p className="text-[11px] font-medium uppercase tracking-widest text-brand-100">Hospital Suite</p>
          </div>
        </div>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4">
        {nav.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-semibold transition-all ${
                isActive
                  ? 'bg-gradient-to-r from-brand-50 to-cyan-50/50 text-brand-700 shadow-sm ring-1 ring-brand-100'
                  : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800'
              }`
            }
          >
            <Icon size={19} className="shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-slate-100 p-4">
        <div className="flex items-center gap-3 rounded-xl bg-gradient-to-r from-slate-50 to-brand-50/40 p-3 ring-1 ring-slate-100">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-500 to-cyan-600 text-sm font-bold text-white shadow-md shadow-brand-500/20">
            {(user?.username || '?')[0].toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-slate-800">{user?.username}</p>
            <Badge tone={roleBadgeTone[user?.role]}>{roleLabels[user?.role] || user?.role}</Badge>
          </div>
          <button
            onClick={handleLogout}
            title="Sign out"
            className="rounded-lg p-2 text-slate-400 transition hover:bg-rose-50 hover:text-rose-600"
          >
            <IconLogout size={18} />
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-slate-100 bg-white lg:block">
        {sidebar}
      </aside>

      {/* Mobile topbar + drawer */}
      <div className="sticky top-0 z-40 flex items-center justify-between bg-gradient-to-r from-brand-600 via-brand-500 to-cyan-600 px-4 py-3 shadow-sm lg:hidden">
        <div className="flex items-center gap-2 text-white">
          <Logo size={30} />
          <span className="font-extrabold">MicroCare</span>
        </div>
        <button
          onClick={() => setMobileOpen((o) => !o)}
          className="rounded-lg p-2 text-white ring-1 ring-white/30"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            {mobileOpen ? <path d="M18 6 6 18M6 6l12 12" /> : <path d="M4 6h16M4 12h16M4 18h16" />}
          </svg>
        </button>
      </div>
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/40" onClick={() => setMobileOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-64 bg-white shadow-pop">{sidebar}</div>
        </div>
      )}

      <main className="lg:pl-64">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
