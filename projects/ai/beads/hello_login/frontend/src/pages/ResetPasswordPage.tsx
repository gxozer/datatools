import React, { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiClient } from '../api/ApiClient';

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await ApiClient.confirmPasswordReset(token, password);
      navigate('/login');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Reset failed');
    }
  }

  return (
    <main>
      <h1>Reset Password</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="password">New Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        {error && <div role="alert">{error}</div>}
        <button type="submit">Reset Password</button>
      </form>
    </main>
  );
}
