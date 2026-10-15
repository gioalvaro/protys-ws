import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Wizard from './Wizard';
import { wizardAPI, alignmentAPI } from '../../services/api';

jest.mock('../../services/api', () => ({
  wizardAPI: { step1Upload: jest.fn(), step2Validate: jest.fn(), step3DefineAlignments: jest.fn(), step4VerifyInferences: jest.fn(), completeIncorporation: jest.fn(), cancelIncorporation: jest.fn() },
  alignmentAPI: { getRules: jest.fn() },
}));
jest.mock('react-toastify', () => ({ toast: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } }));

let container, root, client;
const session = 'session-a';
const valid = { status: 'CONSISTENT', step2_consistent: true };
const verified = { status: 'CONSISTENT', step4_verified: true, selectedRuleIds: ['rule-a'], executedRules: [{ id: 'rule-a', name: 'Actual rule A' }] };
const settle = async () => { await act(async () => { await new Promise(resolve => setTimeout(resolve, 0)); }); };
const button = text => Array.from(container.querySelectorAll('button')).find(node => node.textContent.includes(text));
const click = async text => { await act(async () => { button(text).click(); }); await settle(); };

beforeEach(async () => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.clearAllMocks(); sessionStorage.clear();
  wizardAPI.step1Upload.mockResolvedValue({tempModuleId: session, step1_parsed: true});
  wizardAPI.step2Validate.mockResolvedValue(valid);
  alignmentAPI.getRules.mockResolvedValue([{id:'rule-a',name:'Available A',active:false}]);
  wizardAPI.step3DefineAlignments.mockResolvedValue({status:'SELECTED',selectedRuleIds:['rule-a'],step3_rulesCount:1});
  wizardAPI.step4VerifyInferences.mockResolvedValue(verified);
  wizardAPI.cancelIncorporation.mockResolvedValue({status:'CANCELLED',tempModuleId:session});
  client = new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false,gcTime:0}}});
  container = document.createElement('div'); document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => { root.render(<QueryClientProvider client={client}><Wizard/></QueryClientProvider>); });
});
afterEach(async () => { await act(async () => { root.unmount(); }); client.clear(); container.remove(); sessionStorage.clear(); });

async function upload() {
  const file = new File(['<rdf/>'],'case.owl',{type:'application/rdf+xml'});
  await act(async () => { Simulate.change(container.querySelector('input[type=file]'),{target:{files:[file]}}); });
  await click('Upload & Continue');
}
async function configure() {
  await upload(); await click('Validate & Continue');
  await act(async () => { container.querySelector('input[type=checkbox]').click(); });
  await click('Configure & Continue');
}

test('failed cancellation keeps the session and permits retry before replacing the upload', async () => {
  await upload();
  wizardAPI.cancelIncorporation.mockRejectedValueOnce(new Error('cleanup unavailable'));
  await click('Back');
  expect(wizardAPI.cancelIncorporation).toHaveBeenCalledWith(session);
  expect(container.textContent).toContain('Step 2: Validate Ontology');
  expect(container.querySelector('[role=alert]').textContent).toContain('Retry before uploading another file');
  await click('Back');
  expect(wizardAPI.cancelIncorporation).toHaveBeenCalledTimes(2);
  expect(container.textContent).toContain('Step 1: Upload Ontology');
});

test('an unconfirmed cancellation does not discard the upload', async () => {
  await upload(); wizardAPI.cancelIncorporation.mockResolvedValue({status:'FAILED',tempModuleId:session});
  await click('Back');
  expect(container.textContent).toContain('Step 2: Validate Ontology');
  expect(container.textContent).toContain('Retry before uploading another file');
});

test('pending cancellation prevents double requests and prevents a second upload', async () => {
  await upload(); let finish;
  wizardAPI.cancelIncorporation.mockImplementation(() => new Promise(resolve => {finish=resolve;}));
  await act(async () => { button('Back').click(); button('Back').click(); });
  await settle();
  expect(wizardAPI.cancelIncorporation).toHaveBeenCalledTimes(1);
  expect(container.querySelector('input[type=file]')).toBeNull();
  expect(button('Back').disabled).toBe(true);
  await act(async () => { finish({status:'CANCELLED',tempModuleId:session}); }); await settle();
  expect(container.textContent).toContain('Step 1: Upload Ontology');
});

test('only registered IDs are sent and an unverified selection is not presented as applied', async () => {
  await configure();
  expect(wizardAPI.step3DefineAlignments).toHaveBeenCalledWith(session,[{id:'rule-a'}]);
  expect(container.textContent).toContain('Selected Rules');
  expect(container.textContent).not.toContain('Applied Rules');
  expect(container.textContent).not.toContain('Actual rule A');
  await click('Verify & Continue');
  expect(container.textContent).toContain('Executed Rules');
  expect(container.textContent).toContain('Actual rule A');
});

test('a verification receipt for another selection cannot advance to completion', async () => {
  await configure();
  wizardAPI.step4VerifyInferences.mockResolvedValue({...verified,selectedRuleIds:['rule-b'],executedRules:[{id:'rule-b',name:'Unexpected B'}]});
  await click('Verify & Continue');
  expect(container.textContent).toContain('Step 4: Verify Configuration');
  expect(button('Create Ontology')).toBeUndefined();
});

test('Start Over cancels the server session before clearing the form', async () => {
  await configure(); await click('Verify & Continue'); await click('Start Over');
  expect(wizardAPI.cancelIncorporation).toHaveBeenCalledWith(session);
  expect(container.textContent).toContain('Step 1: Upload Ontology');
});

test('a remount recovers an open session for fresh validation instead of uploading another', async () => {
  await upload();
  await act(async () => { root.unmount(); }); root=createRoot(container);
  await act(async () => { root.render(<QueryClientProvider client={client}><Wizard/></QueryClientProvider>); });
  expect(container.textContent).toContain('Step 2: Validate Ontology');
  await click('Validate & Continue');
  expect(wizardAPI.step2Validate).toHaveBeenCalledWith(session);
  expect(wizardAPI.step1Upload).toHaveBeenCalledTimes(1);
});


test('two immediate upload clicks create only one server session', async () => {
  let finish;
  wizardAPI.step1Upload.mockImplementation(() => new Promise(resolve => { finish=resolve; }));
  const file=new File(['<rdf/>'],'case.owl',{type:'application/rdf+xml'});
  await act(async()=>{Simulate.change(container.querySelector('input[type=file]'),{target:{files:[file]}});});
  await act(async()=>{button('Upload & Continue').click();button('Upload & Continue').click();}); await settle();
  expect(wizardAPI.step1Upload).toHaveBeenCalledTimes(1);
  expect(container.querySelector('input[type=file]').disabled).toBe(true);
  await act(async()=>{finish({tempModuleId:session,step1_parsed:true});}); await settle();
  expect(container.textContent).toContain('Step 2: Validate Ontology');
});

test('an explicit empty selection is sent and its empty execution receipt is shown honestly',async()=>{
  await upload();await click('Validate & Continue');
  wizardAPI.step3DefineAlignments.mockResolvedValue({status:'SELECTED',selectedRuleIds:[],step3_rulesCount:0});
  await click('Configure & Continue');
  expect(wizardAPI.step3DefineAlignments).toHaveBeenCalledWith(session,[]);
  wizardAPI.step4VerifyInferences.mockResolvedValue({...verified,selectedRuleIds:[],executedRules:[]});
  await click('Verify & Continue');
  expect(container.textContent).toContain('No alignment rules were executed for this session.');
  expect(button('Create Ontology').disabled).toBe(false);
});

test('a successful OWL flag without an execution receipt is insufficient to complete',async()=>{
  await configure();wizardAPI.step4VerifyInferences.mockResolvedValue({status:'CONSISTENT',step4_verified:true});
  await click('Verify & Continue');
  expect(container.textContent).toContain('Step 4: Verify Configuration');
  expect(button('Create Ontology')).toBeUndefined();
});

test('completion cannot race with Start Over and clears the recovered session only when confirmed',async()=>{
  await configure();await click('Verify & Continue');let finish;
  wizardAPI.completeIncorporation.mockImplementation(()=>new Promise(resolve=>{finish=resolve;}));
  await act(async()=>{button('Create Ontology').click();button('Start Over').click();});await settle();
  expect(wizardAPI.cancelIncorporation).not.toHaveBeenCalled();
  expect(button('Start Over').disabled).toBe(true);
  await act(async()=>{finish({status:'COMPLETED',finalModuleId:'final-a'});});await settle();
  expect(container.textContent).toContain('Step 1: Upload Ontology');
  expect(sessionStorage.getItem('protys.wizard.session')).toBeNull();
});


test('a failed upload with a registered session ID must be cancelled before replacement',async()=>{
  wizardAPI.step1Upload.mockRejectedValue({response:{status:422,data:{tempModuleId:session,step1_error:true,step1_parsed:false}}});
  await upload();
  expect(container.textContent).toContain('Upload incomplete');
  expect(button('Validate & Continue').disabled).toBe(true);
  expect(JSON.parse(sessionStorage.getItem('protys.wizard.session')).id).toBe(session);
  await click('Back');
  expect(wizardAPI.cancelIncorporation).toHaveBeenCalledWith(session);
  expect(container.textContent).toContain('Step 1: Upload Ontology');
});

test('an already completed session retains its reference until its durable completion receipt is recovered',async()=>{
  await upload();
  wizardAPI.cancelIncorporation.mockRejectedValue({response:{status:409,data:{status:'COMPLETED',tempModuleId:session}}});
  await click('Back');
  expect(sessionStorage.getItem('protys.wizard.session')).not.toBeNull();
  expect(button('Validate & Continue').disabled).toBe(true);
  wizardAPI.completeIncorporation.mockResolvedValue({status:'COMPLETED',finalModuleId:'final-a'});
  await click('Retrieve completion receipt');
  expect(wizardAPI.completeIncorporation).toHaveBeenCalledWith(session);
  expect(sessionStorage.getItem('protys.wizard.session')).toBeNull();
  expect(container.textContent).toContain('Step 1: Upload Ontology');
});

test('a selection response for different IDs cannot reach verification',async()=>{
  await upload();await click('Validate & Continue');
  await act(async()=>{container.querySelector('input[type=checkbox]').click();});
  wizardAPI.step3DefineAlignments.mockResolvedValue({status:'SELECTED',selectedRuleIds:['rule-b']});
  await click('Configure & Continue');
  expect(container.textContent).toContain('Step 3: Configure Alignments');
  expect(wizardAPI.step4VerifyInferences).not.toHaveBeenCalled();
});


test('a rule catalog failure does not masquerade as an empty rule selection',async()=>{
  await upload();alignmentAPI.getRules.mockRejectedValueOnce(new Error('unavailable'));
  await click('Validate & Continue');
  expect(container.textContent).toContain('Step 2: Validate Ontology');
  expect(wizardAPI.step3DefineAlignments).not.toHaveBeenCalled();
  await click('Validate & Continue');
  expect(container.textContent).toContain('Step 3: Configure Alignments');
  expect(container.textContent).toContain('Globally inactive; available for this session');
});


test('completed incorporation with pending cleanup retains the session until a clean retry receipt',async()=>{
  await configure();await click('Verify & Continue');
  wizardAPI.completeIncorporation.mockResolvedValueOnce({status:'COMPLETED',finalModuleId:'final-a',
    selectedRuleIds:['rule-a'],executedRules:[{id:'rule-a',name:'Final executed A'}],ruleDefinitionsSha256:'b'.repeat(64),
    warnings:['Incorporation is complete; temporary graph cleanup will be retried on completion retry']});
  await click('Create Ontology');
  expect(container.textContent).toContain('Ontology incorporated');
  expect(container.textContent).toContain('Temporary cleanup is pending');
  expect(container.textContent).toContain('Final executed A');
  expect(container.querySelector('details code').textContent).toBe('b'.repeat(64));
  expect(button('Start Over')).toBeUndefined();
  expect(button('Create Ontology')).toBeUndefined();
  expect(JSON.parse(sessionStorage.getItem('protys.wizard.session')).id).toBe(session);
  wizardAPI.completeIncorporation.mockRejectedValueOnce(new Error('cleanup unavailable'));
  await click('Retry temporary cleanup');
  expect(container.textContent).toContain('Ontology incorporated');
  expect(sessionStorage.getItem('protys.wizard.session')).not.toBeNull();
  wizardAPI.completeIncorporation.mockResolvedValue({status:'COMPLETED',finalModuleId:'final-a',cleanupPending:false,warnings:[]});
  await click('Retry temporary cleanup');
  expect(wizardAPI.completeIncorporation).toHaveBeenCalledTimes(3);
  expect(wizardAPI.cancelIncorporation).not.toHaveBeenCalled();
  expect(sessionStorage.getItem('protys.wizard.session')).toBeNull();
  expect(container.textContent).toContain('Step 1: Upload Ontology');
});

test('pending cleanup is recoverable after remount without losing the incorporated session',async()=>{
  await configure();await click('Verify & Continue');
  wizardAPI.completeIncorporation.mockResolvedValueOnce({status:'COMPLETED',finalModuleId:'final-a',cleanupPending:true,warnings:[]});
  await click('Create Ontology');
  await act(async()=>{root.unmount();});root=createRoot(container);
  await act(async()=>{root.render(<QueryClientProvider client={client}><Wizard/></QueryClientProvider>);});
  expect(container.textContent).toContain('A previous completion reported pending cleanup');
  expect(button('Validate & Continue').disabled).toBe(true);
  wizardAPI.completeIncorporation.mockResolvedValue({status:'COMPLETED',finalModuleId:'final-a',cleanupPending:false,warnings:[]});
  await click('Retrieve completion receipt');
  expect(wizardAPI.completeIncorporation).toHaveBeenLastCalledWith(session);
  expect(container.textContent).toContain('Step 1: Upload Ontology');
});
