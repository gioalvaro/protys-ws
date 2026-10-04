#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
PYTHON=${PYTHON:-python3}
PREPARE_ONLY=0
if [ "$#" -eq 1 ] && [ "$1" = "--prepare-only" ]; then
 PREPARE_ONLY=1
elif [ "$#" -eq 1 ] && [ "$1" = "--help" ]; then
 echo 'Usage: reproduce.sh --prepare-only | [--functional] [--benchmark] [--output DIRECTORY]'
 "$PYTHON" research/evaluation/run_evaluation.py --help
 exit 0
else
 if [ "$#" -eq 0 ]; then set -- --functional --benchmark; fi
 # Reject occupied evidence before compilation or any output write.
 PROTYS_EVALUATION_OUT=$("$PYTHON" research/evaluation/run_evaluation.py "$@" --select-output)
 export PROTYS_EVALUATION_OUT
fi
: "${JAVA_HOME:?Set JAVA_HOME to a Java17 JDK}"
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -1 | grep '"17\.' >/dev/null || { echo "Java17 is required" >&2; exit 1; }
"$PYTHON" -c 'import hashlib,json,pathlib; p=pathlib.Path("research/runtime/vendor"); m=json.loads((p/"provenance.json").read_text()); assert hashlib.sha256((p/"owlapi-parent-4.5.27.pom").read_bytes()).hexdigest()==m["sha256"]'
./backend/mvnw -q -f research/runtime/swrl-worker/pom.xml install:install-file -Dfile="$ROOT/research/runtime/vendor/owlapi-parent-4.5.27.pom" -DgroupId=net.sourceforge.owlapi -DartifactId=owlapi-parent -Dversion=4.5.27 -Dpackaging=pom -DgeneratePom=false
for module in swrl-worker validator; do
 ./backend/mvnw -q -f "research/runtime/$module/pom.xml" package dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
 ./backend/mvnw -f "research/runtime/$module/pom.xml" dependency:tree -DoutputFile=target/dependencies.txt >/dev/null
done
java -Djava.awt.headless=true -cp "research/runtime/swrl-worker/target/classes:$(cat research/runtime/swrl-worker/target/classpath.txt)" org.protys.research.SwrlSmokeTest research/runtime/swrl-worker/target/smoke.json
if [ "$PREPARE_ONLY" -eq 1 ]; then
 echo 'Runtime prepared; no evaluation directory or results were written.'
 exit 0
fi
"$PYTHON" research/evaluation/run_evaluation.py "$@"
