import axios from 'axios'

// Use environment variable or fallback to localhost for development
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const api = axios.create({ baseURL: API_URL })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('cc_token')
  if (token) cfg.headers = { ...cfg.headers, Authorization: `Bearer ${token}` }
  return cfg
})

export default api
