import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { usePatientNames } from '../usePatientNames';
import { PageLoader, Modal, Badge, EmptyState, PatientCell, IconPlus, IconClock, IconStethoscope, useToast, IconAlert, IconRefresh } from '../components/ui';

const statusTone = {
  PENDING: 'PENDING', CONFIRMED: 'CONFIRMED', REJECTED: 'REJECTED', RESCHEDULED: 'RESCHEDULED', CANCELLED: 'CANCELLED',
};

export default function Appointments() {
  const { user } = useAuth();
  const toast = useToast();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'DOCTOR';
  const patientNames = usePatientNames();
  const [searchParams] = useSearchParams();
  const doctorParam = searchParams.get('doctorId');

  const [appointments, setAppointments] = useState(null);
  const [slots, setSlots] = useState([]);
  const [doctors, setDoctors] = useState([]);
  const [patients, setPatients] = useState([]);
  const [error, setError] = useState('');

  const [bookOpen, setBookOpen] = useState(false);
  const [slotOpen, setSlotOpen] = useState(false);
  const [reschedOpen, setReschedOpen] = useState(false);
  const [selectedAppt, setSelectedAppt] = useState(null);
  const [saving, setSaving] = useState(false);

  const [bookForm, setBookForm] = useState({ patientName: '', patientId: '', doctorId: '', slotId: '' });
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [slotForm, setSlotForm] = useState({ doctorId: user?.role === 'DOCTOR' ? String(user.doctorId || '') : '', slotDate: '', startTime: '09:00', endTime: '09:30' });
  const [reschedForm, setReschedForm] = useState({ newSlotId: '' });

  const patientId = user?.patientId;

  const loadAll = async () => {
    setError('');
    try {
      const [appts, slotList, doctorList] = await Promise.all([
        api.get('/api/appointments', { patientId: isStaff ? undefined : patientId }),
        api.get('/api/appointments/slots'),
        api.get('/api/doctors'),
      ]);
      setAppointments(Array.isArray(appts) ? appts : []);
      setSlots(Array.isArray(slotList) ? slotList : []);
      setDoctors(Array.isArray(doctorList) ? doctorList : []);
      if (isStaff) {
        setPatients(await api.get('/api/patients'));
      }
    } catch (e) {
      setError(e.message);
    }
  };

  useEffect(() => {
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isStaff, patientId]);

  // Deep link from the patient dashboard: /appointments?doctorId={id}
  // opens the booking form with that doctor preselected.
  useEffect(() => {
    if (!isStaff && doctorParam) {
      setBookForm((f) => ({ ...f, doctorId: doctorParam }));
      setBookOpen(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isStaff, doctorParam]);

  if (error && !appointments) {
    return (
      <div className="card mx-auto mt-16 max-w-md p-8 text-center">
        <IconAlert size={28} className="mx-auto mb-3 text-rose-500" />
        <p className="font-semibold text-slate-700">Couldn’t load appointments</p>
        <p className="mt-1 text-sm text-slate-400">{error}</p>
      </div>
    );
  }
  if (!appointments) return <PageLoader label="Loading appointments…" />;

  const openBook = () => {
    if (isStaff) {
      // Doctors create slots and book for their own schedule by default.
      const defaultDoctorId = user?.role === 'DOCTOR' ? String(user.doctorId || '') : '';
      setBookForm({ patientName: '', patientId: '', doctorId: defaultDoctorId, slotId: '' });
    } else {
      setBookForm({
        patientName: patientNames[patientId]?.fullName || '',
        patientId: patientId ? String(patientId) : '',
        doctorId: '',
        slotId: '',
      });
    }
    setBookOpen(true);
  };

  /**
   * Resolves the free-text patient field to a patient record id.
   * Staff may type a name (exact or partial) or a numeric id; the typed text
   * wins unless a suggestion was picked, in which case the picked id is used.
   */
  const resolvePatient = (nameOrId, selectedId) => {
    if (selectedId && patients.some((p) => String(p.id) === selectedId)) {
      return { id: Number(selectedId) };
    }
    const text = (nameOrId || '').trim();
    if (!text) return { error: 'Enter a patient name or ID.' };
    if (/^\d+$/.test(text)) {
      const byId = patients.find((p) => String(p.id) === text);
      return byId ? { id: byId.id } : { error: `No patient record found with ID ${text}.` };
    }
    const lower = text.toLowerCase();
    const exact = patients.filter((p) => (p.fullName || '').toLowerCase() === lower);
    if (exact.length === 1) return { id: exact[0].id };
    if (exact.length > 1) return { error: 'Multiple patients share that name — pick one from the suggestions.' };
    const fuzzy = patients.filter((p) => (p.fullName || '').toLowerCase().includes(lower));
    if (fuzzy.length === 1) return { id: fuzzy[0].id };
    if (fuzzy.length > 1) return { error: 'Multiple patients match that name — pick one from the suggestions.' };
    return { error: `No patient record found for “${text}”.` };
  };

  const patientMatches = bookForm.patientName.trim()
    ? patients
        .filter((p) => {
          const q = bookForm.patientName.trim().toLowerCase();
          return (
            (p.fullName || '').toLowerCase().includes(q) ||
            String(p.id) === q ||
            (p.email || '').toLowerCase().includes(q)
          );
        })
        .slice(0, 8)
    : [];

  const submitBook = async (e) => {
    e.preventDefault();
    setSaving(true);

    let targetPatientId;
    if (isStaff) {
      const resolved = resolvePatient(bookForm.patientName, bookForm.patientId);
      if (resolved.error) {
        toast.error(resolved.error);
        setSaving(false);
        return;
      }
      targetPatientId = resolved.id;
    } else {
      targetPatientId = Number(user?.patientId);
      if (!Number.isInteger(targetPatientId) || targetPatientId <= 0) {
        toast.error('Your patient record is missing — please contact support.');
        setSaving(false);
        return;
      }
    }

    const doctorId = Number(bookForm.doctorId);
    const slotId = Number(bookForm.slotId);
    if (!Number.isInteger(doctorId) || doctorId <= 0) {
      toast.error('Enter a valid doctor ID.');
      setSaving(false);
      return;
    }
    if (!Number.isInteger(slotId) || slotId <= 0) {
      toast.error('Select an available slot.');
      setSaving(false);
      return;
    }

    try {
      const body = { patientId: targetPatientId, doctorId, slotId };
      await api.post('/api/appointments/book', body);
      toast.success('Appointment booked successfully');
      setBookOpen(false);
      await loadAll();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  const submitSlot = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      await api.post('/api/appointments/slots', {
        doctorId: Number(slotForm.doctorId),
        slotDate: slotForm.slotDate,
        startTime: slotForm.startTime,
        endTime: slotForm.endTime,
      });
      toast.success('Slot created');
      setSlotOpen(false);
      await loadAll();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  const openResched = (a) => {
    setSelectedAppt(a);
    setReschedForm({ newSlotId: '' });
    setReschedOpen(true);
  };

  const submitResched = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      await api.put(`/api/appointments/${selectedAppt.id}/reschedule`, { newSlotId: Number(reschedForm.newSlotId) });
      toast.success(`Appointment #${selectedAppt.id} rescheduled`);
      setReschedOpen(false);
      await loadAll();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  const cancel = async (a) => {
    if (!window.confirm(`Cancel appointment #${a.id}?`)) return;
    try {
      await api.del(`/api/appointments/${a.id}`);
      toast.success(`Appointment #${a.id} cancelled`);
      await loadAll();
    } catch (err) {
      toast.error(err.message);
    }
  };

  /**
   * Accept / reject a PENDING booking request. Only the appointment's own
   * doctor (or an ADMIN) may decide.
   */
  const canDecide = (a) =>
    isStaff && (user?.role === 'ADMIN' || String(a.doctorId) === String(user?.doctorId));

  const decide = async (a, accept) => {
    if (!window.confirm(`${accept ? 'Accept' : 'Reject'} appointment #${a.id}?`)) return;
    setSaving(true);
    try {
      await api.post(`/api/appointments/${a.id}/${accept ? 'accept' : 'reject'}`);
      toast.success(`Appointment #${a.id} ${accept ? 'accepted' : 'rejected'}`);
      await loadAll();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  const fmtSlot = (s) => `${s.slotDate} · ${s.startTime}–${s.endTime} (#${s.id})`;
  const slotMap = Object.fromEntries(slots.map((s) => [s.id, s]));
  const slotLabel = (id) => {
    const s = slotMap[id];
    return s ? `${s.slotDate} · ${s.startTime}–${s.endTime}` : `Slot #${id}`;
  };
  const availableSlots = slots.filter((s) => s.status === 'AVAILABLE' && (bookForm.doctorId === '' || String(s.doctorId) === bookForm.doctorId));
  const reschedSlots = slots.filter(
    (s) => s.status === 'AVAILABLE' && (!selectedAppt || String(s.doctorId) === String(selectedAppt.doctorId))
  );

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-800">Appointments</h1>
          <p className="mt-0.5 text-sm text-slate-500">
            {appointments.length} total · {appointments.filter((a) => a.status === 'CONFIRMED' || a.status === 'RESCHEDULED').length} active
          </p>
        </div>
        <div className="flex gap-2">
          {isStaff && (
            <button onClick={() => setSlotOpen(true)} className="btn-secondary">
              <IconClock size={16} /> New slot
            </button>
          )}
          <button onClick={openBook} className="btn-primary">
            <IconPlus size={16} /> Book appointment
          </button>
        </div>
      </div>

      {appointments.length === 0 ? (
        <div className="card">
          <EmptyState
            title="No appointments"
            subtitle={isStaff ? 'Book an appointment for a patient.' : 'You have no appointments on record.'}
            action={
              <div className="mt-3 flex gap-2">
                <button onClick={openBook} className="btn-primary"><IconPlus size={16} /> Book appointment</button>
                {isStaff && <button onClick={() => setSlotOpen(true)} className="btn-secondary"><IconClock size={16} /> Create slot</button>}
              </div>
            }
          />
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60">
                  <th className="th">ID</th>
                  <th className="th">Patient</th>
                  <th className="th">Doctor</th>
                  <th className="th">Slot</th>
                  <th className="th">Status</th>
                  <th className="th">Booked</th>
                  <th className="th text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {appointments.map((a) => (
                  <tr key={a.id} className="transition hover:bg-slate-50/60">
                    <td className="td font-semibold text-slate-500">#{a.id}</td>
                    <td className="td"><PatientCell patient={patientNames[a.patientId]} fallbackId={a.patientId} /></td>
                    <td className="td">
                      <span className="inline-flex items-center gap-1.5 font-medium text-slate-600">
                        <IconStethoscope size={15} className="text-slate-400" /> Doctor #{a.doctorId}
                      </span>
                    </td>
                    <td className="td text-slate-500">{slotLabel(a.slotId)}</td>
                    <td className="td"><Badge tone={statusTone[a.status] || a.status}>{a.status}</Badge></td>
                    <td className="td text-slate-400">{new Date(a.createdAt).toLocaleString()}</td>
                    <td className="td text-right">
                      <div className="inline-flex gap-1">
                        {a.status === 'PENDING' && canDecide(a) && (
                          <>
                            <button onClick={() => decide(a, true)} disabled={saving} className="rounded-lg px-2.5 py-1 text-xs font-semibold text-emerald-600 ring-1 ring-emerald-100 transition hover:bg-emerald-50">Accept</button>
                            <button onClick={() => decide(a, false)} disabled={saving} className="rounded-lg px-2.5 py-1 text-xs font-semibold text-rose-600 ring-1 ring-rose-100 transition hover:bg-rose-50">Reject</button>
                          </>
                        )}
                        {(a.status === 'CONFIRMED' || a.status === 'RESCHEDULED') && (
                          <>
                            <button onClick={() => openResched(a)} className="rounded-lg px-2.5 py-1 text-xs font-semibold text-brand-600 ring-1 ring-brand-100 transition hover:bg-brand-50">Reschedule</button>
                            <button onClick={() => cancel(a)} className="rounded-lg px-2.5 py-1 text-xs font-semibold text-rose-600 ring-1 ring-rose-100 transition hover:bg-rose-50">Cancel</button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Book modal */}
      <Modal open={bookOpen} onClose={() => setBookOpen(false)} title="Book an appointment">
        <form onSubmit={submitBook} className="space-y-4">
          <div>
            <label className="label">Patient {isStaff ? 'name or ID' : ''}</label>
            <div className="relative">
              <input
                type="text"
                className="input"
                placeholder={isStaff ? 'Type a patient name or ID…' : 'Your name'}
                value={bookForm.patientName}
                onChange={(e) => {
                  setBookForm((f) => ({ ...f, patientName: e.target.value, patientId: '' }));
                  setShowSuggestions(true);
                }}
                onFocus={() => setShowSuggestions(true)}
                onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
                autoComplete="off"
                required={isStaff}
              />
              {isStaff && showSuggestions && patientMatches.length > 0 && (
                <div className="absolute z-10 mt-1 max-h-56 w-full overflow-auto rounded-xl border border-slate-200 bg-white py-1 shadow-pop">
                  {patientMatches.map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      className="flex w-full items-center justify-between px-3 py-2 text-left text-sm transition hover:bg-brand-50"
                      onMouseDown={(e) => {
                        e.preventDefault();
                        setBookForm((f) => ({ ...f, patientName: `${p.fullName} (#${p.id})`, patientId: String(p.id) }));
                        setShowSuggestions(false);
                      }}
                    >
                      <span className="font-medium text-slate-700">{p.fullName}</span>
                      <span className="text-xs text-slate-400">#{p.id}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
            <p className="mt-1.5 text-xs text-slate-400">
              {isStaff
                ? 'Pick a matching patient from the suggestions, or type the exact name or ID.'
                : 'Your booking request goes to the doctor — the slot is reserved until they accept or reject it.'}
            </p>
          </div>
          <div>
            <label className="label">Doctor</label>
            <select className="input" value={bookForm.doctorId} onChange={(e) => setBookForm({ ...bookForm, doctorId: e.target.value })} required>
              <option value="">Select a doctor…</option>
              {doctors.map((d) => (
                <option key={d.id} value={d.doctorId}>{d.fullName || d.username} — {d.specialization || 'General'}</option>
              ))}
            </select>
            <p className="mt-1.5 text-xs text-slate-400">Pick the doctor you want to see — available slots update below.</p>
          </div>
          <div>
            <label className="label">Available slot</label>
            <select className="input" value={bookForm.slotId} onChange={(e) => setBookForm({ ...bookForm, slotId: e.target.value })} required>
              <option value="">Select a slot…</option>
              {availableSlots.map((s) => (
                <option key={s.id} value={s.id}>{fmtSlot(s)}</option>
              ))}
            </select>
            <p className="mt-1.5 text-xs text-slate-400">Only available slots for the selected doctor are shown.</p>
          </div>
          <div className="flex justify-end gap-3 pt-1">
            <button type="button" onClick={() => setBookOpen(false)} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={saving} className="btn-primary">{saving ? 'Booking…' : 'Book appointment'}</button>
          </div>
        </form>
      </Modal>

      {/* Slot modal */}
      <Modal open={slotOpen} onClose={() => setSlotOpen(false)} title="Create a doctor slot">
        <form onSubmit={submitSlot} className="space-y-4">
          <div>
            <label className="label">Doctor ID</label>
            <input type="number" className="input" placeholder="e.g. 100" value={slotForm.doctorId} onChange={(e) => setSlotForm({ ...slotForm, doctorId: e.target.value })} required />
            <p className="mt-1.5 text-xs text-slate-400">Doctors are identified by their ID (e.g. 100).</p>
          </div>
          <div>
            <label className="label">Date</label>
            <input type="date" className="input" value={slotForm.slotDate} onChange={(e) => setSlotForm({ ...slotForm, slotDate: e.target.value })} required />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Start</label>
              <input type="time" className="input" value={slotForm.startTime} onChange={(e) => setSlotForm({ ...slotForm, startTime: e.target.value })} required />
            </div>
            <div>
              <label className="label">End</label>
              <input type="time" className="input" value={slotForm.endTime} onChange={(e) => setSlotForm({ ...slotForm, endTime: e.target.value })} required />
            </div>
          </div>
          <div className="flex justify-end gap-3 pt-1">
            <button type="button" onClick={() => setSlotOpen(false)} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={saving} className="btn-primary">{saving ? 'Creating…' : 'Create slot'}</button>
          </div>
        </form>
      </Modal>

      {/* Reschedule modal */}
      <Modal open={reschedOpen} onClose={() => setReschedOpen(false)} title={`Reschedule appointment #${selectedAppt?.id}`}>
        <form onSubmit={submitResched} className="space-y-4">
          <div>
            <label className="label">New slot</label>
            <select className="input" value={reschedForm.newSlotId} onChange={(e) => setReschedForm({ newSlotId: e.target.value })} required>
              <option value="">Select a slot…</option>
              {reschedSlots.map((s) => (
                <option key={s.id} value={s.id}>{fmtSlot(s)}</option>
              ))}
            </select>
          </div>
          <div className="flex justify-end gap-3 pt-1">
            <button type="button" onClick={() => setReschedOpen(false)} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={saving} className="btn-primary">{saving ? 'Rescheduling…' : 'Reschedule'}</button>
          </div>
        </form>
      </Modal>

      {/* Refresh */}
      <button onClick={() => { toast.info('Refreshing…'); loadAll(); }} className="btn-secondary">
        <IconRefresh size={15} /> Refresh
      </button>
    </div>
  );
}
