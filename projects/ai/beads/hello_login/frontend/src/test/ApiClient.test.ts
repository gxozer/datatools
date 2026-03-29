/**
 * ApiClient.test.ts — Unit tests for ApiClient.
 *
 * Uses vi.stubGlobal to mock the global fetch so no real HTTP calls are made.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiClient, AuthError } from '../api/ApiClient';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mockFetch(ok: boolean, body: unknown, status = ok ? 200 : 401, statusText = '') {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok,
      status,
      statusText,
      json: () => Promise.resolve(body),
    }),
  );
}

// ---------------------------------------------------------------------------
// getHello
// ---------------------------------------------------------------------------

describe('ApiClient.getHello', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('returns the parsed JSON response on success', async () => {
    const mockPayload = { message: 'Hello, Test User!', status: 'ok' };
    mockFetch(true, mockPayload, 200);

    const result = await ApiClient.getHello('test-token');

    expect(result).toEqual(mockPayload);
    expect(fetch).toHaveBeenCalledWith('/api/hello', {
      headers: { Authorization: 'Bearer test-token' },
    });
  });

  it('throws AuthError on 401', async () => {
    mockFetch(false, { error: 'Unauthorized' }, 401, 'Unauthorized');

    await expect(ApiClient.getHello('bad-token')).rejects.toBeInstanceOf(AuthError);
  });

  it('throws a generic Error when the response is not ok and not 401', async () => {
    mockFetch(false, {}, 500, 'Internal Server Error');

    await expect(ApiClient.getHello('test-token')).rejects.toThrow(
      'Request failed: 500 Internal Server Error',
    );
    await expect(ApiClient.getHello('test-token')).rejects.not.toBeInstanceOf(AuthError);
  });
});

// ---------------------------------------------------------------------------
// login
// ---------------------------------------------------------------------------

describe('ApiClient.login', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('POSTs email and password to /api/login and returns the token', async () => {
    const payload = { token: 'jwt-abc', status: 'ok' };
    mockFetch(true, payload, 200);

    const result = await ApiClient.login('user@example.com', 'secret');

    expect(result).toEqual(payload);
    expect(fetch).toHaveBeenCalledWith('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'user@example.com', password: 'secret' }),
    });
  });

  it('throws an Error on non-ok response', async () => {
    mockFetch(false, { error: 'Invalid email or password' }, 401, 'Unauthorized');

    await expect(ApiClient.login('x@x.com', 'wrong')).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// signup
// ---------------------------------------------------------------------------

describe('ApiClient.signup', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('POSTs email, password, and full_name to /api/signup', async () => {
    const payload = { status: 'ok' };
    mockFetch(true, payload, 201);

    const result = await ApiClient.signup('Alice', 'alice@example.com', 'pass123');

    expect(result).toEqual(payload);
    expect(fetch).toHaveBeenCalledWith('/api/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ full_name: 'Alice', email: 'alice@example.com', password: 'pass123' }),
    });
  });

  it('throws an Error on non-ok response', async () => {
    mockFetch(false, { error: 'Email already registered' }, 409, 'Conflict');

    await expect(ApiClient.signup('Alice', 'alice@example.com', 'pass')).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// logout
// ---------------------------------------------------------------------------

describe('ApiClient.logout', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('POSTs to /api/logout with the Authorization header', async () => {
    mockFetch(true, { status: 'ok' }, 200);

    await ApiClient.logout('my-jwt');

    expect(fetch).toHaveBeenCalledWith('/api/logout', {
      method: 'POST',
      headers: { Authorization: 'Bearer my-jwt' },
    });
  });

  it('throws an Error on non-ok response', async () => {
    mockFetch(false, {}, 500, 'Internal Server Error');

    await expect(ApiClient.logout('my-jwt')).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// requestPasswordReset
// ---------------------------------------------------------------------------

describe('ApiClient.requestPasswordReset', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('POSTs email to /api/password-reset/request', async () => {
    const payload = { message: 'If that email is registered, a reset link has been sent.', status: 'ok' };
    mockFetch(true, payload, 200);

    const result = await ApiClient.requestPasswordReset('user@example.com');

    expect(result).toEqual(payload);
    expect(fetch).toHaveBeenCalledWith('/api/password-reset/request', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'user@example.com' }),
    });
  });

  it('throws an Error on non-ok response', async () => {
    mockFetch(false, { error: 'email is required' }, 400, 'Bad Request');

    await expect(ApiClient.requestPasswordReset('')).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// confirmPasswordReset
// ---------------------------------------------------------------------------

describe('ApiClient.confirmPasswordReset', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('POSTs token and password to /api/password-reset/confirm', async () => {
    const payload = { message: 'Password has been reset.', status: 'ok' };
    mockFetch(true, payload, 200);

    const result = await ApiClient.confirmPasswordReset('raw-token-xyz', 'newpass123');

    expect(result).toEqual(payload);
    expect(fetch).toHaveBeenCalledWith('/api/password-reset/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: 'raw-token-xyz', password: 'newpass123' }),
    });
  });

  it('throws an Error on non-ok response', async () => {
    mockFetch(false, { error: 'Invalid or expired reset token' }, 400, 'Bad Request');

    await expect(ApiClient.confirmPasswordReset('bad-token', 'newpass')).rejects.toThrow();
  });
});
