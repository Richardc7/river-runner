#!/usr/bin/env python3
"""Run engine kotlin.test assertions with a locally installed Kotlin compiler.

This is a secondary, dependency-free check, NOT a replacement for Gradle/JUnit or
Android builds. Source files are never modified. JUnit @Test annotations are
removed from temporary copies, and each annotated method is called directly.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
COMPILER = shutil.which('kotlinc')
if not COMPILER:
    raise SystemExit('kotlinc is required; otherwise use ./gradlew :engine:test')
KHOME = Path(COMPILER).resolve().parents[1]
ASSERTIONS = KHOME / 'lib' / 'kotlin-test.jar'
if not ASSERTIONS.is_file():
    raise SystemExit('kotlin-test.jar not found in Kotlin installation')

with tempfile.TemporaryDirectory(prefix='river-runner-tests-') as temp:
    d = Path(temp)
    tests = []
    calls = []
    for path in sorted((ROOT / 'engine/src/test/kotlin').rglob('*.kt')):
        text = path.read_text()
        package = re.search(r'^package\s+(\S+)', text, re.M).group(1)
        current = None
        for match in re.finditer(r'^class\s+(\w+)|@Test\s+fun\s+(\w+)', text, re.M):
            if match.group(1):
                current = match.group(1)
            else:
                if current is None:
                    raise SystemExit(f'Unsupported test declaration: {path}')
                name = f'{package}.{current}.{match.group(2)}'
                calls.append((name, f'{package}.{current}().{match.group(2)}()'))
        text = re.sub(r'^import\s+kotlin.test.Test\s*\n', '', text, flags=re.M)
        text = re.sub(r'@Test\s+', '', text)
        out = d / path.name
        out.write_text(text)
        tests.append(str(out))
    if not calls:
        raise SystemExit('No @Test methods found')
    runner = d / 'OfflineTestRunner.kt'
    body = ['fun main() {', 'var failed = 0']
    for name, call in calls:
        body.append(f'try {{ {call}; println("PASS {name}") }} catch (t: Throwable) {{ failed++; System.err.println("FAIL {name}"); t.printStackTrace() }}')
    body += [f'println("RESULT tests={len(calls)} failures=$failed")', 'check(failed == 0) { "$failed tests failed" }', '}']
    runner.write_text('\n'.join(body))
    output = d / 'tests.jar'
    sources = [str(p) for p in sorted((ROOT / 'engine/src/main/kotlin').rglob('*.kt'))]
    compile_args = [COMPILER, '-jvm-target', '17', '-classpath', str(ASSERTIONS), *sources, *tests, str(runner), '-include-runtime', '-d', str(output)]
    subprocess.run(compile_args, cwd=ROOT, check=True, timeout=240)
    subprocess.run(['java', '-cp', str(output) + os.pathsep + str(ASSERTIONS), 'OfflineTestRunnerKt'], cwd=ROOT, check=True, timeout=300)
