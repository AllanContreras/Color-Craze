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
  const [showPwd, setShowPwd] = useState(false)

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

  return (
    <div className="page-section">
      <div className="card card-lg">
        <header style={{marginBottom:12}}>
          <h2 style={{margin:0}}>Color Craze</h2>
        </header>
        <h3 style={{marginTop:0}}>Iniciar sesión</h3>
        <form onSubmit={submit} className="form-grid">
          <div className="form-row">
            <label className="form-label">Email</label>
            <input type="email" autoComplete="email" placeholder="tucorreo@ejemplo.com" value={email} onChange={e=>setEmail(e.target.value)} />
          </div>
          <div className="form-row">
            <label className="form-label">Contraseña</label>
            <div style={{display:'flex', gap:8, alignItems:'center'}}>
              <input style={{flex:1}} placeholder="••••••••" type={showPwd ? 'text' : 'password'} autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} />
              <button type="button" className="btn-secondary" onClick={()=>setShowPwd(s=>!s)}>{showPwd ? 'Ocultar' : 'Ver'}</button>
            </div>
          </div>
          <div className="form-actions">
            <button type="submit" disabled={loading}>Entrar</button>
            <span className="muted">o <a href="/register">registrarse</a></span>
          </div>
        </form>
        <hr style={{margin:"14px 0", borderColor:"rgba(255,255,255,0.08)"}}/>
        <div className="form-grid">
          <div className="form-row">
            <label className="form-label">Nombre de jugador (invitado)</label>
            <input placeholder="Tu nick" value={guestNick} onChange={e=>setGuestNick(e.target.value)} />
          </div>
          <div className="form-actions">
            <button onClick={guest} disabled={loading}>Entrar como invitado</button>
          </div>
        </div>
        {error && <div className="error" style={{marginTop:8}}>{error}</div>}
      </div>
    </div>
  )
}
