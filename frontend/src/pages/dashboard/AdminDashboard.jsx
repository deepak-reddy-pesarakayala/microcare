import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import { useAuth } from '../../auth';
import { usePatientNames } from '../../usePatientNames';
import { PageLoader, Badge, EmptyState, PatientCell, Avatar, IconUsers, IconCalendar, IconReceipt, IconDollar, IconAlert, IconCheck, IconX, IconStethoscope, useToast } from '../../components/ui';
import Hero from './Hero';
import StatCard from './StatCard';

const statusTone = { CONFIRMED: 'CONFIRMED', RESCHEDULED: 'RESCHEDULED', CANCELLED: 'CANCELLED' };

export default function AdminDashboard() {
  const { user } = useAuth();
  const toast = useToast();
  const patientNames = usePatientNames();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    try {
      const [patients, appointments, invoices, doctors] = await Promise.all([
        api.get('/api/patients'),
        api.get('/api/appointments'),
        api.get('/api/invoices'),
        api.get('/api/admin/doctors'),
      ]);
      setData({
        patients: Array.isArray(patients) ? patients : [],
        appointments: Array.isArray(appointments) ? appointments : [],
        invoices: Array.isArray(invoices) ? invoices : [],
        doctors: Array.isArray(doctors) ? doctors : [],
      });
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const decide = async (doctor, approve) => {
    setBusyId(doctor.id);
    try {
      await api.post(`/api/admin/doctors/${doctor.id}/${approve ? 'approve' : 'reject'}`);
      toast.success(`${approve ? 'Approved' : 'Rejected'} ${doctor.fullName || doctor.username}`);
      await load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusyId(null);
    }
  };

  if (error && !data) {
    return (
      <div className="card mx-auto mt-16 max-w-md p-8 text-center">
        <IconAlert size={28} className="mx-auto mb-3 text-rose-500" />
        <p className="font-semibold text-slate-700">Couldn’t load the admin dashboard</p>
        <p className="mt-1 text-sm text-slate-400">{error}</p>
      </div>
    );
  }
  if (!data) return <PageLoader label="Loading admin dashboard…" />;

  const { patients, appointments, invoices, doctors } = data;
  const pendingDoctors = doctors.filter((d) => d.status === 'PENDING');
  const totalInvoice = invoices.reduce((s, i) => s + (i.amount || 0), 0);
  const outstanding = invoices.filter((i) => i.status === 'PENDING').reduce((s, i) => s + (i.amount || 0), 0);
  const recent = appointments.slice(0, 6);
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

  return (
    <div className="space-y-6">
      <Hero
        eyebrow="Administrator · Overview"
        title={`${greeting}, ${user?.username}`}
        subtitle="Full visibility across your practice — patients, appointments, billing and staff."
        badge={<span className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold ring-1 ring-white/20">{doctors.length} doctors</span>}
        action={<Link to="/doctors" className="btn-primary bg-white !text-brand-700 hover:bg-brand-50">Manage doctors</Link>}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard icon={IconUsers} gradient="bg-gradient-to-br from-sky-500 to-cyan-600" label="Patients" value={patients.length} sub="registered records" />
        <StatCard icon={IconCalendar} gradient="bg-gradient-to-br from-brand-500 to-emerald-600" label="Appointments" value={appointments.length} sub={`${appointments.filter((a) => a.status === 'CONFIRMED' || a.status === 'RESCHEDULED').length} active`} />
        <StatCard icon={IconReceipt} gradient="bg-gradient-to-br from-amber-500 to-orange-600" label="Invoices" value={invoices.length} sub={`${invoices.filter((i) => i.status === 'PENDING').length} pending`} />
        <StatCard icon={IconDollar} gradient="bg-gradient-to-br from-violet-500 to-purple-600" label="Billed" value={`$${totalInvoice.toFixed(0)}`} sub={`$${outstanding.toFixed(0)} outstanding`} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        {/* Pending doctor approvals */}
        <div className="card overflow-hidden xl:col-span-1">
          <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
            <h2 className="flex items-center gap-2 font-bold text-slate-800">
              <IconStethoscope size={18} className="text-brand-600" />
              Doctor approvals
              {pendingDoctors.length > 0 && (
                <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-700">{pendingDoctors.length}</span>
              )}
            </h2>
            <Link to="/doctors" className="text-sm font-semibold text-brand-600 hover:text-brand-700">All doctors →</Link>
          </div>
          {pendingDoctors.length === 0 ? (
            <div className="px-6 py-8 text-center">
              <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-500">
                <IconCheck size={22} />
              </div>
              <p className="text-sm font-semibold text-slate-700">All caught up</p>
              <p className="mt-1 text-xs text-slate-400">No doctor applications awaiting review.</p>
            </div>
          ) : (
            <ul className="divide-y divide-slate-50">
              {pendingDoctors.map((d) => (
                <li key={d.id} className="flex items-center gap-3 px-6 py-4">
                  <Avatar name={d.fullName || d.username} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-slate-800">{d.fullName || d.username}</p>
                    <p className="truncate text-xs text-slate-400">
                      {d.specialization || 'Doctor'} · {d.email}
                    </p>
                  </div>
                  <div className="flex shrink-0 gap-1.5">
                    <button
                      onClick={() => decide(d, true)}
                      disabled={busyId === d.id}
                      title="Approve"
                      className="rounded-lg bg-emerald-50 p-2 text-emerald-600 ring-1 ring-emerald-100 transition hover:bg-emerald-100"
                    >
                      <IconCheck size={16} />
                    </button>
                    <button
                      onClick={() => decide(d, false)}
                      disabled={busyId === d.id}
                      title="Reject"
                      className="rounded-lg bg-rose-50 p-2 text-rose-600 ring-1 ring-rose-100 transition hover:bg-rose-100"
                    >
                      <IconX size={16} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Recent appointments */}
        <div className="card overflow-hidden xl:col-span-2">
          <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
            <h2 className="font-bold text-slate-800">Recent appointments</h2>
            <Link to="/appointments" className="text-sm font-semibold text-brand-600 hover:text-brand-700">View all →</Link>
          </div>
          {recent.length === 0 ? (
            <EmptyState title="No appointments yet" subtitle="Book the first appointment to get started." />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50/60">
                    <th className="th">#</th>
                    <th className="th">Patient</th>
                    <th className="th">Doctor</th>
                    <th className="th">Status</th>
                    <th className="th">Booked</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {recent.map((a) => (
                    <tr key={a.id} className="transition hover:bg-slate-50/60">
                      <td className="td font-semibold text-slate-500">#{a.id}</td>
                      <td className="td"><PatientCell patient={patientNames[a.patientId]} fallbackId={a.patientId} /></td>
                      <td className="td">
                        <span className="inline-flex items-center gap-1.5 font-medium text-slate-600">
                          <IconStethoscope size={15} className="text-slate-400" /> Doctor #{a.doctorId}
                        </span>
                      </td>
                      <td className="td"><Badge tone={statusTone[a.status] || a.status}>{a.status}</Badge></td>
                      <td className="td text-slate-400">{new Date(a.createdAt).toLocaleDateString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
