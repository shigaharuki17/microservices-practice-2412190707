#!/usr/bin/env python3
"""Real HTTP evidence against deployed services; no third party Python packages."""
import json, os, sys, time
from pathlib import Path
from urllib.request import Request, ProxyHandler, build_opener
from urllib.error import HTTPError, URLError
import nacos_admin as nacos

phase=sys.argv[1] if len(sys.argv)>1 else 'normal'
urls={
 'user':os.getenv('USER_URL','http://localhost:8091').rstrip('/'),
 'technician':os.getenv('TECHNICIAN_URL','http://localhost:8092').rstrip('/'),
 'repair':os.getenv('REPAIR_URL','http://localhost:8093').rstrip('/')}
opener=build_opener(ProxyHandler({}))
evidence=Path(__file__).resolve().parents[1]/'docs/homework/week-07/evidence'
evidence.mkdir(parents=True,exist_ok=True)
events=[]

def call(svc,method,path,expected,body=None):
 url=urls[svc]+path
 print(method+' '+url,flush=True)
 data=None if body is None else json.dumps(body).encode('utf-8')
 req=Request(url,data=data,method=method,headers={'Content-Type':'application/json'})
 try:
  response=opener.open(req,timeout=10)
 except HTTPError as e:
  response=e
 with response:
  status=response.code
  raw=response.read().decode('utf-8')
  payload=json.loads(raw) if raw else None
 event={'service':svc,'method':method,'url':url,'status':status,'request':body,'response':payload}
 events.append(event)
 print('HTTP '+str(status))
 print(json.dumps(payload,ensure_ascii=False),flush=True)
 assert status==expected, 'Expected '+str(expected)+', got '+str(status)
 return payload

def health(svc):
 for attempt in range(60):
  try:
   with opener.open(urls[svc]+'/actuator/health',timeout=3) as r:
    if r.code==200 and json.load(r).get('status')=='UP':
     return call(svc,'GET','/actuator/health',200)
  except (URLError,HTTPError,TimeoutError,OSError):
   pass
  time.sleep(2)
 raise RuntimeError(svc+' never became healthy')

state_path=evidence/'smoke-state.json'
if phase=='normal':
 nacos.console_check()
 for svc in urls:health(svc)
 for service in nacos.SERVICES:nacos.wait_instance(service,True)
 registered=nacos.snapshot('normal')
 assert all(registered['services'][s] for s in nacos.SERVICES)
 phone='139'+str(time.time_ns())[-8:]
 user=call('user','POST','/api/residents',201,{'name':'Week07 Resident','phone':phone})
 technician=call('technician','POST','/api/technicians',201,
  {'name':'Week07 Plumber','phone':'138'+phone[3:],'skill':'PLUMBING','available':True})
 create={'residentId':user['id'],'location':'Building 7 Room 701',
         'description':'Week07 leaking pipe','category':'PLUMBING'}
 order=call('repair','POST','/api/repair-orders',201,create)
 route='/api/repair-orders/'+str(order['id'])
 assert order['status']=='SUBMITTED'
 assert call('repair','POST',route+'/review',200,{'approved':True,'note':'Approved'})['status']=='APPROVED'
 assert call('repair','POST',route+'/assign',200,{'technicianId':technician['id']})['status']=='ASSIGNED'
 assert call('repair','POST',route+'/accept',200,{'technicianId':technician['id']})['status']=='ACCEPTED'
 final=call('repair','GET',route,200)
 assert final['residentId']==user['id'] and final['technicianId']==technician['id']
 before=len(call('repair','GET','/api/repair-orders',200))
 bad=dict(create,residentId=9223372036854775806)
 call('repair','POST','/api/repair-orders',404,bad)
 after=len(call('repair','GET','/api/repair-orders',200))
 assert before==after, 'Missing dependency incorrectly created a local order'
 print('MISSING_RESOURCE=PASS; local row count unchanged: '+str(before))
 # Persist another approved order for later unavailable-technology or state checks.
 approved=call('repair','POST','/api/repair-orders',201,create)
 call('repair','POST','/api/repair-orders/'+str(approved['id'])+'/review',200,{'approved':True,'note':'Waiting'})
 mismatch=call('technician','POST','/api/technicians',201,
  {'name':'Week07 Electrician','phone':'137'+phone[3:],'skill':'ELECTRICAL','available':True})
 route2='/api/repair-orders/'+str(approved['id'])
 call('repair','POST',route2+'/assign',409,{'technicianId':mismatch['id']})
 unchanged=call('repair','GET',route2,200)
 assert unchanged['status']=='APPROVED' and unchanged['technicianId'] is None
 state={'residentId':user['id'],'technicianId':technician['id'],'orderId':order['id'],'approvedOrderId':approved['id'],'create':create}
 state_path.write_text(json.dumps(state,indent=2)+'\n',encoding='utf-8')
 print('CROSS_SERVICE_WORKFLOW=PASS; SUBMITTED -> APPROVED -> ASSIGNED -> ACCEPTED')
elif phase=='outage':
 nacos.wait_instance('user-service',False)
 nacos.snapshot('offline')
 # An offline endpoint is unreachable; do not fabricate an Actuator DOWN response.
 try:
  opener.open(urls['user']+'/actuator/health',timeout=3)
 except (URLError,HTTPError,TimeoutError,OSError):print('USER_ACTUATOR=UNREACHABLE; stopped service, no DOWN body returned')
 else:raise AssertionError('Stopped user-service still responds')
 health('repair')
 state=json.loads(state_path.read_text())
 before=len(call('repair','GET','/api/repair-orders',200))
 call('repair','POST','/api/repair-orders',503,dict(state['create'],description='Week07 fresh valid request while user-service is offline'))
 after=len(call('repair','GET','/api/repair-orders',200))
 assert before==after, 'Unavailable dependency incorrectly created an order'
 print('DEPENDENCY_UNAVAILABLE=PASS; HTTP 503; local row count unchanged: '+str(before))
elif phase=='recovery':
 health('user')
 nacos.wait_instance('user-service',True)
 nacos.snapshot('recovery')
 state=json.loads(state_path.read_text())
 saved=call('repair','GET','/api/repair-orders/'+str(state['orderId']),200)
 assert saved['status']=='ACCEPTED'
 created=call('repair','POST','/api/repair-orders',201,dict(state['create'],description='Week07 fresh valid request after recovery'))
 assert created['status']=='SUBMITTED' and created['residentId']==state['residentId']
 print('RECOVERY=PASS; old order ACCEPTED; new discovered request HTTP 201; orderId='+str(created['id']))
else:
 raise SystemExit('Unknown phase: '+phase)
(evidence/(phase+'.json')).write_text(json.dumps(events,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('SMOKE_PHASE_'+phase.upper()+'=PASS')
