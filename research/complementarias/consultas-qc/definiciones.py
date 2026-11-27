"""Consultas complementarias QC01-QC18: una por cada regla que el catálogo Q01-Q21 no ejercita.

La pregunta de cada consulta sale de la descripción de la regla en research/model/mechanisms.json (título y
efecto), no de mirar los triples que produce. Cada consulta pregunta por lo que la regla
aporta, con el vocabulario de la red. Viven aquí, aparte del catálogo, con numeración propia.

Este programa escribe QCxx.sparql y tabla.csv / tabla.md (regla, descripción de origen, pregunta, consulta).
uso: python3 definiciones.py
"""
import csv, json
from pathlib import Path

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[2]
PREFIJOS = '''PREFIX i: <http://w3id.org/protys/ontology/iso15531#>
PREFIX e: <http://w3id.org/protys/ontology/iso14040#>
PREFIX c: <http://w3id.org/protys/ontology/core#>
PREFIX a: <http://w3id.org/protys/ontology/alignment#>
PREFIX t: <http://w3id.org/protys/ontology/research#>
'''

# (id, reglas, pregunta, consulta SELECT)
CONSULTAS = [
    ('QC01', ['R02'], '¿Qué inventarios de ciclo de vida registran un flujo de agua?',
     'SELECT DISTINCT ?inventory WHERE { ?inventory e:hasInventoryFlow c:WaterFlow . } ORDER BY ?inventory'),
    ('QC02', ['R03'], '¿Qué flujos de producto del inventario de su proceso quedan registrados como entrada de cada lote?',
     'SELECT DISTINCT ?lot ?flow WHERE { ?lot a:hasRecordedInputFlowForLot ?flow . } ORDER BY ?lot ?flow'),
    ('QC03', ['R04'], '¿Qué indicadores de impacto superan su umbral explícito de la misma unidad y categoría?',
     'SELECT DISTINCT ?indicator WHERE { ?indicator a c:HighImpactIndicator . } ORDER BY ?indicator'),
    ('QC04', ['R05'], '¿Qué indicadores de impacto de sus procesos tiene asociados cada lote?',
     'SELECT DISTINCT ?lot ?indicator WHERE { ?lot a i:Lot ; e:hasImpact ?indicator . } ORDER BY ?lot ?indicator'),
    ('QC05', ['R06'], '¿Qué factor de emisión usa cada flujo de producto, según el material al que se refiere?',
     'SELECT DISTINCT ?flow ?factor WHERE { ?flow a e:ProductFlow ; t:usesEmissionFactor ?factor . } ORDER BY ?flow ?factor'),
    ('QC06', ['R08'], '¿Qué cantidad de unidad funcional tiene cada lote?',
     'SELECT DISTINCT ?lot ?quantity WHERE { ?lot e:hasFunctionalUnitQuantity ?quantity . } ORDER BY ?lot ?quantity'),
    ('QC07', ['R10'], '¿Qué estado de calidad propagable tiene cada lote?',
     'SELECT DISTINCT ?lot ?status WHERE { ?lot i:propagatedQualityStatus ?status . } ORDER BY ?lot ?status'),
    ('QC08', ['R11'], '¿Qué eficiencia (rendimiento sobre duración) tiene cada operación con duración positiva?',
     'SELECT DISTINCT ?operation ?efficiency WHERE { ?operation a:hasEfficiency ?efficiency . } ORDER BY ?operation ?efficiency'),
    ('QC09', ['R12'], '¿Qué equipos tienen capacidad de molienda fina?',
     'SELECT DISTINCT ?resource WHERE { ?resource i:hasCapability a:FineGrinding . } ORDER BY ?resource'),
    ('QC10', ['R13'], '¿Qué eventos de fabricación preceden a otro del mismo lote y proceso?',
     'SELECT DISTINCT ?earlier ?later WHERE { ?earlier i:precedesEvent ?later . } ORDER BY ?earlier ?later'),
    ('QC11', ['R14'], '¿Qué operador es responsable de cada operación?',
     'SELECT DISTINCT ?operation ?operator WHERE { ?operation i:responsibleOperator ?operator . } ORDER BY ?operation ?operator'),
    ('QC12', ['R15'], '¿Qué recursos están temporalmente no disponibles por un mantenimiento activo?',
     'SELECT DISTINCT ?resource WHERE { ?resource a i:temporarilyUnavailable . } ORDER BY ?resource'),
    ('QC13', ['R18'], '¿Qué lotes son candidatos a reproceso por una no conformidad?',
     'SELECT DISTINCT ?lot WHERE { ?lot a i:requiresReprocess . } ORDER BY ?lot'),
    ('QC14', ['R20'], '¿Qué flujo de inventario se vincula con cada operación que consume su material en el mismo proceso?',
     'SELECT DISTINCT ?flow ?operation WHERE { ?flow c:bridgesFlowToActivity ?operation . } ORDER BY ?flow ?operation'),
    ('QC15', ['R22'], '¿Qué recursos cuello de botella son puntos críticos por un indicador alto de su proceso?',
     'SELECT DISTINCT ?resource WHERE { ?resource a c:Hotspot . } ORDER BY ?resource'),
    ('QC16', ['R23'], '¿Qué razón entre tiempo de ciclo y valor del indicador tiene cada par operación-indicador?',
     'SELECT DISTINCT ?observation ?operation ?indicator ?ratio WHERE { ?observation t:forOperation ?operation ; '
     't:forIndicator ?indicator ; t:operationIndicatorRatio ?ratio . } ORDER BY ?observation ?ratio'),
    ('QC17', ['R24'], '¿Qué procesos presentan una brecha de cobertura ambiental?',
     'SELECT DISTINCT ?process WHERE { ?process a c:CoverageGap . } ORDER BY ?process'),
    ('QC18', ['R25'], '¿Qué factores de emisión modificados requieren recalcular los indicadores?',
     'SELECT DISTINCT ?factor WHERE { ?factor a c:RequiresRecomputeIndicators . } ORDER BY ?factor'),
]


def descripciones():
    m = json.loads((RAIZ / 'research/model/mechanisms.json').read_text())['mechanisms']
    return {x['id']: f"{x['title']}. {x['units_and_effect']}" for x in m}


def main():
    desc = descripciones()
    filas = []
    for ident, reglas, pregunta, consulta in CONSULTAS:
        cab = [f'# {ident} (consulta complementaria, fuera del catálogo Q01-Q21)', f'# Pregunta: {pregunta}']
        cab += [f'# {r}: {desc[r]}' for r in reglas]
        (AQUI / f'{ident}.sparql').write_text('\n'.join(cab) + '\n' + PREFIJOS + consulta + '\n', encoding='utf-8')
        filas.append({'consulta': ident, 'regla': ' '.join(reglas), 'descripcion_de_origen': ' | '.join(desc[r] for r in reglas),
                      'pregunta': pregunta, 'sparql': consulta})
    with (AQUI / 'tabla.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=list(filas[0])); w.writeheader(); w.writerows(filas)
    md = ['| Consulta | Regla | Descripción de origen (mechanisms.json) | Pregunta | Consulta |', '|---|---|---|---|---|']
    md += [f"| {x['consulta']} | {x['regla']} | {x['descripcion_de_origen']} | {x['pregunta']} | `{x['sparql']}` |" for x in filas]
    (AQUI / 'tabla.md').write_text('\n'.join(md) + '\n', encoding='utf-8')
    reglas = sorted({r for _, rs, _, _ in CONSULTAS for r in rs})
    print(len(CONSULTAS), 'consultas;', len(reglas), 'reglas cubiertas:', ' '.join(reglas))


if __name__ == '__main__':
    main()
