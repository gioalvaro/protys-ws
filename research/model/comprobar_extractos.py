"""Comprueba que cada extracto de research/model/examples/ esté contenido en los módulos de los que sale.

Un extracto es un archivo c*_extracto_*.ttl cuyo comentario de cabecera nombra los módulos de origen
(«Extracto de ontologies/x.owl, y.owl e z.owl» o «Fuente: ontologies/x.owl»). Se considera contenido si cada triple
del extracto está en la unión de esos módulos. Los nodos en blanco (restricciones, uniones, listas,
owl:AllDisjointClasses) no tienen nombre, así que se comparan por su forma: cada nodo en blanco se reemplaza por la
descripción recursiva de sus triples.

uso: python3 research/model/comprobar_extractos.py [salida.json]
Termina con código 1 si algún extracto no está contenido.
"""
import json, re, sys
from pathlib import Path
import rdflib
from rdflib import BNode

RAIZ = Path(__file__).resolve().parents[2]
EJEMPLOS = RAIZ / 'research/model/examples'


def forma(g, nodo, vistos=()):
    if not isinstance(nodo, BNode):
        return nodo.n3()
    if nodo in vistos:
        return '<ciclo>'
    return '[' + ' ; '.join(sorted(f'{p.n3()} {forma(g, o, vistos + (nodo,))}' for p, o in g.predicate_objects(nodo))) + ']'


def enunciados(g):
    """Triples con sujeto con nombre (el objeto en blanco, por su forma) y nodos en blanco que no son objeto de nada."""
    salida = set()
    for s, p, o in g:
        if not isinstance(s, BNode):
            salida.add(f'{s.n3()} {p.n3()} {forma(g, o)}')
    for b in {s for s in g.subjects() if isinstance(s, BNode)}:
        if not any(True for _ in g.subject_predicates(b)):
            salida.add(forma(g, b))
    return salida


def origenes(texto):
    cabecera = ' '.join(l for l in texto.splitlines() if l.startswith('#') and ('Extracto de' in l or 'Fuente:' in l))
    nombres = list(dict.fromkeys(re.findall(r'([\w.-]+\.owl)', cabecera)))
    return [RAIZ / 'ontologies' / n for n in nombres]


def main(salida=None):
    resultado, ok = [], True
    for f in sorted(EJEMPLOS.glob('c*_extracto_*.ttl')):
        texto = f.read_text(encoding='utf-8')
        mods = origenes(texto)
        ext = rdflib.Graph().parse(f, format='turtle')
        red = rdflib.Graph()
        for m in mods:
            red.parse(m, format='xml')
        faltan = sorted(enunciados(ext) - enunciados(red))
        fila = {'extracto': str(f.relative_to(RAIZ)), 'modulos': [str(m.relative_to(RAIZ)) for m in mods],
                'triples': len(ext), 'contenido': not faltan and bool(mods), 'faltan': faltan}
        ok &= fila['contenido']
        resultado.append(fila)
        print(f"{'CONTENIDO' if fila['contenido'] else 'NO CONTENIDO'} {fila['extracto']} ({fila['triples']} triples) en {', '.join(fila['modulos'])}")
        for x in faltan:
            print('   falta:', x[:300])
    if salida:
        Path(salida).write_text(json.dumps(resultado, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main(*sys.argv[1:2]))
