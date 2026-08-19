import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import { useAuth } from '../../auth';
import { usePatientNames } from '../../usePatientNames';
import { PageLoader, Badge, EmptyState, PatientCell, IconCalendar, IconClock, IconUsers, IconStethoscope, IconAlert, IconPlus, IconRefresh, IconCheck, IconX, useToast } from '../../components/ui';
import Hero from './Hero';
import StatCard from './StatCard';

const statusTone = { PENDING: 'PENDING', CONFIRMED: 'CONFIRMED', REJECTED: 'REJECTED', RESCHEDULED: 'RESCHEDULED', CANCELLED: 'CANCELLED' };
const todayISO = () => new Date().toISOString().slice(0, 10);

export default function DoctorDashboard() {
  const { user } = useAuth();
  const doctorId = user?.doctorId;
  const patientNames = usePatientNames();
  const toast = useToast();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async () => {
    try {
      const [appointments, slots] = await Promise.all([
        api.get('/api/appointments', { doctorId }),
        api.get('/api/appointments/slots'),
      ]);
      setData({
        appointments: Array.isArray(appointments) ? appointments : [],
        slots: Array.isArray(slots) ? slots : [],
      });
      setError('');
    } catch (e) {
      setError(e.message);
    }
  }, [doctorId]);

  useEffect(() => {
    load();
  }, [load]);

  if (error && !data) {
    return (
      <div className="card mx-auto mt-16 max-w-md p-8 text-center">
        <IconAlert size={28} className="mx-auto mb-3 text-rose-500" />
        <p className="font-semibold text-slate-700">Couldn’t load your dashboard</p>
        <p className="mt-1 text-sm text-slate-400">{error}</p>
      </div>
    );
  }
  if (!data) return <PageLoader label="Loading your dashboard…" />;

  const { appointments, slots } = data;
  const mySlots = slots.filter((s) => String(s.doctorId) === String(doctorId));
  const slotMap = Object.fromEntries(slots.map((s) => [s.id, s]));
  const activeStatus = (s) => s === 'CONFIRMED' || s === 'RESCHEDULED';

  const dated = appointments
    .filter((a) => activeStatus(a.status) && slotMap[a.slotId])
    .map((a) => ({ ...a, slotDate: slotMap[a.slotId].slotDate, startTime: slotMap[a.slotId].startTime }))
    .sort((a, b) => (a.slotDate + a.startTime).localeCompare(b.slotDate + b.startTime));

  const todayCount = dated.filter((a) => a.slotDate === todayISO()).length;
  const upcoming = dated.filter((a) => a.slotDate >= todayISO()).length;
  const seen = new Set(appointments.filter((a) => activeStatus(a.status)).map((a) => a.patientId)).size;
  const availableSlots = mySlots.filter((s) => s.status === 'AVAILABLE').length;
  const schedule = dated.filter((a) => a.slotDate >= todayISO()).slice(0, 6);
  const requests = appointments.filter((a) => a.status === 'PENDING');

  const decide = async (a, accept) => {
    setBusyId(a.id);
    try {
      await api.post(`/api/appointments/${a.id}/${accept ? 'accept' : 'reject'}`);
      toast.success(`Appointment #${a.id} ${accept ? 'accepted' : 'rejected'}`);
      await load();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="space-y-6">
      <Hero
        eyebrow="Doctor · My practice"
        title={`Dr. ${user?.username}`}
        subtitle="Your schedule at a glance — manage appointments and keep your availability up to date."
        badge={<span className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold ring-1 ring-white/20">Doctor #{doctorId}</span>}
        action={
          <Link to="/appointments" className="btn-primary bg-white !text-brand-700 hover:bg-brand-50">
            <IconCalendar size={16} /> Manage schedule
          </Link>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard icon={IconCalendar} gradient="bg-gradient-to-br from-brand-500 to-emerald-600" label="Today" value={todayCount} sub="appointments today" />
        <StatCard icon={IconClock} gradient="bg-gradient-to-br from-sky-500 to-cyan-600" label="Upcoming" value={upcoming} sub="future appointments" />
        <StatCard icon={IconUsers} gradient="bg-gradient-to-br from-amber-500 to-orange-600" label="Patients seen" value={seen} sub="unique patients" />
        <StatCard icon={IconStethoscope} gradient="bg-gradient-to-br from-violet-500 to-purple-600" label="Open slots" value={availableSlots} sub="available for booking" />
      </div>

      {/* Booking requests — patients are waiting for your decision */}
      {requests.length > 0 && (
        <div className="card overflow-hidden ring-2 ring-amber-100">
          <div className="flex items-center justify-between border-b border-slate-100 bg-amber-50/50 px-6 py-4">
            <h2 className="flex items-center gap-2 font-bold text-slate-800">
              <IconAlert size={18} className="text-amber-500" /> Booking requests
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-700">{requests.length}</span>
            </h2>
            <span className="text-xs font-medium text-amber-600">Accept to confirm &amp; raise an invoice · Reject to free the slot</span>
          </div>
          <ul className="divide-y divide-slate-50">
            {requests.map((a) => {
              const slot = slotMap[a.slotId];
              return (
                <li key={a.id} className="flex flex-wrap items-center gap-3 px-6 py-4">
                  <div className="min-w-0 flex-1">
                    <PatientCell patient={patientNames[a.patientId]} fallbackId={a.patientId} />
                  </div>
                  <div className="text-sm text-slate-500">
                    {slot ? `${slot.slotDate} · ${slot.startTime}–${slot.endTime}` : `Slot #${a.slotId}`}
                  </div>
                  <Badge tone="PENDING">PENDING</Badge>
                  <div className="flex gap-1.5">
                    <button
                      onClick={() => decide(a, true)}
                      disabled={busyId === a.id}
                      className="inline-flex items-center gap-1 rounded-lg bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-100 transition hover:bg-emerald-100"
                    >
                      <IconCheck size={14} /> Accept
                    </button>
                    <button
                      onClick={() => decide(a, false)}
                      disabled={busyId === a.id}
                      className="inline-flex items-center gap-1 rounded-lg bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 ring-1 ring-rose-100 transition hover:bg-rose-100"
                    >
                      <IconX size={14} /> Reject
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="card overflow-hidden xl:col-span-2">
          <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
            <h2 className="font-bold text-slate-800">Upcoming schedule</h2>
            <Link to="/appointments" className="text-sm font-semibold text-brand-600 hover:text-brand-700">View all →</Link>
          </div>
          {schedule.length === 0 ? (
            <EmptyState
              title="No upcoming appointments"
              subtitle="When patients book your slots, they'll appear here."
              action={
                <div className="mt-3 flex gap-2">
                  <Link to="/appointments" className="btn-primary"><IconPlus size={16} /> Create slot</Link>
                  <button onClick={() => { load(); }} className="btn-secondary"><IconRefresh size={15} /> Refresh</button>
                </div>
              }
            />
          ) : (
            <ul className="divide-y divide-slate-50">
              {schedule.map((a) => {
                const isToday = a.slotDate === todayISO();
                return (
                  <li key={a.id} className="flex items-center gap-4 px-6 py-4 transition hover:bg-slate-50/60">
                    <div className={`flex h-14 w-14 shrink-0 flex-col items-center justify-center rounded-2xl ${isToday ? 'bg-brand-50 text-brand-700 ring-1 ring-brand-100' : 'bg-slate-50 text-slate-600 ring-1 ring-slate-100'}`}>
                      <span className="text-sm font-extrabold leading-none">{a.startTime}</span>
                      <span className="mt-0.5 text-[10px] font-semibold uppercase tracking-wide">{isToday ? 'Today' : a.slotDate.slice(5)}</span>
                    </div>
                    <div className="min-w-0 flex-1">
                      <PatientCell patient={patientNames[a.patientId]} fallbackId={a.patientId} />
                    </div>
                    <Badge tone={statusTone[a.status] || a.status}>{a.status}</Badge>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="space-y-6">
          <div className="card p-6">
            <h2 className="flex items-center gap-2 font-bold text-slate-800">
              <IconStethoscope size={18} className="text-brand-600" /> Quick actions
            </h2>
            <div className="mt-4 space-y-3">
              <Link to="/appointments" className="btn-primary w-full"><IconCalendar size={16} /> Book appointment</Link>
              <Link to="/appointments" className="btn-secondary w-full"><IconClock size={16} /> Create a slot</Link>
            </div>
            <p className="mt-4 rounded-xl bg-slate-50 px-4 py-3 text-xs text-slate-500 ring-1 ring-slate-100">
              Slots you create become bookable by patients immediately. Keep your availability current to avoid missed bookings.
            </p>
          </div>

          <div className="card overflow-hidden">
            <div className="border-b border-slate-100 px-6 py-4">
              <h2 className="font-bold text-slate-800">Your slots</h2>
            </div>
            {mySlots.length === 0 ? (
              <div className="px-6 py-8 text-center">
                <p className="text-sm font-medium text-slate-500">No slots created yet.</p>
                <p className="mt-1 text-xs text-slate-400">Create your first slot to start receiving bookings.</p>
              </div>
            ) : (
              <ul className="divide-y divide-slate-50">
                {mySlots.slice(0, 5).map((s) => (
                  <li key={s.id} className="flex items-center justify-between px-6 py-3.5">
                    <div>
                      <p className="text-sm font-semibold text-slate-700">{s.slotDate}</p>
                      <p className="text-xs text-slate-400">{s.startTime} – {s.endTime}</p>
                    </div>
                    <Badge tone={s.status}>{s.status}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
