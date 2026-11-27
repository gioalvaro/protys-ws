"""Clases disjuntas y consistencia.

1. PARES: pares de clases de primer nivel propuestos como disjuntos, con la fuente de la justificación: la definición
   del término y la norma de referencia, el rdfs:comment de la clase o ambas. Se verifica que cada clase
   esté declarada en la red.
2. Escribe disjointness.ttl, junto a este guion: una ontología aparte que importa los módulos de la red
   (owl:imports) y declara un owl:disjointWith por par, para poder quitar pares de a uno. No toca ontologies/.
3. Corre AcademicRunner (HermiT antes de las reglas, reglas SWRL y HermiT después) sobre la red integrada con los
   datos del caso más ese archivo.
4. Si hay inconsistencia, corre cada par por separado para identificar los que la causan (antes o después de las
   reglas) y repite con todos los pares menos esos.
5. Explicación: con la corrida sin disyunciones (misma red y datos), lista los individuos que quedan tipados con las
   dos clases de cada par causante y rastrea de qué aserciones y axiomas sale cada tipo.

uso: python3 disyunciones.py <carpeta-salida> <carpeta-temporal>
"""
import csv, json, shutil, sys
from pathlib import Path
import rdflib
from rdflib.namespace import OWL, RDF, RDFS
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from comun import RAIZ, ejecutar, entorno, guardar, peticion  # noqa: E402
PAQ = Path(__file__).resolve().parent

P = 'http://w3id.org/protys/ontology/'
F = 'http://w3id.org/protys/fixture#'
T34 = 'definición y norma citada'

# (A, B, fuente de la justificación, justificación)
PARES = [
    # Nivel Principal (core): los cuatro conceptos generales que refinan los módulos de producto, proceso, recurso y empresa
    ('core#Product', 'core#Resource', f'{T34} (ISO 10303-1; ISO 15531-32 / ISO 10303-49; ISO 15531) y rdfs:comment',
     'Product: «cosa o sustancia producida por un proceso»; Resource: «entidad utilizada por los procesos productivos». Ojo: las dos definiciones no excluyen por sí solas que una entidad producida en otro sistema se use como insumo.'),
    ('core#Product', 'core#Process', f'{T34} y rdfs:comment', 'Cosa o sustancia frente a procedimiento con pasos u operaciones.'),
    ('core#Product', 'core#Enterprise', f'{T34} y rdfs:comment', 'Cosa o sustancia frente a organización.'),
    ('core#Process', 'core#Resource', f'{T34} y rdfs:comment', 'Procedimiento frente a entidad utilizada por los procesos.'),
    ('core#Process', 'core#Enterprise', f'{T34} y rdfs:comment', 'Procedimiento frente a organización.'),
    ('core#Resource', 'core#Enterprise', f'{T34} y rdfs:comment', 'Entidad utilizada por los procesos frente a organización que ofrece productos o servicios.'),
    # Módulo de Producto
    ('product#Substance', 'product#Product_Information', f'{T34} (ISO 10303-1)', 'Cosa con existencia material frente a información que define un ítem de producción.'),
    ('product#Substance', 'product#Customer', f'{T34} (ISO 10303-1; ISO 10303-41)', 'Cosa con existencia material producida frente a cliente que adquiere productos.'),
    ('product#Product_Information', 'product#Customer', f'{T34} (ISO 10303-1; ISO 10303-41)', 'Información frente a cliente.'),
    # Módulo de Proceso (Natural_Process ⊥ Artificial_Process ya está declarado en process-module.owl)
    ('process#Process_Plan', 'process#Process_Plant', f'{T34} (ISO 15531) y rdfs:comment', 'Conjunto ordenado de procesos frente a instalación física.'),
    ('process#Procedure', 'process#Parameters', f'{T34} (ISO 10303-49) y rdfs:comment', 'Método o protocolo frente a valores concretos de configuración.'),
    ('process#Process_Plant', 'process#Procedure', f'{T34} y rdfs:comment', 'Instalación física frente a método.'),
    ('process#Process_Plant', 'process#Parameters', f'{T34} y rdfs:comment', 'Instalación física frente a valores de configuración.'),
    ('process#Process_Plan', 'process#Parameters', f'{T34} y rdfs:comment', 'Conjunto ordenado de procesos frente a valores de configuración.'),
    ('process#Process_Plant', 'core#Process', f'{T34} y rdfs:comment', 'Instalación física donde se ejecuta el plan frente a procedimiento.'),
    # Módulo de Recurso (Tool ⊥ Equip ⊥ Device ya está declarado en resource-module.owl)
    ('resource#Person', 'resource#Material', f'{T34} (ISO 15531; ISO 18629 / ISO 10303-232)', 'Recurso humano frente a material.'),
    ('resource#Person', 'resource#Equip', f'{T34}', 'Recurso humano frente a equipo industrial fijo.'),
    ('resource#Person', 'resource#Tool', f'{T34}', 'Recurso humano frente a herramienta.'),
    ('resource#Person', 'resource#Device', f'{T34}', 'Recurso humano frente a dispositivo de medición y control.'),
    ('resource#Person', 'resource#File', f'{T34}', 'Recurso humano frente a archivo digital.'),
    ('resource#File', 'resource#Material', f'{T34}', 'Archivo digital frente a material.'),
    ('resource#File', 'resource#Equip', f'{T34}', 'Archivo digital frente a equipo.'),
    ('resource#File', 'resource#Tool', f'{T34}', 'Archivo digital frente a herramienta.'),
    ('resource#File', 'resource#Device', f'{T34}', 'Archivo digital frente a dispositivo.'),
    ('resource#Material', 'resource#Equip', f'{T34} y rdfs:comment', 'Material gestionado en producción frente a equipo fijo que ejecuta transformaciones. (Material ⊥ Tool no se propone: el comentario de Tool incluye consumibles.)'),
    ('resource#Material', 'resource#Device', f'{T34}', 'Material frente a dispositivo de medición y control.'),
    ('resource#Behaviour', 'resource#Capability', f'{T34} (ISO 10303-49) y rdfs:comment', 'Comportamiento frente a capacidad de un recurso: dos descripciones distintas.'),
    ('resource#Behaviour', 'core#Resource', 'rdfs:comment', 'Describe el comportamiento de un Resource (hasBehaviour); no es el recurso.'),
    ('resource#Capability', 'core#Resource', 'rdfs:comment', 'Describe la capacidad de un Resource (hasCapability); no es el recurso.'),
    ('resource#Role', 'resource#Person', f'{T34} y rdfs:comment', 'Función que desempeña una persona frente a la persona.'),
    ('resource#Activity', 'core#Resource', f'{T34} y rdfs:comment', 'Actividad en la que intervienen recursos frente al recurso.'),
    # Módulo de Empresa: niveles de Zhao et al. (1999) relacionados por isPartOf, y la ubicación
    ('enterprise#Factory', 'enterprise#Shop', f'{T34} (Zhao et al., 1999)', 'Niveles distintos de la jerarquía de manufactura.'),
    ('enterprise#Factory', 'enterprise#Cell', f'{T34} (Zhao et al., 1999)', 'Niveles distintos de la jerarquía.'),
    ('enterprise#Factory', 'enterprise#Station', f'{T34} (Zhao et al., 1999)', 'Niveles distintos de la jerarquía.'),
    ('enterprise#Shop', 'enterprise#Cell', f'{T34} (Zhao et al., 1999)', 'Niveles distintos de la jerarquía.'),
    ('enterprise#Shop', 'enterprise#Station', f'{T34} (Zhao et al., 1999)', 'Niveles distintos de la jerarquía.'),
    ('enterprise#Cell', 'enterprise#Station', f'{T34} (Zhao et al., 1999)', 'Niveles distintos de la jerarquía.'),
    ('enterprise#Location', 'enterprise#Factory', f'{T34} y rdfs:comment', 'Ubicación geográfica frente a instalación.'),
    ('enterprise#Location', 'enterprise#Shop', f'{T34} y rdfs:comment', 'Ubicación geográfica frente a área de producción.'),
    ('enterprise#Location', 'enterprise#Cell', f'{T34} y rdfs:comment', 'Ubicación geográfica frente a agrupación de estaciones.'),
    ('enterprise#Location', 'enterprise#Station', f'{T34} y rdfs:comment', 'Ubicación geográfica frente a estación.'),
    ('enterprise#Location', 'core#Enterprise', f'{T34} y rdfs:comment', 'Ubicación geográfica frente a organización.'),
    # ISO 15531 (EquipmentResource ⊥ HumanResource ya está declarado en iso15531-module.owl)
    ('iso15531#ManufacturingResource', 'iso15531#ManufacturingProcess', f'{T34} (ISO 15531-32:2005, 3.1.20 y 3.1.16)', 'Medio disponible para la manufactura frente a conjunto de actividades.'),
    ('iso15531#ManufacturingResource', 'iso15531#ProcessOperation', f'{T34} (ISO 15531-32:2005, 3.1.20)', 'Medio frente a operación.'),
    ('iso15531#ManufacturingResource', 'iso15531#Lot', f'{T34}', 'Medio frente a cantidad de producto obtenida en una ejecución.'),
    ('iso15531#ManufacturingResource', 'iso15531#ManufacturingData', f'{T34}', 'Medio frente a registro de datos.'),
    ('iso15531#ManufacturingResource', 'iso15531#ManufacturingCapability', f'{T34}', 'Recurso frente a la capacidad que posee.'),
    ('iso15531#ManufacturingProcess', 'iso15531#ProcessPlan', f'{T34} (ISO 15531-32:2005, 3.1.16)', 'Conjunto de actividades (ejecución) frente a plan.'),
    ('iso15531#ProcessOperation', 'iso15531#ProcessPlan', f'{T34}', 'Operación frente a plan que reúne operaciones previstas.'),
    ('iso15531#Lot', 'iso15531#ManufacturingProcess', f'{T34}', 'Cantidad de producto frente a actividades.'),
    ('iso15531#Lot', 'iso15531#ProcessOperation', f'{T34}', 'Cantidad de producto frente a operación.'),
    ('iso15531#ProductionOrder', 'iso15531#WorkOrder', f'{T34}', 'Orden que solicita producción frente a orden que ejecuta una operación.'),
    ('iso15531#ProductionOrder', 'iso15531#Lot', f'{T34}', 'Orden frente a lote.'),
    ('iso15531#WorkOrder', 'iso15531#Lot', f'{T34}', 'La orden de trabajo produce un lote; no es el lote.'),
    ('iso15531#ProcessState', 'iso15531#ResourceState', f'{T34}', 'Estado de un proceso frente a estado de un recurso.'),
    ('iso15531#QualityAttribute', 'iso15531#CostAttribute', f'{T34}', 'Atributos de tipos distintos.'),
    ('iso15531#QualityAttribute', 'iso15531#TimeAttribute', f'{T34}', 'Atributos de tipos distintos.'),
    ('iso15531#CostAttribute', 'iso15531#TimeAttribute', f'{T34}', 'Atributos de tipos distintos.'),
    # ISO 14040 (ElementaryFlow ⊥ ProductFlow ya está declarado en iso14040-module.owl)
    ('iso14040#GoalAndScopeDefinition', 'iso14040#LifeCycleInventory', f'{T34} (ISO 14040:2006, 5.2 y 3.3)', 'Fases distintas del estudio.'),
    ('iso14040#GoalAndScopeDefinition', 'iso14040#LifeCycleImpactAssessment', f'{T34} (ISO 14040:2006, 5.2 y 3.4)', 'Fases distintas.'),
    ('iso14040#GoalAndScopeDefinition', 'iso14040#Interpretation', f'{T34} (ISO 14040:2006, 5.2 y 3.5)', 'Fases distintas.'),
    ('iso14040#LifeCycleInventory', 'iso14040#LifeCycleImpactAssessment', f'{T34} (ISO 14040:2006, 3.3 y 3.4)', 'Fases distintas.'),
    ('iso14040#LifeCycleInventory', 'iso14040#Interpretation', f'{T34} (ISO 14040:2006, 3.3 y 3.5)', 'Fases distintas.'),
    ('iso14040#LifeCycleImpactAssessment', 'iso14040#Interpretation', f'{T34} (ISO 14040:2006, 3.4 y 3.5)', 'Fases distintas.'),
    ('iso14040#LCAStudy', 'iso14040#GoalAndScopeDefinition', f'{T34} (ISO 14040:2006, 3.2)', 'El estudio frente a una de sus fases.'),
    ('iso14040#LCAStudy', 'iso14040#LifeCycleInventory', f'{T34} (ISO 14040:2006, 3.2)', 'El estudio frente a una de sus fases.'),
    ('iso14040#LCAStudy', 'iso14040#LifeCycleImpactAssessment', f'{T34} (ISO 14040:2006, 3.2)', 'El estudio frente a una de sus fases.'),
    ('iso14040#LCAStudy', 'iso14040#Interpretation', f'{T34} (ISO 14040:2006, 3.2)', 'El estudio frente a una de sus fases.'),
    ('iso14040#UnitProcess', 'iso14040#ElementaryFlow', f'{T34} (ISO 14040:2006, 3.34 y 3.12)', 'Proceso unitario frente a flujo.'),
    ('iso14040#UnitProcess', 'iso14040#ProductFlow', f'{T34} (ISO 14040:2006, 3.34 y 3.27)', 'Proceso unitario frente a flujo.'),
    ('iso14040#UnitProcess', 'iso14040#SystemBoundary', f'{T34} (ISO 14040:2006, 3.34 y 3.32)', 'Proceso unitario frente a los criterios que lo incluyen.'),
    ('iso14040#UnitProcess', 'iso14040#AllocationProcedure', f'{T34} (ISO 14040:2006, 3.34 y 3.17)', 'Proceso frente al procedimiento que reparte sus flujos.'),
    ('iso14040#FunctionalUnit', 'iso14040#SystemBoundary', f'{T34} (ISO 14040:2006, 3.20 y 3.32)', 'Desempeño cuantificado frente a criterios de delimitación.'),
    ('iso14040#ImpactCategory', 'iso14040#ImpactIndicator', f'{T34} (ISO 14040:2006, 3.39 y 3.40)', 'Clase de cuestión ambiental frente a su representación cuantificable.'),
    ('iso14040#ImpactCategory', 'iso14040#CharacterizationFactor', f'{T34} (ISO 14040:2006, 3.39 y 3.37)', 'Categoría frente a factor de conversión.'),
    ('iso14040#ImpactIndicator', 'iso14040#CharacterizationFactor', f'{T34} (ISO 14040:2006, 3.40 y 3.37)', 'Indicador frente a factor de conversión.'),
]
EXISTENTES = ['process#Natural_Process ⊥ process#Artificial_Process (process-module.owl)', 'resource#Tool ⊥ Equip ⊥ Device (resource-module.owl)',
              'iso15531#EquipmentResource ⊥ HumanResource (iso15531-module.owl)', 'iso14040#ElementaryFlow ⊥ ProductFlow (iso14040-module.owl)']
IMPORTS = ['core-concepts', 'product-module', 'process-module', 'resource-module', 'enterprise-module', 'iso15531-module', 'iso14040-module',
           'paint-case-extension', 'alignment-rules']


def ident(a, b):
    return f'{a.replace("#", ":")} ⊥ {b.replace("#", ":")}'


def red():
    spec = peticion('integrated')
    g = rdflib.Graph()
    for f in spec['files']:
        if 'research/data/' not in f:
            g.parse(RAIZ / f)
    return g


def iri_ontologia(archivo):
    g = rdflib.Graph().parse(RAIZ / 'ontologies' / (archivo + '.owl'))
    return next(iter(g.subjects(RDF.type, OWL.Ontology)))


def escribir_ttl(ruta, pares, titulo):
    lineas = ['@prefix owl: <http://www.w3.org/2002/07/owl#> .', '@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .',
              '@prefix core: <http://w3id.org/protys/ontology/core#> .', '@prefix product: <http://w3id.org/protys/ontology/product#> .',
              '@prefix process: <http://w3id.org/protys/ontology/process#> .', '@prefix resource: <http://w3id.org/protys/ontology/resource#> .',
              '@prefix enterprise: <http://w3id.org/protys/ontology/enterprise#> .', '@prefix iso15531: <http://w3id.org/protys/ontology/iso15531#> .',
              '@prefix iso14040: <http://w3id.org/protys/ontology/iso14040#> .', '',
              '# Clases disjuntas propuestas, en un archivo aparte de ontologies/.',
              '# Un owl:disjointWith por par, para poder quitar pares de a uno. Justificaciones: pares.csv.', '',
              '<http://w3id.org/protys/complementarias/disjointness> a owl:Ontology ;',
              f'    rdfs:comment "{titulo}"@es ;']
    lineas += [f'    owl:imports <{iri_ontologia(a)}> ;' for a in IMPORTS[:-1]] + [f'    owl:imports <{iri_ontologia(IMPORTS[-1])}> .', '']
    for a, b, fuente, just in pares:
        lineas.append(f'# {ident(a, b)}. Fuente: {fuente}.')
        lineas.append(f'{a.replace("#", ":")} owl:disjointWith {b.replace("#", ":")} .')
    ruta.write_text('\n'.join(lineas) + '\n')


def estado(res, destino):
    pre = json.loads((destino / 'validation.json').read_text()) if (destino / 'validation.json').exists() else {}
    post = json.loads((destino / 'validation-after-swrl.json').read_text()) if (destino / 'validation-after-swrl.json').exists() else {}
    return {'status': res.get('status'), 'perfil_valido': pre.get('profile_valid'),
            'antes_de_las_reglas': pre.get('owl_validation_status', pre.get('status')),
            'despues_de_las_reglas': (post.get('owl_validation_status', post.get('status')) if post else 'NO_EJECUTADO'),
            'clases_insatisfacibles_antes': pre.get('unsatisfiable_classes'), 'clases_insatisfacibles_despues': post.get('unsatisfiable_classes') if post else None,
            'failure_kind': res.get('failure_kind'), 'error': (res.get('error') or pre.get('error') or post.get('error'))}


def correr(nombre, extra, tmp, salida):
    destino = tmp / nombre
    res = ejecutar(peticion('integrated', extra=extra), destino)
    keep = salida / 'corridas' / nombre
    keep.mkdir(parents=True, exist_ok=True)
    for f in ['request.json', 'execution.json', 'validation.json', 'validation-after-swrl.json']:
        if (destino / f).exists():
            shutil.copy(destino / f, keep / f)
    texto = (destino / 'stderr.txt').read_text()
    (keep / 'stderr-final.txt').write_text(texto[-4000:])
    e = estado(res, destino)
    print(nombre, json.dumps(e, ensure_ascii=False), flush=True)
    return e


def es_inconsistente(e):
    return 'INCONSISTENT' in (e['antes_de_las_reglas'], e['despues_de_las_reglas'], e['status'])


# --- explicación -------------------------------------------------------------------------------------------------
def superclases(tbox):
    sup = {}
    for s, o in tbox.subject_objects(RDFS.subClassOf):
        if isinstance(o, rdflib.URIRef):
            sup.setdefault(s, set()).add(o)
    for s, o in tbox.subject_objects(OWL.equivalentClass):
        if isinstance(o, rdflib.URIRef) and isinstance(s, rdflib.URIRef):
            sup.setdefault(s, set()).add(o); sup.setdefault(o, set()).add(s)
    def cierre(c):
        vistos, pila = {c}, [c]
        while pila:
            for x in sup.get(pila.pop(), ()):
                if x not in vistos:
                    vistos.add(x); pila.append(x)
        return vistos
    return cierre


def superpropiedades(tbox):
    sup = {}
    for s, o in tbox.subject_objects(RDFS.subPropertyOf):
        sup.setdefault(s, set()).add(o)
    def cierre(p):
        vistos, pila = {p}, [p]
        while pila:
            for x in sup.get(pila.pop(), ()):
                if x not in vistos:
                    vistos.add(x); pila.append(x)
        return vistos
    return cierre


def corto(x):
    return str(x).replace(P, '').replace(F, 'f:').replace('http://w3id.org/protys/ontology/research#', 't:')


def razones(ind, clase, abox, tbox, cls_sup, prop_sup):
    """Caminos de una sola aserción a la clase: tipo afirmado ⊑ clase, o dominio/rango (también de superpropiedades)."""
    r = []
    for t in abox.objects(ind, RDF.type):
        if clase in cls_sup(t):
            r.append(f'tipo afirmado {corto(t)} ⊑ {corto(clase)}')
    for p, o in abox.predicate_objects(ind):
        for sp in prop_sup(p):
            for d in tbox.objects(sp, RDFS.domain):
                if clase in cls_sup(d):
                    r.append(f'sujeto de {corto(p)} → {corto(o)}; dominio de {corto(sp)} = {corto(d)}')
    for s, p in abox.subject_predicates(ind):
        for sp in prop_sup(p):
            for rg in tbox.objects(sp, RDFS.range):
                if clase in cls_sup(rg):
                    r.append(f'objeto de {corto(s)} {corto(p)}; rango de {corto(sp)} = {corto(rg)}')
    return sorted(set(r))


def explicar(culpables, base_dir, tbox):
    cls_sup, prop_sup = superclases(tbox), superpropiedades(tbox)
    entrada = rdflib.Graph().parse(base_dir / 'prepared-input.ttl')
    pre = rdflib.Graph().parse(base_dir / 'prepared-input.ttl').parse(base_dir / 'dl-pre-deductions.ttl')
    post = rdflib.Graph().parse(base_dir / 'result-model.ttl')
    abox_post = post  # incluye lo que agregan las reglas
    out = {}
    for a, b, *_ in culpables:
        A, B = rdflib.URIRef(P + a), rdflib.URIRef(P + b)
        def ambos(g):
            return sorted(set(g.subjects(RDF.type, A)) & set(g.subjects(RDF.type, B)), key=str)
        antes, despues = ambos(pre), ambos(post)
        out[ident(a, b)] = {
            'individuos_con_las_dos_clases_antes_de_las_reglas': [corto(x) for x in antes],
            'individuos_con_las_dos_clases_despues_de_las_reglas': [corto(x) for x in despues],
            'por_que': {corto(x): {corto(A): razones(x, A, entrada, tbox, cls_sup, prop_sup) or razones(x, A, abox_post, tbox, cls_sup, prop_sup) or ['por inferencia de más de un paso o por una regla'],
                                   corto(B): razones(x, B, entrada, tbox, cls_sup, prop_sup) or razones(x, B, abox_post, tbox, cls_sup, prop_sup) or ['por inferencia de más de un paso o por una regla']}
                        for x in despues},
        }
    return out


def main(salida, tmp):
    salida, tmp = Path(salida), Path(tmp)
    tmp.mkdir(parents=True, exist_ok=True)
    guardar(salida / 'entorno.json', entorno())
    tbox = red()
    clases = set(tbox.subjects(RDF.type, OWL.Class))
    faltan = [c for a, b, *_ in PARES for c in (a, b) if rdflib.URIRef(P + c) not in clases]
    if faltan:
        raise SystemExit(f'clases no declaradas en la red: {faltan}')
    with (salida / 'pares.csv').open('w', newline='') as f:
        w = csv.writer(f); w.writerow(['par', 'clase_a', 'clase_b', 'fuente', 'justificacion'])
        for a, b, fuente, just in PARES:
            w.writerow([ident(a, b), a, b, fuente, just])
    archivo = PAQ / 'disjointness.ttl'
    escribir_ttl(archivo, PARES, f'{len(PARES)} pares disjuntos propuestos')
    rdflib.Graph().parse(archivo)  # se puede leer
    informe = {'pares_propuestos': len(PARES), 'disyunciones_ya_declaradas_en_la_red': EXISTENTES,
               'archivo': str(archivo.relative_to(RAIZ)), 'sha256_archivo': __import__('hashlib').sha256(archivo.read_bytes()).hexdigest()}
    informe['sin_disyunciones'] = correr('00-sin-disyunciones', [], tmp, salida)
    informe['con_todos_los_pares'] = completa = correr('01-todos-los-pares', [archivo], tmp, salida)
    if es_inconsistente(completa) or completa['status'] != 'CONSISTENT':
        por_par = {}
        for i, par in enumerate(PARES, 1):
            uno = tmp / f'par-{i:02d}.ttl'
            escribir_ttl(uno, [par], ident(par[0], par[1]))
            por_par[ident(par[0], par[1])] = correr(f'par-{i:02d}', [uno], tmp, salida)
        informe['par_por_par'] = por_par
        culpables = [p for p in PARES if es_inconsistente(por_par[ident(p[0], p[1])])]
        informe['pares_que_causan_inconsistencia'] = [{'par': ident(p[0], p[1]), 'antes': por_par[ident(p[0], p[1])]['antes_de_las_reglas'],
                                                        'despues': por_par[ident(p[0], p[1])]['despues_de_las_reglas']} for p in culpables]
        resto = [p for p in PARES if p not in culpables]
        sin = PAQ / 'disjointness-sin-pares-inconsistentes.ttl'
        escribir_ttl(sin, resto, f'{len(resto)} pares: los propuestos menos los que causan inconsistencia ({", ".join(ident(p[0], p[1]) for p in culpables)})')
        informe['archivo_sin_pares_inconsistentes'] = str(sin.relative_to(RAIZ))
        informe['sin_pares_inconsistentes'] = correr('02-sin-pares-inconsistentes', [sin], tmp, salida)
        informe['explicacion'] = explicar(culpables, tmp / '00-sin-disyunciones', tbox)
    guardar(salida / 'resultado.json', informe)
    print(json.dumps({k: v for k, v in informe.items() if k != 'par_por_par'}, indent=1, ensure_ascii=False))


if __name__ == '__main__':
    main(*sys.argv[1:3])
