/**
 * AppRoutes.test.tsx — Tests for the top-level React Router routing structure.
 *
 * Verifies that each URL renders the correct page component and that
 * auth-guarded routes redirect appropriately. Page components and
 * AuthContext are mocked so these tests focus purely on routing logic.
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ---------------------------------------------------------------------------
// Mock AuthContext
// ---------------------------------------------------------------------------

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '../context/AuthContext';
const mockUseAuth = vi.mocked(useAuth);

// ---------------------------------------------------------------------------
// Mock page components so tests don't depend on their internals
// ---------------------------------------------------------------------------

vi.mock('../pages/LoginPage', () => ({ LoginPage: () => <div>Login Page</div> }));
vi.mock('../pages/SignupPage', () => ({ SignupPage: () => <div>Signup Page</div> }));
vi.mock('../pages/ForgotPasswordPage', () => ({ ForgotPasswordPage: () => <div>Forgot Password Page</div> }));
vi.mock('../pages/GreetingPage', () => ({ GreetingPage: () => <div>Greeting Page</div> }));

// ---------------------------------------------------------------------------
// Import AppRoutes — the component under test
// ---------------------------------------------------------------------------

import { AppRoutes } from '../AppRoutes';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderAt(path: string, authenticated: boolean) {
  mockUseAuth.mockReturnValue({
    isAuthenticated: authenticated,
    user: authenticated ? { sub: '1', full_name: 'Alice', role: 'user', exp: 9999999999 } : null,
    token: authenticated ? 'jwt-token' : null,
    login: vi.fn(),
    logout: vi.fn(),
  });

  render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('AppRoutes', () => {
  describe('unauthenticated user', () => {
    it('redirects from / to /login', () => {
      renderAt('/', false);
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    it('renders LoginPage at /login', () => {
      renderAt('/login', false);
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    it('renders SignupPage at /signup', () => {
      renderAt('/signup', false);
      expect(screen.getByText('Signup Page')).toBeInTheDocument();
    });

    it('renders ForgotPasswordPage at /forgot-password', () => {
      renderAt('/forgot-password', false);
      expect(screen.getByText('Forgot Password Page')).toBeInTheDocument();
    });

    it('redirects from /hello to /login', () => {
      renderAt('/hello', false);
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
  });

  describe('authenticated user', () => {
    it('redirects from / to /hello', () => {
      renderAt('/', true);
      expect(screen.getByText('Greeting Page')).toBeInTheDocument();
    });

    it('renders GreetingPage at /hello', () => {
      renderAt('/hello', true);
      expect(screen.getByText('Greeting Page')).toBeInTheDocument();
    });

    it('redirects from /login to /hello when already authenticated', () => {
      renderAt('/login', true);
      expect(screen.getByText('Greeting Page')).toBeInTheDocument();
    });
  });
});
