import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ApiError } from '../api';
import { Logo, IconStethoscope, IconShield, IconUsers, IconAlert, IconCheck, Spinner } from '../components/ui';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const justRegistered = location.state?.registered === true;

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(username.trim(), password);
      navigate('/', { replace: true });
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
            Care that runs on
            <br />
            a <span className="text-brand-200">cloud-native</span> platform.
          </h1>
          <p className="max-w-md text-brand-100">
            Manage patients, book appointments, and track invoices across a resilient
            microservices architecture — all from one beautiful dashboard.
          </p>

          <div className="space-y-3 pt-2">
            {[
              { icon: IconStethoscope, text: 'Streamlined appointment booking with conflict-free slots' },
              { icon: IconShield, text: 'Role-based access with JWT-secured APIs' },
              { icon: IconUsers, text: 'Patient records and billing in one place' },
            ].map(({ icon: Icon, text }) => (
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

          <h2 className="text-2xl font-extrabold text-slate-800">Welcome back</h2>
          <p className="mt-1 text-sm text-slate-500">Sign in to your MicroCare account</p>

          {justRegistered && !error && (
            <div className="animate-fade-up mt-5 flex items-start gap-2.5 rounded-xl bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700 ring-1 ring-emerald-200">
              <IconCheck size={18} className="mt-0.5 shrink-0" />
              Account created — please sign in with your new credentials.
            </div>
          )}

          {error && (
            <div className="mt-5 flex items-start gap-2.5 rounded-xl bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700 ring-1 ring-rose-100">
              <IconAlert size={18} className="mt-0.5 shrink-0" />
              {error}
            </div>
          )}

          <form onSubmit={submit} className="mt-6 space-y-4">
            <div>
              <label className="label" htmlFor="username">Username</label>
              <input
                id="username"
                className="input"
                placeholder="e.g. admin"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div>
              <label className="label" htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                className="input"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </div>
            <button type="submit" disabled={loading} className="btn-primary w-full py-3">
              {loading ? <Spinner className="h-5 w-5 text-white" /> : 'Sign in'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-slate-500">
            New to MicroCare?{' '}
            <Link to="/register" className="font-semibold text-brand-600 hover:text-brand-700">
              Create an account
            </Link>
          </p>

          <div className="mt-6 flex items-center justify-center gap-2 text-xs text-slate-400">
            <IconShield size={14} className="text-brand-500" />
            Protected by JWT-secured APIs · MicroCare Cloud
          </div>
        </div>
      </div>
    </div>
  );
}
