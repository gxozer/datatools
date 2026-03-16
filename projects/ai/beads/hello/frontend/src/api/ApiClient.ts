/**
 * ApiClient.ts — HTTP client for the Flask backend API.
 *
 * Encapsulates all backend communication in a single class so that
 * components never call fetch() directly. This makes mocking straightforward
 * in unit tests.
 */

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
  /**
   * Fetch the Hello World greeting from the backend.
   *
   * The Vite dev proxy forwards /api/* requests to http://localhost:5000,
   * so no base URL is needed in development.
   *
   * @returns A promise resolving to the HelloResponse payload.
   * @throws An Error with a descriptive message if the request fails.
   */
  static async getHello(): Promise<HelloResponse> {
    const response = await fetch('/api/hello');

    if (!response.ok) {
      throw new Error(`Request failed: ${response.status} ${response.statusText}`);
    }

    return response.json() as Promise<HelloResponse>;
  }
}
