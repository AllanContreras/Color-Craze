import axios from 'axios'

// Resolve API base URL
function resolveApiBase(){
  if (import.meta.env.VITE_API_URL) return import.meta.env.VITE_API_URL
  if (import.meta.env.PROD && typeof window !== 'undefined' && window.location){
    return window.location.origin
  }
  return 'http://localhost:8080'
}

const API_URL = resolveApiBase()

const api = axios.create({ baseURL: API_URL })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('cc_token') || localStorage.getItem('token') || localStorage.getItem('jwt')
  if (token) cfg.headers = { ...cfg.headers, Authorization: `Bearer ${token}` }
  return cfg
})

export default api
