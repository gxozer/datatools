/**
 * AppRoutes.tsx — Top-level route definitions.
 *
 * Renders inside a <BrowserRouter> (provided by main.tsx). The root path /
 * redirects to /hello for authenticated users and /login otherwise.
 * Authenticated routes are wrapped with ProtectedRoute.
 */

import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { GreetingPage } from './pages/GreetingPage';

export function AppRoutes() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      {/* Root: redirect based on auth state */}
      <Route
        path="/"
        element={<Navigate to={isAuthenticated ? '/hello' : '/login'} replace />}
      />

      {/* Public routes */}
      <Route path="/login" element={
        isAuthenticated ? <Navigate to="/hello" replace /> : <LoginPage />
      } />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/forgot" element={<ForgotPasswordPage />} />
      <Route path="/reset" element={<ResetPasswordPage />} />

      {/* Protected routes */}
      <Route path="/hello" element={
        <ProtectedRoute><GreetingPage /></ProtectedRoute>
      } />
    </Routes>
  );
}
