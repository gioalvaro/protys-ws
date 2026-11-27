"""Reconcile local concept annotations without changing any logical axiom.

This constructor repairs source attribution comments only. It is also called
by build_context.py when rebuilding the contextual model.
"""
import hashlib
import json
from pathlib import Path
from lxml import etree as E

NS = {
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'owl': 'http://www.w3.org/2002/07/owl#',
}
PRODUCT = 'http://w3id.org/protys/ontology/product'
CORE = 'http://w3id.org/protys/ontology/core#'
PAINT = 'http://w3id.org/protys/ontology/paint-ext#'
PROCESS = 'http://w3id.org/protys/ontology/process'
BRUNO = 'https://www.naun.org/main/NAUN/circuitssystemssignal/2015/a062005-021.pdf'
CORRECTIONS = {
    'product-module.owl': {
        PRODUCT: 'Conceptualización propia del Nivel de Refinamiento de PROTYS(KB), que profundiza Product del Nivel Principal. La distinción entre producto y componente y la composición se inspiran en la ontología de referencia de Bruno (2015), sección III.A. Thing, Substance y su posición en esta jerarquía son decisiones locales; no se atribuyen a Bruno ni se presentan como definiciones literales comunes a todos los estándares ISO considerados.',
        PRODUCT + '#Thing': 'Categoría general propia del Módulo de Producto de PROTYS(KB). No es una definición universal de los estándares ISO ni un concepto atribuido a Bruno (2015). Su IRI es product:Thing y es distinto de owl:Thing.',
        PRODUCT + '#Substance': 'Categoría propia de PROTYS(KB), declarada como subclase de product:Thing y superclase de Production_Item en este módulo. Esta elección de jerarquía no se atribuye a Bruno (2015) ni a una definición literal compartida por los estándares ISO.',
        PRODUCT + '#Production_Item': 'Abstracción local que agrupa Product y Production_Component. La distinción entre producto y componente se inspira en Bruno (2015), sección III.A. Su ubicación bajo product:Substance pertenece a la conceptualización propia de PROTYS(KB).',
        PRODUCT + '#Product': 'Especialización local de Production_Item, equivalente a core:Product dentro de PROTYS(KB). Esta equivalencia interna conecta el Nivel Principal con su refinamiento; no constituye una equivalencia universal entre conceptos ISO ni identifica el producto con un registro de ProductFlow.',
        PRODUCT + '#Production_Component': 'Componente de producto de la conceptualización local. Bruno (2015), sección III.A, respalda la distinción entre producto y componente y describe la composición y descomposición de componentes. Este módulo declara la asociación composes con Product; no impone una descomposición recursiva ni nuevas cardinalidades.',
        PRODUCT + '#isComposedOF': 'Asociación local de Product con sus Production_Components, inversa de composes. La composición de productos a partir de componentes se inspira en Bruno (2015), sección III.A; no se presenta como una definición literal universal de ISO 10303.',
    },
    'core-concepts.owl': {
        CORE + 'Product': 'Concepto general de producto elegido para el Nivel Principal de PROTYS(KB), que se refina en el Módulo de Producto. Su descripción mediante cosa o sustancia pertenece a la conceptualización local; no se presenta como una definición literal compartida por ISO 10303, ISO 15531 e ISO 15926.',
    },
    'paint-case-extension.owl': {
        PAINT + 'Chemical_Compound': 'Especialización local del caso simulado de pintura para compuestos químicos, como resinas, aditivos o biocidas. Se declara como subclase de product:Substance dentro de PROTYS(KB); esa relación no se atribuye a una generalización universal de los estándares ISO ni a Bruno (2015).',
    },
    'process-module.owl': {
        PROCESS: 'Conceptualización propia del Nivel de Refinamiento de PROTYS(KB), que profundiza Process del Nivel Principal. Las referencias a ISO 8000, ISO 10303, ISO 13584, ISO 15531, ISO 15926 e ISO 18629 constituyen el contexto normativo del análisis; no se presenta esta jerarquía local como una taxonomía derivada literalmente de todos esos estándares. Los recursos de las actividades se representan en el Módulo de Recursos y se referencian sin redefinirlos aquí.',
        PROCESS + '#Natural_Process': 'Categoría local de PROTYS(KB) para procesos naturales, declarada como subclase de core:Process y disjunta con Artificial_Process. Esta clasificación pertenece a la conceptualización propuesta; no se atribuye como definición literal compartida por los estándares ISO considerados.',
        PROCESS + '#Artificial_Process': 'Categoría local de PROTYS(KB) para procesos artificiales, incluidos los procesos industriales planificados, declarada como subclase de core:Process. Su distinción respecto de Natural_Process es una decisión de la conceptualización propuesta, con referencias normativas de contexto.',
        PROCESS + '#Procedure': 'Método o protocolo para ejecutar un proceso en la conceptualización local. La propiedad specifiedBy declara core:Process como dominio y Procedure como rango. Esa asociación propuesta no se atribuye a una definición literal de ISO 10303 sin una cláusula normativa identificada.',
        PROCESS + '#composedBy': 'Asociación local de core:Process con Process_Activity, declarados como dominio y rango de composedBy. Los axiomas actuales de esta propiedad no imponen una cardinalidad mínima 1 ni una agregación 1..n.',
    },
}

CORRECTIONS.setdefault('iso15531-module.owl', {}).update({
    'http://w3id.org/protys/ontology/iso15531#hasMaintenance': 'Asocia un recurso con un registro de mantenimiento. Sólo el estado research:maintenanceStatus ACTIVE de la instantánea permite R15; los registros PLANNED, COMPLETED o sin estado no implican indisponibilidad. No se infiere vigencia mediante fechas ni se retractan tipos al cambiar una instantánea.',
})
CORRECTIONS.setdefault('alignment-rules.owl', {}).update({
    'http://w3id.org/protys/ontology/alignment#impactThreshold': 'Parámetro histórico genérico conservado por compatibilidad; R04 del catálogo canónico utiliza research:ThresholdRecord con valor, unidad y categoría acoplados en el mismo registro. No se usa GlobalConfig para combinar umbrales de distintas magnitudes.',
})

# Bounded editorial pass over the eight base modules. These comments describe
# the local schema or effects already approved; they introduce no constraints.
CORRECTIONS['core-concepts.owl'].update({
    CORE + 'Process': 'Concepto local para un procedimiento con pasos u operaciones que puede producir un producto o modificar sus características. ISO 10303-49 es una referencia normativa de contexto; esta descripción propuesta no se presenta como una definición literal de una cláusula identificada. Se refina en el Módulo de Proceso.',
    CORE + 'Resource': 'Concepto local para entidades utilizadas por los procesos productivos y descritas mediante comportamientos y capacidades. Las referencias a ISO 10303-49 e ISO 15531 contextualizan la propuesta; no se atribuye esta formulación a una cláusula normativa literal. Se refina en el Módulo de Recurso.',
    CORE + 'Enterprise': 'Concepto local para una organización o agrupación de organizaciones con objetivos de ofrecer productos o servicios. ISO 10303-239 se conserva como referencia normativa de contexto, sin presentar esta descripción como una definición literal del estándar. Se refina en el Módulo de Empresa.',
    CORE + 'involve': 'Asociación local entre Enterprise y Process, declarados como dominio y rango de involve. Representa la vinculación de una empresa con sus procesos; no se presenta como reproducción literal de una relación de ISO 15531 o ISO 18629 ni impone una cantidad mínima de procesos.',
})
CORRECTIONS['product-module.owl'].update({
    PRODUCT + '#Product_Information': 'Categoría de la conceptualización local para información del producto. Physical_Characteristics, Instruction, Fact y Concept se declaran como especializaciones de esta clase; la agrupación física y funcional propuesta no se atribuye a una cláusula identificada de ISO 10303-1 ni constituye una partición exhaustiva formal.',
    PRODUCT + '#Customer': 'Categoría local para un cliente que adquiere productos. acquiredBy declara Product como dominio y Customer como rango; esa asociación no impone una cardinalidad formal muchos-a-muchos ni se presenta como una simplificación literal de una cláusula identificada de ISO 10303-41.',
    PRODUCT + '#definedBy': 'Asociación local de Production_Item con Product_Information, declarados como dominio y rango de definedBy. Los axiomas actuales no imponen una cardinalidad mínima 1 ni una agregación 1..n.',
    PRODUCT + '#composes': 'Asociación local de Production_Component con Product, declarados como dominio y rango de composes. No impone cardinalidades mínimas o máximas ni una multiplicidad formal n:n.',
    PRODUCT + '#acquiredBy': 'Asociación local de Product con Customer, declarados como dominio y rango de acquiredBy. Permite registrar vínculos de adquisición sin imponer cardinalidades mínimas o máximas ni una multiplicidad formal n:n.',
})
RESOURCE = 'http://w3id.org/protys/ontology/resource'
CORRECTIONS['resource-module.owl'] = {
    RESOURCE: 'Conceptualización propia del Nivel de Refinamiento de PROTYS(KB), que profundiza Resource del Nivel Principal mediante Behaviour, Capability, Tool, Equip, Device, Material, Person, Role y File. ISO 10303-49, ISO 15531, ISO 18629 y las partes de ISO 10303 consideradas para materiales son referencias normativas de contexto; la jerarquía y las asociaciones propuestas no se presentan como una transcripción literal de esos estándares.',
    RESOURCE + '#Behaviour': 'Categoría local para describir el comportamiento de un Resource mediante hasBehaviour. ISO 10303-49 es una referencia de contexto; no se atribuye esta categoría propuesta a una cláusula normativa literal identificada.',
    RESOURCE + '#Capability': 'Categoría local para describir la capacidad de un Resource mediante hasCapability. ISO 10303-49 es una referencia de contexto; no se atribuye esta categoría propuesta a una cláusula normativa literal identificada.',
    RESOURCE + '#Tool': 'Especialización local de core:Resource para herramientas manuales o consumibles empleados en operaciones, como cepillos o raspadores. ISO 15531 e ISO 18629 son referencias de contexto; no se presenta esta especialización como una definición literal común a ambos estándares.',
    RESOURCE + '#Material': 'Especialización local de core:Resource para materiales gestionados en las fases de producción. Las referencias a ISO 10303-232, ISO 10303-227 e ISO 10303-1082 contextualizan el análisis; la jerarquía propuesta no se atribuye literalmente a esas partes.',
    RESOURCE + '#hasBehaviour': 'Asociación local de core:Resource con Behaviour, declarados como dominio y rango de hasBehaviour. No exige que cada recurso tenga un comportamiento registrado ni impone una cardinalidad; la referencia a ISO 10303-49 es de contexto.',
    RESOURCE + '#hasCapability': 'Asociación local de core:Resource con Capability, declarados como dominio y rango de hasCapability. No exige que cada recurso tenga una capacidad registrada ni impone una cardinalidad; la referencia a ISO 10303-49 es de contexto.',
    RESOURCE + '#hasRole': 'Asociación local de Person con Role, declarados como dominio y rango de hasRole. Permite registrar roles vinculados a capacidades sin imponer una cardinalidad mínima 1 ni una agregación 1..n.',
    RESOURCE + '#canBeConsultedBy': 'Asociación local de Activity con Role, declarados como dominio y rango de canBeConsultedBy, para representar roles que pueden consultar actividades. No impone una multiplicidad formal n:n ni constituye por sí sola un mecanismo ejecutado de control de acceso.',
}
ENVIRONMENT = 'http://w3id.org/protys/ontology/iso14040#'
CORRECTIONS['iso14040-module.owl'] = {
    ENVIRONMENT + 'hasCharacterizationFactor': 'Propiedad histórica con rango CharacterizationFactor para asociar un sujeto con un factor de caracterización LCIA. R06 utiliza research:hasEmissionFactor y research:usesEmissionFactor para los factores del escenario; no utiliza esta propiedad para convertirlos en factores de caracterización.',
    ENVIRONMENT + 'factorValue': 'Valor numérico decimal de un CharacterizationFactor, declarado como dominio de esta propiedad histórica. Los factores eléctricos y materiales del escenario usan research:factorValue; R01 y R21 no usan esta propiedad de caracterización LCIA.',
    ENVIRONMENT + 'factorUnit': 'Unidad textual de un CharacterizationFactor, declarado como dominio de esta propiedad histórica. Los factores eléctricos y materiales del escenario usan research:factorUnit, con unidades del alcance declarado.',
    ENVIRONMENT + 'hasEnvironmentalImpact': 'Propiedad histórica para un valor ambiental numérico agregado. La consulta Q01 actual calcula la suma de contribuciones eléctricas por operación; no materializa esta propiedad como resultado ni representa un ACV completo.',
}
ALIGNMENT = 'http://w3id.org/protys/ontology/alignment#'
CORRECTIONS['alignment-rules.owl'] = {
    'http://w3id.org/protys/ontology/iso15531#temporarilyUnavailable': 'Clasificación acumulativa de indisponibilidad temporal inferida por R15 únicamente desde mantenimiento declarado ACTIVE en la instantánea. Un registro histórico o programado no basta; una instantánea actualizada debe reevaluarse.',

    ALIGNMENT + 'impactThreshold': 'Parámetro histórico genérico conservado por compatibilidad; R04 del catálogo canónico utiliza research:ThresholdRecord con valor, unidad y categoría acoplados en el mismo registro. No se usa GlobalConfig para combinar umbrales de distintas magnitudes.',
    CORE + 'WaterFlow': 'Registro de flujo de agua de la tecnosfera del caso, tipado como iso14040:ProductFlow. R02 vincula este registro con la representación LCI del proceso cuando existe un dato de consumo de agua; no lo tipa como ElementaryFlow ni cuantifica una emisión a la naturaleza.',
    CORE + 'RequiresRecomputeIndicators': 'Clasificación añadida por R25 a un factor de emisión del escenario que se marca para revisión de indicadores. Los factores research se mantienen separados de CharacterizationFactor; esta clasificación no ejecuta el recálculo numérico.',
    ALIGNMENT + 'R16_ValidationPattern': 'Control de completitud de la instantánea cerrada: detecta una ProcessOperation sin registro hasInput mediante SPARQL ASK y FILTER NOT EXISTS. No es un axioma OWL de cardinalidad ni prueba que exista consumo físico de un material. Fuente canónica: research/model/validations/R16.sparql.',
    ALIGNMENT + 'R26_ValidationPattern': 'Control SPARQL ASK de ausencia de un registro de limpieza para el mismo equipo y el intervalo entre los lotes consecutivos declarados que requieren limpieza. Informa falta de evidencia registrada, sin inferir contaminación física ni ejecutar una limpieza. C26 evalúa el contexto por separado; R24 conserva su identidad de control de cobertura. Fuente canónica: research/model/validations/R26.sparql.',
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def logical_signature(path):
    """Canonical XML after excluding only the annotations being repaired."""
    root = E.parse(str(path), E.XMLParser(remove_blank_text=True)).getroot()
    for node in root.xpath('//rdfs:comment | //rdfs:source | //comment()', namespaces=NS):
        node.getparent().remove(node)
    return hashlib.sha256(E.tostring(root, method='c14n', with_comments=False)).hexdigest()


def declaration_counts(path):
    root = E.parse(str(path)).getroot()
    return {kind: len(set(root.xpath('//owl:' + kind + '/@rdf:about', namespaces=NS)))
            for kind in ('Class', 'ObjectProperty', 'DatatypeProperty')}


def update_annotations(repo_dir):
    repo_dir = Path(repo_dir)
    files = []
    for filename, corrections in CORRECTIONS.items():
        path = repo_dir / 'ontologies' / filename
        before_hash = digest(path)
        before_logic = logical_signature(path)
        before_counts = declaration_counts(path)
        tree = E.parse(str(path))
        root = tree.getroot()
        changes = []
        for iri, replacement in corrections.items():
            nodes = root.xpath('./*[@rdf:about=$iri]', namespaces=NS, iri=iri)
            if len(nodes) != 1:
                raise ValueError('Expected exactly one declared entity: ' + iri)
            node = nodes[0]
            comments = node.findall('{' + NS['rdfs'] + '}comment')
            if len(comments) != 1:
                raise ValueError('Expected exactly one existing comment: ' + iri)
            comment = comments[0]
            old = comment.text
            comment.text = replacement
            removed_sources = []
            # No rdfs:source occurs in the inspected source version. If an older
            # version attributes these local categories to ISO/Bruno, remove it.
            local_categories = (PRODUCT + '#Thing', PRODUCT + '#Substance',
                                PAINT + 'Chemical_Compound')
            if iri in local_categories:
                for source in node.findall('{' + NS['rdfs'] + '}source'):
                    removed_sources.append(E.tostring(source, encoding='unicode'))
                    node.remove(source)
            if old != replacement or removed_sources:
                changes.append({'entity': iri, 'before_comment': old,
                                'after_comment': replacement,
                                'unsupported_sources_removed': removed_sources})
        if changes:
            tree.write(str(path), encoding='UTF-8', xml_declaration=True,
                       pretty_print=True)
        after_logic = logical_signature(path)
        after_counts = declaration_counts(path)
        if before_logic != after_logic or before_counts != after_counts:
            raise AssertionError('Logical content or declaration counts changed: ' + filename)
        files.append({'path': 'ontologies/' + filename, 'before_sha256': before_hash,
                      'after_sha256': digest(path), 'logical_signature_before': before_logic,
                      'logical_signature_after': after_logic,
                      'logical_signature_unchanged': before_logic == after_logic,
                      'declarations_before': before_counts, 'declarations_after': after_counts,
                      'changes': changes})
    return {'scope': 'Annotation-only repair; no new axioms or scientific decisions',
            'reference': {'author': 'Giulia Bruno', 'year': 2015,
                          'title': 'Semantic organization of product lifecycle information through a modular ontology',
                          'primary_url': BRUNO, 'locator': 'Section III.A, printed page17, PDFpage2',
                          'supports': 'Product/component distinction and composition/decomposition',
                          'does_not_establish': 'Thing/Substance or universal ISO definitions',
                          'applies_to': 'Product/component annotations only; not the process taxonomy'},
            'process_annotation_scope': 'Process taxonomy and Procedure association are local conceptualization, with normative references as context; no new claim of direct ISO derivation.',
            'normative_annotation_scope': 'One bounded pass over the eight base modules: local conceptualization, existing domain/range and actual cardinality effects. No invented normative clauses, new references, changed guards or decisions on pending contracts.',
            'files': files, 'status': 'PASS'}


def main():
    repo_dir = Path(__file__).resolve().parents[2]
    report = update_annotations(repo_dir)
    print(json.dumps({'status': report['status'],
                      'changed_entities': sum(len(f['changes']) for f in report['files']),
                      'logical_signatures_unchanged': all(f['logical_signature_unchanged'] for f in report['files'])},
                     ensure_ascii=False))


if __name__ == '__main__':
    main()
