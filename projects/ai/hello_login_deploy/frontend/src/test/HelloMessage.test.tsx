/**
 * HelloMessage.test.tsx — Unit tests for the HelloMessage component.
 *
 * Tests that the component correctly renders its message prop.
 */

import { render, screen } from '@testing-library/react';
import HelloMessage from '../components/HelloMessage';

describe('HelloMessage', () => {
  it('renders the message prop in an h1', () => {
    render(<HelloMessage message="Hello, World!" />);
    const heading = screen.getByRole('heading', { level: 1 });
    expect(heading).toBeInTheDocument();
    expect(heading).toHaveTextContent('Hello, World!');
  });

  it('renders with a custom message', () => {
    render(<HelloMessage message="Greetings from Flask!" />);
    expect(screen.getByText('Greetings from Flask!')).toBeInTheDocument();
  });

  it('wraps the heading in a hello-message div', () => {
    const { container } = render(<HelloMessage message="Hello, World!" />);
    expect(container.firstChild).toHaveClass('hello-message');
  });
});
