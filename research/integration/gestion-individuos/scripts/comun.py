"""Funciones comunes de las pruebas de gestión de individuos por la plataforma. Sin dependencias fuera de la biblioteca estándar (salvo rdflib en estado.py)."""
import json, os, urllib.request, urllib.parse, urllib.error

API = os.environ.get('PROTYS_API', 'http://127.0.0.1:38080/api')
FUSEKI = os.environ.get('PROTYS_FUSEKI', 'http://127.0.0.1:33030/protys_canonical')
GRAFO_CANONICO = 'http://w3id.org/protys/ontology/Canonical_Catalog_Integrated#'
RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..', '..'))


def enc(valor):
    return urllib.parse.quote(valor, safe='')


def http(metodo, ruta, cuerpo=None, tipo='application/json', base=None, timeout=600):
    """Devuelve (código, texto). No lanza excepción por códigos HTTP de error."""
    url = (base or API) + ruta
    datos = None
    if cuerpo is not None:
        datos = cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo).encode()
    req = urllib.request.Request(url, data=datos, method=metodo)
    if datos is not None:
        req.add_header('Content-Type', tipo)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace')


def sparql(consulta):
    """Ejecuta por la plataforma (POST /api/sparql/execute) y devuelve las filas como dicts de valores."""
    codigo, texto = http('POST', '/sparql/execute', {'query': consulta})
    if codigo != 200:
        raise RuntimeError(f'SPARQL {codigo}: {texto[:300]}')
    d = json.loads(texto)
    filas = ((d.get('sparqlJson') or {}).get('results') or {}).get('bindings')
    if filas is None:
        return d
    return [{k: v.get('value') for k, v in f.items()} for f in filas]


def guardar(ruta, valor):
    os.makedirs(os.path.dirname(ruta), exist_ok=True)
    with open(ruta, 'w', encoding='utf-8') as f:
        if isinstance(valor, str):
            f.write(valor)
        else:
            json.dump(valor, f, indent=2, ensure_ascii=False)
            f.write('\n')
