import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import { QueryClient,QueryClientProvider } from '@tanstack/react-query';
import ErpIntegration from './ErpIntegration';
import { useConnectors,useConnector,useRegisterConnector,useTestConnection,useMaterialize,useDeleteConnector } from '../../hooks/useERP';
jest.mock('../../hooks/useERP',()=>({useConnectors:jest.fn(),useConnector:jest.fn(),useRegisterConnector:jest.fn(),useTestConnection:jest.fn(),useMaterialize:jest.fn(),useDeleteConnector:jest.fn()}));
let root,container,client;
const click=async text=>{await act(async()=>{Array.from(container.querySelectorAll('button')).find(b=>b.textContent.includes(text)).click();});};
beforeEach(async()=>{
  global.IS_REACT_ACT_ENVIRONMENT=true;
  useConnectors.mockReturnValue({data:[],isLoading:false}); useConnector.mockReturnValue({});
  for(const hook of [useRegisterConnector,useTestConnection,useMaterialize,useDeleteConnector])hook.mockReturnValue({isPending:false,mutate:jest.fn()});
  client=new QueryClient(); container=document.createElement('div');root=createRoot(container);
  await act(async()=>{root.render(<QueryClientProvider client={client}><ErpIntegration/></QueryClientProvider>);});
});
afterEach(async()=>{await act(async()=>{root.unmount();});client.clear();});
test.each(['Cancel','New Connector'])('closing with %s clears the write-only password before reopening',async closeLabel=>{
  await click('New Connector');
  await act(async()=>{Simulate.change(container.querySelector('input[type=password]'),{target:{value:'synthetic-secret'}});});
  expect(container.querySelector('input[type=password]').value).toBe('synthetic-secret');
  await click(closeLabel); await click('New Connector');
  expect(container.querySelector('input[type=password]').value).toBe('');
});
