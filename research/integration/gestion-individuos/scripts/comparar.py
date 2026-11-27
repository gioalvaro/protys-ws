"""Compara dos fotos de estado.py. uso: python3 comparar.py inicial.json final.json"""
import json, sys
a, b = (json.load(open(x, encoding='utf-8')) for x in sys.argv[1:3])
iguales = True
for clave in a:
    ok = a[clave] == b.get(clave)
    iguales &= ok
    print(('IGUAL   ' if ok else 'DISTINTO'), clave)
    if not ok and clave == 'catalogo':
        for q in a[clave]:
            x, y = a[clave][q], b[clave].get(q) or {}
            if x != y:
                antes = {json.dumps(f, sort_keys=True, ensure_ascii=False) for f in x.get('respuesta', [])}
                despues = {json.dumps(f, sort_keys=True, ensure_ascii=False) for f in y.get('respuesta', [])}
                print(f"    {q}: {x['filas']} -> {y.get('filas')} filas; nuevas {len(despues - antes)}, quitadas {len(antes - despues)}")
                for f in sorted(despues - antes)[:5]:
                    print('       +', f[:300])
                for f in sorted(antes - despues)[:5]:
                    print('       -', f[:300])
    elif not ok:
        print('   ', a[clave], '->', b.get(clave))
print('RESULTADO:', 'MISMO ESTADO' if iguales else 'ESTADO DISTINTO')
sys.exit(0 if iguales else 1)
