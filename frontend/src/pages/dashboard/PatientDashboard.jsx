import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api';
import { useAuth } from '../../auth';
import { PageLoader, Badge, EmptyState, PatientCell, Avatar, IconUsers, IconCalendar, IconReceipt, IconDollar, IconAlert, IconStethoscope } from '../../components/ui';
import Hero from './Hero';
import StatCard from './StatCard';

const statusTone = { CONFIRMED: 'CONFIRMED', RESCHEDULED: 'RESCHEDULED', CANCELLED: 'CANCELLED' };

export default function PatientDashboard() {
  const { user } = useAuth();
  const patientId = user?.patientId;
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [diseaseFilter, setDiseaseFilter] = useState('');

  const appointments = Array.isArray(data?.appointments) ? data.appointments : [];
  const invoices = Array.isArray(data?.invoices) ? data.invoices : [];
  const doctors = Array.isArray(data?.doctors) ? data.doctors : [];
  const patient = Array.isArray(data?.patient) ? data.patient[0] : null;

  // Disease classification: unique diseases across all doctors + filtered view.
  const allDiseases = [...new Set(doctors.flatMap((d) => d.diseases || []))].sort();
  const filteredDoctors = diseaseFilter
    ? doctors.filter((d) => (d.diseases || []).some((dis) => dis === diseaseFilter))
    : doctors;
  const totalInvoice = invoices.reduce((s, i) => s + (i.amount || 0), 0);
  const outstanding = invoices.filter((i) => i.status === 'PENDING').reduce((s, i) => s + (i.amount || 0), 0);
  const recent = appointments.slice(0, 6);
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const [patient, appointments, invoices, doctors] = await Promise.all([
          patientId ? api.get(`/api/patients/${patientId}`).then((p) => [p]) : Promise.resolve([]),
          api.get('/api/appointments', { patientId }),
          api.get('/api/invoices', { patientId }),
          api.get('/api/doctors'),
        ]);
        if (active) setData({ patient, appointments, invoices, doctors });
      } catch (e) {
        if (active) setError(e.message);
      }
    })();
    return () => {
      active = false;
    };
  }, [patientId]);

  if (error) {
    return (
      <div className="card mx-auto mt-16 max-w-md p-8 text-center">
        <IconAlert size={28} className="mx-auto mb-3 text-rose-500" />
        <p className="font-semibold text-slate-700">Couldn’t load your dashboard</p>
        <p className="mt-1 text-sm text-slate-400">{error}</p>
      </div>
    );
  }

  if (!data) return <PageLoader label="Loading your dashboard…" />;

  return (
    <div className="space-y-6">
      <Hero
        eyebrow="Patient · My care"
        title={`${greeting}, ${user?.username}`}
        subtitle="Here's a snapshot of your care — appointments, invoices and your patient record."
        badge={patient ? <span className="rounded-full bg-white/15 px-3 py-1 text-xs font-semibold ring-1 ring-white/20">Patient #{patient.id}</span> : undefined}
        action={<Link to="/appointments" className="btn-primary bg-white !text-brand-700 hover:bg-brand-50"><IconCalendar size={17} /> Book appointment</Link>}
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard icon={IconUsers} gradient="bg-gradient-to-br from-sky-500 to-cyan-600" label="Your patient" value="1" sub={patient?.fullName || 'linked patient record'} />
        <StatCard icon={IconCalendar} gradient="bg-gradient-to-br from-brand-500 to-emerald-600" label="Appointments" value={appointments.length} sub={`${appointments.filter((a) => a.status === 'CONFIRMED').length} active`} />
        <StatCard icon={IconReceipt} gradient="bg-gradient-to-br from-amber-500 to-orange-600" label="Invoices" value={invoices.length} sub={`${invoices.filter((i) => i.status === 'PENDING').length} pending`} />
        <StatCard icon={IconDollar} gradient="bg-gradient-to-br from-violet-500 to-purple-600" label="Billed" value={`$${totalInvoice.toFixed(0)}`} sub={`$${outstanding.toFixed(0)} outstanding`} />
      </div>

      {/* Doctors directory — classified by the diseases they treat */}
      {doctors.length > 0 && (
        <div className="card overflow-hidden">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-6 py-4">
            <h2 className="font-bold text-slate-800">Find a doctor by disease</h2>
            <div className="flex items-center gap-3">
              <span className="text-sm font-semibold text-slate-400">{filteredDoctors.length} of {doctors.length} available</span>
              <select
                className="input !w-auto !py-1.5 text-sm"
                value={diseaseFilter}
                onChange={(e) => setDiseaseFilter(e.target.value)}
              >
                <option value="">All diseases</option>
                {allDiseases.map((d) => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
            </div>
          </div>
          {filteredDoctors.length === 0 ? (
            <div className="px-6 py-12 text-center">
              <p className="text-sm font-medium text-slate-500">No doctors treat “{diseaseFilter}” yet.</p>
              <p className="mt-1 text-xs text-slate-400">Try another disease or clear the filter.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-2 xl:grid-cols-3">
              {filteredDoctors.map((d) => (
                <div key={d.id} className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-100 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-pop">
                  <div className="flex items-start gap-3">
                    <Avatar name={d.fullName || d.username} size="h-12 w-12 text-sm" />
                    <div className="min-w-0">
                      <p className="truncate font-bold text-slate-800">{d.fullName || d.username}</p>
                      <p className="truncate text-xs text-slate-400">@{d.username} · {d.specialization || 'General medicine'}</p>
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {(d.diseases || []).map((dis) => (
                      <span key={dis} className="inline-flex items-center gap-1 rounded-full bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 ring-1 ring-brand-100">
                        <IconStethoscope size={12} /> {dis}
                      </span>
                    ))}
                  </div>
                  <Link to={`/appointments?doctorId=${d.doctorId}`} className="btn-primary w-full justify-center">
                    <IconCalendar size={16} /> Book appointment
                  </Link>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div className="card overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <h2 className="font-bold text-slate-800">Recent appointments</h2>
          <Link to="/appointments" className="text-sm font-semibold text-brand-600 hover:text-brand-700">View all →</Link>
        </div>
        {recent.length === 0 ? (
          <EmptyState
            title="No appointments yet"
            subtitle="Book your first appointment to get started."
            action={<Link to="/appointments" className="btn-primary mt-3">Book appointment</Link>}
          />
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
                    <td className="td"><PatientCell patient={patient} fallbackId={a.patientId} /></td>
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
  );
}
