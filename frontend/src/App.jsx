import React from 'react'
import { Routes, Route, useLocation, useNavigate } from 'react-router-dom'
import Login from './pages/Login'
import Register from './pages/Register'
import Lobby from './pages/Lobby'
import Game from './pages/Game'
import Results from './pages/Results'

export default function App(){
  const location = useLocation()
  const nav = useNavigate()
  const showLogout = location.pathname !== '/' && location.pathname !== '/register'
  const logout = () => {
    const inGame = (()=>{ try { return localStorage.getItem('cc_isPlaying') === 'true' } catch { return false } })()
    // Solo pedir confirmación cuando estamos jugando en la arena
    if (inGame && location.pathname.startsWith('/game/')){
      const ok = window.confirm('¿Seguro que quieres abandonar la partida?')
      if (!ok) return
    }
    try{
      localStorage.removeItem('cc_token')
      localStorage.removeItem('cc_userId')
      localStorage.removeItem('cc_nickname')
      localStorage.removeItem('cc_isPlaying')
    }catch{}
    nav('/')
  }
  return (
    <div className="app-shell">
      <div className="container">
        <div className="card">
          <header>
            <h2>Color Craze</h2>
            {showLogout && (
              <button className="btn-logout" onClick={logout}>Salir</button>
            )}
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
