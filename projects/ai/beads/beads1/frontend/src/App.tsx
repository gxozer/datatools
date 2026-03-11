import { useState, useEffect } from 'react'

const API_URL = 'http://localhost:5001/api/hello'

class HelloService {
  async fetchMessage(): Promise<string> {
    const response = await fetch(API_URL)
    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }
    const data = await response.json()
    return data.message
  }
}

const helloService = new HelloService()

function App() {
  const [message, setMessage] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    helloService
      .fetchMessage()
      .then((msg) => {
        setMessage(msg)
      })
      .catch((err: Error) => {
        setError(err.message)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  return (
    <main>
      <h1>Hello World App</h1>
      {loading && <p>Loading...</p>}
      {error && <p role="alert">Error: {error}</p>}
      {message && <p>{message}</p>}
    </main>
  )
}

export default App
export { HelloService }
