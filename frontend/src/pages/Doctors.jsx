import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { PageLoader, Badge, Avatar, EmptyState, IconSearch, IconCheck, IconX, IconRefresh, IconAlert, IconStethoscope, useToast } from '../components/ui';

const toneByStatus = { APPROVED: 'CONFIRMED', PENDING: 'PENDING', REJECTED: 'CANCELLED' };
const FILTERS = ['ALL', 'PENDING', 'APPROVED', 'REJECTED'];

export default function Doctors() {
  const toast = useToast();
  const [doctors, setDoctors] = useState(null);
  const [filter, setFilter] = useState('ALL');
  const [query, setQuery] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = async () => {
    try {
      setDoctors(await api.get('/api/admin/doctors'));
    } catch (e) {
      toast.error(e.message);
      setDoctors([]);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  const filtered = useMemo(() => {
    if (!doctors) return [];
    const q = query.toLowerCase();
    return doctors.filter((d) => {
      if (filter !== 'ALL' && d.status !== filter) return false;
      if (!q) return true;
      return [d.fullName, d.username, d.email, d.specialization, d.licenseNumber]
        .some((v) => (v || '').toLowerCase().includes(q));
    });
  }, [doctors, filter, query]);

  if (!doctors) return <PageLoader label="Loading doctors…" />;

  const pendingCount = doctors.filter((d) => d.status === 'PENDING').length;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-800">Doctors</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            {doctors.length} accounts · {pendingCount} pending approval
          </p>
        </div>
        <button onClick={() => { toast.info('Refreshing…'); load(); }} className="btn-secondary">
          <IconRefresh size={15} /> Refresh
        </button>
      </div>

      {pendingCount > 0 && (
        <div className="flex items-start gap-2.5 rounded-2xl bg-amber-50 px-5 py-4 text-sm text-amber-800 ring-1 ring-amber-100">
          <IconAlert size={18} className="mt-0.5 shrink-0" />
          <p>
            <b>{pendingCount} doctor application{pendingCount === 1 ? '' : 's'}</b> awaiting review.
            Approve to activate the account and issue their doctor ID, or reject to block sign-in.
          </p>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative max-w-sm flex-1">
          <IconSearch size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            className="input pl-10"
            placeholder="Search by name, username, email, specialization…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div className="flex gap-1.5 rounded-xl bg-slate-100 p-1">
          {FILTERS.map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                filter === f ? 'bg-white text-slate-800 shadow-sm ring-1 ring-slate-200' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {f === 'ALL' ? 'All' : f[0] + f.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
      </div>

      <div className="card overflow-hidden">
        {filtered.length === 0 ? (
          <EmptyState
            title={query || filter !== 'ALL' ? 'No matches' : 'No doctors yet'}
            subtitle={query || filter !== 'ALL' ? 'Try a different search or filter.' : 'Doctors appear here after they register.'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60">
                  <th className="th">Doctor</th>
                  <th className="th">Specialization</th>
                  <th className="th">Doctor ID</th>
                  <th className="th">Applied</th>
                  <th className="th">Status</th>
                  <th className="th text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {filtered.map((d) => (
                  <tr key={d.id} className="transition hover:bg-slate-50/60">
                    <td className="td">
                      <div className="flex items-center gap-3">
                        <Avatar name={d.fullName || d.username} />
                        <div className="min-w-0">
                          <p className="truncate font-semibold text-slate-800">{d.fullName || d.username}</p>
                          <p className="truncate text-xs text-slate-400">@{d.username}{d.email ? ` · ${d.email}` : ''}</p>
                          {d.licenseNumber && <p className="truncate text-xs text-slate-400">Licence {d.licenseNumber}</p>}
                        </div>
                      </div>
                    </td>
                    <td className="td">
                      <span className="inline-flex items-center gap-1.5 font-medium text-slate-600">
                        <IconStethoscope size={15} className="text-slate-400" /> {d.specialization || '—'}
                      </span>
                    </td>
                    <td className="td font-semibold text-slate-500">{d.doctorId ? `#${d.doctorId}` : '—'}</td>
                    <td className="td text-slate-500">{new Date(d.createdAt).toLocaleDateString()}</td>
                    <td className="td"><Badge tone={toneByStatus[d.status] || d.status}>{d.status}</Badge></td>
                    <td className="td text-right">
                      {d.status === 'PENDING' ? (
                        <div className="inline-flex gap-1.5">
                          <button
                            onClick={() => decide(d, true)}
                            disabled={busyId === d.id}
                            className="inline-flex items-center gap-1 rounded-lg bg-emerald-50 px-2.5 py-1.5 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-100 transition hover:bg-emerald-100"
                          >
                            <IconCheck size={14} /> Approve
                          </button>
                          <button
                            onClick={() => decide(d, false)}
                            disabled={busyId === d.id}
                            className="inline-flex items-center gap-1 rounded-lg bg-rose-50 px-2.5 py-1.5 text-xs font-semibold text-rose-700 ring-1 ring-rose-100 transition hover:bg-rose-100"
                          >
                            <IconX size={14} /> Reject
                          </button>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-300">—</span>
                      )}
                    </td>
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
