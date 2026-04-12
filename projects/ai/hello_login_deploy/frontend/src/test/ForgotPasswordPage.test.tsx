/**
 * ForgotPasswordPage.test.tsx — Tests for the ForgotPasswordPage component.
 *
 * ApiClient is mocked. The page should show a success message after submit
 * regardless of whether the API call succeeds (to avoid email enumeration).
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock('../api/ApiClient', () => ({
  ApiClient: { requestPasswordReset: vi.fn() },
}));

import { ApiClient } from '../api/ApiClient';
import { ForgotPasswordPage } from '../pages/ForgotPasswordPage';

const mockRequestPasswordReset = vi.mocked(ApiClient.requestPasswordReset);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderForgotPasswordPage() {
  render(
    <MemoryRouter>
      <ForgotPasswordPage />
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders an email input', () => {
    renderForgotPasswordPage();
    expect(
      screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i)
    ).toBeInTheDocument();
  });

  it('renders a submit button', () => {
    renderForgotPasswordPage();
    expect(
      screen.getByRole('button', { name: /reset|send|submit/i })
    ).toBeInTheDocument();
  });

  it('calls ApiClient.requestPasswordReset with the email on submit', async () => {
    mockRequestPasswordReset.mockResolvedValue({ message: 'ok', status: 'ok' });
    renderForgotPasswordPage();

    const emailInput = screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i);
    fireEvent.change(emailInput, { target: { value: 'user@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: /reset|send|submit/i }));

    await waitFor(() => {
      expect(mockRequestPasswordReset).toHaveBeenCalledWith('user@example.com');
    });
  });

  it('shows a success message after submit even if API fails', async () => {
    mockRequestPasswordReset.mockRejectedValue(new Error('Network error'));
    renderForgotPasswordPage();

    const emailInput = screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i);
    fireEvent.change(emailInput, { target: { value: 'user@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: /reset|send|submit/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/check your email|email sent|if.*account.*exists|sent/i)
      ).toBeInTheDocument();
    });
  });

  it('shows a success message after successful submit', async () => {
    mockRequestPasswordReset.mockResolvedValue({ message: 'ok', status: 'ok' });
    renderForgotPasswordPage();

    const emailInput = screen.getByLabelText(/email/i) ?? screen.getByPlaceholderText(/email/i);
    fireEvent.change(emailInput, { target: { value: 'user@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: /reset|send|submit/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/check your email|email sent|if.*account.*exists|sent/i)
      ).toBeInTheDocument();
    });
  });
});
