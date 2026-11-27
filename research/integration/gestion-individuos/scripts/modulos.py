"""Carga de un módulo Turtle mal formado por la plataforma (POST /api/ontology/modules/upload): debe responder 400
con el mensaje del analizador y no registrar el módulo.

uso: python3 modulos.py <salida.json>
"""
import json, sys, uuid
from comun import http, guardar


def subir(nombre, archivo, contenido, tipo='text/turtle'):
    limite = uuid.uuid4().hex
    cuerpo = (f'--{limite}\r\nContent-Disposition: form-data; name="name"\r\n\r\n{nombre}\r\n'
              f'--{limite}\r\nContent-Disposition: form-data; name="file"; filename="{archivo}"\r\n'
              f'Content-Type: {tipo}\r\n\r\n').encode() + contenido + f'\r\n--{limite}--\r\n'.encode()
    codigo, texto = http('POST', '/ontology/modules/upload', cuerpo, tipo=f'multipart/form-data; boundary={limite}')
    try:
        return codigo, json.loads(texto)
    except ValueError:
        return codigo, texto[:500]


codigo, r = subir('turtle-invalido', 'invalido.ttl', b'@prefix ex: <http://example.org/> .\nex:a ex:b .\n')
_, mods = http('GET', '/ontology/modules')
resultado = {'archivo': 'invalido.ttl (generado: le falta el objeto)', 'codigo': codigo, 'respuesta': r,
             'modulos_registrados_despues': [m['name'] for m in json.loads(mods)]}
print(resultado['codigo'], resultado['respuesta'], resultado['modulos_registrados_despues'])
guardar(sys.argv[1], resultado)
