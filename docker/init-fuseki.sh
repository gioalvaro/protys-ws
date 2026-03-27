#!/bin/bash
# ============================================================================
# PROTYS(KB) - Fuseki Initialization Script
# Loads all ontology modules and instance data into Apache Jena Fuseki TDB2
# ============================================================================

set -e

FUSEKI_URL="${FUSEKI_URL:-http://localhost:3030}"
DATASET="protys"
ONTOLOGY_DIR="${ONTOLOGY_DIR:-/ontologies}"

# Colors for logging
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC}  $1"; }

# ============================================================================
# Step 1: Wait for Fuseki to be ready
# ============================================================================
wait_for_fuseki() {
    log_step "Waiting for Fuseki to be ready at ${FUSEKI_URL}..."
    local retries=30
    local wait_time=2
    for i in $(seq 1 $retries); do
        if curl -sf "${FUSEKI_URL}/\$/ping" > /dev/null 2>&1; then
            log_info "Fuseki is ready! (attempt ${i}/${retries})"
            return 0
        fi
        if curl -sf "${FUSEKI_URL}/${DATASET}/sparql?query=ASK{}" > /dev/null 2>&1; then
            log_info "Fuseki is ready via SPARQL endpoint! (attempt ${i}/${retries})"
            return 0
        fi
        log_warn "Fuseki not ready, retrying in ${wait_time}s... (${i}/${retries})"
        sleep $wait_time
    done
    log_error "Fuseki did not become ready after $((retries * wait_time))s"
    return 1
}

# ============================================================================
# Step 2: Ensure dataset exists
# ============================================================================
ensure_dataset() {
    log_step "Checking if dataset '${DATASET}' exists..."

    local response
    response=$(curl -sf -o /dev/null -w "%{http_code}" "${FUSEKI_URL}/${DATASET}/sparql?query=ASK{}" 2>/dev/null || echo "000")

    if [ "$response" = "200" ]; then
        log_info "Dataset '${DATASET}' already exists."
        return 0
    fi

    log_info "Creating dataset '${DATASET}'..."
    curl -sf -X POST "${FUSEKI_URL}/\$/datasets" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "dbName=${DATASET}&dbType=tdb2" \
        > /dev/null 2>&1 || log_warn "Dataset creation returned error (may already exist via config)"

    sleep 2
    response=$(curl -sf -o /dev/null -w "%{http_code}" "${FUSEKI_URL}/${DATASET}/sparql?query=ASK{}" 2>/dev/null || echo "000")
    if [ "$response" = "200" ]; then
        log_info "Dataset '${DATASET}' verified."
    else
        log_warn "Could not verify dataset. Continuing (may be configured via fuseki-config.ttl)."
    fi
}

# ============================================================================
# Step 3: Load a file into a named graph
# ============================================================================
load_graph() {
    local file="$1"
    local graph="$2"
    local description="$3"

    if [ ! -f "$file" ]; then
        log_warn "File not found: ${file} - Skipping ${description}"
        return 0
    fi

    local content_type="text/turtle"
    case "$file" in
        *.owl|*.rdf|*.xml) content_type="application/rdf+xml" ;;
        *.ttl)             content_type="text/turtle" ;;
        *.n3)              content_type="text/n3" ;;
        *.jsonld)          content_type="application/ld+json" ;;
        *.nt)              content_type="application/n-triples" ;;
    esac

    log_info "Loading ${description}..."
    log_info "  File:  ${file}"
    log_info "  Graph: ${graph}"

    local http_code
    http_code=$(curl -sf -o /tmp/fuseki_response.txt -w "%{http_code}" \
        -X PUT \
        "${FUSEKI_URL}/${DATASET}/data?graph=${graph}" \
        -H "Content-Type: ${content_type}" \
        --data-binary "@${file}" 2>/dev/null || echo "000")

    if [ "$http_code" = "200" ] || [ "$http_code" = "201" ] || [ "$http_code" = "204" ]; then
        log_info "  ✓ Loaded successfully (HTTP ${http_code})"
    else
        log_error "  ✗ Failed to load (HTTP ${http_code})"
        [ -f /tmp/fuseki_response.txt ] && cat /tmp/fuseki_response.txt 2>/dev/null
        return 1
    fi
}

# ============================================================================
# Step 4: Verify loaded data
# ============================================================================
verify_data() {
    log_step "Verifying loaded data..."
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║          PROTYS(KB) - Named Graph Summary                  ║"
    echo "╠══════════════════════════════════════════════════════════════╣"

    local graphs=(
        "urn:protys:kb|PROTYS(KB) Master"
        "urn:protys:core|Core Concepts"
        "urn:protys:product|Product Module"
        "urn:protys:process|Process Module"
        "urn:protys:resource|Resource Module"
        "urn:protys:enterprise|Enterprise Module"
        "urn:protys:iso14040|ISO 14040 (LCA)"
        "urn:protys:iso15531|ISO 15531 (MANDATE)"
        "urn:protys:alignment|Alignment Rules (SWRL)"
        "urn:protys:instances|Paint Case Study"
        "urn:protys:erp:adempiere|ADempiere Data"
        "urn:protys:erp:odoo|Odoo Data"
    )

    local total=0
    for entry in "${graphs[@]}"; do
        IFS='|' read -r graph label <<< "$entry"
        local query="SELECT (COUNT(*) AS ?c) WHERE { GRAPH <${graph}> { ?s ?p ?o } }"
        local count
        count=$(curl -sf -H "Accept: text/csv" \
            "${FUSEKI_URL}/${DATASET}/sparql" \
            --data-urlencode "query=${query}" 2>/dev/null | tail -1 | tr -d '"' || echo "?")

        printf "║  %-38s %10s triples  ║\n" "$label" "$count"
        [[ "$count" =~ ^[0-9]+$ ]] && total=$((total + count))
    done

    echo "╠══════════════════════════════════════════════════════════════╣"
    printf "║  %-38s %10s triples  ║\n" "TOTAL" "$total"
    echo "╚══════════════════════════════════════════════════════════════╝"

    # Interoperability verification
    echo ""
    log_info "Interoperability verification:"
    local sameas_query="SELECT (COUNT(*) AS ?c) WHERE { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o }"
    local sameas_count
    sameas_count=$(curl -sf -H "Accept: text/csv" \
        "${FUSEKI_URL}/${DATASET}/sparql" \
        --data-urlencode "query=${sameas_query}" 2>/dev/null | tail -1 | tr -d '"' || echo "?")
    log_info "  owl:sameAs cross-ERP links: ${sameas_count}"
}

# ============================================================================
# MAIN
# ============================================================================

echo ""
echo "============================================================"
echo "  PROTYS(KB) - Ontology Network Loader v1.0"
echo "  Target: ${FUSEKI_URL}/${DATASET}"
echo "  Source: ${ONTOLOGY_DIR}"
echo "============================================================"
echo ""

wait_for_fuseki
ensure_dataset

log_step "Loading PROTYS(KB) ontology network (12 named graphs)..."
echo ""

# Master ontology
load_graph "${ONTOLOGY_DIR}/protys-kb.ttl"                  "urn:protys:kb"              "PROTYS(KB) Master Ontology"

# Core layer
load_graph "${ONTOLOGY_DIR}/core-concepts.owl"              "urn:protys:core"            "Core Concepts (12 classes)"

# Domain modules
load_graph "${ONTOLOGY_DIR}/product-module.owl"             "urn:protys:product"         "Product Module (8 classes)"
load_graph "${ONTOLOGY_DIR}/process-module.owl"             "urn:protys:process"         "Process Module (10 classes)"
load_graph "${ONTOLOGY_DIR}/resource-module.owl"            "urn:protys:resource"        "Resource Module (9 classes)"
load_graph "${ONTOLOGY_DIR}/enterprise-module.owl"          "urn:protys:enterprise"      "Enterprise Module (7 classes)"

# ISO standard modules
load_graph "${ONTOLOGY_DIR}/iso14040-module.owl"            "urn:protys:iso14040"        "ISO 14040 LCA (15 classes)"
load_graph "${ONTOLOGY_DIR}/iso15531-module.owl"            "urn:protys:iso15531"        "ISO 15531 MANDATE (18 classes)"

# Alignment rules
load_graph "${ONTOLOGY_DIR}/alignment-rules.owl"            "urn:protys:alignment"       "SWRL Alignment Rules (26 rules)"

# Instance data
load_graph "${ONTOLOGY_DIR}/paint-case-study-instances.ttl" "urn:protys:instances"       "Paint Case Study (Chapter 4)"

# Materialized ERP data (R2RML output)
load_graph "${ONTOLOGY_DIR}/paint-instances-adempiere.ttl"  "urn:protys:erp:adempiere"   "ADempiere Materialized Data"
load_graph "${ONTOLOGY_DIR}/paint-instances-odoo.ttl"       "urn:protys:erp:odoo"        "Odoo Materialized Data"

echo ""
verify_data

echo ""
log_info "============================================================"
log_info "  PROTYS(KB) loading complete!"
log_info "  Fuseki UI:  ${FUSEKI_URL}/#/dataset/${DATASET}/query"
log_info "  SPARQL:     ${FUSEKI_URL}/${DATASET}/sparql"
log_info "============================================================"
echo ""
