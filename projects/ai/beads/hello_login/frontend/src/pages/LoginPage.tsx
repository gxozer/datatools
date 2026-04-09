import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiClient } from '../api/ApiClient';
import { useAuth } from '../context/AuthContext';

export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const data = await ApiClient.login(email, password);
      login(data.token);
      navigate('/hello');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    }
  }

  return (
    <main>
      <h1>Log In</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div>
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        {error && <div role="alert">{error}</div>}
        <button type="submit">Log In</button>
      </form>
      <p>Don't have an account? <Link to="/signup">Sign up</Link></p>
      <p><Link to="/forgot-password">Forgot password?</Link></p>
    </main>
  );
}
