#!/usr/bin/env python3
"""Nacos 3.x admin operations. Never print credentials or access tokens."""
import json, os, sys, time
from pathlib import Path
from urllib.request import Request, build_opener, ProxyHandler
from urllib.parse import urlencode
from urllib.error import HTTPError, URLError
ROOT=Path(__file__).resolve().parents[1]
ENV=ROOT/'.env'
if ENV.exists():
    for line in ENV.read_text(encoding='utf-8-sig').splitlines():
        line=line.strip()
        if line and not line.startswith('#') and '=' in line:
            k,v=line.split('=',1); os.environ.setdefault(k.strip(),v.strip().strip('"').strip("'"))
HOST=os.getenv('NACOS_BIND_HOST','127.0.0.1')
API=os.getenv('NACOS_API_URL','http://'+HOST+':'+os.getenv('NACOS_SERVER_PORT','8849')).rstrip('/')
CONSOLE=os.getenv('NACOS_CONSOLE_URL','http://'+HOST+':'+os.getenv('NACOS_CONSOLE_PORT','18088')).rstrip('/')
GROUP=os.getenv('NACOS_GROUP','PERSONAL_GROUP')
NAMESPACE=os.getenv('NACOS_NAMESPACE','') or 'public'
SERVICES=('user-service','technician-service','repair-service')
OPENER=build_opener(ProxyHandler({}))
EVIDENCE=ROOT/'docs/homework/week-07/evidence'

def request(path,data=None,token=None):
    headers={} if not token else {'accessToken':token}
    req=Request(API+path,data=None if data is None else urlencode(data).encode(),headers=headers)
    with OPENER.open(req,timeout=10) as response: result=json.load(response)
    if result.get('code',0)!=0: raise RuntimeError('Nacos API returned a nonzero result code')
    return result

def credentials():
    username=os.getenv('NACOS_USERNAME','nacos');password=os.getenv('NACOS_PASSWORD','')
    if not password or password.startswith('replace_'): raise RuntimeError('Set NACOS_PASSWORD in local .env first')
    return {'username':username,'password':password}

def login():
    result=request('/nacos/v3/auth/user/login',credentials())
    token=result.get('accessToken') or result.get('data',{}).get('accessToken')
    if not token: raise RuntimeError('Nacos login did not return an access token')
    return token

def initialize():
    try: login()
    except (HTTPError,RuntimeError):
        creds=credentials()
        if creds['username']!='nacos': raise RuntimeError('First initialization requires NACOS_USERNAME=nacos')
        try: request('/nacos/v3/auth/user/admin',{'password':creds['password']})
        except HTTPError: raise RuntimeError('Cannot initialize admin; check existing local credentials without resetting data') from None
        login()
    print('NACOS_INITIALIZATION=PASS; administrator login verified; credentials omitted')

def instances(token,service):
    params={'namespaceId':NAMESPACE,'groupName':GROUP,'serviceName':service,'healthyOnly':'false'}
    try:
        data=request('/nacos/v3/admin/ns/instance/list?'+urlencode(params),token=token)['data']
    except HTTPError as error:
        # Nacos 3.2 returns 404 for a service never registered or already removed.
        if error.code==404:return []
        raise
    if isinstance(data,list): return data
    return data.get('hosts',data.get('list',[]))

def snapshot(label):
    token=login()
    result={'time':time.strftime('%Y-%m-%dT%H:%M:%S%z'),'api':API,'group':GROUP,'namespace':NAMESPACE,'services':{}}
    for service in SERVICES:
        result['services'][service]=[{'ip':i['ip'],'port':i['port'],'healthy':i['healthy'],'enabled':i['enabled'],'ephemeral':i.get('ephemeral')} for i in instances(token,service)]
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    (EVIDENCE/('registry-'+label+'.json')).write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print('REGISTRY_SNAPSHOT='+label+'; group='+GROUP)
    for service,items in result['services'].items():print(service+': '+json.dumps(items))
    return result

def wait_instance(service,online):
    token=login()
    for attempt in range(60):
        active=[i for i in instances(token,service) if i.get('healthy') and i.get('enabled')]
        if bool(active)==online:return
        time.sleep(2)
    raise RuntimeError('Registry did not reach expected state: '+service)

def console_check():
    with OPENER.open(CONSOLE+'/',timeout=10) as response:
        if response.status!=200: raise RuntimeError('Console HTTP status is not 200')
        if 'text/html' not in response.headers.get('Content-Type',''): raise RuntimeError('Console response is not HTML')
    print('GET '+CONSOLE+'/ HTTP 200; NACOS_CONSOLE=PASS')

if __name__=='__main__':
    try:
        if sys.argv[1:]==['init']:initialize()
        elif sys.argv[1:]==['console']:console_check()
        elif len(sys.argv)==3 and sys.argv[1]=='snapshot':snapshot(sys.argv[2])
        elif len(sys.argv)==4 and sys.argv[1]=='wait':wait_instance(sys.argv[2],sys.argv[3]=='online')
        else: raise RuntimeError('Usage: nacos_admin.py init|console|snapshot LABEL|wait SERVICE online|offline')
    except Exception as error:
        # Do not expose a response body, URL query with token, or auth request data.
        print('NACOS_CHECK=FAIL; '+type(error).__name__,file=sys.stderr);sys.exit(1)
