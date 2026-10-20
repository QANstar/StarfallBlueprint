"""Verify graceful world persistence across two real, isolated dedicated-server processes.

Run Gradle build prepareGameTestServerRun first. Uses the official prepared server runtime;
no sockets, client assets, existing saves, or Minecraft account login are needed.
"""
from pathlib import Path
import argparse
import os
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java', help='Java 21 executable')
    parser.add_argument('--timeout', type=int, default=180, help='Timeout per server process in seconds')
    args = parser.parse_args()
    prepared = ROOT / 'build/moddev'
    vm = prepared / 'gameTestServerRunVmArgs.txt'
    program = prepared / 'gameTestServerRunProgramArgs.txt'
    legacy = prepared / 'gameTestServerLegacyClasspath.txt'
    game = prepared / 'artifacts/neoforge-21.1.262.jar'
    classes = ROOT / 'build/classes/java/main'
    resources = ROOT / 'build/resources/main'
    for required in (vm, program, legacy, game, classes, resources):
        if not required.exists():
            raise SystemExit(f'Missing prepared runtime: {required}. Run build prepareGameTestServerRun first.')
    # New directory on each run: never edit a player's save or reuse a completed verification fixture.
    run = ROOT / 'run' / f'restart-verification-{time.time_ns()}'
    run.mkdir(parents=True, exist_ok=False)
    (run / 'eula.txt').write_text('eula=true\n', encoding='ascii')
    (run / 'server.properties').write_text(
        'online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n'
        'level-seed=20261020\nlevel-type=minecraft:flat\n'
        'generate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\n'
        'sync-chunk-writes=true\n', encoding='ascii')
    environment = os.environ.copy()
    environment['MOD_CLASSES'] = f'starfall_blueprint%%{classes}{os.pathsep}starfall_blueprint%%{resources}'
    classpath = os.pathsep.join([str(game), str(classes), str(resources), *legacy.read_text().splitlines()])
    print(f'Isolated verification world: {run}', flush=True)
    for phase in ('prepare', 'verify'):
        log = run / f'{phase}.log'
        command = [args.java, '-cp', classpath, f'@{vm}',
                   '-Dneoforge.gameTestServer=false', '-Dneoforge.enableGameTest=false',
                   f'-Dstarfall.verifyRestart={phase}', f'@{program}', '--nogui']
        with log.open('w', encoding='utf-8') as output:
            # Inherit no interactive console. The opt-in probe gracefully stops each server itself.
            try:
                completed = subprocess.run(command, cwd=run, env=environment, stdin=subprocess.DEVNULL,
                                           stdout=output, stderr=subprocess.STDOUT, timeout=args.timeout)
            except subprocess.TimeoutExpired:
                raise SystemExit(f'{phase} timed out; see {log}')
        content = log.read_text(encoding='utf-8', errors='replace')
        expected = 'STARFALL_RESTART_' + ('PREPARED' if phase == 'prepare' else 'VERIFIED')
        if completed.returncode != 0 or expected not in content or 'STARFALL_RESTART_FAILED' in content:
            raise SystemExit(f'{phase} failed (exit {completed.returncode}); see {log}')
        print(f'{expected}: real server exited {completed.returncode}; log: {log}', flush=True)
    print('PASS: two separate JVMs preserved and resumed machine and shared daily trade state.', flush=True)


if __name__ == '__main__':
    main()
