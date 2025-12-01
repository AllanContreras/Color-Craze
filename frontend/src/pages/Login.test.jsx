import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Login from './Login.jsx'

describe('Login.jsx', () => {
  it('shows form elements', () => {
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    )
    // weak assertions to avoid coupling
    const inputs = screen.getAllByRole('textbox')
    expect(inputs.length).toBeGreaterThanOrEqual(1)
  })
})
