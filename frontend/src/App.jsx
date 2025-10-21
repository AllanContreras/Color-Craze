import React from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import Login from './pages/Login'
import Register from './pages/Register'
import Lobby from './pages/Lobby'
import Game from './pages/Game'
import Results from './pages/Results'

export default function App(){
  return (
    <div className="app-shell">
      <div className="container">
        <div className="card">
          <header>
            <h2>Color Craze</h2>
            <nav>
              <Link to="/">Login</Link>
              <Link to="/lobby" style={{marginLeft:12}}>Lobby</Link>
            </nav>
          </header>

          <Routes>
            <Route path="/" element={<Login/>} />
            <Route path="/register" element={<Register/>} />
            <Route path="/lobby" element={<Lobby/>} />
            <Route path="/game/:code" element={<Game/>} />
            <Route path="/results/:code" element={<Results/>} />
          </Routes>
        </div>
      </div>
    </div>
  )
}
