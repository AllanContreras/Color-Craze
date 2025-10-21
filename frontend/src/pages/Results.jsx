import React, { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../api'

export default function Results(){
  const { code } = useParams()
  const [standings, setStandings] = useState([])

  useEffect(()=>{
    // There's no dedicated REST results endpoint yet; try GET /api/games/{code}
    api.get(`/api/games/${code}`).then(r=>{
      // transform
      setStandings(r.data.players || [])
    }).catch(()=>{})
  },[code])

  return (
    <div>
      <h3>Resultados {code}</h3>
      <ol>
        {standings.map(p => <li key={p.playerId}>{p.nickname} - {p.score}</li>)}
      </ol>
    </div>
  )
}
