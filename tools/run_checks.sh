#!/usr/bin/env bash
# Compila e roda todas as checagens automatizadas (lwjgl3/src/test, programas main()).
# Uso: bash tools/run_checks.sh            (da raiz do projeto)
# Sai com código 1 se alguma checagem não imprimir PASS.
set -u
cd "$(dirname "$0")/.."
./gradlew :core:compileJava :lwjgl3:compileTestJava --console=plain -q || exit 1
fail=0
for c in DialogueRunnerCheck NpcCheck PlayerMovementCheck ProjectileSweepCheck QuizCheck \
         ResumeInputCheck SectionRespawnCheck TutorialCheck WeakPointAlignmentCheck StairGateCheck StairRespawnCheck \
         OldGateCheck ${EXTRA_CHECKS:-}; do
  out=$(./gradlew :lwjgl3:runCheck -Pcheck=$c --console=plain -q 2>&1)
  if echo "$out" | grep -q "PASS"; then echo "PASS  $c"; else echo "FAIL  $c"; echo "$out" | tail -15; fail=1; fi
done
# checagens em Python (arte)
for s in audit_pi.py; do if python tools/$s; then echo "PASS  $s"; else echo "FAIL  $s"; fail=1; fi; done
# capturas: só precisam terminar sem exceção
for c in IntroCapture HowToPlayLitCapture BackgroundTour VisualSmokeLauncher; do
  if ./gradlew :lwjgl3:runCheck -Pcheck=$c --console=plain -q >/dev/null 2>&1; then echo "OK    $c"; else echo "FAIL  $c"; fail=1; fi
done
exit $fail
