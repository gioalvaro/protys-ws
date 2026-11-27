"""Respuesta esperada de QC01-QC18 sin razonador ni reglas.

Camino: Python sobre los triples afirmados de research/data/functional.ttl (los datos de entrada del caso), leídos
con rdflib y recorridos a mano, sin SPARQL, sin HermiT y sin SWRL. Lo único que se toma de la red es la taxonomía
declarada (rdfs:subClassOf y rdfs:subPropertyOf afirmados entre IRI con nombre en la TBox de la configuración
integrada, cierre transitivo): por ejemplo, los datos usan t:hasInventoryRepresentation y la red declara
t:hasInventoryRepresentation ⊑ a:hasInventoryRepresentation, y t:FineGrindingOperation ⊑ i:ProcessOperation.
No se usan dominios, rangos, equivalencias, restricciones ni las construcciones previas (R07, CNUM).

Cada respuesta es la pregunta de la consulta respondida desde los datos: lo afirmado tal cual más lo que se
desprende de los datos según la descripción de la regla. Como cada pregunta sale de la descripción de su regla, la
condición que se evalúa es la misma que describe la regla; lo independiente es el camino de ejecución. Las notas de
INDEPENDENCIA dicen dónde además hace falta el criterio de otro mecanismo.

uso: python3 esperadas.py [carpeta-salida]   (por omisión, esperadas/ junto a este archivo)
"""
import json, sys
from decimal import Decimal
from pathlib import Path
import rdflib
from rdflib import RDF, RDFS, Literal, URIRef, XSD

AQUI = Path(__file__).resolve().parent
RAIZ = AQUI.parents[2]
N = 'http://w3id.org/protys/ontology/'
i, e, c, a, t = (rdflib.Namespace(N + x + '#') for x in ('iso15531', 'iso14040', 'core', 'alignment', 'research'))

INDEPENDENCIA = {
    'QC08': 'Aritmética de la descripción de R11 (rendimiento / duración), calculada con Decimal.',
    'QC15': 'Incluye el criterio de R04 (indicador sobre su umbral), porque R22 parte de c:HighImpactIndicator.',
    'QC16': 'Aritmética de la descripción de R23 (tiempo de ciclo / valor del indicador), calculada con Decimal.',
    'QC17': 'Usa el criterio de la construcción previa R07 (proceso de fabricación sin indicador calculado para su '
            'inventario), porque R24 parte de c:MissingEnvironmentalData. Es independiente de R24 y del razonador, no de R07.',
}


class Datos:
    def __init__(self):
        self.g = rdflib.Graph().parse(RAIZ / 'research/data/functional.ttl')
        cat = json.loads((RAIZ / 'research/catalog.json').read_text())
        cfg = next(x for x in cat['configurations'] if x['id'] == 'integrated')
        tbox = rdflib.Graph()
        for f in cfg['tbox_paths'] + cfg['rules_paths']:
            tbox.parse(RAIZ / f)
        self.subclases = self._cierre(tbox, RDFS.subClassOf)
        self.subprop = self._cierre(tbox, RDFS.subPropertyOf)

    @staticmethod
    def _cierre(tbox, rel):
        """{superior: {él mismo y todos sus inferiores declarados}}."""
        directo = {}
        for s, o in tbox.subject_objects(rel):
            if isinstance(s, URIRef) and isinstance(o, URIRef):
                directo.setdefault(o, set()).add(s)
        cierre = {}
        for x in directo:
            vistos, pendientes = {x}, [x]
            while pendientes:
                for y in directo.get(pendientes.pop(), ()):
                    if y not in vistos:
                        vistos.add(y); pendientes.append(y)
            cierre[x] = vistos
        return cierre

    def es(self, x, clase):
        return any((x, RDF.type, k) in self.g for k in self.subclases.get(clase, {clase}))

    def de_tipo(self, clase):
        return {x for k in self.subclases.get(clase, {clase}) for x in self.g.subjects(RDF.type, k)}

    def vals(self, s, p):
        return {o for q in self.subprop.get(p, {p}) for o in self.g.objects(s, q)}

    def pares(self, p):
        return {(s, o) for q in self.subprop.get(p, {p}) for s, o in self.g.subject_objects(q)}

    def sujetos(self, p, o):
        return {s for q in self.subprop.get(p, {p}) for s in self.g.subjects(q, o)}


def num(lit):
    return Decimal(str(lit)) if isinstance(lit, Literal) and lit.datatype in (XSD.integer, XSD.decimal, XSD.double, XSD.float, XSD.int, XSD.long) else None


def dec(valor):
    return Literal(str(valor), datatype=XSD.decimal)


def altos(d):
    """Indicadores sobre su umbral explícito, misma unidad y categoría, frontera estricta (descripción de R04)."""
    res = set(d.de_tipo(c.HighImpactIndicator))
    for ind in d.de_tipo(e.ImpactIndicator):
        for reg in d.vals(ind, t.hasThreshold):
            if not d.es(reg, t.ThresholdRecord):
                continue
            for v in d.vals(ind, e.hasIndicatorValue):
                for u in d.vals(ind, t.unit) & d.vals(reg, t.thresholdUnit):
                    for cat in d.vals(ind, t.indicatorCategory) & d.vals(reg, t.thresholdCategory):
                        for umbral in d.vals(reg, t.thresholdValue):
                            if num(v) is not None and num(umbral) is not None and num(v) > num(umbral):
                                res.add(ind)
    return res


def calcular(d):
    r = {}
    inv = lambda p: d.vals(p, a.hasInventoryRepresentation)
    ops = d.de_tipo(i.ProcessOperation)
    lotes = d.de_tipo(i.Lot)
    # QC01 / R02: inventarios con flujo de agua.
    r['QC01'] = (['inventory'], {(x,) for x in d.sujetos(e.hasInventoryFlow, c.WaterFlow)} |
                 {(lci,) for op in ops if d.vals(op, i.consumesWaterL) for p in d.vals(op, i.partOfProcess) for lci in inv(p)})
    # QC02 / R03: flujos de producto del inventario del proceso del lote.
    r['QC02'] = (['lot', 'flow'], set(d.pares(a.hasRecordedInputFlowForLot)) |
                 {(lot, fl) for lot in lotes if d.sujetos(i.producesLot, lot) for p in d.vals(lot, i.hasProcess) for lci in inv(p)
                  if d.es(lci, e.UnitProcess) for fl in d.vals(lci, e.hasInventoryFlow) if d.es(fl, e.ProductFlow)})
    # QC03 / R04
    r['QC03'] = (['indicator'], {(x,) for x in altos(d)})
    # QC04 / R05: indicadores de los procesos del lote (más los ya afirmados del lote).
    r['QC04'] = (['lot', 'indicator'], {(lot, ind) for lot in lotes for ind in d.vals(lot, e.hasImpact)} |
                 {(lot, ind) for lot in lotes for p in d.vals(lot, i.hasProcess) for lci in inv(p) for ind in d.vals(lci, e.hasImpact)})
    # QC05 / R06: factor del material del flujo de producto.
    flujos = d.de_tipo(e.ProductFlow)
    r['QC05'] = (['flow', 'factor'], {(fl, f) for fl in flujos for f in d.vals(fl, t.usesEmissionFactor)} |
                 {(fl, f) for fl in flujos for m in d.vals(fl, e.refersToMaterial) for f in d.vals(m, t.hasEmissionFactor)})
    # QC06 / R08: cantidad en la unidad funcional declarada del lote.
    r['QC06'] = (['lot', 'quantity'], set(d.pares(e.hasFunctionalUnitQuantity)) |
                 {(lot, q) for lot in lotes if d.vals(lot, i.functionalUnit) for q in d.vals(lot, i.producesQuantity)})
    # QC07 / R10: estado de calidad del mismo lote.
    r['QC07'] = (['lot', 'status'], set(d.pares(i.propagatedQualityStatus)) |
                 {(lot, s) for lot in lotes for s in d.vals(lot, i.hasQualityStatus)})
    # QC08 / R11: rendimiento / duración, duración > 0.
    r['QC08'] = (['operation', 'efficiency'], set(d.pares(a.hasEfficiency)) |
                 {(op, dec(num(y) * Decimal('1.0000000000000000') / num(du))) for op in ops for y in d.vals(op, i['yield'])
                  for du in d.vals(op, i.duration) if num(y) is not None and num(du) is not None and num(du) > 0})
    # QC09 / R12: equipo requerido por una operación de molienda fina.
    r['QC09'] = (['resource'], {(x,) for x in d.sujetos(i.hasCapability, a.FineGrinding)} |
                 {(res,) for op in d.de_tipo(t.FineGrindingOperation) for res in d.vals(op, i.requiresResource) if d.es(res, i.EquipmentResource)})
    # QC10 / R13: eventos del mismo lote y proceso, en orden de tiempo.
    evs = d.de_tipo(i.ManufacturingData)
    r['QC10'] = (['earlier', 'later'], set(d.pares(i.precedesEvent)) |
                 {(e1, e2) for e1 in evs for e2 in evs if d.vals(e1, t.forLot) & d.vals(e2, t.forLot) and d.vals(e1, t.eventForProcess) & d.vals(e2, t.eventForProcess)
                  and any(num(x) is not None and num(y) is not None and num(x) < num(y) for x in d.vals(e1, t.eventTimeSeconds) for y in d.vals(e2, t.eventTimeSeconds))})
    # QC11 / R14: operador humano que ejecuta la orden de la operación.
    r['QC11'] = (['operation', 'operator'], set(d.pares(i.responsibleOperator)) |
                 {(op, x) for o in d.de_tipo(i.WorkOrder) for x in d.vals(o, i.executedBy) if d.es(x, i.HumanResource) for op in d.vals(o, i.executesActivity)})
    # QC12 / R15: recurso con mantenimiento ACTIVE en la instantánea.
    r['QC12'] = (['resource'], {(x,) for x in d.de_tipo(i.temporarilyUnavailable)} |
                 {(res,) for res in d.de_tipo(i.ManufacturingResource) for m in d.vals(res, i.hasMaintenance) if any(str(s) == 'ACTIVE' for s in d.vals(m, t.maintenanceStatus))})
    # QC13 / R18: lote con no conformidad.
    r['QC13'] = (['lot'], {(x,) for x in d.de_tipo(i.requiresReprocess)} | {(lot,) for lot in lotes if d.vals(lot, i.hasNonConformity)})
    # QC14 / R20: flujo del inventario del proceso que se refiere al material que consume la operación del mismo proceso.
    r['QC14'] = (['flow', 'operation'], set(d.pares(c.bridgesFlowToActivity)) |
                 {(fl, op) for op in ops for m in d.vals(op, i.consumesMaterial) for p in d.vals(op, i.partOfProcess) for lci in inv(p)
                  for fl in d.vals(lci, e.hasInventoryFlow) if d.es(fl, e.ProductFlow) and m in d.vals(fl, e.refersToMaterial) and p in d.vals(fl, t.belongsToProcess)})
    # QC15 / R22: cuello de botella requerido por una operación de un proceso con indicador alto.
    alto = altos(d)
    r['QC15'] = (['resource'], {(x,) for x in d.de_tipo(c.Hotspot)} |
                 {(res,) for ind in alto for lci in d.vals(ind, e.computedForProcess) for p in d.sujetos(a.hasInventoryRepresentation, lci)
                  for op in d.sujetos(i.partOfProcess, p) for res in d.vals(op, i.requiresResource) if d.es(res, c.BottleneckResource)})
    # QC16 / R23: tiempo de ciclo / valor del indicador, por observación operación-indicador.
    q16 = set()
    for lot in lotes:
        for o in d.sujetos(i.producesLot, lot):
            if not d.es(o, i.WorkOrder):
                continue
            for op in d.vals(lot, i.hasActivity):
                for p in d.vals(lot, i.hasProcess) & d.vals(op, i.partOfProcess):
                    for lci in inv(p):
                        for ind in d.sujetos(e.computedForProcess, lci):
                            for obs in d.sujetos(t.forOperation, op) & d.sujetos(t.forIndicator, ind):
                                for tc in d.vals(op, i.cycleTime):
                                    for v in d.vals(ind, e.indicatorValue):
                                        if num(v) is not None and num(tc) is not None and num(v) > 0:
                                            q16.add((obs, op, ind, dec(num(tc) * Decimal('1.0000000000000000') / num(v))))
    q16 |= {(obs, op, ind, x) for obs, x in d.pares(t.operationIndicatorRatio) for op in d.vals(obs, t.forOperation) for ind in d.vals(obs, t.forIndicator)}
    r['QC16'] = (['observation', 'operation', 'indicator', 'ratio'], q16)
    # QC17 / R24: proceso con faltante ambiental (criterio de R07).
    sin_indicador = {p for p in d.de_tipo(i.ManufacturingProcess)
                     if not any(d.es(ind, e.ImpactIndicator) and d.vals(ind, e.indicatorValue) for lci in d.vals(p, t.hasInventoryRepresentation)
                                for ind in d.sujetos(e.computedForProcess, lci))}
    r['QC17'] = (['process'], {(x,) for x in d.de_tipo(c.CoverageGap) | d.de_tipo(c.MissingEnvironmentalData) | sin_indicador})
    # QC18 / R25: factor de emisión con versión en la que fue modificado.
    r['QC18'] = (['factor'], {(x,) for x in d.de_tipo(c.RequiresRecomputeIndicators)} |
                 {(f,) for f in d.de_tipo(t.EmissionFactor) if d.vals(f, c.hasVersion) & d.vals(f, c.modifiedInVersion)})
    return r


def termino(x):
    if isinstance(x, URIRef):
        return {'type': 'uri', 'value': str(x)}
    b = {'type': 'literal', 'value': str(x)}
    if x.language:
        b['xml:lang'] = x.language
    elif x.datatype:
        b['datatype'] = str(x.datatype)
    return b


def main(salida=None):
    salida = Path(salida) if salida else AQUI / 'esperadas'
    salida.mkdir(parents=True, exist_ok=True)
    resumen = {}
    for ident, (vars_, filas) in calcular(Datos()).items():
        orden = sorted(filas, key=lambda f: [str(x) for x in f])
        res = {'head': {'vars': vars_}, 'results': {'bindings': [{v: termino(x) for v, x in zip(vars_, f)} for f in orden]}}
        (salida / f'{ident}.json').write_text(json.dumps(res, indent=2, ensure_ascii=False) + '\n')
        resumen[ident] = {'filas': len(orden), 'independencia': INDEPENDENCIA.get(ident, 'Lectura directa de los datos según la descripción de la regla.')}
        print(ident, len(orden))
    (salida / 'resumen.json').write_text(json.dumps({'metodo': __doc__.strip(), 'consultas': resumen}, indent=2, ensure_ascii=False) + '\n')


if __name__ == '__main__':
    main(*sys.argv[1:2])
