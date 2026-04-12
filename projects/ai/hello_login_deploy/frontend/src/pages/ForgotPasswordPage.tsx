import React, { useState } from 'react';
import { ApiClient } from '../api/ApiClient';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    try {
      await ApiClient.requestPasswordReset(email);
    } catch {
      // Intentionally swallowed — always show success to avoid email enumeration.
    }
    setSubmitted(true);
  }

  if (submitted) {
    return (
      <main>
        <p>Check your email — if an account exists, we sent a reset link.</p>
      </main>
    );
  }

  return (
    <main>
      <h1>Forgot Password</h1>
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
        <button type="submit">Send Reset Link</button>
      </form>
    </main>
  );
}
