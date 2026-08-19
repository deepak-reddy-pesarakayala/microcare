import { useEffect, useState } from 'react';
import { api } from './api';
import { useAuth } from './auth';

/**
 * Loads real patient records once and exposes them as an id -> patient map.
 * Staff see the full directory (names for every appointment/invoice); a
 * PATIENT-role user only fetches their own record.
 */
export function usePatientNames() {
  const { user } = useAuth();
  const [names, setNames] = useState({});

  useEffect(() => {
    let active = true;
    const isStaff = user?.role === 'ADMIN' || user?.role === 'DOCTOR';
    (async () => {
      try {
        const list = isStaff
          ? await api.get('/api/patients')
          : user?.patientId
            ? [await api.get(`/api/patients/${user.patientId}`)]
            : [];
        if (active) {
          setNames(Object.fromEntries((Array.isArray(list) ? list : []).map((p) => [p.id, p])));
        }
      } catch {
        if (active) setNames({});
      }
    })();
    return () => {
      active = false;
    };
  }, [user?.role, user?.patientId]);

  return names;
}
