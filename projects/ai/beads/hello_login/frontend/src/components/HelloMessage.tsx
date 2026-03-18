/**
 * HelloMessage.tsx — Presentational component that displays the Hello World message.
 *
 * Receives the message string as a prop and renders it inside a styled container.
 * This component is intentionally stateless — all data fetching is handled by the parent.
 */

interface HelloMessageProps {
  /** The message string to display, e.g. "Hello, World!" */
  message: string;
}

/**
 * HelloMessage renders the greeting message returned by the backend API.
 */
function HelloMessage({ message }: HelloMessageProps) {
  return (
    <div className="hello-message">
      <h1>{message}</h1>
    </div>
  );
}

export default HelloMessage;
