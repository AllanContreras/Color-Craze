import React, { useEffect, useState, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { createStompClient } from '../ws'
import api from '../api'

const ROWS = 15
const COLS = 31

function makeEmptyGrid(){
  const g = []
  for(let r=0;r<ROWS;r++){
    const row = new Array(COLS).fill(null)
    g.push(row)
  }
  return g
}

export default function Game(){
  const { code } = useParams()
  const [grid, setGrid] = useState(makeEmptyGrid)
  const [players, setPlayers] = useState([])
  const [playerCells, setPlayerCells] = useState({}) // key: "r,c" -> { playerId, color }
  const [messages, setMessages] = useState([])
  const [client, setClient] = useState(null)
  const [timeLeft, setTimeLeft] = useState(null)
  const [endStandings, setEndStandings] = useState(null)
  const [fullMode, setFullMode] = useState(false)
  const [joinLeft, setJoinLeft] = useState(null)
  const timerRef = useRef(null)
  const joinTimerRef = useRef(null)
  const playerColorMapRef = useRef(new Map()) // playerId -> color
  const playerAvatarMapRef = useRef(new Map()) // playerId -> avatar string
  const [canMove, setCanMove] = useState(false)
  const openedFullRef = useRef(false)
  const [ultraMode, setUltraMode] = useState(false)
  const navigate = useNavigate()
  // Arena (2D) mode state
  const [arenaMode, setArenaMode] = useState(false)
  const [arenaConfig, setArenaConfig] = useState(null) // { width, height, platforms:[{x,y,width,height,cells}] }
  const [arenaFrame, setArenaFrame] = useState(null)   // { players:[{playerId,x,y,onGround}], paint: { idx: [colors] } }
  const canvasRef = useRef(null)
  const inputRef = useRef({left:false,right:false,jump:false})
  const [arenaTheme, setArenaTheme] = useState(null) // 'metal' | 'cyber' | 'moon' (null => assign randomly on start)
  const [coverageByColor, setCoverageByColor] = useState({}) // color name -> percentage (0-100)
  // Cyberpunk visuals state
  const platformPatternRef = useRef(null) // CanvasPattern cache for platform texture
  const lastPaintRef = useRef({})         // Per-platform snapshot of previous paint colors
  const particlesRef = useRef([])         // Lightweight paint splash particles
  const lastThemeRef = useRef(null)
  const themeAssignedKeyRef = useRef(null) // code:startTimestamp guard for randomization
  const moonStarsRef = useRef(null)        // cached starfield for moon theme

  const pickRandomTheme = () => {
    const themes = ['metal','cyber','moon']
    return themes[Math.floor(Math.random()*themes.length)]
  }

  // Spawn a few particles when a cell gets painted to give it a cyber splash
  const spawnPaintParticles = (x, y, width, height, color) => {
    const out = particlesRef.current
    const n = (arenaTheme === 'cyber') ? 2 : 3
    for (let i=0;i<n;i++){
      const angle = Math.random()*Math.PI - Math.PI/2
      const speed = 30 + Math.random()*90
      out.push({
        x: x + width*0.5,
        y: y + height*0.5,
        vx: Math.cos(angle)*speed,
        vy: Math.sin(angle)*speed - 30,
        life: 320, // ms
        born: Date.now(),
        color
      })
    }
    // Keep the list bounded
    if (out.length > 300) out.splice(0, out.length-300)
  }

  useEffect(()=>{
    let c = createStompClient((stomp) => {
      stomp.subscribe(`/topic/board/${code}/state`, m => handleState(JSON.parse(m.body)))
      stomp.subscribe(`/topic/board/${code}`, m => handleMove(JSON.parse(m.body)))
      stomp.subscribe(`/topic/board/${code}/end`, m => handleEnd(JSON.parse(m.body)))
      stomp.subscribe(`/topic/board/${code}/arena`, m => {
        try{
          const fr = JSON.parse(m.body)
          setArenaFrame(fr)
          if (fr && fr.scores){
            setPlayers(prev => prev.map(p => ({...p, score: (fr.scores[p.playerId] ?? p.score ?? 0)})))
          }
        } catch {}
      })
    })
    setClient(c)
    return ()=> c.deactivate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  },[code])

  useEffect(()=>{
    return ()=>{
      if (timerRef.current) clearInterval(timerRef.current)
      if (joinTimerRef.current) clearInterval(joinTimerRef.current)
    }
  },[])

  // Keyboard controls: Arrow keys to move
  useEffect(()=>{
    const onKey = (e) => {
      let dir = null
      switch(e.key){
        case 'ArrowUp': dir = 'UP'; break
        case 'ArrowDown': dir = 'DOWN'; break
        case 'ArrowLeft': dir = 'LEFT'; break
        case 'ArrowRight': dir = 'RIGHT'; break
        default: break
      }
      if (dir){
        // Solo interceptar y mover cuando ya se puede mover
        // Evitar enviar movimientos del grid cuando estamos en modo arena
        if (canMove && !arenaMode){
          e.preventDefault()
          sendMove(dir)
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return ()=> window.removeEventListener('keydown', onKey)
  }, [client, canMove, arenaMode])

  // Cargar estado inicial (jugadores y countdown de unión) al entrar
  useEffect(()=>{
    const load = async () => {
      try{
        const res = await api.get(`/api/games/${code}`)
        if (res.data.players) setPlayers(res.data.players)
        if (res.data.joinDeadlineMs) startJoinTimer(res.data.joinDeadlineMs)
        const params = new URLSearchParams(window.location.search)
        const alreadyFull = params.get('full') === '1'
        if (alreadyFull) setUltraMode(true)
  if (res.data.status === 'PLAYING'){
          // activar modo grande en la misma ventana
          setFullMode(true)
          // permitir movimiento inmediato si ya está en juego
          setCanMove(true)
          // Fallback: si ya estamos en PLAYING y el cliente no alcanzó el WS inicial,
          // arranca el contador desde startedAtMs + durationMs del GET
          const startMs = res.data.startedAtMs
          const durMs = res.data.durationMs || 40000
          if (startMs){
            const endAt = startMs + durMs
            const now = Date.now()
            startTimer(Math.max(0, Math.floor((endAt - now)/1000)))
            // randomizar tema si aún no se eligió
            const key = `${code}:${startMs}`
            if (themeAssignedKeyRef.current !== key){
              // prefer server-provided theme if available
              if (res.data.theme) setArenaTheme(res.data.theme)
              else setArenaTheme(prev => prev ?? pickRandomTheme())
              themeAssignedKeyRef.current = key
            }
          }
          // Fallback de posiciones iniciales
          if (Array.isArray(res.data.playerPositions)){
            const cells = {}
            for (const pos of res.data.playerPositions){
              const key = `${pos.row},${pos.col}`
              cells[key] = { playerId: pos.playerId, color: colorToHex(pos.color) }
            }
            setPlayerCells(cells)
          }
          // Arena config via GET (mid-game refresh)
          if (res.data.arena){ setArenaMode(true); setArenaConfig(res.data.arena) }
          // Apply theme from GET if present (including WAITING state cases)
          if (res.data.theme){ setArenaTheme(res.data.theme) }
        } else if (res.data.status === 'WAITING') {
          // Show arena preview with the chosen theme while waiting
          if (res.data.theme) setArenaTheme(res.data.theme)
          setArenaMode(true)
          setCanMove(false)
          setArenaConfig({
            width: 980,
            height: 540,
            platforms: [
              { x: 60, y: 420, width: 280, height: 22, cells: 14 },
              { x: 360, y: 340, width: 260, height: 22, cells: 13 },
              { x: 660, y: 260, width: 240, height: 22, cells: 12 },
              { x: 220, y: 200, width: 200, height: 22, cells: 10 },
              { x: 520, y: 160, width: 160, height: 22, cells: 8 }
            ]
          })
        }
      }catch{}
    }
    load()
  },[code])

  const handleState = (body) => {
    setMessages(msgs => [...msgs, {t:'state', body}])
    // body may contain platforms and players and startTimestamp
    if (body.platforms) applyPlatforms(body.platforms)
  if (body.players) setPlayers(body.players)
    if (body.players){
      const m = new Map()
      for (const p of body.players){
        if (p.playerId) m.set(p.playerId, p.color)
      }
      playerColorMapRef.current = m
      const am = new Map()
      for (const p of body.players){
        if (p.playerId) am.set(p.playerId, sanitizeAvatar(p.avatar))
      }
      playerAvatarMapRef.current = am
    }
    if (body.playerPositions){
      // set player positions overlay
      const cells = {}
      for (const pos of body.playerPositions){
        const key = `${pos.row},${pos.col}`
        cells[key] = { playerId: pos.playerId, color: colorToHex(pos.color) }
      }
      setPlayerCells(cells)
    }
    if (body.status === 'PLAYING'){
      // activar modo grande en la misma ventana (no abrir nueva ventana)
      setFullMode(true)
      setCanMove(true)
      // Forzar arena mode en PLAYING; si no viene config aún, usa un fallback
      setArenaMode(true)
      if (body.arena){
        setArenaConfig(body.arena)
      } else if (!arenaConfig){
        setArenaConfig({ width: 980, height: 540, platforms: [] })
      }
      // Randomizar tema al inicio de la partida (una vez por startTimestamp)
      if (body.startTimestamp){
        const key = `${code}:${body.startTimestamp}`
        if (themeAssignedKeyRef.current !== key){
          if (body.theme) setArenaTheme(body.theme)
          else setArenaTheme(prev => prev ?? pickRandomTheme())
          themeAssignedKeyRef.current = key
        }
      }
    } else if (body.status === 'WAITING') {
      setCanMove(false)
      // Show preview arena during WAITING
      setArenaMode(true)
      if (body.theme) setArenaTheme(body.theme)
      setArenaConfig(prev => prev && prev.platforms?.length ? prev : {
        width: 980,
        height: 540,
        platforms: [
          { x: 60, y: 420, width: 280, height: 22, cells: 14 },
          { x: 360, y: 340, width: 260, height: 22, cells: 13 },
          { x: 660, y: 260, width: 240, height: 22, cells: 12 },
          { x: 220, y: 200, width: 200, height: 22, cells: 10 },
          { x: 520, y: 160, width: 160, height: 22, cells: 8 }
        ]
      })
      // limpiar patrón para recalcular en el siguiente tema/partida
      platformPatternRef.current = null
      lastThemeRef.current = null
      lastPaintRef.current = {}
    }
    // Apply theme updates during WAITING
    if (typeof body.theme !== 'undefined' && body.theme){
      setArenaTheme(body.theme)
    }
    // fallback: si llega un timestamp de inicio, habilitar movimiento
    if (body.startTimestamp){
      setCanMove(true)
    }
    if (body.joinDeadlineMs && (!body.status || body.status === 'WAITING')){
      startJoinTimer(body.joinDeadlineMs)
    }
    if (body.startTimestamp){
      // al iniciar la partida, dejar de mostrar joinLeft
      if (joinTimerRef.current) clearInterval(joinTimerRef.current)
      setJoinLeft(null)
      const now = Date.now()
      const durationMs = body.duration || body.gameDurationMs || 40000
      const endAt = body.startTimestamp + durationMs
      startTimer(Math.max(0, Math.floor((endAt - now)/1000)))
    }
  }

  // Arena inputs (left/right/jump) handlers
  useEffect(()=>{
    const sendInput = () => {
      if (!client || !client.connected || !arenaMode || !canMove) return
      const playerId = localStorage.getItem('cc_userId')
      const { left, right, jump } = inputRef.current
      client.publish({ destination:'/app/arena/input', body: JSON.stringify({ code, playerId, left, right, jump }) })
    }
    const down = (e) => {
      if (!arenaMode || !canMove) return
      switch(e.key){
        case 'ArrowLeft': case 'a': case 'A': e.preventDefault(); inputRef.current.left = true; sendInput(); break
        case 'ArrowRight': case 'd': case 'D': e.preventDefault(); inputRef.current.right = true; sendInput(); break
        case 'ArrowUp': case 'w': case 'W': case ' ': e.preventDefault(); inputRef.current.jump = true; sendInput(); break
        case 'ArrowDown': e.preventDefault(); /* no-op in arena, just prevent page scroll */ break
        default: break
      }
    }
    const up = (e) => {
      if (!arenaMode || !canMove) return
      switch(e.key){
        case 'ArrowLeft': case 'a': case 'A': e.preventDefault(); inputRef.current.left = false; sendInput(); break
        case 'ArrowRight': case 'd': case 'D': e.preventDefault(); inputRef.current.right = false; sendInput(); break
        case 'ArrowUp': case 'w': case 'W': case ' ': e.preventDefault(); inputRef.current.jump = false; /* optional send */ break
        case 'ArrowDown': e.preventDefault(); /* prevent page scroll */ break
        default: break
      }
    }
    window.addEventListener('keydown', down)
    window.addEventListener('keyup', up)
    return ()=>{
      window.removeEventListener('keydown', down)
      window.removeEventListener('keyup', up)
    }
  },[client, arenaMode, canMove, code])

  // Draw arena on canvas; in WAITING we draw a preview (no frames required)
  useEffect(()=>{
    if (!arenaMode || !arenaConfig) return
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    const { width, height, platforms } = arenaConfig
    canvas.width = width
    canvas.height = height
    const now = Date.now()
    if (arenaTheme === 'cyber'){
      // Background: deep cyber gradient with a slight time shift
      const t = (now % 20000) / 20000
      const grad = ctx.createLinearGradient(0, 0, 0, height)
      grad.addColorStop(Math.max(0, 0.00 + t*0.05), '#0b1020')
      grad.addColorStop(Math.max(0, 0.35 + t*0.05), '#121a35')
      grad.addColorStop(Math.min(1, 0.70 + t*0.05), '#1a2454')
      ctx.fillStyle = grad
      ctx.fillRect(0,0,width,height)
      // Neon horizon glow
      const hg = ctx.createLinearGradient(0, height*0.65, 0, height)
      hg.addColorStop(0,'rgba(0,246,255,0.00)')
      hg.addColorStop(1,'rgba(0,246,255,0.15)')
      ctx.fillStyle = hg
      ctx.fillRect(0, height*0.65, width, height*0.35)
      // City skyline silhouettes (3 parallax layers)
      const baseY = Math.floor(height*0.72)
      const layers = [
        { y: baseY, h: 90, color: '#0d142b', alpha: 0.8, step: 42 },
        { y: baseY+12, h: 70, color: '#111a39', alpha: 0.65, step: 50 },
        { y: baseY+24, h: 50, color: '#162049', alpha: 0.5, step: 58 },
      ]
      ctx.save()
      for (const L of layers){
        ctx.globalAlpha = L.alpha
        ctx.fillStyle = L.color
        for (let x=0; x<width;){
          const w = 24 + ((x/ L.step) % 1)*20 // deterministic pseudo-variation
          const bh = 20 + ((Math.sin(x*0.03)+1)*0.5)*L.h
          ctx.fillRect(x, L.y - bh, w, bh)
          // a few horizontal window bands
          ctx.globalAlpha = L.alpha * 0.12
          ctx.fillStyle = '#37e5ff'
          const rows = 2
          for (let r=1;r<=rows;r++){
            const wy = L.y - Math.floor(bh*(r/(rows+1)))
            ctx.fillRect(x+3, wy, w-6, 2)
          }
          ctx.globalAlpha = L.alpha
          ctx.fillStyle = L.color
          x += Math.max(18, w)
        }
      }
      ctx.restore()
      // Subtle diagonal light streaks
      ctx.save()
      ctx.globalAlpha = 0.10
      ctx.fillStyle = '#00f6ff'
      for (let i=0;i<3;i++){
        const yy = (now*0.02 + i*60) % (height+120) - 120
        ctx.fillRect(((i+1)*37)%width - 120, yy, width*0.6, 6)
      }
      ctx.restore()
    } else if (arenaTheme === 'metal') {
      // Metal: warehouse vibe (steel walls, beams, lights)
      const grad = ctx.createLinearGradient(0, 0, 0, height)
      grad.addColorStop(0, '#1a1d22')
      grad.addColorStop(1, '#2b2f36')
      ctx.fillStyle = grad
      ctx.fillRect(0,0,width,height)
      // Horizontal brushed strokes
      ctx.save()
      ctx.globalAlpha = 0.06
      ctx.strokeStyle = '#ffffff'
      for (let y=0; y<height; y+=2){
        ctx.beginPath(); ctx.moveTo(0,y+0.5); ctx.lineTo(width,y+0.5); ctx.stroke()
      }
      ctx.restore()
      // Vertical steel beams with cross braces
      ctx.save()
      const beamW = Math.max(14, Math.floor(width*0.02))
      for (let x=0; x<width; x+= Math.max(160, Math.floor(width*0.16))){
        ctx.fillStyle = '#232830'; ctx.fillRect(x, 0, beamW, height)
        // bevel
        ctx.fillStyle = 'rgba(255,255,255,0.10)'; ctx.fillRect(x+1, 0, 1, height)
        ctx.fillStyle = 'rgba(0,0,0,0.30)'; ctx.fillRect(x+beamW-2, 0, 1, height)
        // cross braces
        ctx.strokeStyle = 'rgba(255,255,255,0.08)'; ctx.lineWidth = 2
        ctx.beginPath();
        for (let y=0; y<height; y+=40){
          ctx.moveTo(x, y); ctx.lineTo(x+beamW, y+40)
          ctx.moveTo(x+beamW, y); ctx.lineTo(x, y+40)
        }
        ctx.stroke()
      }
      ctx.restore()
      // Overhead lights
      ctx.save()
      ctx.globalAlpha = 0.45
      for (let i=0; i<5; i++){
        const lx = Math.floor((i+1)*(width/6))
        const ly = Math.floor(height*0.12)
        ctx.fillStyle = '#d7dbe6'; ctx.fillRect(lx-18, ly-6, 36, 6)
        const glow = ctx.createRadialGradient(lx, ly, 2, lx, ly, 120)
        glow.addColorStop(0,'rgba(220,230,255,0.18)')
        glow.addColorStop(1,'rgba(220,230,255,0.00)')
        ctx.fillStyle = glow
        ctx.fillRect(lx-140, ly-40, 280, 160)
      }
      ctx.restore()
      // Large rolling door silhouette in the back
      ctx.save()
      const doorW = Math.min(380, width*0.38)
      const doorH = Math.min(120, height*0.22)
      const dx = Math.floor((width-doorW)/2)
      const dy = Math.floor(height*0.54)
      ctx.fillStyle = '#1c2027'; ctx.fillRect(dx, dy, doorW, doorH)
      ctx.fillStyle = 'rgba(255,255,255,0.05)'
      for (let y=dy+6; y<dy+doorH; y+=10){ ctx.fillRect(dx+6, y, doorW-12, 2) }
      ctx.restore()
      // Caution stripes along the floor
      ctx.save()
      const floorY = Math.floor(height*0.92)
      ctx.fillStyle = '#13161b'; ctx.fillRect(0, floorY, width, height-floorY)
      for (let x=0; x<width; x+=28){
        ctx.fillStyle = '#f2c200'; ctx.fillRect(x, floorY, 20, 8)
        ctx.fillStyle = '#2a2f36'; ctx.fillRect(x+20, floorY, 8, 8)
      }
      ctx.restore()
      // Soft side vignette
      ctx.save()
      const v = ctx.createLinearGradient(0,0,0,height)
      v.addColorStop(0,'rgba(0,0,0,0.35)')
      v.addColorStop(0.08,'rgba(0,0,0,0.0)')
      v.addColorStop(0.92,'rgba(0,0,0,0.0)')
      v.addColorStop(1,'rgba(0,0,0,0.35)')
      ctx.fillStyle = v
      ctx.fillRect(0,0,width,height)
      ctx.restore()
    } else {
      // Moon: space sky with stars and Earth; lunar ground band and side "walls"
      // Space background
      const grad = ctx.createLinearGradient(0, 0, 0, height)
      // Slightly brighter deep space for moon ambience
      grad.addColorStop(0, '#0e1118')
      grad.addColorStop(1, '#181c24')
      ctx.fillStyle = grad
      ctx.fillRect(0,0,width,height)
      // Stars (cached)
      if (!moonStarsRef.current || lastThemeRef.current !== 'moon'){
        const stars = []
        const count = Math.min(160, Math.floor((width*height)/6000))
        for (let i=0;i<count;i++){
          stars.push({ x: Math.random()*width, y: Math.random()*height*0.7, r: Math.random()*1.4+0.2, phase: Math.random()*Math.PI*2 })
        }
        moonStarsRef.current = stars
      }
      ctx.save()
      ctx.fillStyle = '#ffffff'
      for (const s of (moonStarsRef.current || [])){
        const tw = 0.7 + 0.3*Math.sin(now*0.003 + s.phase)
        ctx.globalAlpha = 0.5*tw
        ctx.beginPath(); ctx.arc(s.x, s.y, s.r, 0, Math.PI*2); ctx.fill()
      }
      ctx.restore()
      // Earth disc (top-right)
      const ex = width - Math.min(180, width*0.18)
      const ey = Math.max(80, height*0.18)
      const er = Math.min(90, width*0.09)
      const egrad = ctx.createRadialGradient(ex, ey, er*0.1, ex, ey, er)
      egrad.addColorStop(0, '#7fd3ff')
      egrad.addColorStop(0.5, '#2aa6ff')
      egrad.addColorStop(1, 'rgba(0,0,0,0)')
      ctx.save()
      ctx.globalAlpha = 0.8
      ctx.fillStyle = egrad
      ctx.beginPath(); ctx.arc(ex, ey, er, 0, Math.PI*2); ctx.fill()
      ctx.restore()
  // Lunar ground band with wavy (ondulada) horizon
      const groundH = Math.max(80, height*0.22)
      const y0 = height - groundH
      const A = Math.min(22, height*0.04)
      const waves = 2.5
  // Fill with a slightly less bright lunar regolith gradient
  const gbg = ctx.createLinearGradient(0, y0, 0, height)
  gbg.addColorStop(0,'#c8c7c5')
  gbg.addColorStop(1,'#b6b5b2')
      ctx.save()
      ctx.fillStyle = gbg
      ctx.beginPath()
      ctx.moveTo(0, y0 + A*Math.sin(0))
      for (let x=0; x<=width; x+=16){
        const yy = y0 + A*Math.sin((x/width)*Math.PI*2*waves)
        ctx.lineTo(x, yy)
      }
      ctx.lineTo(width, height)
      ctx.lineTo(0, height)
      ctx.closePath()
      ctx.fill()
      ctx.restore()
      // Soft horizon haze to lift brightness near the crest
      ctx.save()
      const hz = ctx.createLinearGradient(0, y0-24, 0, y0+24)
      hz.addColorStop(0,'rgba(220,230,255,0.06)')
      hz.addColorStop(0.5,'rgba(220,230,255,0.10)')
      hz.addColorStop(1,'rgba(220,230,255,0.00)')
      ctx.fillStyle = hz
      ctx.fillRect(0, Math.max(0,y0-24), width, 48)
      ctx.restore()
  // Side walls (darker/black as requested)
      const wallW = Math.max(10, Math.floor(width*0.02))
  ctx.fillStyle = '#000000'
      ctx.fillRect(0, 0, wallW, height)
      ctx.fillRect(width-wallW, 0, wallW, height)
    }

    // Ensure platform texture pattern exists for current theme
    if (!platformPatternRef.current || lastThemeRef.current !== arenaTheme){
      const off = document.createElement('canvas')
      const S = 16
      off.width = S; off.height = S
      const octx = off.getContext('2d')
      if (arenaTheme === 'cyber'){
        octx.fillStyle = '#2a2f44'
        octx.fillRect(0,0,S,S)
        octx.strokeStyle = 'rgba(255,255,255,0.06)'
        octx.lineWidth = 1
        octx.beginPath(); octx.moveTo(-2, S-2); octx.lineTo(S-2, -2); octx.stroke()
      } else if (arenaTheme === 'metal') {
        // metal crosshatch + rivets
        octx.fillStyle = '#3a3f48'
        octx.fillRect(0,0,S,S)
        octx.strokeStyle = 'rgba(255,255,255,0.05)'
        octx.lineWidth = 1
        octx.beginPath(); octx.moveTo(0,S); octx.lineTo(S,0); octx.stroke()
        octx.beginPath(); octx.moveTo(0,0); octx.lineTo(S,S); octx.stroke()
        // rivets
        octx.fillStyle = 'rgba(0,0,0,0.35)'
        octx.beginPath(); octx.arc(S*0.25,S*0.25,1.2,0,Math.PI*2); octx.fill()
        octx.beginPath(); octx.arc(S*0.75,S*0.75,1.2,0,Math.PI*2); octx.fill()
      } else {
        // moon: regolith texture (speckles + small craters)
        octx.fillStyle = '#3b3a38'
        octx.fillRect(0,0,S,S)
        // speckles
        octx.fillStyle = 'rgba(255,255,255,0.05)'
        for (let i=0;i<8;i++){
          octx.fillRect(Math.random()*S, Math.random()*S, 1, 1)
        }
        // tiny craters
        octx.fillStyle = 'rgba(0,0,0,0.18)'
        octx.beginPath(); octx.arc(S*0.35,S*0.35,1.2,0,Math.PI*2); octx.fill()
        octx.beginPath(); octx.arc(S*0.7,S*0.6,1.0,0,Math.PI*2); octx.fill()
      }
      platformPatternRef.current = ctx.createPattern(off, 'repeat')
      lastThemeRef.current = arenaTheme
    }

    // draw platforms with paint and theme edges
    for (let i=0;i<platforms.length;i++){
      const pl = platforms[i]
      if (arenaTheme === 'cyber'){
        // shadow + base
        ctx.save()
        ctx.shadowColor = 'rgba(0,246,255,0.20)'
        ctx.shadowBlur = 8
        ctx.fillStyle = '#21263a'
        ctx.fillRect(pl.x, pl.y, pl.width, pl.height)
        ctx.restore()
        // texture overlay
        ctx.save()
        ctx.globalAlpha = 0.25
        ctx.fillStyle = platformPatternRef.current
        ctx.fillRect(pl.x, pl.y, pl.width, pl.height)
        ctx.restore()
        // neon edge
        ctx.save()
        ctx.shadowColor = '#00f6ff'
        ctx.shadowBlur = 6
        ctx.strokeStyle = 'rgba(0,246,255,0.6)'
        ctx.lineWidth = 2
        ctx.strokeRect(pl.x+1, pl.y+1, pl.width-2, pl.height-2)
        ctx.restore()
      } else if (arenaTheme === 'metal') {
        // metal: soft shadow + steel base
        ctx.save()
        ctx.shadowColor = 'rgba(0,0,0,0.35)'
        ctx.shadowBlur = 8
        ctx.fillStyle = '#2f3540'
        ctx.fillRect(pl.x, pl.y, pl.width, pl.height)
        ctx.restore()
        // texture overlay
        ctx.save()
        ctx.globalAlpha = 0.35
        ctx.fillStyle = platformPatternRef.current
        ctx.fillRect(pl.x, pl.y, pl.width, pl.height)
        ctx.restore()
        // bevel edges
        ctx.save()
        ctx.strokeStyle = '#111'
        ctx.lineWidth = 2
        ctx.strokeRect(pl.x, pl.y, pl.width, pl.height)
        ctx.strokeStyle = 'rgba(255,255,255,0.25)'
        ctx.lineWidth = 1
        ctx.strokeRect(pl.x+1.5, pl.y+1.5, pl.width-3, pl.height-3)
        // top highlight
        ctx.strokeStyle = 'rgba(255,255,255,0.15)'
        ctx.beginPath(); ctx.moveTo(pl.x+2, pl.y+1.5); ctx.lineTo(pl.x+pl.width-2, pl.y+1.5); ctx.stroke()
        ctx.restore()
      } else {
        // moon: lunar regolith platforms with craters and soft edge
        ctx.save()
  ctx.fillStyle = '#c9c8c6'
        ctx.fillRect(pl.x, pl.y, pl.width, pl.height)
        // texture overlay (pattern)
  ctx.globalAlpha = 0.18
        ctx.fillStyle = platformPatternRef.current
        ctx.fillRect(pl.x, pl.y, pl.width, pl.height)
        ctx.globalAlpha = 1
        // crater speckles
        ctx.fillStyle = 'rgba(0,0,0,0.15)'
        for (let c=0;c<3;c++){
          const rx = pl.x + 4 + Math.random()*(pl.width-8)
          const ry = pl.y + 3 + Math.random()*(pl.height-6)
          const rr = 1 + Math.random()*2.5
          ctx.beginPath(); ctx.arc(rx, ry, rr, 0, Math.PI*2); ctx.fill()
        }
        ctx.restore()
        // soft edge outline for readability (slightly darker on bright surface)
        ctx.save()
        ctx.strokeStyle = 'rgba(0,0,0,0.15)'
        ctx.lineWidth = 2
        ctx.strokeRect(pl.x+0.5, pl.y+0.5, pl.width-1, pl.height-1)
        ctx.restore()
      }
      // paint cells
  const paintArr = arenaFrame && arenaFrame.paint ? arenaFrame.paint[i] : null
      if (paintArr){
        const cellW = pl.width / pl.cells
        const prev = lastPaintRef.current[i] || []
        for (let cIdx=0;cIdx<paintArr.length;cIdx++){
          const col = paintArr[cIdx]
          if (!col) continue
          const hex = colorToHex(col)
          const cx = pl.x + cIdx*cellW
          const cy = pl.y
          // base fill
          ctx.fillStyle = hex
          ctx.fillRect(cx, cy, cellW, pl.height)
          // style-specific enhancement
          if (arenaTheme === 'metal') {
            // subtle sheen
            const g = ctx.createLinearGradient(cx, cy, cx+cellW, cy+pl.height)
            g.addColorStop(0,'rgba(255,255,255,0.05)')
            g.addColorStop(1,'rgba(0,0,0,0.10)')
            ctx.save(); ctx.globalAlpha = 0.5; ctx.fillStyle = g; ctx.fillRect(cx, cy, cellW, pl.height); ctx.restore()
          } else {
            // moon: paint dust overlay and subtle dark outline for contrast on bright surface
            ctx.save()
            ctx.globalAlpha = 0.24
            ctx.fillStyle = hex
            ctx.fillRect(cx, cy, cellW, pl.height)
            ctx.restore()
            // contrast outline
            ctx.save()
            ctx.strokeStyle = 'rgba(0,0,0,0.06)'
            ctx.lineWidth = 1
            ctx.strokeRect(cx+0.5, cy+0.5, Math.max(0, cellW-1), Math.max(0, pl.height-1))
            ctx.restore()
          }
          // splash on new paint
          if (prev[cIdx] !== col){
            spawnPaintParticles(cx, cy, cellW, pl.height, hex)
          }
        }
        // snapshot for next frame
        lastPaintRef.current[i] = paintArr.slice()
      }
    }
    // draw players
  const pp = arenaFrame && arenaFrame.players ? arenaFrame.players : []
    for (const p of pp){
      const playerMeta = players.find(x=>x.playerId===p.playerId)
      const color = colorToHex(playerMeta?.color || 'PINK')
      const avatar = sanitizeAvatar(playerMeta?.avatar)
      drawAvatar(ctx, p.x, p.y, color, avatar, arenaTheme)
    }
    // particles
    const arr = particlesRef.current
    for (let k=arr.length-1;k>=0;k--){
      const pt = arr[k]
      const age = now - pt.born
      if (age > pt.life){ arr.splice(k,1); continue }
      const tnorm = age / pt.life
      const alpha = 1 - tnorm
      // integrate simple motion
      const px = pt.x + pt.vx * (age/1000)
      const py = pt.y + pt.vy * (age/1000) + 0.5*200*(age/1000)*(age/1000)
      ctx.save()
  const particleAlpha = (arenaTheme==='cyber'?0.8 : arenaTheme==='metal' ? 0.5 : 0.6)
      ctx.globalAlpha = Math.max(0, alpha * particleAlpha)
      ctx.fillStyle = pt.color
      ctx.beginPath()
      ctx.arc(px, py, 2.0, 0, Math.PI*2)
      ctx.fill()
      ctx.restore()
    }
  },[arenaMode, arenaConfig, arenaFrame, players, arenaTheme])

  // Compute territory coverage by color (percentage of painted cells)
  useEffect(()=>{
    if (!arenaMode || !arenaConfig) { setCoverageByColor({}); return }
    const platforms = arenaConfig.platforms || []
    const totalCells = platforms.reduce((sum, pl)=> sum + (pl?.cells || 0), 0)
    if (!arenaFrame || !arenaFrame.paint || totalCells === 0){ setCoverageByColor({}); return }
    const counts = {}
    for (let i=0;i<platforms.length;i++){
      const arr = arenaFrame.paint[i]
      if (!arr) continue
      for (let c of arr){
        if (!c) continue
        counts[c] = (counts[c]||0) + 1
      }
    }
    const out = {}
    for (const [col, n] of Object.entries(counts)){
      out[col] = Math.round((n/totalCells)*100)
    }
    setCoverageByColor(out)
  },[arenaMode, arenaConfig, arenaFrame])

  const handleMove = (body) => {
    setMessages(msgs => [...msgs, {t:'move', body}])
    if (body && body.success === false) return
    if (body.platforms) applyPlatforms(body.platforms)
    if (body.players) setPlayers(body.players)
    if (body.affectedPlayers && Array.isArray(body.affectedPlayers)){
      setPlayers(prev => {
        const copy = prev.map(p => ({...p}))
        for (const up of body.affectedPlayers){
          const id = (up.playerId && (typeof up.playerId === 'string' ? up.playerId : up.playerId.toString()))
          const idx = copy.findIndex(pp => pp.playerId === id)
          if (idx >= 0) copy[idx].score = up.newScore
        }
        return copy
      })
    }
    // update single player position if provided
    if (body.playerId && (typeof body.newRow === 'number') && (typeof body.newCol === 'number')){
      setPlayerCells(prev => {
        const next = {...prev}
        // remove any previous cell for this playerId
        for (const k of Object.keys(next)){
          if (next[k].playerId === body.playerId) delete next[k]
        }
        const key = `${body.newRow},${body.newCol}`
        const color = colorToHex(playerColorMapRef.current.get(body.playerId))
        next[key] = { playerId: body.playerId, color }
        return next
      })
    }
  }

  const handleEnd = (body) => {
    setMessages(msgs => [...msgs, {t:'end', body}])
    if (timerRef.current) clearInterval(timerRef.current)
    setTimeLeft(0)
    if (body.platforms) applyPlatforms(body.platforms)
    if (body.players) setPlayers(body.players)
    if (body.standings) setEndStandings(body.standings)
  }

  const applyPlatforms = (platforms) => {
    setGrid(g => {
      const copy = g.map(r => r.slice())
      for (const p of platforms){
        if (p.row >=0 && p.row < ROWS && p.col >=0 && p.col < COLS){
          copy[p.row][p.col] = colorToHex(p.color) || '#cccccc'
        }
      }
      return copy
    })
  }

  const startTimer = (seconds) => {
    setTimeLeft(seconds)
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(()=>{
      setTimeLeft(t => {
        if (t === null) return null
        if (t <= 1){ clearInterval(timerRef.current); return 0 }
        return t-1
      })
    },1000)
  }

  const startJoinTimer = (deadlineMs) => {
    if (joinTimerRef.current) clearInterval(joinTimerRef.current)
    const update = () => {
      const remain = Math.max(0, Math.ceil((deadlineMs - Date.now())/1000))
      setJoinLeft(remain)
      if (remain <= 0 && joinTimerRef.current){
        clearInterval(joinTimerRef.current)
      }
    }
    update()
    joinTimerRef.current = setInterval(update, 1000)
  }

  const sendMove = (direction) => {
    const playerId = localStorage.getItem('cc_userId')
    if (!client || !client.connected) return
    if (!canMove) return
    client.publish({destination:'/app/move', body: JSON.stringify({ code, playerId, direction })})
  }

  return (
    <>
    <div className="game-page">
      <h3>Game {code}</h3>
      <div className="game-layout">
        <main className="board-wrap" style={{position:'relative'}}>
          {arenaMode ? (
            <>
              <canvas ref={canvasRef} style={{background:'#111827', border:'1px solid #333'}} />
              {/* Territory HUD (show only when playing/movable) */}
              {canMove && (
                <div style={{position:'absolute', top: (typeof timeLeft === 'number' ? 80 : 8), left:8, right:8, display:'flex', gap:12, justifyContent:'center', pointerEvents:'none', zIndex:5}}>
                  {players.map(p => {
                    const colName = (p.color||'').toUpperCase()
                    const percent = coverageByColor[colName] || 0
                    const fill = colorToHex(colName)
                    const emoji = avatarToEmoji(sanitizeAvatar(p.avatar))
                    const barBg = arenaTheme==='metal' ? '#1f2430' : arenaTheme==='cyber' ? '#0b1020' : '#1a1d22'
                    return (
                      <div key={p.playerId} style={{display:'flex', alignItems:'center', gap:8, padding:'6px 10px', borderRadius:16, background:'rgba(0,0,0,0.35)', border:'1px solid rgba(255,255,255,0.15)', boxShadow:'0 2px 6px rgba(0,0,0,0.25)', pointerEvents:'none'}}>
                        <div style={{width:28,height:28,borderRadius:'50%',background:'#222',border:`2px solid ${fill}`,display:'flex',alignItems:'center',justifyContent:'center',color:'#fff',fontSize:16,lineHeight:'28px',boxShadow:`0 0 6px ${arenaTheme==='cyber'?fill+'66':'#00000055'}`}}>{emoji}</div>
                        <div style={{width:160, height:16, borderRadius:12, background:barBg, border:'1px solid #333', overflow:'hidden', position:'relative'}}>
                          <div style={{width:`${percent}%`, height:'100%', background:fill, opacity:0.9}} />
                          <div style={{position:'absolute', inset:0, display:'flex', alignItems:'center', justifyContent:'center', color:'#fff', fontSize:12, textShadow:'0 1px 2px #000'}}>{percent}%</div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
              {/* Waiting players list (only in WAITING) */}
              {!canMove && players && players.length > 0 && (
                <div className={`waiting-players ${arenaTheme || 'default'}`}>
                  <div className="wp-title">Jugadores</div>
                  <ul>
                    {players.map(p => {
                      const col = colorToHex(p.color)
                      const emoji = avatarToEmoji(sanitizeAvatar(p.avatar))
                      return (
                        <li key={p.playerId || p.id}>
                          <span className="wp-dot" style={{background: col}} />
                          <span className="wp-name">{p.nickname || p.playerId || p.id}</span>
                          <span className="wp-emoji">{emoji}</span>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )}
            </>
          ) : (
            <div className={`grid ${fullMode ? 'large' : ''} ${ultraMode ? 'ultra' : ''}`}>
              {grid.map((row, rIdx) => row.map((cell, cIdx) => {
                const key = `${rIdx},${cIdx}`
                const pc = playerCells[key]
                const avatar = pc ? (playerAvatarMapRef.current.get(pc.playerId) || null) : null
                return (
                  <div
                    key={`${rIdx}-${cIdx}`}
                    className="box"
                    style={{
                      position: 'relative',
                      background: (pc?.color) || cell || '#ffffff',
                      border: (pc) ? '2px solid #000' : (cell ? '1px solid #666' : '1px solid #ddd')
                    }}
                  >
                    {pc && avatar && (
                      <span className="avatar-mark">{avatarToEmoji(avatar)}</span>
                    )}
                  </div>
                )
              }))}
            </div>
          )}
          {/* Prominent match timer during PLAYING or WAITING (theme-styled) */}
          {(canMove && typeof timeLeft === 'number') && (
            <div className={`hud-timer ${arenaTheme || 'default'} playing`}>{formatTime(timeLeft)}</div>
          )}
          {(!canMove && typeof joinLeft === 'number') && (
            <div className={`hud-timer ${arenaTheme || 'default'} waiting`}>{formatTime(joinLeft)}</div>
          )}
        </main>
        {/* Removed right sidebar: style/time/players panel hidden as requested */}
      </div>
  </div>
    {endStandings && (
      (()=>{
        const idToColor = new Map(players.map(p => [p.playerId, colorToHex(p.color)]))
        const idToColorName = new Map(players.map(p => [p.playerId, (p.color||'').toUpperCase()]))
        // Build percent-by-player map from final coverage; fallback to score ratio if missing
        const pctById = new Map()
        let missingAny = false
        for (const s of endStandings){
          const cname = idToColorName.get(s.playerId)
          let pct = (cname && typeof coverageByColor[cname] === 'number') ? coverageByColor[cname] : null
          if (pct === null || Number.isNaN(pct)) { missingAny = true }
          pctById.set(s.playerId, pct)
        }
        if (missingAny){
          const total = endStandings.reduce((acc, s)=> acc + (s.score||0), 0)
          for (const s of endStandings){
            if (pctById.get(s.playerId) == null){
              const pct = total>0 ? Math.round(((s.score||0)/total)*100) : 0
              pctById.set(s.playerId, pct)
            }
          }
        }
        const winner = endStandings[0]
        const winnerColor = idToColor.get(winner?.playerId) || '#7c3aed'
        const winnerPct = pctById.get(winner?.playerId) ?? 0
        const pieces = Array.from({length:18}).map((_,i)=>{
          const left = Math.floor((i/18)*100)
          const delay = (i%6)*0.25
          const hue = (i*37)%360
          return <span key={i} className="confetti-piece" style={{left: left+'%', background:`hsl(${hue} 90% 60%)`, animationDelay: `${delay}s`}} />
        })
        return (
          <div className="results-overlay">
            <div className="confetti">{pieces}</div>
            <div className="results-card">
              {winner && (
                <div className="winner-card" style={{borderColor: winnerColor}}>
                  <div className="winner-badge" style={{boxShadow: `0 0 12px ${winnerColor}55`}}>
                    <span className="crown">👑</span>
                    <span className="avatar">{winner.avatar ? avatarToEmoji(winner.avatar) : '⭐'}</span>
                  </div>
                  <div className="winner-text">
                    <div className="title">¡Ganador!</div>
                    <div className="name">{winner.nickname || winner.playerId}</div>
                    <div className="score"><span style={{color:winnerColor,fontWeight:700}}>{winnerPct}%</span> del mapa</div>
                  </div>
                </div>
              )}
              <div className="standings">
                <ul>
                  {endStandings.map((s, idx)=>{
                    const col = idToColor.get(s.playerId) || '#9aa4b2'
                    const pct = pctById.get(s.playerId) ?? 0
                    return (
                      <li key={s.playerId} className="standing-item" style={{borderColor: col}}>
                        <div className="place">{idx+1}</div>
                        <div className="who">
                          <span className="who-avatar" style={{borderColor: col}}>{s.avatar ? avatarToEmoji(s.avatar) : '⭐'}</span>
                          <div className="who-name">{s.nickname || s.playerId}</div>
                        </div>
                        <div className="pts"><span style={{color:col}}>{pct}%</span></div>
                      </li>
                    )
                  })}
                </ul>
              </div>
              <div className="results-actions">
                <button onClick={async ()=> { try { await api.post(`/api/games/${code}/restart`) } catch {} navigate(`/lobby?code=${code}`) }}>Regresar a la sala</button>
                <button onClick={()=> { if (window.opener && !window.opener.closed) window.close(); else navigate('/') }}>Salir</button>
              </div>
            </div>
          </div>
        )
      })()
    )}
    </>
  )
}

// Mapeo simple de nombre de color a color visible en tablero/leyenda
function colorToHex(name){
  switch((name||'').toUpperCase()){
    case 'YELLOW': return '#FFD700'
    case 'PINK': return '#FF69B4'
    case 'PURPLE': return '#800080'
    case 'GREEN': return '#2E8B57'
    case 'WHITE': return '#FFFFFF'
    default: return '#CCCCCC'
  }
}

// Format seconds into MM:SS
function formatTime(total){
  const s = Math.max(0, Math.floor(total||0))
  const m = Math.floor(s/60)
  const ss = s%60
  return `${String(m).padStart(2,'0')}:${String(ss).padStart(2,'0')}`
}

// Simple mapping from avatar name to an emoji marker
function avatarToEmoji(name){
  switch((name||'').toUpperCase()){
    case 'ROBOT': return '🤖'
    case 'COWBOY': return '🤠'
    case 'ALIEN': return '👽'
    case 'WITCH': return '🧙‍♀️'
    default: return '⭐'
  }
}

// Normalize avatar value to allowed set
function sanitizeAvatar(name){
  const v = (name||'').toUpperCase()
  if (v==='PRINCESS' || v==='COWGIRL') return 'WITCH' // migración a la bruja
  if (v==='ROBOT' || v==='COWBOY' || v==='ALIEN' || v==='WITCH') return v
  return 'ROBOT'
}

// Draw avatar shapes on canvas (24x32 footprint)
function drawAvatar(ctx, x, y, color, avatar, theme){
  const drawBase = () => {
    ctx.fillStyle = color
    ctx.fillRect(x, y, 24, 32)
  }
  const outline = (stroke='rgba(0,0,0,0.35)') => {
    ctx.strokeStyle = stroke
    ctx.lineWidth = 1
    ctx.strokeRect(x+0.5, y+0.5, 23, 31)
  }
  const glow = () => {
    if (theme==='cyber'){
      ctx.save()
      ctx.shadowColor = color
      ctx.shadowBlur = 8
      ctx.fillStyle = color
      ctx.fillRect(x, y, 24, 32)
      ctx.restore()
    }
  }

  switch(avatar){
    case 'ROBOT':{
      glow()
      // cuerpo
      ctx.fillStyle = color; ctx.fillRect(x, y, 24, 24)
      // cabeza
      ctx.fillStyle = '#cbd5e1'; ctx.fillRect(x+4, y-8, 16, 10)
      // antena
      ctx.strokeStyle = '#ddd'; ctx.lineWidth = 2
      ctx.beginPath(); ctx.moveTo(x+12, y-10); ctx.lineTo(x+12, y-8); ctx.stroke()
      ctx.fillStyle = '#f44'; ctx.beginPath(); ctx.arc(x+12, y-12, 2, 0, Math.PI*2); ctx.fill()
      // visor ojos
      ctx.fillStyle = '#111'; ctx.fillRect(x+6, y-4, 12, 5)
      ctx.fillStyle = '#0ef'; ctx.fillRect(x+7, y-3, 3, 3); ctx.fillRect(x+14, y-3, 3, 3)
      // brazos
      ctx.fillStyle = '#9ca3af'; ctx.fillRect(x-3, y+6, 6, 6); ctx.fillRect(x+21, y+6, 6, 6)
      // panel pecho
      ctx.fillStyle = '#333'; ctx.fillRect(x+6, y+14, 12, 6)
      ctx.fillStyle = '#0ff'; ctx.fillRect(x+7, y+15, 4, 4)
      ctx.fillStyle = '#ff0'; ctx.fillRect(x+13, y+15, 4, 4)
      // rejilla inferior
      ctx.strokeStyle = '#222'; ctx.lineWidth = 1
      ctx.beginPath(); ctx.moveTo(x+4, y+22); ctx.lineTo(x+20, y+22); ctx.moveTo(x+4, y+24); ctx.lineTo(x+20, y+24); ctx.stroke()
      // piernas
      ctx.fillStyle = '#9ca3af'; ctx.fillRect(x+4, y+24, 6, 8); ctx.fillRect(x+14, y+24, 6, 8)
      outline()
      break
    }
    case 'COWBOY':{
      // torso base
      ctx.fillStyle = color; ctx.fillRect(x, y, 24, 28)
      // sombrero ala
      ctx.fillStyle = '#8b5a2b'
      ctx.fillRect(x-2, y-2, 28, 3)
      // copa
      ctx.fillRect(x+4, y-10, 16, 8)
      // banda sombrero
      ctx.fillStyle = '#caa472'; ctx.fillRect(x+6, y-7, 12, 2)
      // rostro
      ctx.fillStyle = '#f1d0b7'; ctx.fillRect(x+5, y-2, 14, 6)
      // bigote
      ctx.fillStyle = '#3b2a1a'
      ctx.beginPath(); ctx.moveTo(x+9, y+2); ctx.lineTo(x+12, y+3); ctx.lineTo(x+9, y+4); ctx.closePath(); ctx.fill()
      ctx.beginPath(); ctx.moveTo(x+15, y+2); ctx.lineTo(x+12, y+3); ctx.lineTo(x+15, y+4); ctx.closePath(); ctx.fill()
      // chaleco (dos triángulos formando una V)
      ctx.fillStyle = '#5b3a1a'
      ctx.beginPath(); ctx.moveTo(x, y+2); ctx.lineTo(x+12, y+12); ctx.lineTo(x, y+22); ctx.closePath(); ctx.fill()
      ctx.beginPath(); ctx.moveTo(x+24, y+2); ctx.lineTo(x+12, y+12); ctx.lineTo(x+24, y+22); ctx.closePath(); ctx.fill()
      // pañuelo (encima del chaleco)
      ctx.fillStyle = '#b21e2a'; ctx.beginPath(); ctx.moveTo(x+10, y+8); ctx.lineTo(x+14, y+8); ctx.lineTo(x+12, y+12); ctx.closePath(); ctx.fill()
      // botones de camisa
      ctx.fillStyle = '#eee'; ctx.fillRect(x+11, y+12, 2, 2); ctx.fillRect(x+11, y+16, 2, 2)
      // estrella sheriff (dos triángulos)
      ctx.fillStyle = '#d4c28a';
      ctx.beginPath(); ctx.moveTo(x+12, y+14); ctx.lineTo(x+9, y+18); ctx.lineTo(x+15, y+18); ctx.closePath(); ctx.fill()
      ctx.beginPath(); ctx.moveTo(x+12, y+20); ctx.lineTo(x+9, y+16); ctx.lineTo(x+15, y+16); ctx.closePath(); ctx.fill()
      // cinturón y hebilla
      ctx.fillStyle = '#3b2a1a'; ctx.fillRect(x, y+18, 24, 3)
      ctx.fillStyle = '#d4c28a'; ctx.fillRect(x+9, y+18, 6, 4)
      ctx.fillStyle = '#3b2a1a'; ctx.fillRect(x+11, y+19, 2, 2)
      // botas (bordes inferiores)
      ctx.fillStyle = '#3b2a1a'; ctx.fillRect(x+2, y+26, 7, 2); ctx.fillRect(x+15, y+26, 7, 2)
      // espuelas
      ctx.fillStyle = '#d4c28a'; ctx.fillRect(x+1, y+27, 2, 2); ctx.fillRect(x+21, y+27, 2, 2)
      // funda lateral
      ctx.fillStyle = '#5b3a1a'; ctx.fillRect(x-3, y+16, 3, 8)
      // lazo (rollo)
      ctx.strokeStyle = '#caa472'; ctx.lineWidth = 2
      ctx.beginPath(); ctx.arc(x+22, y+10, 4, 0, Math.PI*2); ctx.stroke()
      outline()
      break
    }
    case 'ALIEN':{
      // mochila dorsal (detrás del torso)
      ctx.fillStyle = '#243b4a'; ctx.fillRect(x+3, y+8, 18, 5)
      // torso
      ctx.fillStyle = color; ctx.fillRect(x+2, y+6, 20, 22)
      // cabeza ovalada
      ctx.save(); ctx.fillStyle = color; ctx.beginPath(); ctx.ellipse(x+12, y, 10, 7, 0, 0, Math.PI*2); ctx.fill(); ctx.restore()
      // ojos y boca
      ctx.fillStyle = '#000'; ctx.beginPath(); ctx.ellipse(x+8, y, 3, 4, 0, 0, Math.PI*2); ctx.fill();
      ctx.beginPath(); ctx.ellipse(x+16, y, 3, 4, 0, 0, Math.PI*2); ctx.fill()
      // brillo de ojos
      ctx.fillStyle = '#fff'; ctx.fillRect(x+7, y-2, 1, 1); ctx.fillRect(x+15, y-2, 1, 1)
      ctx.fillStyle = '#000'; ctx.fillRect(x+10, y+5, 4, 1)
      // antenitas
      ctx.strokeStyle = '#333'; ctx.lineWidth = 1
      ctx.beginPath(); ctx.moveTo(x+6, y-6); ctx.lineTo(x+8, y-2); ctx.moveTo(x+18, y-6); ctx.lineTo(x+16, y-2); ctx.stroke()
      ctx.fillStyle = '#0f0'; ctx.beginPath(); ctx.arc(x+6, y-7, 1.5, 0, Math.PI*2); ctx.fill(); ctx.beginPath(); ctx.arc(x+18, y-7, 1.5, 0, Math.PI*2); ctx.fill()
      // hombreras
      ctx.fillStyle = 'rgba(255,255,255,0.15)'; ctx.fillRect(x+1, y+8, 6, 3); ctx.fillRect(x+17, y+8, 6, 3)
      // manos
      ctx.fillStyle = color; ctx.fillRect(x-2, y+12, 6, 5); ctx.fillRect(x+20, y+12, 6, 5)
      // franjas pecho
      ctx.strokeStyle = 'rgba(0,0,0,0.2)'; ctx.lineWidth = 1
      ctx.beginPath(); ctx.moveTo(x+5, y+14); ctx.lineTo(x+19, y+14); ctx.moveTo(x+5, y+17); ctx.lineTo(x+19, y+17); ctx.moveTo(x+5, y+20); ctx.lineTo(x+19, y+20); ctx.stroke()
      // cinturón
      ctx.fillStyle = '#2e2e2e'; ctx.fillRect(x+4, y+20, 16, 2)
      ctx.fillStyle = '#d4d4d8'; ctx.fillRect(x+11, y+20, 2, 2)
      // vientre oval
      ctx.fillStyle = 'rgba(255,255,255,0.2)'; ctx.beginPath(); ctx.ellipse(x+12, y+16, 7, 5, 0, 0, Math.PI*2); ctx.fill()
      // pies
      ctx.fillStyle = '#2e2e2e'; ctx.fillRect(x+4, y+28, 6, 2); ctx.fillRect(x+14, y+28, 6, 2)
      outline()
      break
    }
    case 'WITCH':{
      // cabeza
      ctx.fillStyle = '#f1d0b7'; ctx.beginPath(); ctx.arc(x+12, y, 5, 0, Math.PI*2); ctx.fill()
      // cabello lateral
      ctx.fillStyle = '#2a1b12'; ctx.fillRect(x+4, y+2, 3, 8); ctx.fillRect(x+17, y+2, 3, 8)
      // sombrero puntiagudo con ala
      ctx.fillStyle = '#2e2b3f'
      ctx.fillRect(x-2, y-4, 28, 3) // ala
      ctx.beginPath(); ctx.moveTo(x+7, y-4); ctx.lineTo(x+12, y-14); ctx.lineTo(x+17, y-4); ctx.closePath(); ctx.fill() // punta
      // banda del sombrero
      ctx.fillStyle = '#6b5bd1'; ctx.fillRect(x+7, y-3, 10, 2)
      // cuerpo/vestido
      ctx.fillStyle = color
      ctx.beginPath(); ctx.moveTo(x+12, y+6); ctx.lineTo(x+2, y+28); ctx.lineTo(x+22, y+28); ctx.closePath(); ctx.fill()
      // mangas
      ctx.beginPath(); ctx.moveTo(x+2, y+14); ctx.lineTo(x+8, y+12); ctx.lineTo(x+8, y+18); ctx.closePath(); ctx.fill()
      ctx.beginPath(); ctx.moveTo(x+22, y+14); ctx.lineTo(x+16, y+12); ctx.lineTo(x+16, y+18); ctx.closePath(); ctx.fill()
      // cinturón
      ctx.fillStyle = '#3b2a1a'; ctx.fillRect(x+4, y+18, 16, 2)
      ctx.fillStyle = '#d4c28a'; ctx.fillRect(x+11, y+18, 2, 2)
      // escoba en diagonal (mango + cerdas)
      ctx.strokeStyle = '#7a5a2b'; ctx.lineWidth = 2
      ctx.beginPath(); ctx.moveTo(x+18, y+18); ctx.lineTo(x+30, y+30); ctx.stroke()
      ctx.fillStyle = '#c49a6c';
      ctx.beginPath(); ctx.moveTo(x+28, y+28); ctx.lineTo(x+33, y+31); ctx.lineTo(x+27, y+33); ctx.closePath(); ctx.fill()
      ctx.beginPath(); ctx.moveTo(x+26, y+30); ctx.lineTo(x+31, y+33); ctx.lineTo(x+25, y+35); ctx.closePath(); ctx.fill()
      outline('rgba(0,0,0,0.25)')
      break
    }
    default: {
      drawBase(); outline(); break
    }
  }
}
