/**
 * AuthContext.tsx — Authentication state and actions for the React app.
 *
 * AuthProvider reads any existing JWT from localStorage on mount, validates
 * its expiry, and exposes login/logout actions and decoded user state to the
 * component tree via useAuth().
 */

import React, { createContext, useContext, useEffect, useState } from 'react';
import { ApiClient } from '../api/ApiClient';

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
  token: string | null;
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
    // JWT payloads are base64url-encoded (uses - and _ instead of + and /)
    // and may omit padding. Normalize to standard base64 before calling atob().
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + (4 - (base64.length % 4)) % 4, '=');
    return JSON.parse(atob(padded)) as JwtPayload;
  } catch {
    return null;
  }
}

function isExpired(payload: JwtPayload): boolean {
  if (typeof payload.exp !== 'number') return true;
  return payload.exp < Math.floor(Date.now() / 1000);
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<JwtPayload | null>(null);
  const [token, setToken] = useState<string | null>(null);

  // Token storage: localStorage is used for simplicity. The trade-off is that any XSS payload
  // running in this origin can read the token. The alternative (HttpOnly cookies) would require
  // backend Set-Cookie, CORS credentials:include, and CSRF protection — a larger change.
  // Mitigation: strong CSP headers; tokens expire in 24h; logout revokes server-side.

  // On mount: restore valid session from localStorage, clear expired tokens.
  useEffect(() => {
    const stored = localStorage.getItem('token');
    if (!stored) return;
    const payload = decodeJwt(stored);
    if (payload && !isExpired(payload)) {
      setToken(stored);
      setUser(payload);
    } else {
      localStorage.removeItem('token');
    }
  }, []);

  function login(tok: string) {
    const payload = decodeJwt(tok);
    if (payload && !isExpired(payload)) {
      localStorage.setItem('token', tok);
      setToken(tok);
      setUser(payload);
    }
  }

  function logout() {
    if (token) {
      ApiClient.logout(token).catch(() => {
        // best-effort revocation — proceed with local logout regardless
      });
    }
    localStorage.removeItem('token');
    setToken(null);
    setUser(null);
  }

  // Mid-session expiry timer: clear state when the token reaches its exp
  useEffect(() => {
    if (!user) return;
    const msUntilExpiry = user.exp * 1000 - Date.now();
    if (msUntilExpiry <= 0) return;
    const timer = setTimeout(() => {
      localStorage.removeItem('token');
      setToken(null);
      setUser(null);
    }, msUntilExpiry);
    return () => clearTimeout(timer);
  }, [user]);

  return (
    <AuthContext.Provider value={{ isAuthenticated: user !== null, user, token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
