#!/usr/bin/env python3
"""Bootstrap asserted canonical sources through the API, without inference.

A complete unchanged bootstrap can be revisited without changing rule activation.
State the platform itself registered through its API afterwards (modules incorporated
with the wizard or uploaded from the explorer, their SWRL rules, ERP connector graphs and
open wizard sessions) is preserved. Foreign data, a partial bootstrap or a changed
canonical module or rule are rejected; use a new Compose project instead of deleting or
replacing an existing dataset.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import time
import urllib.parse
import urllib.request
import uuid

import rdflib
from rdflib import BNode, Graph, Literal, Namespace, URIRef
from rdflib.compare import isomorphic
from rdflib.namespace import RDF, XSD

# Preserve source lexical forms; serialization is a transport change, not inference.
rdflib.NORMALIZE_LITERALS = False
ROOT = Path(os.environ.get("PROTYS_REPOSITORY", "/opt/protys")).resolve()
BASE = os.environ.get("FUSEKI_URL", "http://localhost:3030").rstrip("/")
BACKEND = os.environ.get("PROTYS_BACKEND_URL", "http://localhost:8080").rstrip("/")
DATASET = os.environ.get("FUSEKI_DATASET", "protys_canonical")
MODULE_NAME = "Canonical_Catalog_Integrated"
MARKER_GRAPH = "urn:protys:bootstrap:metadata"
MARKER_SUBJECT = URIRef("urn:protys:bootstrap:integrated")
MARKER_PREDICATE = URIRef("urn:protys:bootstrap:manifest")
RULE_GRAPH = "http://w3id.org/protys/ontology/active-rule-definitions"
INFERENCE_GRAPH = "http://w3id.org/protys/ontology/inference-results"
SWRL = Namespace("http://www.w3.org/2003/11/swrl#")
# Grafos que la plataforma crea por su API sin registrar un módulo: sesiones abiertas del
# asistente (borrables con cancelar) y materializaciones de conectores ERP.
PLATFORM_GRAPH_PREFIXES = ("http://w3id.org/protys/ontology/temp/wizard/", "http://w3id.org/protys/erp/")


def call(url, data=None, content=None, method=None, accept="application/json"):
    headers = {"Accept": accept}
    if content:
        headers["Content-Type"] = content
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def api(path):
    return json.loads(call(BACKEND + path))


def query(text):
    params = urllib.parse.urlencode({"query": text})
    return json.loads(call(BASE + "/" + DATASET + "/query?" + params,
                           accept="application/sparql-results+json"))


def graph(uri):
    params = urllib.parse.urlencode({"graph": uri})
    content = call(BASE + "/" + DATASET + "/data?" + params,
                   accept="text/turtle")
    return Graph().parse(data=content, format="turtle")


def transport_graph(model):
    # RDF1.1 plain strings and xsd:string have the same meaning. Compare that
    # transport distinction without changing source bytes or numeric lexical forms.
    result = Graph()
    for subject, predicate, obj in model:
        if isinstance(obj, Literal) and obj.datatype is None and obj.language is None:
            obj = Literal(str(obj), datatype=XSD.string, normalize=False)
        result.add((subject, predicate, obj))
    return result


def equal_graph(left, right):
    return isomorphic(transport_graph(left), transport_graph(right))


def upload(path, data, filename, name=None):
    boundary = "protys-" + uuid.uuid4().hex
    parts = []
    if name is not None:
        parts.append(("--" + boundary + '\r\nContent-Disposition: form-data; name="name"'
                      + "\r\n\r\n" + name + "\r\n").encode())
    parts.extend([
        ("--" + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="'
         + filename + '"\r\nContent-Type: application/rdf+xml\r\n\r\n').encode(),
        data,
        ("\r\n--" + boundary + "--\r\n").encode(),
    ])
    return json.loads(call(BACKEND + path, b"".join(parts),
                           "multipart/form-data; boundary=" + boundary, "POST"))


def prepare():
    if not re.fullmatch(r"[A-Za-z0-9_-]+", DATASET):
        raise ValueError("Invalid dataset identifier")
    if os.environ.get("PROTYS_INCLUDE_HISTORICAL") == "1":
        raise RuntimeError("Canonical bootstrap excludes historical ERP data; use a separate dataset")
    catalog_path = ROOT / "research/catalog.json"
    catalog = json.loads(catalog_path.read_text())
    config = next(item for item in catalog["configurations"] if item["id"] == "integrated")
    paths = list(dict.fromkeys(config["tbox_paths"] + config["abox_paths"] + config["rules_paths"]))
    combined = Graph()
    rules = Graph()
    sources = []
    for relative in paths:
        source = (ROOT / relative).resolve()
        if not source.is_relative_to(ROOT) or not source.is_file():
            raise FileNotFoundError("Required canonical file absent/outside repository: " + relative)
        fmt = "turtle" if source.suffix == ".ttl" else "xml"
        parsed = Graph().parse(source, format=fmt)
        combined += parsed
        if relative in config["rules_paths"]:
            rules += parsed
        sources.append({"path": relative, "sha256": hashlib.sha256(source.read_bytes()).hexdigest()})
    rule_ids = sorted(str(rule).rsplit("#", 1)[-1] for rule in rules.subjects(RDF.type, SWRL.Imp))
    if len(rule_ids) != 22 or len(set(rule_ids)) != 22:
        raise RuntimeError("Canonical bootstrap requires exactly22distinct SWRL rules")
    manifest = {
        "schema": "protys-canonical-bootstrap/1",
        "configuration": "integrated",
        "catalog_sha256": hashlib.sha256(catalog_path.read_bytes()).hexdigest(),
        "sources": sources,
        "expected_rule_ids": rule_ids,
        "asserted_only": True,
        "inference_performed": False,
    }
    return combined, rules, manifest


def existing_marker():
    result = query("SELECT ?manifest WHERE { GRAPH <" + MARKER_GRAPH + "> { <"
                   + str(MARKER_SUBJECT) + "> <" + str(MARKER_PREDICATE) + "> ?manifest } }")
    bindings = result["results"]["bindings"]
    if not bindings:
        return None
    if len(bindings) != 1:
        raise RuntimeError("Ambiguous bootstrap marker; no state was modified")
    return json.loads(bindings[0]["manifest"]["value"])


def rule_id(rule):
    return str(rule).rsplit("#", 1)[-1]


def rule_definitions(model):
    """Cada swrl:Imp con todo lo que cuelga de ella por nodos en blanco (cuerpo, cabeza, átomos)."""
    definitions = {}
    for rule in model.subjects(RDF.type, SWRL.Imp):
        if isinstance(rule, BNode):
            raise RuntimeError("Anonymous SWRL rule in stored definitions; no state was modified")
        definition, pending, seen = Graph(), [rule], set()
        while pending:
            subject = pending.pop()
            if subject in seen:
                continue
            seen.add(subject)
            for triple in model.triples((subject, None, None)):
                definition.add(triple)
                if isinstance(triple[2], BNode):
                    pending.append(triple[2])
        if rule_id(rule) in definitions:
            raise RuntimeError("Duplicate SWRL rule identifier " + rule_id(rule) + "; no state was modified")
        definitions[rule_id(rule)] = definition
    return definitions


def verify_rule_definitions(rules, stored, added_rule_ids):
    """Las 22 definiciones canónicas siguen iguales y cada regla agregada tiene la suya."""
    if not added_rule_ids:
        if not equal_graph(rules, stored):
            raise RuntimeError("Stored asserted sources or rule definitions changed; no state was modified")
        return
    canonical, current = rule_definitions(rules), rule_definitions(stored)
    for identifier, definition in canonical.items():
        if identifier not in current or not equal_graph(definition, current[identifier]):
            raise RuntimeError("Canonical rule " + identifier + " changed; no state was modified")
    missing = [identifier for identifier in added_rule_ids if identifier not in current]
    if missing:
        raise RuntimeError("Registered rules without definition " + ", ".join(missing) + "; no state was modified")
    undeclared = sorted(set(current) - set(canonical) - set(added_rule_ids))
    if undeclared:
        raise RuntimeError("Unregistered rule definitions " + ", ".join(undeclared) + "; no state was modified")
    # Lo que no es definición de regla (declaraciones de variables, cabecera) debe seguir estando.
    defined = set()
    for definition in canonical.values():
        defined.update(definition)
    remainder = [triple for triple in rules if triple not in defined]
    if any(isinstance(term, BNode) for triple in remainder for term in triple):
        raise RuntimeError("Canonical rule graph cannot be compared term by term; no state was modified")
    stored_terms = set(transport_graph(stored))
    shared = Graph()
    for triple in remainder:
        shared.add(triple)
    if any(triple not in stored_terms for triple in transport_graph(shared)):
        raise RuntimeError("Canonical rule declarations changed; no state was modified")


def verify_existing(marker, combined, rules, expected, modules, registered_rules):
    if marker.get("source_manifest") != expected:
        raise RuntimeError("Existing bootstrap uses different sources; use a new Compose project")
    canonical = [item for item in modules if item.get("id") == marker.get("module_id")]
    if len(canonical) != 1:
        raise RuntimeError("Foreign or incomplete module metadata; no state was modified")
    module_graph = canonical[0]["namedGraph"]
    if module_graph != marker.get("module_graph") or canonical[0].get("name") != MODULE_NAME:
        raise RuntimeError("Canonical module identity changed; no state was modified")
    # Módulos registrados después por la plataforma (asistente, explorador): cada uno con su grafo.
    added_graphs = [item.get("namedGraph") for item in modules if item is not canonical[0]]
    if (any(not name for name in added_graphs) or len(set(added_graphs)) != len(added_graphs)
            or module_graph in added_graphs):
        raise RuntimeError("Registered modules share or lack a named graph; no state was modified")
    registered = [item["ruleId"] for item in registered_rules]
    if len(set(registered)) != len(registered) or not set(expected["expected_rule_ids"]) <= set(registered):
        raise RuntimeError("Foreign or incomplete rule metadata; no state was modified")
    added_rules = sorted(set(registered) - set(expected["expected_rule_ids"]))
    names = [item["g"]["value"] for item in
             query("SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s ?p ?o } }")["results"]["bindings"]]
    allowed = {MARKER_GRAPH, module_graph, RULE_GRAPH, INFERENCE_GRAPH, *added_graphs}
    foreign = sorted(name for name in names
                     if name not in allowed and not name.startswith(PLATFORM_GRAPH_PREFIXES))
    if foreign:
        raise RuntimeError("Foreign named graph present (" + ", ".join(foreign) + "); no state was modified")
    if not equal_graph(combined, graph(module_graph)):
        raise RuntimeError("Stored asserted sources or rule definitions changed; no state was modified")
    verify_rule_definitions(rules, graph(RULE_GRAPH), added_rules)
    # Never reset activation, remove inferences, or replace any existing graph here.
    print(json.dumps({"status": "PRESERVED", "module_id": marker["module_id"],
                      "source_manifest": expected, "rule_activation_preserved": True,
                      "added_modules": sorted(added_graphs), "added_rules": added_rules,
                      "platform_graphs": sorted(name for name in names
                                                if name.startswith(PLATFORM_GRAPH_PREFIXES))}), flush=True)


def main():
    combined, rules, source_manifest = prepare()
    for attempt in range(60):
        try:
            if api("/actuator/health").get("status") == "UP":
                break
        except OSError:
            pass
        if attempt == 59:
            raise RuntimeError("Backend health did not become UP")
        time.sleep(1)
    modules = api("/api/ontology/modules")
    registered_rules = api("/api/alignment/rules")
    marker = existing_marker()
    if marker is not None:
        verify_existing(marker, combined, rules, source_manifest, modules, registered_rules)
        return
    counts = query("SELECT (COUNT(*) AS ?n) WHERE { { ?s ?p ?o } UNION { GRAPH ?g { ?s ?p ?o } } }")
    if modules or registered_rules or int(counts["results"]["bindings"][0]["n"]["value"]) != 0:
        raise RuntimeError("Foreign data or incomplete bootstrap found; use a new Compose project. Nothing overwritten.")
    raw = combined.serialize(format="xml", encoding="utf-8")
    module = upload("/api/ontology/modules/upload", raw, "canonical-integrated.owl", MODULE_NAME)
    loaded_rules = upload("/api/alignment/rules/upload", rules.serialize(format="xml", encoding="utf-8"),
                          "canonical-alignment.owl")
    if sorted(item["ruleId"] for item in loaded_rules) != source_manifest["expected_rule_ids"]:
        raise RuntimeError("Rule import incomplete; bootstrap is not marked complete")
    if not equal_graph(combined, graph(module["namedGraph"])) or not equal_graph(rules, graph(RULE_GRAPH)):
        raise RuntimeError("Stored asserted input differs; bootstrap is not marked complete")
    marker = {"source_manifest": source_manifest, "module_id": module["id"],
              "module_graph": module["namedGraph"], "raw_input_sha256": hashlib.sha256(raw).hexdigest(),
              "asserted_triple_count": len(combined), "imported_rule_count": len(loaded_rules)}
    metadata = Graph()
    metadata.add((MARKER_SUBJECT, MARKER_PREDICATE, Literal(json.dumps(marker, sort_keys=True), datatype=XSD.string)))
    params = urllib.parse.urlencode({"graph": MARKER_GRAPH})
    call(BASE + "/" + DATASET + "/data?" + params, metadata.serialize(format="turtle", encoding="utf-8"),
         "text/turtle", "PUT")
    print(json.dumps({"status": "BOOTSTRAPPED", **marker}), flush=True)


if __name__ == "__main__":
    main()
