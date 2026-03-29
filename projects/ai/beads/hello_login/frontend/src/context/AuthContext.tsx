/**
 * AuthContext.tsx — Authentication state and actions for the React app.
 *
 * AuthProvider reads any existing JWT from localStorage on mount, validates
 * its expiry, and exposes login/logout actions and decoded user state to the
 * component tree via useAuth().
 */

import React, { createContext, useContext, useEffect, useState } from 'react';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface JwtPayload {
  sub: string;
  full_name: string;
  role: string;
  exp: number;
  [key: string]: unknown;
}

interface AuthContextValue {
  isAuthenticated: boolean;
  user: JwtPayload | null;
  login: (token: string) => void;
  logout: () => void;
}

// ---------------------------------------------------------------------------
// JWT helpers
// ---------------------------------------------------------------------------

function decodeJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    return JSON.parse(atob(parts[1])) as JwtPayload;
  } catch {
    return null;
  }
}

function isExpired(payload: JwtPayload): boolean {
  return payload.exp < Math.floor(Date.now() / 1000);
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<JwtPayload | null>(null);

  // On mount: restore valid session from localStorage, clear expired tokens.
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) return;
    const payload = decodeJwt(token);
    if (payload && !isExpired(payload)) {
      setUser(payload);
    } else {
      localStorage.removeItem('token');
    }
  }, []);

  function login(token: string) {
    const payload = decodeJwt(token);
    if (payload) {
      localStorage.setItem('token', token);
      setUser(payload);
    }
  }

  function logout() {
    localStorage.removeItem('token');
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ isAuthenticated: user !== null, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
