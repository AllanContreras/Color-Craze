import axios from 'axios'

export function getApiBase(env = import.meta.env){
  return env.PROD
    ? 'https://color-craze-production.up.railway.app'
    : (env.VITE_API_URL || 'http://localhost:8080')
}

const api = axios.create({ baseURL: getApiBase() })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('cc_token')
  if (token) cfg.headers = { ...cfg.headers, Authorization: `Bearer ${token}` }
  return cfg
})

export default api
