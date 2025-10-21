import React, { useEffect, useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
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
  const [messages, setMessages] = useState([])
  const [client, setClient] = useState(null)
  const [timeLeft, setTimeLeft] = useState(null)
  const timerRef = useRef(null)

  useEffect(()=>{
    let c = createStompClient((stomp) => {
      stomp.subscribe(`/topic/board/${code}/state`, m => handleState(JSON.parse(m.body)))
      stomp.subscribe(`/topic/board/${code}`, m => handleMove(JSON.parse(m.body)))
      stomp.subscribe(`/topic/board/${code}/end`, m => handleEnd(JSON.parse(m.body)))
    })
    setClient(c)
    return ()=> c.deactivate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  },[code])

  useEffect(()=>{
    return ()=>{
      if (timerRef.current) clearInterval(timerRef.current)
    }
  },[])

  const handleState = (body) => {
    setMessages(msgs => [...msgs, {t:'state', body}])
    // body may contain platforms and players and startTimestamp
    if (body.platforms) applyPlatforms(body.platforms)
    if (body.players) setPlayers(body.players)
    if (body.startTimestamp){
      const now = Date.now()
      const endAt = body.startTimestamp + (body.duration || 45000)
      startTimer(Math.max(0, Math.floor((endAt - now)/1000)))
    }
  }

  const handleMove = (body) => {
    setMessages(msgs => [...msgs, {t:'move', body}])
    if (body.platforms) applyPlatforms(body.platforms)
    if (body.players) setPlayers(body.players)
  }

  const handleEnd = (body) => {
    setMessages(msgs => [...msgs, {t:'end', body}])
    if (timerRef.current) clearInterval(timerRef.current)
    setTimeLeft(0)
    if (body.platforms) applyPlatforms(body.platforms)
    if (body.players) setPlayers(body.players)
  }

  const applyPlatforms = (platforms) => {
    setGrid(g => {
      const copy = g.map(r => r.slice())
      for (const p of platforms){
        if (p.row >=0 && p.row < ROWS && p.col >=0 && p.col < COLS){
          copy[p.row][p.col] = p.color || '#cccccc'
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

  const sendMove = (direction) => {
    const playerId = localStorage.getItem('cc_userId')
    if (!client || !client.connected) return
    client.publish({destination:'/app/move', body: JSON.stringify({ code, playerId, direction })})
  }

  return (
    <div>
      <h3>Game {code}</h3>
      <div style={{display:'flex', gap:16}}>
        <div>
          <div style={{marginBottom:8}}>
            <button onClick={()=>sendMove('UP')}>UP</button>
            <button onClick={()=>sendMove('LEFT')}>LEFT</button>
            <button onClick={()=>sendMove('RIGHT')}>RIGHT</button>
            <button onClick={()=>sendMove('DOWN')}>DOWN</button>
          </div>
          <div>Time left: {timeLeft===null?'-':timeLeft}s</div>
          <div style={{marginTop:12}}>
            <h4>Players</h4>
            <ul>
              {players.map(p => <li key={p.id}>{p.nickname || p.id} - {p.score ?? 0}</li>)}
            </ul>
          </div>
        </div>

        <div>
          <div className="grid">
            {grid.map((row, rIdx) => row.map((cell, cIdx) => (
              <div
                key={`${rIdx}-${cIdx}`}
                className="box"
                style={{background: cell || '#ffffff', border: cell ? '1px solid #666' : '1px solid #ddd'}}
              />
            )))}
          </div>
        </div>
      </div>

      <div style={{marginTop:12}}>
        <h4>Eventos</h4>
        <pre style={{background:'#eee', padding:8, maxHeight:200, overflow:'auto'}}>{JSON.stringify(messages.slice(-20),null,2)}</pre>
      </div>
    </div>
  )
}
