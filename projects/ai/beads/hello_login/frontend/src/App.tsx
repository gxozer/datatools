/**
 * App.tsx — Root component.
 *
 * Manages the fetch lifecycle (loading / success / error) for the Hello World
 * message from the backend API. Delegates rendering to HelloMessage.
 */

import { useState, useEffect } from 'react';
import HelloMessage from './components/HelloMessage';
import { ApiClient } from './api/ApiClient';
import './App.css';

/**
 * App is the root component. It fetches the personalised greeting on mount
 * if a JWT is present in localStorage, otherwise prompts the user to log in.
 *
 * The full login UI (AuthContext, LoginPage) is being built separately and
 * will replace the localStorage read once it lands.
 */
function App() {
  // The greeting message returned by the backend
  const [message, setMessage] = useState<string | null>(null);

  // True while the fetch is in-flight
  const [loading, setLoading] = useState<boolean>(true);

  // Holds an error string if the fetch fails
  const [error, setError] = useState<string | null>(null);

  // Fetch the greeting once on mount, only if a token is available
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      setLoading(false);
      return;
    }
    ApiClient.getHello(token)
      .then((data) => setMessage(data.message))
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <p className="status">Loading…</p>;
  }

  if (error) {
    return <p className="status error">Error: {error}</p>;
  }

  if (!message) {
    return <p className="status">Please log in to see your greeting.</p>;
  }

  return (
    <main className="app">
      <HelloMessage message={message} />
    </main>
  );
}

export default App;
