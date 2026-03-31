/**
 * App.tsx — Root component.
 *
 * Wraps the application in AuthProvider and renders the route tree.
 */

import { AuthProvider } from './context/AuthContext';
import { AppRoutes } from './AppRoutes';
import './App.css';

export default function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  );
}
