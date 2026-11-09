#!/usr/bin/env python3
"""Real business HTTP verification; run through e2e-test.sh from repository root."""
import json,os,sys,time,uuid
from pathlib import Path
from urllib.request import Request,ProxyHandler,build_opener
from urllib.error import HTTPError,URLError
root=Path(__file__).resolve().parents[1]
out=root/'docs/homework/week-08/evidence';out.mkdir(parents=True,exist_ok=True)
base=os.getenv('REPAIR_URL','http://localhost:8093').rstrip('/')
op=build_opener(ProxyHandler({}));events=[]
def call(method,path,expected,body=None,record=True):
    req=Request(base+path,method=method,data=None if body is None else json.dumps(body).encode(),
        headers={'Content-Type':'application/json','X-Request-ID':'week08-'+uuid.uuid4().hex})
    try:response=op.open(req,timeout=12)
    except HTTPError as error:response=error
    with response:
        raw=response.read().decode();result=json.loads(raw) if raw else None
        status=response.status;instances=response.headers.get_all('X-Dependency-Instance') or []
    assert status==expected, (method,path,'expected',expected,'actual',status,result)
    if record:
        event={'method':method,'url':req.full_url,'status':status,'request':body,'response':result,'dependencyInstances':instances}
        events.append(event);print(json.dumps(event,ensure_ascii=False),flush=True)
    return result,instances
def count():return len(call('GET','/api/repair-orders',200,record=False)[0])
def health(url):
    for _ in range(60):
        try:
            with op.open(url+'/actuator/health',timeout=2) as response:
                if response.status==200 and json.load(response)['status']=='UP':
                    print('HEALTH=PASS '+url,flush=True);return
        except (URLError,TimeoutError,OSError):pass
        time.sleep(1)
    raise RuntimeError('Service failed to recover: '+url)
def dependency_ready():
    for _ in range(60):
        try:
            with op.open(base+'/api/portal/residents',timeout=5) as response:
                if response.status==200:return
        except (HTTPError,URLError,TimeoutError,OSError):pass
        time.sleep(1)
    raise RuntimeError('Feign dependency did not become available')
phase=sys.argv[1];statefile=out/'state.json'
if phase=='normal':
    for url in [base,os.getenv('USER_URL','http://localhost:8091'),os.getenv('USER_SECOND_URL','http://localhost:8094'),os.getenv('TECHNICIAN_URL','http://localhost:8092')]:health(url)
    dependency_ready()
    phone='139'+str(time.time_ns())[-8:]
    resident,_=call('POST','/api/portal/residents',201,{'name':'Week08 Demo resident','phone':phone})
    tech,_=call('POST','/api/portal/technicians',201,{'name':'Week08 Demo plumber','phone':'138'+phone[3:],'skill':'PLUMBING','available':True})
    seen=set();samples=[]
    for _ in range(40):
        data,ids=call('GET','/api/portal/residents/'+str(resident['id']),200)
        assert data['id']==resident['id']
        seen.update(ids);samples.append(ids)
        if seen=={'user-1','user-2'} and len(samples)>=6:break
        time.sleep(0.3)
    assert seen=={'user-1','user-2'}, 'Caller did not actually reach both instances: '+repr(seen)
    (out/'load-balancing.json').write_text(json.dumps({'instances':sorted(seen),'samples':samples,'source':'actual callee response headers forwarded by Feign'},indent=2))
    print('LOAD_BALANCING=PASS; actual caller reached '+','.join(sorted(seen)),flush=True)
    create={'residentId':resident['id'],'location':'Week08 Building 8 Room 801','description':'Week08 demo '+uuid.uuid4().hex,'category':'PLUMBING'}
    order,_=call('POST','/api/repair-orders',201,create);path='/api/repair-orders/'+str(order['id'])
    assert order['status']=='SUBMITTED'
    assert call('POST',path+'/review',200,{'approved':True,'note':'Week08 demo approval'})[0]['status']=='APPROVED'
    assert call('POST',path+'/assign',200,{'technicianId':tech['id']})[0]['status']=='ASSIGNED'
    assert call('POST',path+'/accept',200,{'technicianId':tech['id']})[0]['status']=='ACCEPTED'
    assert call('GET',path,200)[0]['residentId']==resident['id']
    before=count();invalid=dict(create,residentId=9223372036854775806)
    call('POST','/api/repair-orders',404,invalid);assert count()==before
    statefile.write_text(json.dumps({'create':create,'normalOrderId':order['id'],'beforeOutage':before},indent=2))
    print('WORKFLOW=PASS; SUBMITTED -> APPROVED -> ASSIGNED -> ACCEPTED',flush=True)
    print('MISSING_RESOURCE=PASS; local count unchanged',flush=True)
elif phase=='outage':
    state=json.loads(statefile.read_text());before=count()
    body=dict(state['create'],description='Week08 stopped dependency '+uuid.uuid4().hex)
    call('POST','/api/repair-orders',503,body)
    after=count();assert before==after
    print('UNAVAILABLE=PASS; local count '+str(before)+' -> '+str(after),flush=True)
elif phase=='recovery':
    for url in [os.getenv('USER_URL','http://localhost:8091'),os.getenv('USER_SECOND_URL','http://localhost:8094')]:health(url)
    # Both user-service instances must re-register before the new business operation.
    state=json.loads(statefile.read_text())
    dependency_ready()
    body=dict(state['create'],description='Week08 recovery '+uuid.uuid4().hex)
    order,_=call('POST','/api/repair-orders',201,body)
    assert order['status']=='SUBMITTED'
    assert call('GET','/api/repair-orders/'+str(order['id']),200)[0]['description']==body['description']
    print('RECOVERY=PASS; new order '+str(order['id']),flush=True)
else:raise ValueError('Unknown phase')
(out/(phase+'.json')).write_text(json.dumps(events,ensure_ascii=False,indent=2),encoding='utf-8')
print('E2E_PHASE_'+phase.upper()+'=PASS',flush=True)
