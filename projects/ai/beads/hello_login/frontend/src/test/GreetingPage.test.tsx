/**
 * GreetingPage.test.tsx — Tests for the GreetingPage component.
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
  ApiClient: { getHello: vi.fn() },
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
import { GreetingPage } from '../pages/GreetingPage';

const mockGetHello = vi.mocked(ApiClient.getHello);
const mockUseAuth = vi.mocked(useAuth);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function renderGreetingPage(token = 'jwt-token') {
  const authLogout = vi.fn();
  mockUseAuth.mockReturnValue({
    isAuthenticated: true,
    user: null,
    login: vi.fn(),
    logout: authLogout,
    token,
  });
  render(
    <MemoryRouter>
      <GreetingPage />
    </MemoryRouter>,
  );
  return { authLogout };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('GreetingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
  });

  it('fetches greeting from ApiClient on mount', async () => {
    mockGetHello.mockResolvedValue({ message: 'Hello, Alice!', status: 'ok' });
    renderGreetingPage();

    await waitFor(() => {
      expect(mockGetHello).toHaveBeenCalledWith('jwt-token');
    });
  });

  it('displays the greeting message on success', async () => {
    mockGetHello.mockResolvedValue({ message: 'Hello, Alice!', status: 'ok' });
    renderGreetingPage();

    await waitFor(() => {
      expect(screen.getByText(/Hello, Alice!/i)).toBeInTheDocument();
    });
  });

  it('renders a logout button', async () => {
    mockGetHello.mockResolvedValue({ message: 'Hello, Alice!', status: 'ok' });
    renderGreetingPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /log.?out|sign.?out/i })).toBeInTheDocument();
    });
  });

  it('calls AuthContext.logout when logout button is clicked', async () => {
    mockGetHello.mockResolvedValue({ message: 'Hello, Alice!', status: 'ok' });
    const { authLogout } = renderGreetingPage();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /log.?out|sign.?out/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /log.?out|sign.?out/i }));
    expect(authLogout).toHaveBeenCalled();
  });

  it('shows an error message when the API call fails', async () => {
    mockGetHello.mockRejectedValue(new Error('Unauthorized'));
    renderGreetingPage();

    await waitFor(() => {
      expect(
        screen.getByRole('alert') ?? screen.getByText(/error|failed|unauthorized/i)
      ).toBeInTheDocument();
    });
  });
});
