import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const root=fs.realpathSync(process.argv[2])
const port=Number(process.env.EXAM_WEB_PORT || 5190)
const api=new URL(process.env.EXAM_API_TARGET || 'http://127.0.0.1:8190')
const release=process.env.EXAM_RELEASE || 'unknown'
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.ico':'image/x-icon','.woff2':'font/woff2','.ttf':'font/ttf'}
const server=http.createServer((req,res) => {
  res.setHeader('X-Content-Type-Options','nosniff')
  let url
  try { url=new URL(req.url,'http://localhost') } catch { res.writeHead(400).end(); return }
  if(url.pathname==='/release-health') {
    res.setHeader('Cache-Control','no-store')
    res.setHeader('Content-Type','application/json')
    res.end(JSON.stringify({status:'UP',release})); return
  }
  if(url.pathname==='/prod-api' || url.pathname.startsWith('/prod-api/')) {
    const upstream=http.request({
      hostname:api.hostname,port:api.port,path:(url.pathname.slice(9) || '/')+url.search,
      method:req.method,headers:{...req.headers,host:api.host},timeout:60000
    },response => {
      res.writeHead(response.statusCode,response.headers)
      response.pipe(res)
      response.on('error',()=>res.destroy())
    })
    upstream.on('timeout',()=>upstream.destroy(new Error('upstream timeout')))
    upstream.on('error',()=>{ if(!res.headersSent) res.writeHead(502,{'Content-Type':'application/json'}); res.end('{"code":502,"msg":"Backend unavailable. Check deployment logs."}') })
    req.on('aborted',()=>upstream.destroy())
    req.pipe(upstream); return
  }
  if(!['GET','HEAD'].includes(req.method)) { res.writeHead(405).end(); return }
  let decoded
  try { decoded=decodeURIComponent(url.pathname) } catch { res.writeHead(400).end(); return }
  if(decoded.includes('\0') || decoded.includes('\\')) { res.writeHead(400).end(); return }
  let file=path.resolve(root,`.${decoded}`)
  if(file!==root && !file.startsWith(root+path.sep)) { res.writeHead(403).end(); return }
  if(!fs.existsSync(file) || !fs.statSync(file).isFile()) {
    if(path.extname(decoded)) { res.writeHead(404).end(); return }
    file=path.join(root,'index.html')
  }
  try {
    file=fs.realpathSync(file)
    if(!file.startsWith(root+path.sep)) { res.writeHead(403).end(); return }
    const stat=fs.statSync(file)
    res.writeHead(200,{'Content-Type':types[path.extname(file)] || 'application/octet-stream',
      'Content-Length':stat.size,'Cache-Control':file.endsWith('index.html')?'no-store':'public, max-age=3600'})
    if(req.method==='HEAD') res.end()
    else fs.createReadStream(file).on('error',()=>res.destroy()).pipe(res)
  } catch { res.writeHead(404).end() }
})
server.headersTimeout=15000
server.requestTimeout=65000
server.listen(port,process.env.EXAM_WEB_HOST || '127.0.0.1',()=>console.log(`Release ${release}: http://127.0.0.1:${port}`))
