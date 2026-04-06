/**
 * ResetPasswordPage.test.tsx — Tests for the ResetPasswordPage component.
 *
 * ApiClient is mocked. useNavigate is mocked. The page reads the reset token
 * from the URL query string (?token=...) and calls confirmPasswordReset.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock('../api/ApiClient', () => ({
  ApiClient: { confirmPasswordReset: vi.fn() },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import { ApiClient } from '../api/ApiClient';
import { ResetPasswordPage } from '../pages/ResetPasswordPage';

const mockConfirmPasswordReset = vi.mocked(ApiClient.confirmPasswordReset);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderResetPasswordPage(token = 'reset-token-abc') {
  render(
    <MemoryRouter initialEntries={[`/reset?token=${token}`]}>
      <Routes>
        <Route path="/reset" element={<ResetPasswordPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
  });

  it('renders a new password input', () => {
    renderResetPasswordPage();
    expect(
      screen.getByLabelText(/new.?password|password/i) ??
      screen.getByPlaceholderText(/new.?password|password/i)
    ).toBeInTheDocument();
  });

  it('renders a submit button', () => {
    renderResetPasswordPage();
    expect(
      screen.getByRole('button', { name: /reset|submit|save/i })
    ).toBeInTheDocument();
  });

  it('calls ApiClient.confirmPasswordReset with token and new password', async () => {
    mockConfirmPasswordReset.mockResolvedValue({ message: 'ok', status: 'ok' });
    renderResetPasswordPage('my-token-123');

    const passwordInput =
      screen.getByLabelText(/new.?password|password/i) ??
      screen.getByPlaceholderText(/new.?password|password/i);
    fireEvent.change(passwordInput, { target: { value: 'NewPassword1!' } });
    fireEvent.click(screen.getByRole('button', { name: /reset|submit|save/i }));

    await waitFor(() => {
      expect(mockConfirmPasswordReset).toHaveBeenCalledWith('my-token-123', 'NewPassword1!');
    });
  });

  it('navigates to /login on successful reset', async () => {
    mockConfirmPasswordReset.mockResolvedValue({ message: 'ok', status: 'ok' });
    renderResetPasswordPage();

    const passwordInput =
      screen.getByLabelText(/new.?password|password/i) ??
      screen.getByPlaceholderText(/new.?password|password/i);
    fireEvent.change(passwordInput, { target: { value: 'NewPassword1!' } });
    fireEvent.click(screen.getByRole('button', { name: /reset|submit|save/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/login');
    });
  });

  it('shows an error message on failed reset', async () => {
    mockConfirmPasswordReset.mockRejectedValue(new Error('Invalid or expired token'));
    renderResetPasswordPage();

    const passwordInput =
      screen.getByLabelText(/new.?password|password/i) ??
      screen.getByPlaceholderText(/new.?password|password/i);
    fireEvent.change(passwordInput, { target: { value: 'NewPassword1!' } });
    fireEvent.click(screen.getByRole('button', { name: /reset|submit|save/i }));

    await waitFor(() => {
      expect(
        screen.getByRole('alert') ?? screen.getByText(/error|invalid|expired|failed/i)
      ).toBeInTheDocument();
    });
  });
});
