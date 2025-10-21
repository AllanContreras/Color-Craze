import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'

export default function Lobby(){
  const nav = useNavigate()
  const [code, setCode] = useState('')

  const createGame = async () => {
    const res = await api.post('/api/games')
    nav(`/game/${res.data.code}`)
  }

  const joinGame = async () => {
    const playerId = localStorage.getItem('cc_userId')
    const nickname = localStorage.getItem('cc_nickname')
    await api.post(`/api/games/${code}/join`, { playerId, nickname })
    nav(`/game/${code}`)
  }

  return (
    <div>
      <h3>Lobby</h3>
      <button onClick={createGame}>Crear sala</button>
      <hr />
      <input value={code} onChange={e=>setCode(e.target.value)} placeholder="Código" />
      <button onClick={joinGame}>Unirse</button>
    </div>
  )
}
