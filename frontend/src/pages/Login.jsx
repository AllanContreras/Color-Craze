import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'

export default function Login(){
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [guestNick, setGuestNick] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const guest = async () => {
    setLoading(true); setError(null)
    try{
      const res = await api.post('/api/auth/guest')
      localStorage.setItem('cc_token', res.data.token)
      localStorage.setItem('cc_userId', res.data.userData.id)
      const nick = (guestNick || '').trim() || res.data.userData.nickname
      localStorage.setItem('cc_nickname', nick)
      nav('/lobby')
    }catch(err){ setError(err.message) }finally{ setLoading(false) }
  }

  const submit = async (e) => {
    e.preventDefault(); setLoading(true); setError(null)
    try{
      const res = await api.post('/api/auth/login', { email, password })
      localStorage.setItem('cc_token', res.data.token)
      localStorage.setItem('cc_userId', res.data.userData.id)
      localStorage.setItem('cc_nickname', res.data.userData.nickname)
      nav('/lobby')
    }catch(err){ setError(err.response?.data?.message || err.message) }finally{ setLoading(false) }
  }

  const bgStyle = {
    minHeight: '100vh',
    background: "url('/assets/login-lobby-bg.png') center/cover no-repeat, linear-gradient(180deg, #0f172a 0%, #0b1220 100%)",
    display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24
  }
  const cardStyle = { width: '100%', maxWidth: 720 }

  return (
    <div style={bgStyle}>
      <div className="card" style={cardStyle}>
        <h3>Login</h3>
        <form onSubmit={submit}>
          <div><input placeholder="Email" value={email} onChange={e=>setEmail(e.target.value)} /></div>
          <div><input placeholder="Contraseña" type="password" value={password} onChange={e=>setPassword(e.target.value)} /></div>
          <div style={{marginTop:8}}>
            <button type="submit" disabled={loading}>Entrar</button>
          </div>
        </form>
        <div style={{marginTop:12}}>
          <div style={{marginBottom:8}}>
            <input placeholder="Nombre de jugador (invitado)" value={guestNick} onChange={e=>setGuestNick(e.target.value)} />
          </div>
          <button onClick={guest} disabled={loading}>Entrar como invitado</button>
          <p style={{marginTop:8}}>o <a href="/register">registrarse</a></p>
        </div>
        {error && <div style={{color:'salmon', marginTop:8}}>{error}</div>}
      </div>
    </div>
  )
}
