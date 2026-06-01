import React, { useEffect, useState } from 'react';
import { ApiClient } from '../api/ApiClient';
import { useAuth } from '../context/AuthContext';

export function GreetingPage() {
  const { logout, token } = useAuth();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    ApiClient.getHello(token)
      .then((data) => setMessage(data.message))
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load'));
  }, [token]);

  return (
    <main>
      <h1>Hello!</h1>
      {message && <p>{message}</p>}
      {error && <div role="alert">{error}</div>}
      <button type="button" onClick={logout}>Log Out</button>
    </main>
  );
}
