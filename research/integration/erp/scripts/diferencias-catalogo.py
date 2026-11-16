#!/usr/bin/env python3
"""Compara las respuestas de las 21 consultas del catálogo sin y con los grafos ERP.

Uso: diferencias-catalogo.py <dir_sin_erp> <dir_con_erp> <salida.md> [titulo] [descripcion]

También lo usa la Fase 3 (asistente) para comparar antes y después de una incorporación;
los dos argumentos opcionales cambian el título y el párrafo del cuadro.

Cada directorio tiene un Qxx.json por consulta, tal como lo devuelve /api/sparql/execute.
Una fila es el conjunto de pares variable=valor; se comparan como conjuntos (las filas
repetidas cuentan una vez en "nuevas" y "quitadas", pero el total de filas es el real).
Sale con código 1 si alguna consulta falló en cualquiera de las dos corridas.
"""
import json
import sys
from pathlib import Path

ERP = "http://w3id.org/protys/erp/"
MAX_FILAS_DETALLE = 40


def filas(path):
    d = json.loads(Path(path).read_text(encoding="utf-8"))
    sj = d.get("sparqlJson") or {}
    if "boolean" in sj:
        return None, [], d
    rows = (sj.get("results") or {}).get("bindings")
    if rows is None:
        return None, [], d
    vars_ = (sj.get("head") or {}).get("vars") or []
    out = [tuple((v, r[v]["value"]) for v in vars_ if v in r) for r in rows]
    return vars_, out, d


def corto(valor):
    for ns, p in (("http://w3id.org/protys/erp/", "erp:"), ("http://w3id.org/protys/ontology/", ""),
                  ("http://w3id.org/protys/", "protys:")):
        if valor.startswith(ns):
            return p + valor[len(ns):]
    return valor


def titulo(qfile):
    for linea in Path(qfile).read_text(encoding="utf-8").splitlines():
        if linea.startswith("# Q"):
            return linea[2:].strip()
    return Path(qfile).stem


def main():
    sin, con, salida = map(Path, sys.argv[1:4])
    titulo_md = sys.argv[4] if len(sys.argv) > 4 else "Consultas del catálogo sin y con los grafos ERP"
    descripcion = sys.argv[5] if len(sys.argv) > 5 else None
    queries = Path(__file__).resolve().parents[3] / "model" / "queries"
    resumen, detalle, errores = [], [], 0
    for q in sorted(queries.glob("Q*.sparql")):
        n = q.stem
        try:
            _, a, _ = filas(sin / f"{n}.json")
            vars_, b, _ = filas(con / f"{n}.json")
        except (OSError, ValueError, KeyError) as e:
            resumen.append(f"| {n} | {titulo(q)} | error | error | — | — | — |")
            errores += 1
            print(f"{n}: error {e}", file=sys.stderr)
            continue
        if vars_ is None:
            resumen.append(f"| {n} | {titulo(q)} | (ASK/otra forma) | | | | |")
            continue
        sa, sb = set(a), set(b)
        nuevas, quitadas = sorted(sb - sa), sorted(sa - sb)
        con_erp = [f for f in nuevas if any(v.startswith(ERP) for _, v in f)]
        resumen.append(f"| {n} | {titulo(q)} | {len(a)} | {len(b)} | {len(nuevas)} | {len(quitadas)} | {len(con_erp)} |")
        if nuevas or quitadas:
            detalle.append(f"\n### {n} — {titulo(q)}\n")
            detalle.append(f"Filas sin ERP: {len(a)}; con ERP: {len(b)}. Nuevas: {len(nuevas)} "
                           f"({len(con_erp)} mencionan un individuo ERP). Quitadas: {len(quitadas)}.\n")
            limite = None if n == "Q20" else MAX_FILAS_DETALLE
            for etiqueta, conjunto in (("Nuevas", nuevas), ("Quitadas", quitadas)):
                if not conjunto:
                    continue
                detalle.append(f"\n{etiqueta}:\n")
                detalle.append("| " + " | ".join(vars_) + " |")
                detalle.append("|" + "---|" * len(vars_))
                for f in conjunto[:limite]:
                    m = dict(f)
                    detalle.append("| " + " | ".join(corto(m.get(v, "")) for v in vars_) + " |")
                if limite and len(conjunto) > limite:
                    detalle.append(f"\n… y {len(conjunto) - limite} filas más (ver los JSON de la corrida).")
    descripcion = descripcion or ("Generado por `scripts/diferencias-catalogo.py` a partir de `03b-catalogo-sin-erp/` y "
             "`08-catalogo-con-erp/`. Misma plataforma y misma red; la única diferencia es que en la "
             "segunda corrida están materializados los dos grafos ERP y el razonamiento los incluyó.")
    texto = [f"# {titulo_md}", "", descripcion, "",
             "| Consulta | Pregunta | Filas antes (sin ERP) | Filas después (con ERP) | Nuevas | Quitadas | Nuevas con individuos ERP |",
             "|---|---|---|---|---|---|---|", *resumen, "", "## Filas que cambian", *detalle, ""]
    salida.write_text("\n".join(texto), encoding="utf-8")
    print("\n".join(resumen))
    sys.exit(1 if errores else 0)


if __name__ == "__main__":
    main()
