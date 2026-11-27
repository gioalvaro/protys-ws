# Asistente de incorporación

`incorporar.sh` incorpora por la API del asistente el piloto ISO 10303 (`ontologies/iso10303-ap242-fragment.owl`,
datos `research/data/automotive.ttl` y reglas R27–R29 de `ontologies/iso10303-alignment-rules.owl`) y compara el
resultado con la configuración `h3_extended` de la evaluación. `reiniciar.sh` reinicia la plataforma y comprueba que
lo incorporado se conserva. El mismo recorrido por la interfaz está en `../capturas-interfaz/` (serie B).

```sh
sh research/integration/asistente/correr.sh
```

Necesita la fase funcional de la evaluación (`research/evaluation/current/assertion-groups`) para los controles H3.
Proyecto Compose `protys-v110` (puertos 38080, 38081, 33030 y 35432).

## Resultado (`salidas/api/RESUMEN.txt`: APROBADO en los 12 pasos)

| Paso | Valor |
|---|---|
| Fragmento AP242 | HTTP 201, 46 triples |
| Datos del piloto | HTTP 201, 203 triples |
| Validación | OWL consistente para la sesión y los módulos permanentes |
| Reglas de la sesión | R27, R28, R29 |
| Verificación | 22 reglas registradas y 3 de la sesión, 1419 triples inferidos |
| Reglas activas después | 25 (22 + R27–R29), como `h3_extended` |
| Razonamiento global | CONSISTENT; entrada 4318 → 4662; inferidos 1167 → 1419 |
| Q01–Q21 | idénticas antes y después |
| Controles H3 | 8 de 8 iguales a la evaluación |
| Reinicio (`salidas/03-reinicio.txt`) | módulos y 25 reglas activas conservados |

`salidas/api/` guarda cada respuesta de la API, el catálogo y los controles antes (`00-*`) y después (`06-*`).
