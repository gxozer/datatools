/**
 * ApiClient.test.ts — Unit tests for ApiClient.
 *
 * Uses vi.stubGlobal to mock the global fetch so no real HTTP calls are made.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiClient } from '../api/ApiClient';

describe('ApiClient.getHello', () => {
  beforeEach(() => {
    // Reset all mocks before each test
    vi.restoreAllMocks();
  });

  it('returns the parsed JSON response on success', async () => {
    // Arrange: mock fetch to return a successful response
    const mockPayload = { message: 'Hello, World!', status: 'ok' };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockPayload),
    }));

    // Act
    const result = await ApiClient.getHello();

    // Assert
    expect(result).toEqual(mockPayload);
    expect(fetch).toHaveBeenCalledWith('/api/hello');
  });

  it('throws an Error when the response is not ok', async () => {
    // Arrange: mock fetch to return a 500 error
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
    }));

    // Act & Assert
    await expect(ApiClient.getHello()).rejects.toThrow(
      'Request failed: 500 Internal Server Error'
    );
  });
});
