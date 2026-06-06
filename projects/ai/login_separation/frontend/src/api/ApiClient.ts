/**
 * ApiClient.ts — HTTP client for the Flask backend API.
 *
 * Encapsulates all backend communication in a single class so that
 * components never call fetch() directly. This makes mocking straightforward
 * in unit tests.
 */

/** Thrown when the server returns HTTP 401 Unauthorized. */
export class AuthError extends Error {
  constructor(message = 'Authentication required') {
    super(message);
    this.name = 'AuthError';
  }
}

/** Shape of the response returned by GET /api/hello */
export interface HelloResponse {
  message: string;
  status: string;
}

/**
 * ApiClient provides static methods for each backend endpoint.
 * Using a class (rather than bare functions) makes it easy to swap in
 * a mock implementation during testing.
 */
export class ApiClient {
  private static async _handleResponse<T>(response: Response): Promise<T> {
    if (response.status === 401) {
      throw new AuthError();
    }
    if (!response.ok) {
      throw new Error(`Request failed: ${response.status} ${response.statusText}`);
    }
    return response.json() as Promise<T>;
  }

  /**
   * Fetch the personalised greeting from the backend.
   *
   * @param token - A valid JWT to send in the Authorization header.
   */
  static async getHello(token: string): Promise<HelloResponse> {
    const response = await fetch('/api/hello', {
      headers: { Authorization: `Bearer ${token}` },
    });
    return ApiClient._handleResponse<HelloResponse>(response);
  }

  /**
   * Authenticate a user and return a JWT.
   */
  static async login(email: string, password: string): Promise<{ token: string; status: string }> {
    const response = await fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    });
    return ApiClient._handleResponse(response);
  }

  /**
   * Register a new user account.
   */
  static async signup(fullName: string, email: string, password: string): Promise<{ token: string; status: string }> {
    const response = await fetch('/api/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ full_name: fullName, email, password }),
    });
    return ApiClient._handleResponse(response);
  }

  /**
   * Log out the current user (invalidates session server-side if applicable).
   *
   * @param token - A valid JWT to send in the Authorization header.
   */
  static async logout(token: string): Promise<void> {
    const response = await fetch('/api/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });
    return ApiClient._handleResponse(response);
  }

  /**
   * Request a password reset email.
   */
  static async requestPasswordReset(email: string): Promise<{ message: string; status: string }> {
    const response = await fetch('/api/password-reset/request', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    });
    return ApiClient._handleResponse(response);
  }

  /**
   * Confirm a password reset with a token and new password.
   */
  static async confirmPasswordReset(token: string, password: string): Promise<{ message: string; status: string }> {
    const response = await fetch('/api/password-reset/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, password }),
    });
    return ApiClient._handleResponse(response);
  }
}
