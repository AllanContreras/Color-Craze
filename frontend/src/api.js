import axios from 'axios'

// En producción (Railway) usa directamente el backend público;
// en desarrollo sigue usando VITE_API_URL o localhost.
const API_URL = import.meta.env.PROD
  ? 'https://color-craze-production.up.railway.app'
  : (import.meta.env.VITE_API_URL || 'http://localhost:8080')

const api = axios.create({ baseURL: API_URL })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('cc_token')
  if (token) cfg.headers = { ...cfg.headers, Authorization: `Bearer ${token}` }
  return cfg
})

export default api
