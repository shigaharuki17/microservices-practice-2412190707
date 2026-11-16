"""Generate local week09 credentials once; never print their values."""
from pathlib import Path
import secrets
p=Path(__file__).resolve().parents[1]/'.env'
if not p.exists():raise SystemExit('Copy .env.example to .env and configure the existing database/Nacos settings first')
lines=p.read_text().splitlines();values={}
for line in lines:
 if '=' in line and not line.lstrip().startswith('#'):
  k,v=line.split('=',1);values[k]=v
for key,size in [('JWT_SECRET',48),('INTERNAL_API_KEY',48),('DEMO_ADMIN_PASSWORD',18),('DEMO_RESIDENT_PASSWORD',18)]:
 if not values.get(key) or values[key].startswith('replace_'):
  values[key]=secrets.token_urlsafe(size)
  lines=[line for line in lines if not line.startswith(key+'=')]
  lines.append(key+'='+values[key])
if 'GATEWAY_PORT' not in values:lines.append('GATEWAY_PORT=18081')
p.write_text('\n'.join(lines)+'\n');p.chmod(0o600)
print('Local authentication settings initialized; secret values were not printed.')
