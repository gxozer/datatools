import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiClient } from '../api/ApiClient';
import { useAuth } from '../context/AuthContext';

export function SignupPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const { login } = useAuth();
  const navigate = useNavigate();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const data = await ApiClient.signup(fullName, email, password);
      login(data.token);
      navigate('/hello');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Signup failed');
    }
  }

  return (
    <main>
      <h1>Sign Up</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="fullName">Full Name</label>
          <input
            id="fullName"
            type="text"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            required
          />
        </div>
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
        <button type="submit">Sign Up</button>
      </form>
    </main>
  );
}
