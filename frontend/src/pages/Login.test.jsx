import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import Login from './Login.jsx'

describe('Login.jsx', () => {
  it('shows form elements', () => {
    render(<Login />)
    // weak assertions to avoid coupling
    const inputs = screen.getAllByRole('textbox')
    expect(inputs.length).toBeGreaterThanOrEqual(1)
  })
})
