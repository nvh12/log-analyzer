import axios from 'axios'

const client = axios.create({ baseURL: '/api' })

const params = (p) => ({ params: p })

export const api = {
  getHttpLogs: (p) => client.get('/logs/http', params(p)).then(r => r.data),
  getHttpLog: (id) => client.get(`/logs/http/${id}`).then(r => r.data),
  getFlowLogs: (p) => client.get('/logs/flow', params(p)).then(r => r.data),
  getFlowLog: (id) => client.get(`/logs/flow/${id}`).then(r => r.data),
  getDetections: (p) => client.get('/detections', params(p)).then(r => r.data),
  getDetection: (id) => client.get(`/detections/${id}`).then(r => r.data),
  getReactions: (p) => client.get('/reactions', params(p)).then(r => r.data),
  getActiveReactions: () => client.get('/reactions/active').then(r => r.data),
  liftBlock: (id) => client.post(`/reactions/${id}/lift`),
  getSystemHealth: () => client.get('/system/health').then(r => r.data),
  getSystemConfig: () => client.get('/system/config').then(r => r.data),

  simulationStatus: () => axios.get('/simulate/status').then(r => r.data),
  simulationStart:  (p) => axios.post('/simulate/start', p).then(r => r.data),
  simulationStop:   () => axios.post('/simulate/stop').then(r => r.data),
  simulationReplay: (p) => axios.post('/simulate/replay', p).then(r => r.data),
}
