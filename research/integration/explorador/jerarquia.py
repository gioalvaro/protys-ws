"""Jerarquía de clases del catálogo integrado por la API del explorador (GET /api/ontology/modules/{id}/classes).

Recorre la lista de raíces que devuelve la API, cuenta las clases distintas alcanzadas, las raíces y la profundidad,
y lo compara con las clases nombradas del catálogo integrado (research/integration/erp-solo-lectura/contar-clases.py).
uso: python3 jerarquia.py <salida.json>   (PROTYS_API, por omisión http://127.0.0.1:38080/api)
"""
import json, os, sys, urllib.request

API = os.environ.get('PROTYS_API', 'http://127.0.0.1:38080/api')


def get(ruta):
    with urllib.request.urlopen(API + ruta, timeout=120) as r:
        return json.loads(r.read())


def main(salida):
    modulos = get('/ontology/modules')
    mod = next(m for m in modulos if m['name'] == 'Canonical_Catalog_Integrated')
    raices = get(f"/ontology/modules/{mod['id']}/classes")
    vistas, prof = set(), 0

    def recorrer(n, d):
        nonlocal prof
        vistas.add(n['uri']); prof = max(prof, d)
        for h in n.get('subclasses') or n.get('children') or []:
            recorrer(h, d + 1)
    for r in raices:
        recorrer(r, 1)
    resumen = {'modulo': mod['name'], 'tipo_de_respuesta': type(raices).__name__, 'raices': len(raices),
               'clases_distintas': len(vistas), 'profundidad_maxima': prof,
               'nodos_con_type_class': all(r.get('type') == 'class' for r in raices) if raices and 'type' in raices[0] else None}
    json.dump({'resumen': resumen, 'respuesta': raices}, open(salida, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
    print(json.dumps(resumen, ensure_ascii=False))


if __name__ == '__main__':
    main(sys.argv[1])
