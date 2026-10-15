import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { erpAPI } from '../services/api';
import { useMaterialize } from './useERP';
jest.mock('../services/api',()=>({erpAPI:{materialize:jest.fn()}}));
jest.mock('react-toastify',()=>({toast:{success:jest.fn(),error:jest.fn(),warning:jest.fn()}}));
let container,root,client,mutation;
function Harness(){mutation=useMaterialize();return <p>{mutation.progress.currentStep}</p>;}
beforeEach(async()=>{
  global.IS_REACT_ACT_ENVIRONMENT=true; jest.clearAllMocks();
  client=new QueryClient({defaultOptions:{mutations:{retry:false,gcTime:0}}});
  container=document.createElement('div'); root=createRoot(container);
  await act(async()=>{root.render(<QueryClientProvider client={client}><Harness/></QueryClientProvider>);});
});
afterEach(async()=>{await act(async()=>{root.unmount();});client.clear();});
test('EMPTY is an explicit preserved-graph warning, not successful publication',async()=>{
  erpAPI.materialize.mockResolvedValue({status:'EMPTY',tripleCount:0});
  await act(async()=>{await mutation.mutateAsync('erp-a');});
  expect(toast.success).not.toHaveBeenCalled();
  expect(toast.warning).toHaveBeenCalledWith(expect.stringContaining('existing graph was preserved'));
  expect(mutation.progress.isRunning).toBe(false);
  expect(mutation.progress.currentStep).toContain('No data published');
  expect(mutation.progress.percentage).toBe(0);
});
test('only confirmed SUCCESS is presented as publication',async()=>{
  erpAPI.materialize.mockResolvedValue({status:'SUCCESS',tripleCount:3});
  await act(async()=>{await mutation.mutateAsync('erp-a');});
  expect(toast.success).toHaveBeenCalledWith('Materialization completed: 3 triples generated');
  expect(mutation.progress.percentage).toBe(100);
  expect(mutation.progress.tripleCount).toBe(3);
});
test('a success HTTP response without a success status cannot claim publication',async()=>{
  erpAPI.materialize.mockResolvedValue({tripleCount:7});
  await act(async()=>{await mutation.mutateAsync('erp-a');});
  expect(toast.success).not.toHaveBeenCalled();
  expect(toast.error).toHaveBeenCalledWith('Materialization was not confirmed');
  expect(mutation.progress.percentage).toBe(0);
});
