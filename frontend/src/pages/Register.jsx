import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ApiError } from '../api';
import { Logo, IconStethoscope, IconShield, IconUsers, IconAlert, IconCheck, Spinner } from '../components/ui';

const BRAND_POINTS = [
  { icon: IconStethoscope, text: 'Book appointments with conflict-free slots' },
  { icon: IconShield, text: 'Role-based access with JWT-secured APIs' },
  { icon: IconUsers, text: 'Your records and billing in one place' },
];

function RoleToggle({ role, setRole }) {
  return (
    <div className="grid grid-cols-2 gap-2 rounded-xl bg-slate-100 p-1">
      {[
        { key: 'PATIENT', label: 'Patient', icon: IconUsers },
        { key: 'DOCTOR', label: 'Doctor', icon: IconStethoscope },
      ].map(({ key, label, icon: Icon }) => (
        <button
          key={key}
          type="button"
          onClick={() => setRole(key)}
          className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold transition-all ${
            role === key
              ? 'bg-white text-brand-700 shadow-sm ring-1 ring-brand-100'
              : 'text-slate-500 hover:text-slate-700'
          }`}
        >
          <Icon size={16} />
          {label}
        </button>
      ))}
    </div>
  );
}

export default function Register() {
  const { register, registerDoctor } = useAuth();
  const navigate = useNavigate();

  const [role, setRole] = useState('PATIENT');
  const [form, setForm] = useState({
    username: '', password: '', confirm: '',
    fullName: '', email: '', contactNumber: '', dateOfBirth: '', gender: 'Male', bloodGroup: '', address: '',
    specialization: '', diseases: '', licenseNumber: '',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [pendingApproval, setPendingApproval] = useState(false);

  const isDoctor = role === 'DOCTOR';
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError('');

    const username = form.username.trim();
    const password = form.password;
    const fullName = form.fullName.trim();
    const email = form.email.trim();

    if (username.length < 3) {
      setError('Username must be at least 3 characters.');
      return;
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    if (password !== form.confirm) {
      setError('Passwords do not match.');
      return;
    }
    if (!fullName) {
      setError('Enter your full name.');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Enter a valid email address.');
      return;
    }

    setLoading(true);
    try {
      if (isDoctor) {
        await registerDoctor(username, password, {
          fullName,
          email,
          specialization: form.specialization.trim(),
          diseases: form.diseases.trim() || null,
          contactNumber: form.contactNumber.trim() || null,
          licenseNumber: form.licenseNumber.trim() || null,
        });
        setPendingApproval(true);
        setDone(true);
      } else {
        await register(username, password, {
          fullName,
          email,
          contactNumber: form.contactNumber.trim() || null,
          dateOfBirth: form.dateOfBirth || null,
          gender: form.gender || null,
          bloodGroup: form.bloodGroup || null,
          address: form.address.trim() || null,
        });
        setDone(true);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to connect to the server');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen">
      {/* Brand panel */}
      <div className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-gradient-to-br from-brand-700 via-brand-600 to-cyan-700 p-12 text-white lg:flex">
        <div className="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-cyan-400/20 blur-3xl" />

        <div className="relative flex items-center gap-3">
          <Logo size={42} />
          <div>
            <p className="text-xl font-extrabold tracking-tight">MicroCare</p>
            <p className="text-xs font-medium uppercase tracking-widest text-brand-100">Hospital Management</p>
          </div>
        </div>

        <div className="relative space-y-6">
          <h1 className="text-4xl font-extrabold leading-tight">
            {isDoctor ? (
              <>
                Join our
                <br />
                care <span className="text-brand-200">team</span>.
              </>
            ) : (
              <>
                Your care, in
                <br />
                your <span className="text-brand-200">hands</span>.
              </>
            )}
          </h1>
          <p className="max-w-md text-brand-100">
            {isDoctor
              ? 'Register as a doctor to manage your schedule and appointments. Your account is activated once an administrator approves it.'
              : 'Create your patient account to book appointments, track your visits, and manage invoices — all from one secure dashboard.'}
          </p>

          <div className="space-y-3 pt-2">
            {BRAND_POINTS.map(({ icon: Icon, text }) => (
              <div key={text} className="flex items-center gap-3 text-sm text-brand-50">
                <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/15">
                  <Icon size={18} />
                </span>
                {text}
              </div>
            ))}
          </div>
        </div>

        <p className="relative text-xs text-brand-200">Spring Boot 3 · Spring Cloud · React · Tailwind</p>
      </div>

      {/* Form panel */}
      <div className="flex w-full items-center justify-center bg-slate-50 p-6 lg:w-1/2">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <Logo size={40} />
            <span className="text-xl font-extrabold text-slate-800">MicroCare</span>
          </div>

          {done ? (
            <div className="animate-fade-up text-center">
              <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-100 text-emerald-600">
                <IconCheck size={30} />
              </div>
              <h2 className="text-2xl font-extrabold text-slate-800">
                {pendingApproval ? 'Application submitted' : 'Account created'}
              </h2>
              <p className="mt-2 text-sm text-slate-500">
                {pendingApproval
                  ? 'Your doctor account is pending admin approval. You will be able to sign in once an administrator approves it.'
                  : 'Your patient account is ready. Sign in to access your dashboard.'}
              </p>
              <button
                onClick={() => navigate('/login', { state: { registered: true } })}
                className="btn-primary mt-7 w-full py-3"
              >
                Go to sign in
              </button>
            </div>
          ) : (
            <>
              <h2 className="text-2xl font-extrabold text-slate-800">Create your account</h2>
              <p className="mt-1 text-sm text-slate-500">
                Join MicroCare {isDoctor ? 'as a doctor' : 'as a patient'}
              </p>

              <div className="mt-5">
                <RoleToggle role={role} setRole={setRole} />
              </div>

              {error && (
                <div className="mt-5 flex items-start gap-2.5 rounded-xl bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700 ring-1 ring-rose-100">
                  <IconAlert size={18} className="mt-0.5 shrink-0" />
                  {error}
                </div>
              )}

              <form onSubmit={submit} className="mt-6 space-y-4">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div className="sm:col-span-2">
                    <label className="label" htmlFor="fullName">Full name</label>
                    <input
                      id="fullName"
                      className="input"
                      placeholder={isDoctor ? 'e.g. Dr. Maya Kapoor' : 'e.g. Alex Rivera'}
                      value={form.fullName}
                      onChange={set('fullName')}
                      autoComplete="name"
                      required
                    />
                  </div>
                  <div className="sm:col-span-2">
                    <label className="label" htmlFor="email">Email</label>
                    <input
                      id="email"
                      type="email"
                      className="input"
                      placeholder="name@example.com"
                      value={form.email}
                      onChange={set('email')}
                      autoComplete="email"
                      required
                    />
                  </div>

                  {isDoctor ? (
                    <>
                      <div>
                        <label className="label" htmlFor="specialization">Specialization</label>
                        <input
                          id="specialization"
                          className="input"
                          placeholder="e.g. Cardiology"
                          value={form.specialization}
                          onChange={set('specialization')}
                          required
                        />
                      </div>
                      <div>
                        <label className="label" htmlFor="contactNumber">Contact number</label>
                        <input
                          id="contactNumber"
                          className="input"
                          placeholder="+1-555-000-0000"
                          value={form.contactNumber}
                          onChange={set('contactNumber')}
                          autoComplete="tel"
                        />
                      </div>
                      <div className="sm:col-span-2">
                        <label className="label" htmlFor="diseases">Diseases treated <span className="font-normal text-slate-400">(optional)</span></label>
                        <input
                          id="diseases"
                          className="input"
                          placeholder="e.g. Heart disease, Hypertension"
                          value={form.diseases}
                          onChange={set('diseases')}
                        />
                        <p className="mt-1.5 text-xs text-slate-400">Comma-separated. Patients find you by disease — leave blank to auto-derive from your specialization.</p>
                      </div>
                      <div className="sm:col-span-2">
                        <label className="label" htmlFor="licenseNumber">License number</label>
                        <input
                          id="licenseNumber"
                          className="input"
                          placeholder="e.g. MED-2024-00123"
                          value={form.licenseNumber}
                          onChange={set('licenseNumber')}
                        />
                      </div>
                    </>
                  ) : (
                    <>
                      <div>
                        <label className="label" htmlFor="contactNumber">Contact number</label>
                        <input
                          id="contactNumber"
                          className="input"
                          placeholder="+1-555-000-0000"
                          value={form.contactNumber}
                          onChange={set('contactNumber')}
                          autoComplete="tel"
                        />
                      </div>
                      <div>
                        <label className="label" htmlFor="dateOfBirth">Date of birth</label>
                        <input
                          id="dateOfBirth"
                          type="date"
                          className="input"
                          value={form.dateOfBirth}
                          onChange={set('dateOfBirth')}
                        />
                      </div>
                      <div>
                        <label className="label" htmlFor="gender">Gender</label>
                        <select id="gender" className="input" value={form.gender} onChange={set('gender')}>
                          {['Male', 'Female', 'Other'].map((g) => <option key={g}>{g}</option>)}
                        </select>
                      </div>
                      <div>
                        <label className="label" htmlFor="bloodGroup">Blood group</label>
                        <select id="bloodGroup" className="input" value={form.bloodGroup} onChange={set('bloodGroup')}>
                          <option value="">—</option>
                          {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map((b) => <option key={b}>{b}</option>)}
                        </select>
                      </div>
                      <div className="sm:col-span-2">
                        <label className="label" htmlFor="address">Address</label>
                        <input
                          id="address"
                          className="input"
                          placeholder="123 Main St, Springfield"
                          value={form.address}
                          onChange={set('address')}
                          autoComplete="street-address"
                        />
                      </div>
                    </>
                  )}
                </div>

                <div>
                  <label className="label" htmlFor="username">Username</label>
                  <input
                    id="username"
                    className="input"
                    placeholder="e.g. alex.rivera"
                    value={form.username}
                    onChange={set('username')}
                    autoComplete="username"
                    required
                  />
                  <p className="mt-1.5 text-xs text-slate-400">3–50 characters — your sign-in name</p>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="label" htmlFor="password">Password</label>
                    <input
                      id="password"
                      type="password"
                      className="input"
                      placeholder="••••••••"
                      value={form.password}
                      onChange={set('password')}
                      autoComplete="new-password"
                      required
                    />
                  </div>
                  <div>
                    <label className="label" htmlFor="confirm">Confirm</label>
                    <input
                      id="confirm"
                      type="password"
                      className="input"
                      placeholder="••••••••"
                      value={form.confirm}
                      onChange={set('confirm')}
                      autoComplete="new-password"
                      required
                    />
                  </div>
                </div>
                <p className="text-xs text-slate-400">At least 8 characters</p>

                <p className="rounded-xl bg-brand-50 px-4 py-3 text-xs text-brand-800 ring-1 ring-brand-100">
                  {isDoctor
                    ? 'Doctor accounts are activated after an administrator approves your application.'
                    : 'Your patient record is created automatically when you sign up — no patient ID needed.'}
                </p>

                <button type="submit" disabled={loading} className="btn-primary w-full py-3">
                  {loading ? <Spinner className="h-5 w-5 text-white" /> : isDoctor ? 'Submit for approval' : 'Create account'}
                </button>
              </form>

              <p className="mt-6 text-center text-sm text-slate-500">
                Already have an account?{' '}
                <Link to="/login" className="font-semibold text-brand-600 hover:text-brand-700">
                  Sign in
                </Link>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
