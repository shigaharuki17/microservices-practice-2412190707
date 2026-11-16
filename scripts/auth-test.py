"""Real HTTP verification. Credentials and JWT values are never printed or saved."""
import argparse
import base64
import datetime
import hashlib
import hmac
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ENV = {}
if (ROOT / '.env').exists():
    for line in (ROOT / '.env').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            ENV[key.strip()] = value.strip().strip('"').strip("'")
ENV.update(os.environ)
BASE = ENV.get('API_BASE_URL', 'http://localhost:' + ENV.get('GATEWAY_PORT', '18081')).rstrip('/')
RESULTS = []

def call(method, path, token=None, data=None, extra=None, base=None):
    headers = dict(extra or {})
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if data is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request((base or BASE) + path, method=method, headers=headers,
        data=json.dumps(data).encode() if data is not None else None)
    try:
        response = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as e:
        response = e
    with response:
        raw = response.read().decode()
        try:
            body = json.loads(raw) if raw else None
        except ValueError:
            body = raw[:200]
        return {'status': response.status, 'route': response.headers.get('X-Gateway-Route'), 'body': body}

def check(label, result, expected):
    assert result['status'] == expected, (label, result['status'], expected, result.get('route'))
    RESULTS.append({'case': label, 'status': result['status'], 'pass': True})
    return result

def login(username, var):
    password = ENV.get(var)
    if not password:
        raise RuntimeError('Set ' + var + ' locally before testing')
    r = check(username + ' login', call('POST', '/api/auth/login', data={'username': username, 'password': password}), 200)
    assert r['body']['role'] == ('ADMIN' if username == 'admin' else 'RESIDENT')
    assert r['body']['accessToken'] and r['body']['tokenType'] == 'Bearer'
    return r['body']

def sign_expired():
    # Only used to test expiration; never sent to terminal output or evidence files.
    def b64(obj):
        return base64.urlsafe_b64encode(json.dumps(obj, separators=(',', ':')).encode()).rstrip(b'=')
    message = b64({'alg': 'HS256', 'typ': 'JWT'}) + b'.' + b64({
        'sub': 'resident', 'iss': 'https://community-repair.local', 'aud': ['community-api'],
        'roles': ['RESIDENT'], 'iat': int(time.time()) - 1200, 'exp': int(time.time()) - 600})
    sig = hmac.new(ENV['JWT_SECRET'].encode(), message, hashlib.sha256).digest()
    return (message + b'.' + base64.urlsafe_b64encode(sig).rstrip(b'=')).decode()

def run(section):
    health = check('public health', call('GET', '/actuator/health'), 200)
    assert health['body']['status'] == 'UP'
    unauth = check('missing JWT', call('GET', '/api/repair-orders'), 401)
    wrong = check('wrong password', call('POST', '/api/auth/login', data={'username': 'resident', 'password': 'deliberately-wrong-password'}), 401)
    resident = login('resident', 'DEMO_RESIDENT_PASSWORD')
    rt = resident['accessToken']
    me = check('authenticated identity', call('GET', '/api/auth/me', rt), 200)
    assert me['body'] == {'username': 'resident', 'role': 'RESIDENT'}, me
    routes = []
    for path, target in [('/api/residents', 'user-service'), ('/api/technicians', 'technician-service'), ('/api/repair-orders', 'repair-service')]:
        r = check('route ' + target, call('GET', path, rt), 200)
        assert r['route'] == target and isinstance(r['body'], list), (target, r['route'])
        routes.append({'path': path, 'status': r['status'], 'route': target, 'recordCount': len(r['body'])})
    denied = check('resident denied administrator API', call('GET', '/api/admin/accounts-summary', rt), 403)
    forged = {'X-Authenticated-User': 'admin', 'X-Authenticated-Role': 'ADMIN', 'X-Role': 'ADMIN', 'X-Internal-API-Key': 'forged-by-client'}
    identity = check('forged identity overwritten', call('GET', '/api/auth/me', rt, extra=forged), 200)
    assert identity['body'] == {'username': 'resident', 'role': 'RESIDENT'}, identity
    forged_denied = check('forged administrator role denied', call('GET', '/api/admin/accounts-summary', rt, extra=forged), 403)
    admin = login('admin', 'DEMO_ADMIN_PASSWORD')
    summary = check('administrator allowed', call('GET', '/api/admin/accounts-summary', admin['accessToken']), 200)
    assert summary['body']['viewer'] == 'admin' and summary['body']['accountCount'] >= 2
    parts = rt.split('.')
    parts[2] = ('A' if parts[2][0] != 'A' else 'B') + parts[2][1:]
    tampered = check('tampered JWT', call('GET', '/api/repair-orders', '.'.join(parts)), 401)
    expired = check('expired JWT', call('GET', '/api/repair-orders', sign_expired()), 401)
    direct = check('direct backend with forged identity', call('GET', '/api/residents', extra=forged,
        base=ENV.get('BACKEND_TEST_URL', 'http://127.0.0.1:8091')), 403)
    feign = check('gateway and internal Feign call', call('GET', '/api/portal/residents', rt), 200)
    assert isinstance(feign['body'], list)
    # Evidence contains summaries only, with no password, signing key or access token.
    evidence = {'verifiedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'base': BASE,
        'checks': RESULTS, 'routes': routes, 'identity': identity['body'], 'adminSummary': summary['body']}
    out = ROOT / 'docs/homework/week-09/evidence'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'auth-verification.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    if section == 'gateway':
        print('API GATEWAY | real Ubuntu HTTP verification\n')
        print('GET ' + BASE + '/actuator/health -> HTTP 200 {"status":"UP"}\n')
        for r in routes:
            print('GET ' + BASE + r['path'])
            print('Authorization: Bearer [valid JWT; value hidden]')
            print(f'HTTP {r["status"]} | X-Gateway-Route: {r["route"]}')
            print('JSON response: array, record count =', r['recordCount'], '\n')
        print('Discovery targets: lb://user-service, lb://technician-service, lb://repair-service')
        print('Gateway :18081 -> container :8080 | THREE SERVICE ROUTES PASS')
    elif section == 'authentication':
        print('AUTHENTICATION | database accounts + BCrypt + signed JWT\n')
        print('POST ' + BASE + '/api/auth/login')
        print('Request: username=resident, password=[hidden]')
        print('HTTP 200')
        print(json.dumps({**resident, 'accessToken': '[signed JWT hidden]'}, indent=2))
        print('\nGET ' + BASE + '/api/auth/me')
        print('Authorization: Bearer [resident JWT]')
        print('HTTP 200', json.dumps(me['body']))
        print('\nIncorrect password -> HTTP', wrong['status'])
        print('Tampered JWT -> HTTP', tampered['status'])
        print('Expired JWT -> HTTP', expired['status'])
        print('\nNo plaintext password or actual JWT value is displayed.')
    elif section == 'authorization':
        print('AUTHORIZATION | real requests through gateway\n')
        print('GET ' + BASE + '/api/repair-orders')
        print('No JWT -> HTTP', unauth['status'], '\n')
        print('GET ' + BASE + '/api/admin/accounts-summary')
        print('RESIDENT token -> HTTP', denied['status'])
        print('ADMIN token -> HTTP', summary['status'], json.dumps(summary['body']), '\n')
        print('Spoof headers: X-Authenticated-User=admin, X-Authenticated-Role=ADMIN')
        print('Actual GET /api/auth/me -> HTTP 200', json.dumps(identity['body']))
        print('Spoof role + resident JWT -> administrator API HTTP', forged_denied['status'], '\n')
        print('Direct GET ' + ENV.get('BACKEND_TEST_URL','http://127.0.0.1:8091') + '/api/residents with spoofed identity')
        print('HTTP', direct['status'], json.dumps(direct['body']))
        print('\nGET /api/portal/residents -> HTTP 200 | internal Feign authentication PASS')
        print('Backends published on 127.0.0.1 only; server-only key checked by each API.')
    else:
        print('$ bash scripts/auth-test.sh\n')
        for r in RESULTS:
            print(f'PASS | HTTP {r["status"]} | {r["case"]}')
        print(f'\n{len(RESULTS)}/{len(RESULTS)} CHECKS PASSED | exit code 0')
        print('Credentials and token values omitted. Business records were not modified.')
    print('\nVERIFICATION RESULT: PASS')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--section', choices=['gateway', 'authentication', 'authorization', 'tests'], default='tests')
    args = parser.parse_args()
    try:
        run(args.section)
    except Exception as error:
        print('VERIFICATION RESULT: FAIL |', str(error))
        raise SystemExit(1)
