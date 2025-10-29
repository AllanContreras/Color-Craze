import React from 'react'
import { Routes, Route, Link, useLocation, useNavigate } from 'react-router-dom'
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
    try{
      localStorage.removeItem('cc_token')
      localStorage.removeItem('cc_userId')
      localStorage.removeItem('cc_nickname')
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
