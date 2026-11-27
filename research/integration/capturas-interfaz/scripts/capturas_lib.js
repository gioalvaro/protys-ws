// Utilidades de Playwright. PLAYWRIGHT_MODULE y CHROMIUM_PATH se pasan por entorno (no se guardan rutas locales).
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const BASE = process.env.PROTYS_WEB || 'http://127.0.0.1:38081';
const OUTDIR = process.env.OUTDIR || 'capturas';
const log = [];
async function abrir() {
  const b = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, colorScheme: 'light', locale: 'en-US' });
  const p = await ctx.newPage();
  await p.mouse.move(1430, 890);
  return { b, p, version: b.version() };
}
async function quieto(p) {
  await p.waitForLoadState('networkidle');
  await p.waitForFunction(() => !document.querySelector('.animate-spin') && !/Loading|Executing\.\.\.|validating\.\.\.|Saving\.\.\.|Deleting\.\.\.|Undoing\.\.\./.test(document.body.innerText), null, { timeout: 120000 });
  await p.waitForTimeout(800);
}
async function captura(p, archivo, pasos, { esperarAvisos = true } = {}) {
  await quieto(p);
  if (esperarAvisos) await p.waitForTimeout(4500);
  // Los avisos emergentes se cierran antes de capturar: algunos no se cierran solos y tapan el contenido.
  for (const x of await p.locator('.Toastify__close-button').all()) await x.click().catch(() => {});
  await p.waitForTimeout(800);
  await p.mouse.move(1430, 890);
  await p.screenshot({ path: OUTDIR + '/' + archivo });
  log.push({ archivo, url: p.url(), utc: new Date().toISOString(), pasos });
  console.log('OK ' + archivo);
}
module.exports = { abrir, quieto, captura, BASE, OUTDIR, log };
