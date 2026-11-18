import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import ErpIntegration, { describeJdbcUrl } from './ErpIntegration';
import { useConnectors,useConnector,useRegisterConnector,useTestConnection,useIntrospectSchema,useMaterialize,useDeleteConnector } from '../../hooks/useERP';
jest.mock('../../hooks/useERP',()=>({useConnectors:jest.fn(),useConnector:jest.fn(),useRegisterConnector:jest.fn(),useTestConnection:jest.fn(),useIntrospectSchema:jest.fn(),useMaterialize:jest.fn(),useDeleteConnector:jest.fn()}));
let root,container,client;
const click=async text=>{await act(async()=>{Array.from(container.querySelectorAll('button')).find(b=>b.textContent.includes(text)).click();});};
const render=async()=>{await act(async()=>{root.render(<QueryClientProvider client={client}><ErpIntegration/></QueryClientProvider>);});};
// Forma de GET /api/erp/connectors (ERPConnectorResponse); la contraseña nunca viene en la respuesta.
const connector=(over={})=>({id:'c-1',name:'odoo-demo',type:'ODOO',jdbcUrl:'jdbc:postgresql://odoo-db:5432/odoo17?ApplicationName=protys',
  username:'protys_ro',databaseName:'odoo',namedGraph:'http://w3id.org/protys/erp/odoo',r2rmlMappingPath:'/opt/m/odoo.r2rml.ttl',
  status:'MATERIALIZED',lastMaterializationAt:'2026-11-18T18:43:10.123',tripleCount:201,hasPassword:true,...over});
beforeEach(async()=>{
  global.IS_REACT_ACT_ENVIRONMENT=true;
  useConnectors.mockReturnValue({data:[],isLoading:false}); useConnector.mockReturnValue({});
  for(const hook of [useRegisterConnector,useTestConnection,useIntrospectSchema,useMaterialize,useDeleteConnector])hook.mockReturnValue({isPending:false,mutate:jest.fn()});
  client=new QueryClient(); container=document.createElement('div');root=createRoot(container);
  await render();
});
afterEach(async()=>{await act(async()=>{root.unmount();});client.clear();});
test.each(['Cancel','New Connector'])('closing with %s clears the write-only password before reopening',async closeLabel=>{
  await click('New Connector');
  await act(async()=>{Simulate.change(container.querySelector('input[type=password]'),{target:{value:'synthetic-secret'}});});
  expect(container.querySelector('input[type=password]').value).toBe('synthetic-secret');
  await click(closeLabel); await click('New Connector');
  expect(container.querySelector('input[type=password]').value).toBe('');
});
test.each([
  ['CONNECTED','Connected','bg-green-100'],
  ['MATERIALIZED','Materialized','bg-green-100'],
  ['REGISTERED','Registered','bg-gray-100'],
  ['ERROR','Error','bg-red-100'],
])('a %s connector shows its own status style',async(status,label,cls)=>{
  useConnectors.mockReturnValue({data:[connector({status})],isLoading:false}); await render();
  const badge=container.querySelector('[data-testid=connector-status]');
  expect(badge.textContent).toBe(label);
  expect(badge.className).toContain(cls);
  expect(badge.querySelector('svg')).not.toBeNull();
});
test('the card shows server, database, user, graph and last materialization, never the password',async()=>{
  useConnectors.mockReturnValue({data:[connector({password:'synthetic-secret'})],isLoading:false}); await render();
  const card=container.querySelector('[data-testid=connector-card]').textContent;
  expect(card).toContain('odoo-db:5432'); expect(card).toContain('odoo17'); expect(card).toContain('protys_ro');
  expect(card).toContain('http://w3id.org/protys/erp/odoo'); expect(card).toContain('2026-11-18 18:43'); expect(card).toContain('201 triples');
  expect(card).not.toContain('ApplicationName'); expect(card).not.toContain('synthetic-secret');
  expect(container.textContent).not.toContain('synthetic-secret');
});
test('describeJdbcUrl drops user-info and parameters',()=>{
  expect(describeJdbcUrl('jdbc:postgresql://[REDACTED]@db.example:5433/erp?password=[REDACTED]')).toEqual({host:'db.example',port:'5433',database:'erp'});
  expect(describeJdbcUrl('jdbc:h2:mem:x')).toBeNull();
});
test('creating a connector sends the body POST /api/erp/connectors expects',async()=>{
  const mutate=jest.fn(); useRegisterConnector.mockReturnValue({isPending:false,mutate}); await render();
  await click('New Connector');
  const set=async(id,value)=>{await act(async()=>{Simulate.change(container.querySelector('#'+id),{target:{value}});});};
  await set('erp-name','odoo-demo'); await set('erp-type','ODOO'); await set('erp-jdbc-url','jdbc:postgresql://odoo-db:5432/odoo');
  await set('erp-username','protys_ro'); await set('erp-password','synthetic-secret');
  await set('erp-graph','http://w3id.org/protys/erp/odoo'); await set('erp-mapping','/opt/m/odoo.r2rml.ttl');
  await act(async()=>{Simulate.submit(container.querySelector('form'));});
  expect(mutate).toHaveBeenCalledTimes(1);
  expect(mutate.mock.calls[0][0]).toEqual({name:'odoo-demo',type:'ODOO',jdbcUrl:'jdbc:postgresql://odoo-db:5432/odoo',username:'protys_ro',
    password:'synthetic-secret',namedGraph:'http://w3id.org/protys/erp/odoo',r2rmlMappingPath:'/opt/m/odoo.r2rml.ttl',databaseName:'odoo'});
});
test('the type list only offers the types the API accepts',async()=>{
  await click('New Connector');
  expect(Array.from(container.querySelectorAll('#erp-type option')).map(o=>o.value)).toEqual(['ADEMPIERE','ODOO','CUSTOM']);
});
test('card buttons call the existing endpoints and show the result or the error',async()=>{
  const test_=jest.fn((id,{onSuccess})=>onSuccess({connected:true}));
  const inspect=jest.fn((id,{onSuccess})=>onSuccess({c_order:[],m_product:[]}));
  const materialize=jest.fn((id,{onError})=>onError({response:{data:{message:'ERP materialization failed'}}}));
  useTestConnection.mockReturnValue({isPending:false,mutate:test_}); useIntrospectSchema.mockReturnValue({isPending:false,mutate:inspect});
  useMaterialize.mockReturnValue({isPending:false,mutate:materialize});
  useConnectors.mockReturnValue({data:[connector()],isLoading:false}); await render();
  await act(async()=>{container.querySelector('[data-testid=connector-card]').click();});
  await click('Test Connection'); expect(test_.mock.calls[0][0]).toBe('c-1');
  expect(container.querySelector('[role=status]').textContent).toBe('Connection test passed');
  await click('Inspect Schema'); expect(inspect.mock.calls[0][0]).toBe('c-1');
  expect(container.querySelector('[role=status]').textContent).toBe('Schema inspected: 2 tables');
  await click('Materialize'); expect(materialize.mock.calls[0][0]).toBe('c-1');
  expect(container.querySelector('[role=status]').textContent).toBe('Materialization: ERP materialization failed');
});
