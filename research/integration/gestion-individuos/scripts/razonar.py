"""Ejecuta el razonamiento por la plataforma (POST /api/alignment/reasoning/execute) y guarda el resultado.
uso: python3 razonar.py <salida.json>"""
import json, sys, time
from comun import http, guardar
t0 = time.time()
codigo, texto = http('POST', '/alignment/reasoning/execute', timeout=1800)
ms = round((time.time() - t0) * 1000)
try:
    d = json.loads(texto)
except ValueError:
    d = {'cuerpo': texto[:2000]}
claves = ('status', 'validationStatus', 'owlValidationStatus', 'activeRules', 'inputTripleCount', 'inferredTripleCount',
          'reasoningTimeMs', 'executionTimeMs', 'cacheHit', 'errorMessage')
resumen = {'codigo': codigo, 'ms_reloj': ms, **{k: d.get(k) for k in claves}}
guardar(sys.argv[1], {'resumen': resumen, 'respuesta': d})
print(json.dumps(resumen, ensure_ascii=False))
