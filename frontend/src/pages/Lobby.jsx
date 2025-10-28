import React, { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'
import { createStompClient } from '../ws'

export default function Lobby(){
  const nav = useNavigate()
  const [code, setCode] = useState('')
  // Color is no longer selectable; it will be auto-assigned by the server
  const [color] = useState('')
  const [avatar, setAvatar] = useState('ROBOT')
  // No color availability tracking needed anymore
  const [usedColors, setUsedColors] = useState([])
  const [playersInRoom, setPlayersInRoom] = useState([])
  const [joinCountdown, setJoinCountdown] = useState(null)
  const stompRef = useRef(null)
  const playerIdRef = useRef(localStorage.getItem('cc_userId') || '')

  // Prefill code from query param if present
  useEffect(()=>{
    const params = new URLSearchParams(window.location.search)
    const c = params.get('code')
    if (c && c.length === 6) setCode(c.toUpperCase())
  },[])

  // Cuando el código tiene 6 caracteres, consultar la sala para saber colores ocupados y deadline
  useEffect(()=>{
    let timer
    const fetchRoom = async () => {
      if (!code || code.length !== 6) { setUsedColors([]); setPlayersInRoom([]); setJoinCountdown(null); return }
      try{
        const res = await api.get(`/api/games/${code}`)
        const players = res.data.players || []
        setPlayersInRoom(players)
  // Keep players list for display; ignore color availability UI
        // If I'm already in the room, reflect my current selection
        const me = players.find(p => p.playerId === playerIdRef.current)
        if (me){
          if (!avatar) setAvatar(me.avatar || 'ROBOT')
        }
        const deadline = res.data.joinDeadlineMs
        if (deadline) {
          const update = () => {
            const ms = Math.max(0, deadline - Date.now())
            setJoinCountdown(Math.ceil(ms / 1000))
          }
          update()
          timer = setInterval(update, 1000)
        } else {
          setJoinCountdown(null)
        }
        // Si ya está en PLAYING, entrar a la pantalla de juego
        if (res.data.status === 'PLAYING') {
          nav(`/game/${code}`)
        }
      }catch(err){
        setUsedColors([]); setPlayersInRoom([]); setJoinCountdown(null)
      }
    }
    fetchRoom()
    return ()=>{ if (timer) clearInterval(timer) }
  },[code])

  // Suscribirse por WS al estado de la sala para navegar automáticamente cuando cambie a PLAYING
  useEffect(()=>{
    if (!code || code.length !== 6) { if (stompRef.current) { stompRef.current.deactivate(); stompRef.current=null } ; return }
    const c = createStompClient((stomp)=>{
      stomp.subscribe(`/topic/board/${code}/state`, m => {
        try{
          const body = JSON.parse(m.body)
          if (body.status === 'PLAYING' || body.startTimestamp) {
            nav(`/game/${code}`)
            return
          }
          if (body.status === 'WAITING'){
            const players = body.players || []
            setPlayersInRoom(players)
            const me = players.find(p => p.playerId === playerIdRef.current)
            if (me){
              // Preselect my current avatar if not chosen yet
              if (!avatar) setAvatar(me.avatar || 'ROBOT')
            }
            if (body.joinDeadlineMs){
              const ms = Math.max(0, body.joinDeadlineMs - Date.now())
              setJoinCountdown(Math.ceil(ms/1000))
            }
          }
        }catch{}
      })
    })
    stompRef.current = c
    return ()=>{ if (stompRef.current) { stompRef.current.deactivate(); stompRef.current=null } }
  },[code, nav])

  const createGame = async () => {
  const res = await api.post('/api/games')
    const newCode = res.data.code
    // auto-join; color will be assigned by server
    const playerId = localStorage.getItem('cc_userId')
    const nickname = localStorage.getItem('cc_nickname')
    try{
  await api.post(`/api/games/${newCode}/join`, { playerId, nickname, avatar })
    }catch(err){
      alert(err.response?.data?.message || 'No se pudo unir a la sala')
      return
    }
    nav(`/game/${newCode}`)
  }

  const joinGame = async () => {
    const playerId = playerIdRef.current
    const nickname = localStorage.getItem('cc_nickname')
    // If already in room, update avatar instead of re-joining
    const already = playersInRoom.some(p => p.playerId === playerId)
    if (already){
      try{
        await api.post(`/api/games/${code}/player`, { playerId, avatar })
        alert('Avatar actualizado')
      }catch(err){
        alert(err.response?.data?.message || 'No se pudo actualizar')
      }
      return
    }
    try{
      await api.post(`/api/games/${code}/join`, { playerId, nickname, avatar })
    }catch(err){
      alert(err.response?.data?.message || 'No se pudo unir a la sala')
      return
    }
    nav(`/game/${code}`)
  }

  return (
    <div>
      <h3>Lobby</h3>
      {/* Color selection removed: colors are assigned automatically by the server */}
      <div style={{marginBottom:12}}>
        <label style={{marginRight:8}}>Personaje:</label>
        {['ROBOT','COWBOY','ALIEN','COWGIRL'].map(a => (
          <label key={a} style={{marginRight:8}}>
            <input type="radio" name="avatar" value={a} checked={avatar===a} onChange={()=>setAvatar(a)} /> {a}
          </label>
        ))}
      </div>
      {joinCountdown !== null && (
        <div style={{marginBottom:8}}>Tiempo para unirse: {joinCountdown}s</div>
      )}
      {playersInRoom.length > 0 && (
        <div style={{marginBottom:8}}>
          <strong>Jugadores en sala:</strong>
          <ul>
            {playersInRoom.map(p => (
              <li key={p.playerId}>{p.nickname || p.playerId}: {p.color}</li>
            ))}
          </ul>
        </div>
      )}
      <button onClick={createGame}>Crear sala</button>
      <hr />
      <input value={code} onChange={e=>setCode(e.target.value)} placeholder="Código" />
      {/* Show join or update depending on membership */}
      {playersInRoom.some(p => p.playerId === playerIdRef.current) ? (
  <button onClick={joinGame}>Actualizar avatar</button>
      ) : (
        <button onClick={joinGame}>Unirse</button>
      )}
    </div>
  )
}
