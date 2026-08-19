import { useEffect, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { usePatientNames } from '../usePatientNames';
import { PageLoader, Modal, Badge, EmptyState, PatientCell, IconDollar, useToast, IconAlert, IconRefresh } from '../components/ui';

export default function Invoices() {
  const { user } = useAuth();
  const toast = useToast();
  const isStaff = user?.role === 'ADMIN' || user?.role === 'DOCTOR';
  const patientId = user?.patientId;
  const patientNames = usePatientNames();

  const [invoices, setInvoices] = useState(null);
  const [payTarget, setPayTarget] = useState(null);
  const [amount, setAmount] = useState('');
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const data = await api.get('/api/invoices', { patientId: isStaff ? undefined : patientId });
      setInvoices(Array.isArray(data) ? data : []);
    } catch (e) {
      toast.error(e.message);
      setInvoices([]);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isStaff, patientId]);

  const openPay = (inv) => {
    setPayTarget(inv);
    setAmount(String(inv.amount ?? ''));
  };

  const submitPay = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      await api.put(`/api/invoices/${payTarget.id}/pay`, { amount: Number(amount) });
      toast.success(`Invoice #${payTarget.id} marked as paid`);
      setPayTarget(null);
      await load();
    } catch (err) {
      toast.error(err.message);
    } finally {
      setSaving(false);
    }
  };

  if (!invoices) return <PageLoader label="Loading invoices…" />;

  const total = invoices.reduce((s, i) => s + (i.amount || 0), 0);
  const pending = invoices.filter((i) => i.status === 'PENDING').reduce((s, i) => s + (i.amount || 0), 0);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-800">Invoices</h1>
          <p className="mt-0.5 text-sm text-slate-500">{invoices.length} invoices · ${pending.toFixed(2)} pending · ${total.toFixed(2)} total</p>
        </div>
        <button onClick={() => { toast.info('Refreshing…'); load(); }} className="btn-secondary">
          <IconRefresh size={15} /> Refresh
        </button>
      </div>

      {invoices.length === 0 ? (
        <div className="card">
          <EmptyState title="No invoices" subtitle="Invoices are generated automatically when appointments are confirmed." />
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60">
                  <th className="th">ID</th>
                  <th className="th">Appointment</th>
                  <th className="th">Patient</th>
                  <th className="th">Amount</th>
                  <th className="th">Due</th>
                  <th className="th">Status</th>
                  {isStaff && <th className="th text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {invoices.map((inv) => (
                  <tr key={inv.id} className="transition hover:bg-slate-50/60">
                    <td className="td font-semibold text-slate-500">#{inv.id}</td>
                    <td className="td">#{inv.appointmentId}</td>
                    <td className="td"><PatientCell patient={patientNames[inv.patientId]} fallbackId={inv.patientId} /></td>
                    <td className="td font-bold text-slate-800 tabular-nums">{inv.currency} {Number(inv.amount).toFixed(2)}</td>
                    <td className="td text-slate-500">{inv.dueDate}</td>
                    <td className="td"><Badge tone={inv.status}>{inv.status}</Badge></td>
                    {isStaff && (
                      <td className="td text-right">
                        {inv.status === 'PENDING' ? (
                          <button onClick={() => openPay(inv)} className="btn-primary px-3 py-1.5 text-xs">
                            <IconDollar size={14} /> Mark paid
                          </button>
                        ) : (
                          <span className="text-xs text-slate-300">—</span>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <Modal open={!!payTarget} onClose={() => setPayTarget(null)} title={`Pay invoice #${payTarget?.id}`}>
        <form onSubmit={submitPay} className="space-y-4">
          <div className="rounded-xl bg-brand-50 p-4 text-sm text-brand-800 ring-1 ring-brand-100">
            Appointment <b>#{payTarget?.appointmentId}</b> ·{' '}
            {patientNames[payTarget?.patientId]?.fullName
              ? <>Patient <b>{patientNames[payTarget?.patientId].fullName}</b></>
              : <>Patient <b>#{payTarget?.patientId}</b></>}
          </div>
          <div>
            <label className="label">Payment amount ({payTarget?.currency})</label>
            <input type="number" step="0.01" min="0" className="input" value={amount} onChange={(e) => setAmount(e.target.value)} required />
            <p className="mt-1.5 text-xs text-slate-400">Must match the invoice amount to prevent partial payments.</p>
          </div>
          <div className="flex justify-end gap-3 pt-1">
            <button type="button" onClick={() => setPayTarget(null)} className="btn-secondary">Cancel</button>
            <button type="submit" disabled={saving} className="btn-primary">{saving ? 'Paying…' : 'Confirm payment'}</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
