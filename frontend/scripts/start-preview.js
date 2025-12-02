#!/usr/bin/env node
// Script de inicio que construye y lanza vite preview escuchando el PORT asignado
// Compatible con Railway y desarrollo local.

const { execSync, spawn } = require('child_process');

const port = process.env.PORT || '4173';

function run(cmd) {
  execSync(cmd, { stdio: 'inherit' });
}

try {
  console.log(`[start-preview] Construyendo frontend (vite build)...`);
  run('npm run build');
  console.log(`[start-preview] Lanzando vista previa en puerto ${port} ...`);
  const preview = spawn('node', ['node_modules/vite/bin/vite.js', 'preview', '--port', port, '--host', '0.0.0.0'], {
    stdio: 'inherit'
  });
  preview.on('close', (code) => {
    console.log(`[start-preview] proceso finalizado con código ${code}`);
    process.exit(code);
  });
} catch (err) {
  console.error('[start-preview] Error durante build o preview:', err.message);
  process.exit(1);
}
