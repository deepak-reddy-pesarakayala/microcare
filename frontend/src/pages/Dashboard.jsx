import { useAuth } from '../auth';
import AdminDashboard from './dashboard/AdminDashboard';
import DoctorDashboard from './dashboard/DoctorDashboard';
import PatientDashboard from './dashboard/PatientDashboard';

/**
 * Role-based dashboard dispatcher. Each role gets its own curated overview:
 *   - ADMIN  → platform-wide stats + pending doctor approvals
 *   - DOCTOR → personal schedule, slots and patients seen
 *   - PATIENT→ personal care snapshot
 */
export default function Dashboard() {
  const { user } = useAuth();

  if (user?.role === 'ADMIN') return <AdminDashboard />;
  if (user?.role === 'DOCTOR') return <DoctorDashboard />;
  return <PatientDashboard />;
}
