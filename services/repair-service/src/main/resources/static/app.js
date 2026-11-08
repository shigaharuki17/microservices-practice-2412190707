'use strict';
const $ = (selector) => document.querySelector(selector);
const statusLabels = {SUBMITTED:'待审核',APPROVED:'待派单',ASSIGNED:'待接单',ACCEPTED:'已接单',REJECTED:'已驳回'};
const categories = {PLUMBING:'水管维修',ELECTRICAL:'电路维修',LOCK:'门锁维修'};
const state = {view:'orders',orders:null,residents:null,technicians:null,page:1,pageSize:8,form:null,order:null,refreshing:false};
const escape = (value) => String(value ?? '').replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const badge = (status) => `<span class="badge ${escape(status)}">${escape(statusLabels[status] || status)}</span>`;
const orderNumber = (id) => `BX-${String(id).padStart(4,'0')}`;
const date = (value) => value ? String(value).replace('T',' ').slice(0,16) : '—';
const options = (values,selected) => Object.entries(values).map(([value,label]) => `<option value="${escape(value)}"${value === selected ? ' selected' : ''}>${escape(label)}</option>`).join('');

async function request(path, body) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(path, {method:body === undefined ? 'GET':'POST',headers:body === undefined ? {}:{'Content-Type':'application/json'},body:body === undefined ? undefined:JSON.stringify(body),signal:controller.signal,cache:'no-store'});
    const type = response.headers.get('content-type') || '';
    const payload = type.includes('json') ? await response.json() : null;
    if (!response.ok) throw new Error(`${payload?.message || '请求失败，请刷新后重试'}（HTTP ${response.status}）`);
    return payload;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('请求超时，请检查服务运行状态后重试。');
    if (error instanceof TypeError) throw new Error('无法连接服务，请检查网络或服务是否启动。');
    throw error;
  } finally { clearTimeout(timeout); }
}

function notify(message) {
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => { $('#toast').hidden = true; }, 5000);
}

async function refresh() {
  if (state.refreshing) return;
  state.refreshing = true;
  $('#refresh').disabled = true;
  const sources = [['orders','/api/repair-orders','工单'],['residents','/api/portal/residents','住户档案'],['technicians','/api/portal/technicians','维修人员']];
  const results = await Promise.allSettled(sources.map(([,path]) => request(path)));
  const errors = [];
  results.forEach((result,index) => {
    const [key,,label] = sources[index];
    if (result.status === 'fulfilled' && Array.isArray(result.value)) state[key] = result.value;
    else errors.push(`${label}加载失败：${result.reason?.message || '响应格式不正确'}`);
  });
  $('#page-alert').hidden = !errors.length;
  $('#page-alert').textContent = errors.join('\n') + (errors.length ? '\n已加载的数据仍可查看。恢复服务后请点击“刷新数据”。' : '');
  $('#connection').textContent = errors.length ? '部分数据暂不可用' : '数据已连接';
  $('#connection').className = `connection${errors.length ? ' error':''}`;
  $('#updated').textContent = `最近更新 ${new Date().toLocaleTimeString('zh-CN',{hour12:false})}`;
  render();
  state.refreshing = false;
  $('#refresh').disabled = false;
}

function setView(view) {
  if (!['orders','residents','technicians'].includes(view)) view = 'orders';
  state.view = view;
  const titles = {orders:'工单工作台',residents:'住户档案',technicians:'维修人员'};
  const subtitles = {orders:'及时响应每一个维修需求，让处理进度清晰可见。',residents:'管理报修联系信息，为每一次响应找到准确的人。',technicians:'了解人员技能与接单状态，让维修需求找到合适的人。'};
  const creates = {orders:'新建报修',residents:'新增住户',technicians:'新增维修人员'};
  $('#page-title').textContent = titles[view];
  $('#breadcrumb').textContent = titles[view];
  $('#page-subtitle').textContent = subtitles[view];
  $('#create').innerHTML = `<span aria-hidden="true">＋</span> ${creates[view]}`;
  $('#orders-section').hidden = view !== 'orders';
  $('#stats').hidden = view !== 'orders';
  $('#directory-section').hidden = view === 'orders';
  document.querySelectorAll('[data-view]').forEach(button => {button.classList.toggle('active',button.dataset.view === view);button.setAttribute('aria-current',button.dataset.view === view ? 'page':'false');});
  render();
}

function filteredOrders() {
  const term = $('#search').value.trim().toLowerCase();
  const status = $('#status-filter').value;
  const category = $('#category-filter').value;
  return [...(state.orders || [])].sort((a,b) => b.id-a.id).filter(order => (!status || order.status === status) && (!category || order.category === category) && (!term || [order.id,orderNumber(order.id),order.residentName,order.location,order.description,order.contactPhone].some(value => String(value ?? '').toLowerCase().includes(term))));
}

function render() {
  $('#nav-count').textContent = state.orders?.length ?? '—';
  if (state.view !== 'orders') return renderDirectory();
  const stats = [['','全部工单','已登记的报修需求','▤'],['SUBMITTED','待审核','等待物业确认','◷'],['APPROVED','待派单','等待安排维修人员','↗'],['ASSIGNED','待接单','等待维修人员确认','♧'],['ACCEPTED','已接单','已确认受理维修需求','✓']];
  $('#stats').innerHTML = stats.map(([value,label,description,symbol]) => `<button class="stat${$('#status-filter').value === value ? ' active':''}" data-status="${value}"><span class="stat-top">${label}<span class="stat-symbol" aria-hidden="true">${symbol}</span></span><div class="stat-value">${state.orders === null ? '—' : value ? state.orders.filter(order => order.status === value).length : state.orders.length}</div><div class="stat-description">${description}</div></button>`).join('');
  $('#total-count').textContent = state.orders?.length ?? '—';
  const orders = filteredOrders();
  const pages = Math.max(1,Math.ceil(orders.length/state.pageSize));
  state.page = Math.min(state.page,pages);
  const offset = (state.page-1)*state.pageSize;
  $('#order-rows').innerHTML = orders.slice(offset,offset+state.pageSize).map(order => `<tr><td><div class="order-title" title="${escape(order.description)}">${escape(order.description)}</div><span class="order-id">${orderNumber(order.id)}</span></td><td><span class="resident-name">${escape(order.residentName)}</span><span class="resident-phone">${escape(order.contactPhone)}</span></td><td class="location">${escape(order.location)}</td><td><span class="category">${escape(categories[order.category] || order.category)}</span></td><td>${badge(order.status)}</td><td class="time">${escape(date(order.createdAt)).replace(' ','<br>')}</td><td class="align-right"><button class="link-button" data-order="${order.id}">查看详情 ↗</button></td></tr>`).join('');
  $('#order-empty').hidden = orders.length > 0;
  $('#order-empty').innerHTML = state.orders === null ? '<strong>暂时无法读取工单</strong>请检查上方提示，确认服务启动后刷新。' : state.orders.length ? '<strong>没有找到匹配的工单</strong>试试其他关键词，或调整状态和类型筛选。' : '<strong>还没有报修工单</strong>点击右上角“新建报修”，登记第一条维修需求。';
  $('#result-count').textContent = orders.length ? `显示 ${offset+1}–${Math.min(offset+state.pageSize,orders.length)} 条，共 ${orders.length} 条` : state.orders === null ? '数据暂不可用' : '共 0 条工单';
  $('#page-label').textContent = `${state.page} / ${pages}`;
  $('#prev').disabled = state.page <= 1;
  $('#next').disabled = state.page >= pages;
}

function renderDirectory() {
  const isResident = state.view === 'residents';
  const data = state[state.view];
  $('#directory-title').textContent = isResident ? '住户联系档案':'维修人员档案';
  $('#directory-subtitle').textContent = isResident ? '报修前先登记住户姓名和联系方式':'派单时需满足技能匹配、可接单两项条件';
  $('#directory-count').textContent = data === null ? '数据暂不可用' : `共 ${data.length} ${isResident ? '位住户':'位维修人员'}`;
  $('#directory-head').innerHTML = `<tr><th>姓名</th><th>编号</th><th>联系电话</th>${isResident ? '' : '<th>维修技能</th><th>接单状态</th>'}</tr>`;
  $('#directory-rows').innerHTML = [...(data || [])].sort((a,b) => b.id-a.id).map(person => `<tr><td><span class="person-mark">${escape(person.name?.slice(0,1) || '人')}</span><span class="resident-name">${escape(person.name)}</span></td><td>#${person.id}</td><td>${escape(person.phone)}</td>${isResident ? '' : `<td>${escape(categories[person.skill] || person.skill)}</td><td><span class="badge ${person.available ? 'available':'unavailable'}">${person.available ? '可接单':'暂不可接单'}</span></td>`}</tr>`).join('');
  $('#directory-empty').hidden = Boolean(data?.length);
  $('#directory-empty').innerHTML = data === null ? '<strong>档案暂时无法加载</strong>请检查服务运行状态后刷新。' : `<strong>还没有${isResident ? '住户':'维修人员'}档案</strong>点击右上角按钮，添加第一条记录。`;
}

function showDetail(order) {
  state.order = order;
  $('#detail-title').textContent = orderNumber(order.id);
  const technician = state.technicians?.find(person => person.id === order.technicianId);
  const steps = ['SUBMITTED','APPROVED','ASSIGNED','ACCEPTED'];
  const current = steps.indexOf(order.status);
  const buttons = {SUBMITTED:'<button class="button danger" data-operation="reject">驳回报修</button><button class="button primary" data-operation="review">审核通过</button>',APPROVED:'<button class="button primary" data-operation="assign">安排维修人员</button>',ASSIGNED:'<button class="button primary" data-operation="accept">确认接单</button>'};
  $('#detail-body').innerHTML = `<div class="detail-status">${badge(order.status)}<span>${escape(categories[order.category] || order.category)}</span></div><div class="progress">${steps.map((step,index) => `<div class="progress-step${index <= current ? ' done':''}"><span>${index <= current ? '●':'○'}　0${index+1}</span><b>${['提交报修','物业审核','安排人员','确认接单'][index]}</b></div>`).join('')}</div><dl class="detail-grid"><div><dt>报修住户</dt><dd>${escape(order.residentName)}（#${order.residentId}）</dd></div><div><dt>联系电话</dt><dd>${escape(order.contactPhone)}</dd></div><div class="full"><dt>报修位置</dt><dd>${escape(order.location)}</dd></div><div class="full"><dt>问题描述</dt><dd>${escape(order.description)}</dd></div><div><dt>维修人员</dt><dd>${order.technicianId ? escape(technician?.name || '维修人员')+'（#'+order.technicianId+'）' : '尚未分配'}</dd></div><div><dt>提交时间</dt><dd>${escape(date(order.createdAt))}</dd></div><div><dt>审核说明</dt><dd>${escape(order.reviewNote || '尚未审核')}</dd></div><div><dt>最后更新</dt><dd>${escape(date(order.updatedAt))}</dd></div></dl><div class="detail-actions">${buttons[order.status] || ''}</div><p class="detail-help">${order.status === 'REJECTED' ? '该工单已驳回。如有新的维修需求，请重新提交报修。' : order.status === 'ACCEPTED' ? '维修人员已确认接单。当前版本尚未提供完工与验收操作。' : '按提交、审核、派单、接单的顺序处理，后端会再次检查状态及业务规则。'}</p>`;
  if (!$('#detail').open) $('#detail').showModal();
}

const field = (label,input,help='') => `<label class="field">${label}${input}${help ? `<small>${help}</small>`:''}</label>`;
function openForm(type,order=null) {
  state.form = {type,order};
  $('#form-error').hidden = true;
  $('#form-error').textContent = '';
  $('#editor-form').reset();
  $('#submit').disabled = false;
  let title, description, fields;
  if (type === 'orders') {
    title = '新建报修'; description = '登记故障信息，提交后由物业审核。住户需先在“住户档案”中登记。';
    const residents = state.residents || [];
    fields = field('报修住户',`<select name="residentId" required><option value="">请选择住户</option>${residents.map(person => `<option value="${person.id}">${escape(person.name)} · ${escape(person.phone)}</option>`).join('')}</select>`,residents.length ? '提交时会校验该住户是否存在。':'暂无可选住户，请先关闭窗口并新增住户，或恢复住户服务后刷新。') + field('报修位置','<input name="location" maxlength="200" placeholder="例如：5 幢 501 室 · 厨房" required>') + field('维修类型',`<select name="category" required>${options(categories)}</select>`) + field('问题描述','<textarea name="description" maxlength="1000" placeholder="请描述故障现象和需要处理的问题…" required></textarea>');
    $('#submit').disabled = !residents.length;
  } else if (type === 'residents' || type === 'technicians') {
    title = type === 'residents' ? '新增住户':'新增维修人员';
    description = type === 'residents' ? '用于报修联系与住户存在性校验，不会创建登录账号。':'填写真实的技能和接单状态，派单时会再次校验。';
    fields = field('姓名','<input name="name" maxlength="50" autocomplete="off" placeholder="请输入姓名" required>') + field('联系电话','<input name="phone" type="tel" minlength="6" maxlength="20" pattern="[0-9+ &#32;\-]{6,20}" placeholder="例如：13900000001" required>','联系电话不可重复登记。');
    if (type === 'technicians') fields += field('维修技能',`<select name="skill" required>${options(categories)}</select>`) + field('接单状态','<select name="available"><option value="true">可接单</option><option value="false">暂不可接单</option></select>');
  } else if (type === 'review' || type === 'reject') {
    title = type === 'review' ? '审核通过':'驳回报修'; description = `${orderNumber(order.id)} · ${order.description}`;
    fields = field('审核说明',`<textarea name="note" maxlength="300" placeholder="${type === 'review' ? '例如：情况属实，同意安排维修。':'请填写驳回原因。'}" required></textarea>`);
  } else if (type === 'assign') {
    title = '安排维修人员';description = `${orderNumber(order.id)} · ${categories[order.category]}。仅列出技能匹配且可接单的人员。`;
    const people = (state.technicians || []).filter(person => person.available && person.skill === order.category);
    fields = field('维修人员',`<select name="technicianId" required><option value="">请选择维修人员</option>${people.map(person => `<option value="${person.id}">${escape(person.name)} · ${escape(person.phone)}</option>`).join('')}</select>`,people.length ? '派单后需由被分配人员确认接单。':'暂无符合条件的人员。请先新增对应技能且可接单的维修人员，或刷新档案。');
    $('#submit').disabled = !people.length;
  } else if (type === 'accept') {
    title = '确认接单';description = `${orderNumber(order.id)} · ${order.description}`;
    const person = state.technicians?.find(person => person.id === order.technicianId);
    fields = field('被分配的维修人员',`<select name="technicianId" required><option value="${order.technicianId}">${escape(person?.name || '维修人员')}（#${order.technicianId}）</option></select>`,'课程演示中模拟该人员确认接单，尚未启用登录身份验证。');
  }
  $('#dialog-title').textContent = title;
  $('#dialog-description').textContent = description;
  $('#form-fields').innerHTML = fields;
  $('#submit').textContent = type === 'reject' ? '确认驳回' : type === 'assign' ? '确认派单' : type === 'accept' ? '确认接单' : '确认提交';
  $('#detail').close();
  $('#editor').showModal();
}

$('#editor-form').addEventListener('submit',async event => {
  event.preventDefault();
  const button = $('#submit');
  if (button.disabled) return;
  button.disabled = true;
  const previousText = button.textContent;
  button.textContent = '正在提交…';
  $('#form-error').hidden = true;
  const values = Object.fromEntries(new FormData(event.target));
  for (const key of Object.keys(values)) if (typeof values[key] === 'string') values[key] = values[key].trim();
  const {type,order} = state.form;
  let path,body;
  if (type === 'orders') {path='/api/repair-orders';body={...values,residentId:Number(values.residentId)};}
  else if (type === 'residents' || type === 'technicians') {path='/api/portal/'+type;body=type === 'technicians' ? {...values,available:values.available === 'true'} : values;}
  else if (type === 'review' || type === 'reject') {path=`/api/repair-orders/${order.id}/review`;body={approved:type === 'review',note:values.note};}
  else {path=`/api/repair-orders/${order.id}/${type}`;body={technicianId:Number(values.technicianId)};}
  try {
    const saved = await request(path,body);
    $('#editor').close();
    if (type === 'orders') {$('#search').value='';$('#status-filter').value='';$('#category-filter').value='';state.page=1;}
    await refresh();
    if (!['residents','technicians'].includes(type)) showDetail(saved);
    notify(type === 'orders' ? `${orderNumber(saved.id)} 报修已提交，等待审核` : type === 'residents' ? '住户档案已保存' : type === 'technicians' ? '维修人员档案已保存' : `${orderNumber(saved.id)} 已更新为${statusLabels[saved.status]}`);
  } catch (error) {
    $('#form-error').textContent = error.message;
    $('#form-error').hidden = false;
  } finally {button.disabled=false;button.textContent=previousText;}
});

document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click',() => {location.hash=button.dataset.view;}));
window.addEventListener('hashchange',() => setView(location.hash.slice(1)));
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click',() => $('#'+button.dataset.close).close()));
$('#refresh').addEventListener('click',refresh);
$('#create').addEventListener('click',() => openForm(state.view));
$('#stats').addEventListener('click',event => {const stat=event.target.closest('[data-status]');if(stat){$('#status-filter').value=stat.dataset.status;state.page=1;render();}});
for (const selector of ['#search','#status-filter','#category-filter']) $(selector).addEventListener(selector === '#search' ? 'input':'change',() => {state.page=1;render();});
$('#prev').addEventListener('click',() => {state.page--;render();});
$('#next').addEventListener('click',() => {state.page++;render();});
$('#order-rows').addEventListener('click',async event => {
  const button = event.target.closest('[data-order]');
  if (!button) return;
  button.disabled = true;
  try {showDetail(await request('/api/repair-orders/'+button.dataset.order));}
  catch (error) {$('#page-alert').textContent=error.message;$('#page-alert').hidden=false;}
  finally {button.disabled=false;}
});
$('#detail-body').addEventListener('click',event => {const button=event.target.closest('[data-operation]');if(button)openForm(button.dataset.operation,state.order);});
$('#today').textContent = new Date().toLocaleDateString('zh-CN',{year:'numeric',month:'long',day:'numeric',weekday:'short'});
setView(location.hash.slice(1));
refresh();
