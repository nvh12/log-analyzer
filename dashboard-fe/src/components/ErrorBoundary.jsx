import { Component } from 'react'

export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <div className="p-6 space-y-3">
          <h2 className="text-red-400 font-semibold">Something went wrong</h2>
          <pre className="mono text-xs text-red-300 bg-red-950/50 border border-red-800 rounded p-3 overflow-auto">
            {this.state.error.message}
          </pre>
          <button
            onClick={() => this.setState({ error: null })}
            className="mono text-xs px-3 py-1.5 rounded bg-gray-800 border border-gray-700 text-gray-300 hover:bg-gray-700"
          >
            Try again
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
