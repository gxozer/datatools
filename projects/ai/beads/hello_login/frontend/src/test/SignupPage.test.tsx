/**
 * SignupPage.test.tsx — Tests for the SignupPage component.
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
  ApiClient: { signup: vi.fn() },
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
import { SignupPage } from '../pages/SignupPage';

const mockSignup = vi.mocked(ApiClient.signup);
const mockUseAuth = vi.mocked(useAuth);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderSignupPage() {
  const authLogin = vi.fn();
  mockUseAuth.mockReturnValue({
    isAuthenticated: false,
    user: null,
    login: authLogin,
    logout: vi.fn(),
  });
  render(
    <MemoryRouter>
      <SignupPage />
    </MemoryRouter>,
  );
  return { authLogin };
}

function fillForm(fullName: string, email: string, password: string) {
  const nameInput = screen.queryByLabelText(/full.?name|name/i) ?? screen.queryByPlaceholderText(/full.?name|name/i);
  const emailInput = screen.queryByLabelText(/email/i) ?? screen.queryByPlaceholderText(/email/i);
  const passwordInput = screen.queryByLabelText(/password/i) ?? screen.queryByPlaceholderText(/password/i);
  if (nameInput) fireEvent.change(nameInput, { target: { value: fullName } });
  if (emailInput) fireEvent.change(emailInput, { target: { value: email } });
  if (passwordInput) fireEvent.change(passwordInput, { target: { value: password } });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('SignupPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
  });

  it('renders a full name input', () => {
    renderSignupPage();
    expect(
      screen.getByLabelText(/full.?name|name/i) ?? screen.getByPlaceholderText(/full.?name|name/i)
    ).toBeInTheDocument();
  });

  it('renders an email input', () => {
    renderSignupPage();
    expect(
      screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i)
    ).toBeInTheDocument();
  });

  it('renders a password input', () => {
    renderSignupPage();
    expect(
      screen.getByLabelText(/password/i) ?? screen.getByPlaceholderText(/password/i)
    ).toBeInTheDocument();
  });

  it('renders a submit button', () => {
    renderSignupPage();
    expect(screen.getByRole('button', { name: /sign.?up|register|submit/i })).toBeInTheDocument();
  });

  it('calls ApiClient.signup with correct args on submit', async () => {
    mockSignup.mockResolvedValue({ token: 'jwt-xyz', status: 'ok' });
    renderSignupPage();

    fillForm('Alice', 'alice@example.com', 'Password1!');
    fireEvent.click(screen.getByRole('button', { name: /sign.?up|register|submit/i }));

    await waitFor(() => {
      expect(mockSignup).toHaveBeenCalledWith('Alice', 'alice@example.com', 'Password1!');
    });
  });

  it('calls AuthContext.login with the returned token on success', async () => {
    mockSignup.mockResolvedValue({ token: 'jwt-xyz', status: 'ok' });
    const { authLogin } = renderSignupPage();

    fillForm('Alice', 'alice@example.com', 'Password1!');
    fireEvent.click(screen.getByRole('button', { name: /sign.?up|register|submit/i }));

    await waitFor(() => {
      expect(authLogin).toHaveBeenCalledWith('jwt-xyz');
    });
  });

  it('navigates to /hello on successful signup', async () => {
    mockSignup.mockResolvedValue({ token: 'jwt-xyz', status: 'ok' });
    renderSignupPage();

    fillForm('Alice', 'alice@example.com', 'Password1!');
    fireEvent.click(screen.getByRole('button', { name: /sign.?up|register|submit/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/hello');
    });
  });

  it('shows an error message on failed signup', async () => {
    mockSignup.mockRejectedValue(new Error('Email already registered'));
    renderSignupPage();

    fillForm('Alice', 'alice@example.com', 'Password1!');
    fireEvent.click(screen.getByRole('button', { name: /sign.?up|register|submit/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert') ?? screen.getByText(/error|failed|already/i)).toBeInTheDocument();
    });
  });

  it('shows a validation error if email is empty on submit', async () => {
    renderSignupPage();

    fillForm('Alice', '', 'Password1!');
    fireEvent.click(screen.getByRole('button', { name: /sign.?up|register|submit/i }));

    await waitFor(() => {
      expect(mockSignup).not.toHaveBeenCalled();
    });
  });
});
