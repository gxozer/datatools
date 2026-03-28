/**
 * AuthContext.test.tsx — Unit tests for AuthContext.
 *
 * Tests exercise the AuthProvider + useAuth hook in isolation using
 * renderHook. No real JWTs are signed — payloads are base64-encoded
 * directly since the context only decodes, never verifies, the signature.
 */

import React from 'react';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { AuthProvider, useAuth } from '../context/AuthContext';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Build a minimal unsigned JWT with the given payload. */
function makeJwt(payload: object): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fakesignature`;
}

function futureExp(secondsFromNow = 3600): number {
  return Math.floor(Date.now() / 1000) + secondsFromNow;
}

function pastExp(secondsAgo = 3600): number {
  return Math.floor(Date.now() / 1000) - secondsAgo;
}

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <AuthProvider>{children}</AuthProvider>
);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  // -------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------

  describe('initial state — no token', () => {
    it('isAuthenticated is false when localStorage has no token', () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      expect(result.current.isAuthenticated).toBe(false);
    });

    it('user is null when localStorage has no token', () => {
      const { result } = renderHook(() => useAuth(), { wrapper });

      expect(result.current.user).toBeNull();
    });
  });

  describe('initial state — valid token in localStorage', () => {
    it('isAuthenticated is true on mount when a non-expired token exists', () => {
      const token = makeJwt({ sub: '1', full_name: 'Alice', role: 'user', exp: futureExp() });
      localStorage.setItem('token', token);

      const { result } = renderHook(() => useAuth(), { wrapper });

      expect(result.current.isAuthenticated).toBe(true);
    });

    it('decodes the stored token and exposes its payload as user', () => {
      const token = makeJwt({ sub: '1', full_name: 'Alice', role: 'user', exp: futureExp() });
      localStorage.setItem('token', token);

      const { result } = renderHook(() => useAuth(), { wrapper });

      expect(result.current.user?.full_name).toBe('Alice');
      expect(result.current.user?.role).toBe('user');
    });
  });

  describe('initial state — expired token in localStorage', () => {
    it('isAuthenticated is false when the stored token is expired', () => {
      const token = makeJwt({ sub: '1', full_name: 'Alice', role: 'user', exp: pastExp() });
      localStorage.setItem('token', token);

      const { result } = renderHook(() => useAuth(), { wrapper });

      expect(result.current.isAuthenticated).toBe(false);
    });

    it('removes the expired token from localStorage on mount', () => {
      const token = makeJwt({ sub: '1', full_name: 'Alice', role: 'user', exp: pastExp() });
      localStorage.setItem('token', token);

      renderHook(() => useAuth(), { wrapper });

      expect(localStorage.getItem('token')).toBeNull();
    });

    it('user is null when the stored token is expired', () => {
      const token = makeJwt({ sub: '1', full_name: 'Alice', role: 'user', exp: pastExp() });
      localStorage.setItem('token', token);

      const { result } = renderHook(() => useAuth(), { wrapper });

      expect(result.current.user).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // login()
  // -------------------------------------------------------------------------

  describe('login()', () => {
    it('stores the token in localStorage', () => {
      const token = makeJwt({ sub: '2', full_name: 'Bob', role: 'user', exp: futureExp() });
      const { result } = renderHook(() => useAuth(), { wrapper });

      act(() => { result.current.login(token); });

      expect(localStorage.getItem('token')).toBe(token);
    });

    it('sets isAuthenticated to true', () => {
      const token = makeJwt({ sub: '2', full_name: 'Bob', role: 'user', exp: futureExp() });
      const { result } = renderHook(() => useAuth(), { wrapper });

      act(() => { result.current.login(token); });

      expect(result.current.isAuthenticated).toBe(true);
    });

    it('decodes and stores the JWT payload in user', () => {
      const token = makeJwt({ sub: '3', full_name: 'Carol', role: 'admin', exp: futureExp() });
      const { result } = renderHook(() => useAuth(), { wrapper });

      act(() => { result.current.login(token); });

      expect(result.current.user?.full_name).toBe('Carol');
      expect(result.current.user?.role).toBe('admin');
    });
  });

  // -------------------------------------------------------------------------
  // logout()
  // -------------------------------------------------------------------------

  describe('logout()', () => {
    it('removes the token from localStorage', () => {
      const token = makeJwt({ sub: '4', full_name: 'Dave', role: 'user', exp: futureExp() });
      const { result } = renderHook(() => useAuth(), { wrapper });
      act(() => { result.current.login(token); });

      act(() => { result.current.logout(); });

      expect(localStorage.getItem('token')).toBeNull();
    });

    it('sets isAuthenticated to false', () => {
      const token = makeJwt({ sub: '4', full_name: 'Dave', role: 'user', exp: futureExp() });
      const { result } = renderHook(() => useAuth(), { wrapper });
      act(() => { result.current.login(token); });

      act(() => { result.current.logout(); });

      expect(result.current.isAuthenticated).toBe(false);
    });

    it('clears the user state', () => {
      const token = makeJwt({ sub: '4', full_name: 'Dave', role: 'user', exp: futureExp() });
      const { result } = renderHook(() => useAuth(), { wrapper });
      act(() => { result.current.login(token); });

      act(() => { result.current.logout(); });

      expect(result.current.user).toBeNull();
    });
  });
});
