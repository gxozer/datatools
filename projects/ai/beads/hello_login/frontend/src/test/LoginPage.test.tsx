/**
 * LoginPage.test.tsx — Tests for the LoginPage component.
 *
 * ApiClient and AuthContext are mocked. useNavigate is mocked so we can
 * assert navigation without a real router.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock('../api/ApiClient', () => ({
  ApiClient: { login: vi.fn() },
}));

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import { ApiClient } from '../api/ApiClient';
import { useAuth } from '../context/AuthContext';
import { LoginPage } from '../pages/LoginPage';

const mockLogin = vi.mocked(ApiClient.login);
const mockUseAuth = vi.mocked(useAuth);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderLoginPage() {
  const authLogin = vi.fn();
  mockUseAuth.mockReturnValue({
    isAuthenticated: false,
    user: null,
    token: null,
    login: authLogin,
    logout: vi.fn(),
  });
  render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
  return { authLogin };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
  });

  it('renders an email input', () => {
    renderLoginPage();
    expect(screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i)).toBeInTheDocument();
  });

  it('renders a password input', () => {
    renderLoginPage();
    expect(screen.getByLabelText(/password/i) ?? screen.getByPlaceholderText(/password/i)).toBeInTheDocument();
  });

  it('renders a submit button', () => {
    renderLoginPage();
    expect(screen.getByRole('button', { name: /log.?in|sign.?in|submit/i })).toBeInTheDocument();
  });

  it('calls ApiClient.login with email and password on submit', async () => {
    mockLogin.mockResolvedValue({ token: 'jwt-abc', status: 'ok' });
    renderLoginPage();

    fireEvent.change(screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i) ?? screen.getByPlaceholderText(/password/i), {
      target: { value: 'secret123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /log.?in|sign.?in|submit/i }));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('user@example.com', 'secret123');
    });
  });

  it('calls AuthContext.login with the returned token on success', async () => {
    mockLogin.mockResolvedValue({ token: 'jwt-abc', status: 'ok' });
    const { authLogin } = renderLoginPage();

    fireEvent.change(screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i) ?? screen.getByPlaceholderText(/password/i), {
      target: { value: 'secret123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /log.?in|sign.?in|submit/i }));

    await waitFor(() => {
      expect(authLogin).toHaveBeenCalledWith('jwt-abc');
    });
  });

  it('navigates to /hello on successful login', async () => {
    mockLogin.mockResolvedValue({ token: 'jwt-abc', status: 'ok' });
    renderLoginPage();

    fireEvent.change(screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i) ?? screen.getByPlaceholderText(/password/i), {
      target: { value: 'secret123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /log.?in|sign.?in|submit/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/hello');
    });
  });

  it('renders a link to the signup page', () => {
    renderLoginPage();
    const link = screen.getByRole('link', { name: /sign.?up/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/signup');
  });

  it('renders a link to the forgot password page', () => {
    renderLoginPage();
    const link = screen.getByRole('link', { name: /forgot.?password/i });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/forgot-password');
  });

  it('shows an error message on failed login', async () => {
    mockLogin.mockRejectedValue(new Error('Invalid email or password'));
    renderLoginPage();

    fireEvent.change(screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/password/i) ?? screen.getByPlaceholderText(/password/i), {
      target: { value: 'wrong' },
    });
    fireEvent.click(screen.getByRole('button', { name: /log.?in|sign.?in|submit/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert') ?? screen.getByText(/invalid|error|failed/i)).toBeInTheDocument();
    });
  });
});
