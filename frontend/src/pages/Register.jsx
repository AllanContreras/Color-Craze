import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'

export default function Register(){
  const nav = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const submit = async (e) => {
    e.preventDefault()
    setLoading(true); setError(null)
    try{
      const res = await api.post('/api/auth/register', { email, password, nickname })
      localStorage.setItem('cc_token', res.data.token)
      localStorage.setItem('cc_userId', res.data.userData.id)
      localStorage.setItem('cc_nickname', res.data.userData.nickname)
      nav('/lobby')
    }catch(err){
      setError(err.response?.data?.message || err.message)
    }finally{ setLoading(false) }
  }

  return (
    <div>
      <h3>Registro</h3>
      <form onSubmit={submit}>
        <div><input placeholder="Email" value={email} onChange={e=>setEmail(e.target.value)} /></div>
        <div><input placeholder="Contraseña" type="password" value={password} onChange={e=>setPassword(e.target.value)} /></div>
        <div><input placeholder="Nickname" value={nickname} onChange={e=>setNickname(e.target.value)} /></div>
        <div style={{marginTop:8}}>
          <button type="submit" disabled={loading}>Crear cuenta</button>
        </div>
        {error && <div style={{color:'salmon', marginTop:8}}>{error}</div>}
      </form>
    </div>
  )
}
