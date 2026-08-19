import { createContext, useContext, useMemo, useState } from 'react';
import { api, getToken, setToken } from './api';

const AuthContext = createContext(null);

function decodeToken(token) {
  try {
    const payload = token.split('.')[1];
    const padded = payload.replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(getToken());
  const [user, setUser] = useState(() => {
    const t = getToken();
    if (!t) return null;
    const claims = decodeToken(t);
    if (!claims) return null;
    return {
      username: claims.sub,
      uid: claims.uid,
      role: claims.role,
      patientId: claims.pid ?? null,
      doctorId: claims.did ?? null,
    };
  });

  const login = async (username, password) => {
    const data = await api.post('/auth/login', { username, password });
    setToken(data.token);
    setTokenState(data.token);
    const claims = decodeToken(data.token) || {};
    const u = {
      username: claims.sub || data.username,
      uid: claims.uid,
      role: data.role || claims.role,
      patientId: claims.pid ?? null,
      doctorId: claims.did ?? null,
    };
    setUser(u);
    return u;
  };

  const logout = () => {
    setToken(null);
    setTokenState(null);
    setUser(null);
  };

  /**
   * Self-service registration for PATIENT-role accounts.
   * The gateway creates the patient record from the profile details and links
   * the account to it. No token is returned — the user signs in afterwards.
   */
  const register = async (username, password, profile) => {
    return api.post('/auth/register', { username, password, ...profile });
  };

  /**
   * Self-service registration for DOCTOR-role accounts. The account is created
   * in PENDING status — it can only sign in after an ADMIN approves it.
   */
  const registerDoctor = async (username, password, profile) => {
    return api.post('/auth/register-doctor', { username, password, ...profile });
  };

  const value = useMemo(() => ({ user, token, login, logout, register, registerDoctor }), [user, token]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
