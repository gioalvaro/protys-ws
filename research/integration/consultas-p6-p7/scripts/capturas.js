// Captura de P6 y P7 ejecutadas en la consola SPARQL de la interfaz (con el razonamiento hecho).
const fs = require('fs');
const path = require('path');
const { abrir, quieto, captura, BASE, OUTDIR, log } = require('./capturas_lib');
(async () => {
  const { b, p, version } = await abrir();
  const r = { chromium: version, consultas: {} };
  for (const q of ['P6', 'P7']) {
    const texto = fs.readFileSync(path.join(__dirname, '..', 'consultas', q + '.rq'), 'utf8');
    await p.goto(BASE + '/sparql'); await quieto(p);
    await p.locator('textarea').fill(texto);
    await p.getByRole('button', { name: /^Execute/ }).click(); await quieto(p);
    const titulo = p.locator('h2', { hasText: /^Results/ });
    await titulo.scrollIntoViewIfNeeded();
    r.consultas[q] = { resultado: await titulo.innerText(), tabla: await p.locator('table').last().innerText() };
    await captura(p, `${q}-consola.png`, `SPARQL Console → pegar consultas/${q}.rq → Execute; desplazado hasta los resultados.`);
  }
  fs.writeFileSync(path.join(OUTDIR, '..', 'salidas', 'capturas.json'), JSON.stringify({ r, capturas: log }, null, 1));
  console.log(JSON.stringify(Object.fromEntries(Object.entries(r.consultas).map(([k, v]) => [k, v.resultado]))));
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
