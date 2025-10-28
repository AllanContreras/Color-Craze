import React, { useEffect, useRef, useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import api from '../api'
import { createStompClient } from '../ws'

export default function Lobby(){
  const nav = useNavigate()
  const location = useLocation()
  const [code, setCode] = useState('')
  // Color is no longer selectable; it will be auto-assigned by the server
  const [color] = useState('')
  const [avatar, setAvatar] = useState('ROBOT')
  const [theme, setTheme] = useState(null) // 'metal' | 'cyber' | 'moon'
  const [creating, setCreating] = useState(false)
  // No color availability tracking needed anymore
  const [usedColors, setUsedColors] = useState([])
  const [playersInRoom, setPlayersInRoom] = useState([])
  const [joinCountdown, setJoinCountdown] = useState(null)
  const stompRef = useRef(null)
  const playerIdRef = useRef(localStorage.getItem('cc_userId') || '')

  // Reflect ?code=... from URL into local state; update on navigation
  useEffect(()=>{
    const params = new URLSearchParams(location.search)
    const c = params.get('code')
    if (c && c.length === 6) setCode(c.toUpperCase())
  },[location.search])

  // Cuando el código tiene 6 caracteres, consultar la sala para saber colores ocupados y deadline
  useEffect(()=>{
    let timer
    const fetchRoom = async () => {
      if (!code || code.length !== 6) { setUsedColors([]); setPlayersInRoom([]); setJoinCountdown(null); return }
      try{
        const res = await api.get(`/api/games/${code}`)
        const players = res.data.players || []
        setPlayersInRoom(players)
        setTheme(res.data.theme || null)
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
            if (typeof body.theme !== 'undefined') setTheme(body.theme || null)
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
    if (creating) return
    setCreating(true)
    // Ensure identity exists
    let playerId = localStorage.getItem('cc_userId')
    if (!playerId || playerId.trim() === ''){
      playerId = `guest_${Math.random().toString(36).slice(2,8)}`
      localStorage.setItem('cc_userId', playerId)
    }
    let nickname = localStorage.getItem('cc_nickname')
    if (!nickname || nickname.trim() === ''){
      nickname = playerId
      localStorage.setItem('cc_nickname', nickname)
    }
    try {
      const res = await api.post('/api/games')
      const newCode = res.data.code
      setCode(newCode)
      // auto-join; color will be assigned by the server
      try{
        await api.post(`/api/games/${newCode}/join`, { playerId, nickname, avatar })
      }catch(err){
        alert(err?.response?.data?.message || 'No se pudo unir a la sala')
        return
      }
      // If a theme was chosen before creating, apply it now as host
      try{
        if (theme) {
          await api.post(`/api/games/${newCode}/theme`, { playerId, theme })
        }
      }catch{ /* non-blocking */ }
      // Stay in lobby so the host can configure the theme
      nav(`/lobby?code=${newCode}`)
    } catch (err) {
      alert(err?.response?.data?.message || 'No se pudo crear la sala')
    } finally {
      setCreating(false)
    }
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

  const isHost = playersInRoom.length > 0 && playersInRoom[0]?.playerId === playerIdRef.current
  const inRoom = playersInRoom.length > 0

  const applyTheme = async (newTheme) => {
    setTheme(newTheme)
    // If not in a room yet, just persist locally; apply on createGame
    if (!code || code.length !== 6 || !inRoom) return
    try{
      await api.post(`/api/games/${code}/theme`, { playerId: playerIdRef.current, theme: newTheme })
    }catch(err){
      alert(err.response?.data?.message || 'No se pudo cambiar el estilo (solo el creador puede)')
    }
  }

  return (
    <div>
      <h3>Lobby</h3>
      {/* Theme selection: before joining/creating it's open for anyone; once inside a room, only the host can change it */}
      <div style={{marginBottom:12}}>
        <label style={{marginRight:8}}>Estilo de la partida:</label>
        <select value={theme || ''} onChange={(e)=> applyTheme(e.target.value)} disabled={inRoom && !isHost}>
          <option value="" disabled>{theme ? '(seleccionado)' : '(elige uno)'}</option>
          <option value="metal">Metal</option>
          <option value="cyber">Cyberpunk</option>
          <option value="moon">Luna</option>
        </select>
        {inRoom && !isHost ? (
          <span style={{marginLeft:8, color:'#666'}}>(El creador elige el estilo)</span>
        ) : (
          <span style={{marginLeft:8, color:'#666'}}>(Puedes elegir antes de crear la sala)</span>
        )}
      </div>
      {/* Color selection removed: colors are assigned automatically by the server */}
      <div style={{marginBottom:12}}>
        <label style={{marginRight:8}}>Personaje:</label>
        {['ROBOT','COWBOY','ALIEN','WITCH'].map(a => (
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
  <button onClick={createGame} disabled={creating}>{creating ? 'Creando...' : 'Crear sala'}</button>
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
