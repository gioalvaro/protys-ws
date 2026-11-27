// Gestión de individuos por la interfaz web: alta, lista, detalle, modificación, baja y deshacer, con capturas.
const fs = require('fs');
const { abrir, quieto, captura, BASE, OUTDIR, log } = require('./capturas_lib');
const IRI = 'http://example.org/individuos/operario-web';
const IRI_DESHACER = 'http://example.org/individuos/operario-web-deshacer';
async function claseRecursoHumano(p) {
  await p.goto(BASE + '/explorer'); await quieto(p);
  await p.selectOption('select', { label: 'Canonical_Catalog_Integrated' }); await quieto(p);
  await p.getByPlaceholder('Filter class hierarchy...').fill('humano'); await quieto(p);
  const padre = p.locator('span.truncate', { hasText: /^Recurso de manufactura$/ }).first().locator('xpath=..');
  if (await padre.locator('button').count()) await padre.locator('button').first().click();
  await quieto(p);
  await p.locator('span.truncate', { hasText: /^Recurso humano$/ }).first().click(); await quieto(p);
}
const lista = p => p.locator('[data-testid=class-individuals] li').allInnerTexts();
const banda = p => p.locator('[data-testid=edit-result]').innerText();
const avisos = p => p.locator('.Toastify__toast').allInnerTexts();
async function esperarBanda(p, texto) {
  await p.locator('[data-testid=edit-result]', { hasText: texto }).waitFor({ timeout: 120000 }); await quieto(p);
}
(async () => {
  const { b, p, version } = await abrir();
  const dialogos = []; p.on('dialog', d => { dialogos.push(d.message()); d.dismiss(); });
  const peticiones = [];
  p.on('response', r => { if (r.url().includes('/api/ontology/individuals') && r.request().method() !== 'GET') peticiones.push({ metodo: r.request().method(), url: r.url().replace(BASE, ''), codigo: r.status() }); });
  const r = { chromium: version, dialogos, peticiones };

  await claseRecursoHumano(p);
  r.lista_inicial = await lista(p);
  await captura(p, 'W1-clase-con-individuos.png', 'Ontology Explorer → «Recurso humano»: el detalle lista los individuos de la clase.');

  await p.getByRole('button', { name: 'Add Individual' }).click(); await quieto(p);
  await p.getByPlaceholder('http://example.org/individual/1').fill(IRI);
  await p.getByPlaceholder('Display name').fill('Operario web');
  await captura(p, 'W2-alta-formulario.png', '«Add Individual» con IRI y etiqueta.', { esperarAvisos: false });
  await p.getByRole('button', { name: 'Create' }).click();
  await esperarBanda(p, 'Individual created');
  r.alta = { banda: await banda(p), avisos: await avisos(p) };
  await captura(p, 'W3-alta-validada.png', '«Create»: banda con la validación posterior y un único aviso.', { esperarAvisos: false });
  r.lista_tras_alta = await lista(p);

  await p.locator('[data-testid=class-individuals] button', { hasText: 'Operario web' }).click(); await quieto(p);
  r.detalle = await p.locator('main').innerText();
  await captura(p, 'W4-detalle.png', 'Detalle del individuo creado: IRI como sujeto, tipo y rdfs:label.', { esperarAvisos: false });

  await p.getByRole('button', { name: 'Edit' }).click();
  await p.getByLabel('Label', { exact: true }).fill('Operario web (modificado)');
  await p.getByRole('button', { name: 'Save' }).click();
  await esperarBanda(p, 'Individual updated');
  r.modificacion = { banda: await banda(p), titulo: await p.locator('main h2').first().innerText() };
  await captura(p, 'W5-modificacion-validada.png', '«Edit» → nueva etiqueta → «Save»: banda con la validación.', { esperarAvisos: false });

  await p.getByRole('button', { name: 'Delete', exact: true }).click(); await quieto(p);
  await captura(p, 'W6-baja-confirmacion.png', '«Delete»: confirmación en la página (sin diálogo del navegador).', { esperarAvisos: false });
  await p.getByRole('button', { name: 'Confirm delete' }).click();
  await esperarBanda(p, 'Individual deleted');
  r.baja = { banda: await banda(p), lista: await lista(p) };
  await captura(p, 'W7-baja-validada.png', '«Confirm delete»: banda con la validación; la lista ya no tiene el individuo.', { esperarAvisos: false });

  await p.getByRole('button', { name: 'Add Individual' }).click(); await quieto(p);
  await p.getByPlaceholder('http://example.org/individual/1').fill(IRI_DESHACER);
  await p.getByPlaceholder('Display name').fill('Operario para deshacer');
  await p.getByRole('button', { name: 'Create' }).click();
  await esperarBanda(p, 'Individual created');
  r.lista_antes_de_deshacer = await lista(p);
  await p.locator('[data-testid=edit-result]').getByRole('button', { name: 'Undo' }).click();
  await esperarBanda(p, 'Edit undone');
  r.deshacer = { banda: await banda(p), lista: await lista(p) };
  await captura(p, 'W8-deshacer.png', 'Alta de otro individuo y «Undo» en la banda: «Edit undone» y la lista vuelve a como estaba.', { esperarAvisos: false });

  fs.writeFileSync(OUTDIR + '/../salidas/individuos-web.json', JSON.stringify({ r, capturas: log }, null, 1));
  console.log(JSON.stringify({ inicial: r.lista_inicial.length, tras_alta: r.lista_tras_alta.length, avisos_alta: r.alta.avisos.length,
    tras_baja: r.baja.lista.length, tras_deshacer: r.deshacer.lista.length, dialogos: dialogos.length, peticiones }));
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
