// Capturas de la interfaz de PROTYS-WS. Uso: node capturas.js A|B|C
//   A: red integrada sin el piloto (tablero, explorador, consola SPARQL, alineamiento, razonamiento, conectores).
//   B: incorporación del piloto ISO 10303 con el asistente, paso por paso, y el explorador con el piloto.
//   C: conectores ERP materializados y la consulta que cruza los dos ERP (plataforma con los ERP de prueba).
// Ventana 1440×900 con deviceScaleFactor 2 (PNG de 2880×1800), colorScheme light. PROTYS_WEB fija la URL base.
const fs = require('fs');
const path = require('path');
const { abrir, quieto, captura, BASE, OUTDIR, log } = require('./capturas_lib');
const RAIZ = path.join(__dirname, '..', '..', '..', '..');

async function irA(p, ruta) { await p.goto(BASE + ruta); await quieto(p); }
async function desplazarHasta(p, locator) { await locator.first().evaluate(e => e.scrollIntoView({ block: 'start' })); await p.waitForTimeout(500); }
async function moduloExplorador(p, nombre) {
  await irA(p, '/explorer');
  await p.selectOption('select', { label: nombre }); await quieto(p);
}
function nodo(p, texto) { return p.locator('span.truncate', { hasText: new RegExp('^' + texto + '$') }).first(); }
async function expandir(p, texto) {
  const fila = nodo(p, texto).locator('xpath=..');
  if (await fila.locator('button').count()) await fila.locator('button').first().click();
  await quieto(p);
}
async function consulta(p, texto) {
  await irA(p, '/sparql');
  await p.locator('textarea').fill(texto);
  await agrandar(p);
  await p.getByRole('button', { name: /^Execute/ }).click(); await quieto(p);
}
async function plantilla(p, ident) {
  await irA(p, '/sparql');
  await p.getByRole('button', { name: 'Templates' }).click(); await quieto(p);
  await p.getByRole('button', { name: new RegExp('^' + ident + ' - ') }).click(); await quieto(p);
  await agrandar(p);
  await p.getByRole('button', { name: /^Execute/ }).click(); await quieto(p);
}
async function agrandar(p) {
  // Agranda el campo de la consulta hasta ver el texto completo, como con su control de tamaño.
  await p.locator('textarea').evaluate(t => { t.style.height = Math.max(t.scrollHeight + 8, 256) + 'px'; });
  await p.locator('textarea').scrollIntoViewIfNeeded();
}
async function verConsulta(p) {
  // Desde el título «Query»: se ve el comienzo de la consulta (el texto sigue más abajo).
  await p.locator('h2,h3', { hasText: /^Query$/ }).first().evaluate(e => e.scrollIntoView({ block: 'start' }));
  await p.waitForTimeout(500);
}
async function pulsar(p, nombre) {
  // Espera a que se cierren los avisos emergentes, que tapan los botones de abajo a la derecha.
  for (const x of await p.locator('.Toastify__close-button').all()) await x.click().catch(() => {});
  await p.waitForTimeout(800);
  await p.getByRole('button', { name: nombre }).evaluate(boton => boton.click()); await quieto(p);
}
function resultados(p) { return p.locator('h2', { hasText: /^Results/ }); }

async function serieA(p, r) {
  await irA(p, '/');
  await captura(p, 'A1a-tablero.png', 'Dashboard.');
  await desplazarHasta(p, p.locator('h2,h3', { hasText: 'Triples per Module' }));
  await captura(p, 'A1b-tablero-graficos.png', 'Dashboard desplazado hasta «Triples per Module».');

  await irA(p, '/alignment');
  await p.locator('summary', { hasText: 'SWRL' }).first().click();
  await desplazarHasta(p, p.locator('h2,h3', { hasText: 'Alignment Rules' }));
  await captura(p, 'A6-alineamiento-reglas.png', 'Alignment → desplegar «SWRL» de R01; desplazado hasta «Alignment Rules».');

  await p.getByRole('button', { name: 'Execute Reasoning' }).click();
  await p.waitForFunction(() => !/Executing/.test(document.body.innerText), null, { timeout: 300000 });
  await quieto(p);
  r.razonamiento = (await p.locator('main').innerText()).slice(0, 2000);
  await captura(p, 'A7-razonamiento-controles.png', 'Alignment → Execute Reasoning → esperar el resultado.');

  await moduloExplorador(p, 'Canonical_Catalog_Integrated');
  for (const n of ['Proceso', 'Artificial_Process', 'Recurso', 'Device']) await expandir(p, n);
  await desplazarHasta(p, nodo(p, 'Proceso'));
  await captura(p, 'A2-explorador-jerarquia.png', 'Ontology Explorer → «Canonical_Catalog_Integrated» → abrir Proceso, Artificial_Process, Recurso y Device; desplazado hasta Proceso.');

  await moduloExplorador(p, 'Canonical_Catalog_Integrated');
  await p.getByPlaceholder('Filter class hierarchy...').fill('Device'); await quieto(p);
  await expandir(p, 'Recurso');
  await nodo(p, 'Device').click(); await quieto(p);
  await captura(p, 'A3-explorador-detalle-clase.png', 'Ontology Explorer → «Canonical_Catalog_Integrated» → filtrar «Device» → abrir Recurso → elegir Device.');

  for (const [q, a, b] of [['Q06', 'A4a-consulta-Q06.png', 'A4b-consulta-Q06.png'], ['Q04', 'A5a-consulta-Q04.png', 'A5b-consulta-Q04.png']]) {
    await plantilla(p, q);
    await verConsulta(p);
    await captura(p, a, `SPARQL Console → Templates → ${q} → agrandar el campo de la consulta → Execute; vista de la consulta.`);
    await desplazarHasta(p, resultados(p));
    r[q] = await resultados(p).innerText();
    await captura(p, b, `La misma pantalla, desplazada hasta «${r[q]}».`);
  }

  await irA(p, '/erp');
  await captura(p, 'A8-erp-conectores.png', '/erp sin conectores registrados.');
}

async function serieB(p, r) {
  const fragmento = path.join(RAIZ, 'ontologies/iso10303-ap242-fragment.owl');
  const datos = path.join(RAIZ, 'research/data/automotive.ttl');
  const reglas = path.join(RAIZ, 'ontologies/iso10303-alignment-rules.owl');
  await irA(p, '/wizard');
  await p.locator('input[type=file]').first().setInputFiles(fragmento);
  await p.getByLabel('Instance data (optional)').setInputFiles(datos);
  await captura(p, 'B1-paso1-estandar-y-datos.png', '/wizard → elegir el fragmento AP242 y, en «Instance data (optional)», automotive.ttl.', { esperarAvisos: false });
  await pulsar(p, 'Upload & Continue');
  await pulsar(p, 'Validate & Continue');
  await captura(p, 'B2-paso2-validado.png', '«Upload & Continue» → «Validate & Continue»; el resultado del paso 2 aparece al abrir el paso 3.');
  const casillas = p.locator('input[type=checkbox]');
  r.reglas_seleccionables = await casillas.count();
  for (let i = 0; i < r.reglas_seleccionables; i++) { if (!(await casillas.nth(i).isChecked())) await casillas.nth(i).check(); }
  await captura(p, 'B3a-paso3-reglas-seleccion.png', `Paso 3: las ${r.reglas_seleccionables} reglas registradas, marcadas.`, { esperarAvisos: false });
  await p.getByLabel('Session rules (SWRL)').setInputFiles(reglas); await quieto(p);
  await desplazarHasta(p, p.locator('h3', { hasText: 'Session rules (SWRL)' }));
  await captura(p, 'B3b-paso3-reglas-sesion.png', 'Paso 3, al final: «Session rules (SWRL)» con iso10303-alignment-rules.owl cargado.', { esperarAvisos: false });
  await pulsar(p, 'Configure & Continue');
  await pulsar(p, 'Verify & Continue');
  await p.evaluate(() => { document.querySelectorAll('*').forEach(e => { if (e.scrollHeight > e.clientHeight) e.scrollTop = 0; }); window.scrollTo(0, 0); });
  await p.waitForTimeout(500);
  await captura(p, 'B4a-paso4-verificado.png', '«Configure & Continue» → «Verify & Continue»: pantalla de cierre con «Executed Rules».');
  await desplazarHasta(p, p.locator('h3', { hasText: 'Executed Rules' }));
  await captura(p, 'B4b-paso4-verificado.png', 'La misma, desplazada hasta «Executed Rules».', { esperarAvisos: false });
  await desplazarHasta(p, p.getByRole('button', { name: 'Create Ontology' }));
  await captura(p, 'B5-paso5-confirmar.png', 'La misma, al final: «Summary» y «Create Ontology», antes de confirmar.', { esperarAvisos: false });
  await pulsar(p, 'Create Ontology');
  r.completado = (await p.locator('main').innerText()).slice(0, 1500);
  await captura(p, 'B6-completado.png', '«Create Ontology»: la incorporación queda registrada.');
  await irA(p, '/explorer');
  r.modulos = await p.locator('select option').allInnerTexts();
  await p.selectOption('select', { label: 'iso10303-ap242-fragment' }); await quieto(p);
  await captura(p, 'B7-explorador-con-piloto.png', 'Ontology Explorer → elegir «iso10303-ap242-fragment».');
}

async function serieC(p, r) {
  await irA(p, '/erp');
  r.conectores = (await p.locator('main').innerText()).slice(0, 2000);
  await captura(p, 'C1-erp-conectores.png', '/erp con los dos conectores registrados y materializados.');
  const texto = fs.readFileSync(path.join(RAIZ, 'research/integration/erp/queries/CRUZADA.rq'), 'utf8');
  await consulta(p, texto);
  await verConsulta(p);
  await captura(p, 'C2a-consulta-cruzada.png', 'SPARQL Console → pegar research/integration/erp/queries/CRUZADA.rq → agrandar el campo → Execute; vista de la consulta.');
  await resultados(p).evaluate(e => e.scrollIntoView({ block: 'start' }));
  await p.waitForTimeout(500);
  r.cruzada = await resultados(p).innerText();
  await captura(p, 'C2b-consulta-cruzada.png', `La misma pantalla, desplazada hasta el final: «${r.cruzada}».`, { esperarAvisos: false });
}

(async () => {
  const serie = process.argv[2];
  const { b, p, version } = await abrir();
  const dialogos = []; p.on('dialog', d => { dialogos.push(d.message()); d.dismiss(); });
  const r = { serie, chromium: version, dialogos };
  await ({ A: serieA, B: serieB, C: serieC })[serie](p, r);
  fs.mkdirSync(path.join(OUTDIR, '..', 'salidas'), { recursive: true });
  fs.writeFileSync(path.join(OUTDIR, '..', 'salidas', `serie-${serie}.json`), JSON.stringify({ r, capturas: log }, null, 1));
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
