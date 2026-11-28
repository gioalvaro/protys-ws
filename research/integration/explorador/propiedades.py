"""Árboles de propiedades del catálogo integrado por la API del explorador (GET /api/ontology/modules/{id}/properties).

Pide el servicio para el módulo Canonical_Catalog_Integrated y recorre los dos árboles (propiedades de objeto y de
datos). Por grupo informa la cantidad de propiedades distintas alcanzadas, la cantidad que declara el servicio, las
raíces y la profundidad máxima, contada de dos formas:
  - por nodos: una raíz tiene profundidad 1 y cada subpropiedad, la de su padre más 1 (igual que jerarquia.py);
  - por aristas: una raíz tiene profundidad 0, es decir, la cantidad de niveles de rdfs:subPropertyOf bajo las raíces.
uso: python3 propiedades.py <salida.json>   (PROTYS_API, por omisión http://127.0.0.1:38080/api)
"""
import json, os, sys, urllib.request

API = os.environ.get('PROTYS_API', 'http://127.0.0.1:38080/api')


def get(ruta):
    with urllib.request.urlopen(API + ruta, timeout=120) as r:
        return json.loads(r.read())


def recorrer(raices):
    vistas, nodos, prof = set(), 0, 0

    def visitar(n, d):
        nonlocal nodos, prof
        vistas.add(n['uri']); nodos += 1; prof = max(prof, d)
        for h in n.get('subproperties') or []:
            visitar(h, d + 1)
    for r in raices:
        visitar(r, 1)
    return {'raices': len(raices), 'nombres_raices': [r['localName'] for r in raices], 'propiedades_distintas': len(vistas),
            'nodos_del_arbol': nodos, 'profundidad_maxima_por_nodos': prof,
            'profundidad_maxima_por_aristas': prof - 1 if prof else 0}, vistas


def main(salida):
    modulos = get('/ontology/modules')
    mod = next(m for m in modulos if m['name'] == 'Canonical_Catalog_Integrated')
    resp = get(f"/ontology/modules/{mod['id']}/properties")
    objeto, uris_objeto = recorrer(resp['objectProperties'])
    datos, uris_datos = recorrer(resp['dataProperties'])
    objeto['declaradas_por_el_servicio'] = resp['objectPropertyCount']
    datos['declaradas_por_el_servicio'] = resp['dataPropertyCount']
    resumen = {'modulo': mod['name'], 'propiedades_de_objeto': objeto, 'propiedades_de_datos': datos,
               'propiedades_de_anotacion': resp['annotationPropertyCount'],
               'en_los_dos_grupos': sorted(uris_objeto & uris_datos),
               'forma_de_contar_la_profundidad': 'por nodos: raíz = 1; por aristas: raíz = 0 (niveles de subPropertyOf bajo las raíces)'}
    json.dump({'resumen': resumen, 'respuesta': resp}, open(salida, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
    print(json.dumps(resumen, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main(sys.argv[1])
