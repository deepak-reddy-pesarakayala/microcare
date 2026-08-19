import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import { PageLoader, Modal, Badge, EmptyState, IconSearch, IconPlus, IconPencil, useToast, IconAlert } from '../components/ui';

const emptyForm = {
  fullName: '', dateOfBirth: '', gender: 'Male', contactNumber: '',
  email: '', address: '', bloodGroup: '',
};

const bloodGroups = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

export default function Patients() {
  const toast = useToast();
  const [patients, setPatients] = useState(null);
  const [query, setQuery] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setPatients(await api.get('/api/patients'));
    } catch (e) {
      toast.error(e.message);
      setPatients([]);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filtered = useMemo(() => {
    if (!patients) return [];
    const q = query.toLowerCase();
    return patients.filter((p) =>
      [p.fullName, p.email, p.contactNumber, p.bloodGroup].some((v) => (v || '').toLowerCase().includes(q))
    );
  }, [patients, query]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setError('');
    setModalOpen(true);
  };

  const openEdit = (p) => {
    setEditing(p);
    setForm({
      fullName: p.fullName || '',
      dateOfBirth: (p.dateOfBirth || '').slice(0, 10),
      gender: p.gender || 'Male',
      contactNumber: p.contactNumber || '',
      email: p.email || '',
      address: p.address || '',
      bloodGroup: p.bloodGroup || 'O+',
    });
    setError('');
    setModalOpen(true);
  };

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      const payload = { ...form };
      if (editing) {
        await api.put(`/api/patients/${editing.id}`, payload);
        toast.success(`Patient #${editing.id} updated`);
      } else {
        const created = await api.post('/api/patients', payload);
        toast.success(`Patient ${created.fullName} registered`);
      }
      setModalOpen(false);
      await load();
    } catch (err) {
      const msg = err.data?.validationErrors?.map((v) => `${v.field}: ${v.message}`).join(', ') || err.message;
      setError(msg);
    } finally {
      setSaving(false);
    }
  };

  if (!patients) return <PageLoader label="Loading patients…" />;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-800">Patients</h1>
          <p className="mt-0.5 text-sm text-slate-500">{patients.length} registered records</p>
        </div>
        <button onClick={openCreate} className="btn-primary">
          <IconPlus size={17} /> New patient
        </button>
      </div>

      <div className="relative max-w-sm">
        <IconSearch size={17} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          className="input pl-10"
          placeholder="Search by name, email, phone, blood group…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>

      <div className="card overflow-hidden">
        {filtered.length === 0 ? (
          <EmptyState
            title={query ? 'No matches' : 'No patients yet'}
            subtitle={query ? 'Try a different search.' : 'Register your first patient to get started.'}
            action={!query && <button onClick={openCreate} className="btn-primary mt-3"><IconPlus size={16} /> New patient</button>}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60">
                  <th className="th">ID</th>
                  <th className="th">Full name</th>
                  <th className="th">Gender</th>
                  <th className="th">DOB</th>
                  <th className="th">Contact</th>
                  <th className="th">Email</th>
                  <th className="th">Blood</th>
                  <th className="th text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {filtered.map((p) => (
                  <tr key={p.id} className="transition hover:bg-slate-50/60">
                    <td className="td font-semibold text-slate-500">#{p.id}</td>
                    <td className="td font-semibold text-slate-800">{p.fullName}</td>
                    <td className="td">{p.gender}</td>
                    <td className="td text-slate-500">{(p.dateOfBirth || '').slice(0, 10)}</td>
                    <td className="td text-slate-500">{p.contactNumber}</td>
                    <td className="td text-slate-500">{p.email}</td>
                    <td className="td"><Badge tone="AVAILABLE">{p.bloodGroup}</Badge></td>
                    <td className="td text-right">
                      <button onClick={() => openEdit(p)} className="rounded-lg p-2 text-slate-400 transition hover:bg-brand-50 hover:text-brand-600" title="Edit">
                        <IconPencil size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? `Edit patient #${editing.id}` : 'Register a new patient'} wide>
        <form onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {error && (
            <div className="col-span-full flex items-start gap-2 rounded-xl bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700 ring-1 ring-rose-100">
              <IconAlert size={17} className="mt-0.5 shrink-0" /> {error}
            </div>
          )}
          <div className="sm:col-span-2">
            <label className="label">Full name</label>
            <input className="input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required />
          </div>
          <div>
            <label className="label">Date of birth</label>
            <input type="date" className="input" value={form.dateOfBirth} onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })} required />
          </div>
          <div>
            <label className="label">Gender</label>
            <select className="input" value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value })}>
              {['Male', 'Female', 'Other'].map((g) => <option key={g}>{g}</option>)}
            </select>
          </div>
          <div>
            <label className="label">Contact number</label>
            <input className="input" placeholder="+1-555-000-0000" value={form.contactNumber} onChange={(e) => setForm({ ...form, contactNumber: e.target.value })} required />
          </div>
          <div>
            <label className="label">Email</label>
            <input type="email" className="input" placeholder="name@example.com" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required />
          </div>
          <div className="sm:col-span-2">
            <label className="label">Address</label>
            <input className="input" placeholder="123 Main St, Springfield" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
          </div>
          <div>
            <label className="label">Blood group</label>
            <select className="input" value={form.bloodGroup} onChange={(e) => setForm({ ...form, bloodGroup: e.target.value })}>
              {bloodGroups.map((b) => <option key={b}>{b}</option>)}
            </select>
          </div>
          <div className="col-span-full mt-2 flex justify-end gap-3">
            <button type="button" onClick={() => setModalOpen(false)} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={saving} className="btn-primary">{saving ? 'Saving…' : editing ? 'Save changes' : 'Register patient'}</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
