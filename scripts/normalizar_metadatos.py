#!/usr/bin/env python3
"""Normaliza autoría, licencia y contacto en el árbol de la versión evaluada.

Cada operación identifica una línea por ruta, número y SHA-256 de su contenido original, y la
reemplaza por el texto nuevo (o la elimina). Si una línea no coincide con su hash, el guion no
escribe nada y termina con código 1. Con --comprobar sólo informa el estado de cada operación.

Uso:
    python3 scripts/normalizar_metadatos.py [--comprobar] [--raiz RUTA]
"""
import argparse
import hashlib
import sys
from pathlib import Path

JAVA = 'backend/src/main/java/org/protys/ws/ProtyswsApplication.java'
SANGRIA = ' ' * 32

# (ruta, línea, sha256 de la línea original, línea nueva o None para eliminarla)
OPERACIONES = [
    (JAVA, 27, '03525e04d0c18856d3d64526f23edd967c21b56b82bebcde61c33ff64d79244a', ' * @author Alvaro Luis Fraga'),
    ('frontend/public/index.html', 11, 'b3c28b71d4a69c1189642961f2093251827b4cda202772d34843c71e872c8634',
     '    <meta name="author" content="Alvaro Luis Fraga" />'),
    # Contacto de la API sin correo ni URL, licencia MIT y sólo el servidor local.
    (JAVA, 69, 'a15ac6b03f937a4daa3064882fef71eb317712a3364f271736c830a369ea21eb', SANGRIA + '.name("Álvaro Luis Fraga"))'),
    (JAVA, 70, '0e75d34c9249eac871aa8a4e6c6d55271234e57c01349a37f422e631c4b285f9', None),
    (JAVA, 71, '900c15b1026cfb8e28b4fbdeeb282427d563c5617d374984d3f7e552089437ba', None),
    (JAVA, 73, 'a9c0a827a1295c422bcdb9c88da31b9b2e5cb1d722e65e04e987d62cfad62fbc', SANGRIA + '.name("MIT")'),
    (JAVA, 74, '47a36b984762fc7fdd5142df6a9e737dfb7c09d01369eb2ea5485a7da1fabfb3',
     SANGRIA + '.url("https://opensource.org/licenses/MIT")))'),
    (JAVA, 78, '2a4c45b6501bdd3fbb2cdc31dda51619b234c92f6eb38a9321e12122be583dfe', SANGRIA + '.description("Development Server")'),
    (JAVA, 79, '0f05455911ad1c8c8897e77fb09a9092b4ccf7c78429f605cec572c694b59a58', None),
    (JAVA, 80, '1cf19f2071e6fa0e37ceee1353af1e44db5c8dceb246bc3903e4b36d9fb1b419', None),
    (JAVA, 81, '83b6cc344aafd42eeb599358aeff7d2c4c6f468955f6dfe398a01c5b14a53b14', None),
    ('frontend/README.md', 177, 'e99d147027c6d430001c92f3f9cfeff418103af9bcbb6193509cf28ac7cc8939',
     'MIT; ver LICENSE en la raíz.'),
    ('research/model/approved-contracts.json', 3, '0d6f0fba2e90e30fc22349c7bcf0d2cfae943fa8a21037c69847f1c5d9e37b4d',
     '  "human_authorization": "C030-C033: contratos fijados antes de la ejecución",'),
    ('research/model/build_catalog.py', 151, 'c8615aa8a304c91db88dd9a4f59677037646685ae0a8079e5795a38c2d6acf2e',
     '# Contratos de umbrales acoplados, mantenimiento activo y valores numéricos estrictos de la instantánea.'),
]

# research/README.md, línea 55: se reemplaza sólo la primera oración.
README_RESEARCH = ('research/README.md', 55, 'c702d70aa159abab219b30005802491b4373a21d8c4d6d9759b30b26a2f55d97')
README_PRIMERA_NUEVA = 'Cuatro contratos del caso fijan el alcance de R04, R15, los controles numéricos y C26.'

# Archivos de classpath con rutas de una máquina concreta; los ejecutores usan los de target/.
ELIMINAR = [
    ('research/runtime/validator/classpath.txt', '9f85e64308350a0a00fae2ab565df9529fa2d985e0f9caea085fcc44ccd5951b'),
    ('research/runtime/swrl-worker/classpath.txt', '47bd9343eb90bd3591b0dd2a8878b44cab46b4535949799fb2f7b4e56ad3d2f9'),
]


def sha(texto):
    return hashlib.sha256(texto.encode('utf-8')).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--comprobar', action='store_true', help='sólo informa; no escribe')
    parser.add_argument('--raiz', default=Path(__file__).resolve().parent.parent, type=Path)
    args = parser.parse_args()
    raiz = args.raiz

    operaciones = list(OPERACIONES)
    ruta, n, h = README_RESEARCH
    actual = (raiz / ruta).read_text(encoding='utf-8').split('\n')[n - 1]
    if sha(actual) == h:
        operaciones.append((ruta, n, h, README_PRIMERA_NUEVA + ' ' + actual.split('. ', 1)[1]))
    elif actual.startswith(README_PRIMERA_NUEVA):
        operaciones.append((ruta, n, h, actual))
    else:
        print(f'{ruta}:{n} no coincide con el hash original ni con el texto nuevo', file=sys.stderr)
        return 1

    por_archivo = {}
    for ruta, n, h, nueva in operaciones:
        por_archivo.setdefault(ruta, []).append((n, h, nueva))

    # Una vez aplicadas, las eliminaciones corren las líneas: el estado se juzga por archivo.
    pendientes, aplicadas, errores = [], 0, []
    for ruta, ops in por_archivo.items():
        lineas = (raiz / ruta).read_text(encoding='utf-8').split('\n')
        coinciden = [(n, h) for n, h, _ in ops if n <= len(lineas) and sha(lineas[n - 1]) == h]
        if len(coinciden) == len(ops):
            pendientes.extend((ruta, n) for n, _ in coinciden)
        elif not coinciden and all(nueva in lineas for _, _, nueva in ops if nueva is not None):
            aplicadas += len(ops)
        else:
            errores.append(f'{ruta}: estado mixto ({len(coinciden)} de {len(ops)} líneas originales)')
    for ruta, h in ELIMINAR:
        p = raiz / ruta
        if p.exists():
            if hashlib.sha256(p.read_bytes()).hexdigest() != h:
                errores.append(f'{ruta}: contenido distinto del esperado')
            else:
                pendientes.append((ruta, 'eliminar'))
        else:
            aplicadas += 1

    if errores:
        print('\n'.join(errores), file=sys.stderr)
        return 1
    if args.comprobar:
        print(f'pendientes={len(pendientes)} aplicadas={aplicadas}')
        for p in pendientes:
            print('pendiente', *p)
        return 0 if not pendientes else 3

    a_escribir = {r for r, _ in pendientes}
    for ruta, ops in por_archivo.items():
        if ruta not in a_escribir:
            continue
        p = raiz / ruta
        lineas = p.read_text(encoding='utf-8').split('\n')
        for n, h, nueva in sorted(ops, key=lambda o: o[0], reverse=True):
            if nueva is None:
                del lineas[n - 1]
            else:
                lineas[n - 1] = nueva
        p.write_text('\n'.join(lineas), encoding='utf-8')
    for ruta, h in ELIMINAR:
        p = raiz / ruta
        if p.exists():
            p.unlink()
    print(f'aplicadas={len(pendientes)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
