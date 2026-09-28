#!/usr/bin/env python3
"""Local macOS IP aliases, k6 runner, and read-only session-IP verification."""
import argparse
import csv
import datetime as dt
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent
RESULTS = HERE / 'results'
OWNED = RESULTS / 'ip-aliases.json'
ALL_IPS = {f'127.0.0.{n}' for n in range(11, 111)}


def addresses(users):
    return [f'127.0.0.{n}' for n in range(11, 11 + users)]


def configured_ips():
    result = subprocess.run(['/sbin/ifconfig', 'lo0'], check=True, text=True, capture_output=True)
    return set(re.findall(r'^\s*inet\s+(\S+)', result.stdout, re.MULTILINE))


def owned_ips():
    items = json.loads(OWNED.read_text()) if OWNED.exists() else []
    if not isinstance(items, list) or any(not isinstance(x, str) or x not in ALL_IPS for x in items):
        raise RuntimeError('Unexpected content in results/ip-aliases.json')
    return set(items)


def save_owned(items):
    RESULTS.mkdir(parents=True, exist_ok=True)
    temporary = OWNED.with_suffix('.tmp')
    temporary.write_text(json.dumps(sorted(items), indent=2) + '\n')
    temporary.replace(OWNED)


def add_ips(users):
    if sys.platform != 'darwin':
        raise RuntimeError('The alias helper is for macOS; this package uses local loopback addresses')
    existing, owned = configured_ips(), owned_ips()
    missing = [ip for ip in addresses(users) if ip not in existing]
    if missing:
        subprocess.run(['sudo', '-v'], check=True)
    for ip in missing:
        subprocess.run(['sudo', '/sbin/ifconfig', 'lo0', 'inet', ip,
                        'netmask', '255.255.255.255', 'alias'], check=True)
        owned.add(ip)
        save_owned(owned)
    print(f'{users} addresses ready: {addresses(users)[0]} through {addresses(users)[-1]}')
    print('Only addresses newly added by this helper are recorded for removal.')


def remove_ips():
    owned, existing = owned_ips(), configured_ips()
    if owned & existing:
        subprocess.run(['sudo', '-v'], check=True)
    for ip in sorted(owned.copy()):
        if ip in existing:
            subprocess.run(['sudo', '/sbin/ifconfig', 'lo0', 'inet', ip, '-alias'], check=True)
        owned.remove(ip)
        save_owned(owned)
    print('Removed the aliases recorded by this helper; other addresses were preserved.')


def validate_run_id(run_id):
    if not re.fullmatch(r'[A-Za-z0-9_]{1,32}', run_id):
        raise RuntimeError('Invalid run ID')


def compose_psql(query):
    return subprocess.run([
        'docker', 'compose', '-f', str(PROJECT / 'compose.e2e.yml'),
        'exec', '-T', 'postgres', 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
        '-U', 'ums_e2e', '-d', 'usermanagement_e2e', '--csv', '-P', 'footer=off', '-c', query,
    ], check=True, text=True, capture_output=True)


def check_http(url, expected):
    # Do not route local verification through proxy settings.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(url, timeout=5) as response:
            actual = response.status
    except urllib.error.HTTPError as error:
        actual = error.code
    if actual != expected:
        raise RuntimeError(f'{url}: expected HTTP {expected}, got {actual}')


def verify(run_id, users, directory):
    validate_run_id(run_id)
    prefix = f'ipload_{run_id}_'
    # RUN_ID has been restricted to letters, digits, and underscores. No LIKE wildcards.
    result = compose_psql(f"""
        SELECT u.username, COALESCE(t.ip_address, '') AS ip_address,
               COALESCE(t.revoked::text, '') AS revoked,
               COALESCE(t.user_agent, '') AS user_agent
        FROM users u LEFT JOIN refresh_tokens t ON t.user_id = u.id
        WHERE left(u.username, {len(prefix)}) = '{prefix}'
        ORDER BY u.username;
    """)
    directory.mkdir(parents=True, exist_ok=True)
    (directory / 'ip-mapping.csv').write_text(result.stdout)
    rows = list(csv.DictReader(io.StringIO(result.stdout)))
    expected_users = {f'{prefix}{n}' for n in range(1, users + 1)}
    checks = {
        'one_session_per_account': len(rows) == users and {r['username'] for r in rows} == expected_users,
        'unique_ip_per_account': len(rows) == users and len({r['ip_address'] for r in rows}) == users,
        'expected_ip_range': {r['ip_address'] for r in rows} == set(addresses(users)),
        'sessions_logged_out': len(rows) == users and all(r['revoked'] == 'true' for r in rows),
        'matching_virtual_users': len(rows) == users and all(
            r['user_agent'] == f"ums-ip-load/{run_id}/vu/{r['username'].removeprefix(prefix)}" for r in rows),
    }
    (directory / 'ip-verification.json').write_text(json.dumps(checks, indent=2) + '\n')
    for key, ok in checks.items():
        print(f'{"PASS" if ok else "FAIL"}: {key}')
    print(f'Actual account/IP mapping: {directory / "ip-mapping.csv"}')
    return all(checks.values())


def preflight(users):
    for command in ('k6', 'docker'):
        if not shutil.which(command):
            raise RuntimeError(f'{command} is not installed or is not on PATH')
    if not (PROJECT / 'compose.e2e.yml').is_file():
        raise RuntimeError('Place ip-load.py in the project load-tests directory beside compose.e2e.yml')
    missing = set(addresses(users)) - configured_ips()
    if missing:
        raise RuntimeError(f'Local addresses are missing; first run: python3 load-tests/ip-load.py add-ips {users}')
    check_http('http://127.0.0.1:18080/api/users/me', 401)
    check_http('http://127.0.0.1:18025/api/v1/info', 200)
    identity = list(csv.DictReader(io.StringIO(compose_psql('SELECT current_database() AS db;').stdout)))
    if identity != [{'db': 'usermanagement_e2e'}]:
        raise RuntimeError('The disposable database identity did not match')


def run(users):
    RESULTS.mkdir(parents=True, exist_ok=True)
    lock = RESULTS / '.ip-load-running'
    try:
        lock.mkdir()
    except FileExistsError:
        raise RuntimeError('Another IP test may be running. If a prior run was forcibly killed, '
                           'confirm it has stopped before removing results/.ip-load-running')
    try:
        preflight(users)
        run_id = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + f'_{os.getpid()}'
        validate_run_id(run_id)
        directory = RESULTS / f'ipload_{run_id}'
        directory.mkdir()
        metadata = {'run_id': run_id, 'users': users, 'source_ips': addresses(users),
                    'pause_seconds_per_user': users / 10, 'measurement_seconds': 120}
        (directory / 'run.json').write_text(json.dumps(metadata, indent=2) + '\n')
        print(f'Run {run_id}: {users} accounts on {users} local IPs', flush=True)
        command = ['k6', 'run', '--no-usage-report', '--no-color',
                   f'--local-ips={addresses(users)[0]}-{addresses(users)[-1]}',
                   '-e', f'USERS={users}', '-e', f'RUN_ID={run_id}', str(HERE / 'per-user-ip.js')]
        environment = os.environ.copy()
        # These runs are direct local connections; no proxy should collapse source addresses.
        for key in list(environment):
            if key.lower() in ('http_proxy', 'https_proxy', 'all_proxy'):
                environment.pop(key)
        environment['NO_PROXY'] = '127.0.0.1,localhost'
        with (directory / 'k6.log').open('w') as logfile:
            with subprocess.Popen(command, cwd=PROJECT, env=environment, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, bufsize=1) as process:
                for line in process.stdout:
                    print(line, end='', flush=True)
                    logfile.write(line)
                code = process.wait()
        mapping_ok = verify(run_id, users, directory)
        metadata.update(k6_exit_code=code, ip_verification_passed=mapping_ok)
        (directory / 'run.json').write_text(json.dumps(metadata, indent=2) + '\n')
        if code != 0 or not mapping_ok:
            raise RuntimeError(f'Run did not pass both k6 and IP verification; see {directory}')
        print(f'PASS: workload thresholds and {users} distinct recorded source IPs. Results: {directory}')
    finally:
        lock.rmdir()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    for name in ('add-ips', 'run'):
        child = commands.add_parser(name)
        child.add_argument('users', type=int, choices=(10, 100))
    commands.add_parser('remove-ips')
    child = commands.add_parser('verify')
    child.add_argument('run_id')
    child.add_argument('users', type=int, choices=(10, 100))
    args = parser.parse_args()
    if args.command == 'add-ips': add_ips(args.users)
    elif args.command == 'remove-ips': remove_ips()
    elif args.command == 'run': run(args.users)
    else:
        if not verify(args.run_id, args.users, RESULTS / f'ipload_{args.run_id}'):
            raise RuntimeError('IP verification failed')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError, ValueError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr.strip(), file=sys.stderr)
        sys.exit(1)
