import { render, screen, waitFor } from '@testing-library/react'
import App, { HelloService } from '../App'

describe('HelloService', () => {
  it('returns message from API response', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ message: 'Hello, World!' }),
    } as Response)

    const service = new HelloService()
    const message = await service.fetchMessage()
    expect(message).toBe('Hello, World!')
  })

  it('throws on non-ok response', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 500,
    } as Response)

    const service = new HelloService()
    await expect(service.fetchMessage()).rejects.toThrow('Request failed with status 500')
  })
})

describe('App component', () => {
  it('renders loading state initially', () => {
    global.fetch = jest.fn().mockReturnValue(new Promise(() => {}))
    render(<App />)
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('renders message after successful fetch', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ message: 'Hello, World!' }),
    } as Response)

    render(<App />)
    await waitFor(() => {
      expect(screen.getByText('Hello, World!')).toBeInTheDocument()
    })
    expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
  })

  it('renders error message on failed fetch', async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error('Network error'))

    render(<App />)
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
    expect(screen.getByText(/Network error/)).toBeInTheDocument()
  })

  it('calls fetch once on mount with correct URL', async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ message: 'Hello, World!' }),
    } as Response)

    render(<App />)
    await waitFor(() => screen.getByText('Hello, World!'))
    expect(global.fetch).toHaveBeenCalledTimes(1)
    expect(global.fetch).toHaveBeenCalledWith('http://localhost:5000/api/hello')
  })
})
