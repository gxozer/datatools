/**
 * ProtectedRoute.test.tsx — Unit tests for the ProtectedRoute component.
 *
 * ProtectedRoute renders its children when the user is authenticated, and
 * redirects to /login when they are not. AuthContext is mocked so these
 * tests are independent of the AuthContext implementation.
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ProtectedRoute } from '../components/ProtectedRoute';

// ---------------------------------------------------------------------------
// Mock AuthContext
// ---------------------------------------------------------------------------

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '../context/AuthContext';
const mockUseAuth = vi.mocked(useAuth);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Render ProtectedRoute inside a MemoryRouter so that Navigate works.
 * A /login route is included so we can assert the redirect landed there.
 */
function renderProtectedRoute(authenticated: boolean) {
  mockUseAuth.mockReturnValue({
    isAuthenticated: authenticated,
    user: authenticated ? { sub: '1', full_name: 'Alice', role: 'user', exp: 9999999999 } : null,
    token: authenticated ? 'jwt-token' : null,
    login: vi.fn(),
    logout: vi.fn(),
  });

  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route
          path="/protected"
          element={
            <ProtectedRoute>
              <div>Protected Content</div>
            </ProtectedRoute>
          }
        />
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ProtectedRoute', () => {
  it('renders children when the user is authenticated', () => {
    renderProtectedRoute(true);

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('does not render children when the user is not authenticated', () => {
    renderProtectedRoute(false);

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('redirects to /login when the user is not authenticated', () => {
    renderProtectedRoute(false);

    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });
});
