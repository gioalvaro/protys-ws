#!/usr/bin/env python3
"""Bootstrap asserted canonical sources through the API, without inference.

A complete unchanged bootstrap can be revisited without changing rule activation.
Foreign data or a partial bootstrap are rejected; use a new Compose project instead
of deleting or replacing an existing dataset.
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
from rdflib import Graph, Literal, Namespace, URIRef
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


def verify_existing(marker, combined, rules, expected, modules, registered_rules):
    if marker.get("source_manifest") != expected:
        raise RuntimeError("Existing bootstrap uses different sources; use a new Compose project")
    if len(modules) != 1 or modules[0].get("id") != marker.get("module_id"):
        raise RuntimeError("Foreign or incomplete module metadata; no state was modified")
    module_graph = modules[0]["namedGraph"]
    if module_graph != marker.get("module_graph") or modules[0].get("name") != MODULE_NAME:
        raise RuntimeError("Canonical module identity changed; no state was modified")
    if sorted(item["ruleId"] for item in registered_rules) != expected["expected_rule_ids"]:
        raise RuntimeError("Foreign or incomplete rule metadata; no state was modified")
    names = query("SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s ?p ?o } }")["results"]["bindings"]
    allowed = {MARKER_GRAPH, module_graph, RULE_GRAPH, INFERENCE_GRAPH}
    if any(item["g"]["value"] not in allowed for item in names):
        raise RuntimeError("Foreign named graph present; no state was modified")
    if not equal_graph(combined, graph(module_graph)) or not equal_graph(rules, graph(RULE_GRAPH)):
        raise RuntimeError("Stored asserted sources or rule definitions changed; no state was modified")
    # Never reset activation, remove inferences, or replace any existing graph here.
    print(json.dumps({"status": "PRESERVED", "module_id": marker["module_id"],
                      "source_manifest": expected, "rule_activation_preserved": True}), flush=True)


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
