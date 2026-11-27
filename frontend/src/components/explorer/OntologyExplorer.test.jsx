import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import OntologyExplorer from './OntologyExplorer';
import { ontologyAPI } from '../../services/api';
import hierarchy from './__fixtures__/class-hierarchy.json';
import singleObject from './__fixtures__/class-hierarchy-objeto-unico.json';

// Forma real del contrato: GET /api/ontology/modules/{id}/classes devuelve una lista de raíces con
// hijos en "subclasses". class-hierarchy-objeto-unico.json es la respuesta real anterior a la
// corrección (un objeto, no una lista), capturada de la plataforma con el catálogo integrado.
jest.mock('../../services/api', () => ({
  ontologyAPI: {
    getModules: jest.fn(),
    getClassHierarchy: jest.fn(),
    listIndividuals: jest.fn(),
    getIndividual: jest.fn(),
    createIndividual: jest.fn(),
    updateIndividual: jest.fn(),
    deleteIndividual: jest.fn(),
    undoIndividualEdit: jest.fn(),
  },
}));
jest.mock('react-toastify', () => ({ toast: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } }));
const { toast } = require('react-toastify');

let container, root, client;
const settle = async () => { await act(async () => { await new Promise(resolve => setTimeout(resolve, 0)); }); };
const byText = (selector, text) =>
  Array.from(container.querySelectorAll(selector)).find(node => node.textContent.trim() === text);

async function openIntegratedCatalog() {
  await settle();
  await act(async () => {
    Simulate.change(container.querySelector('select'), { target: { value: 'catalogo-integrado' } });
  });
  await settle();
}

beforeEach(async () => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.clearAllMocks();
  ontologyAPI.getModules.mockResolvedValue([{ id: 'catalogo-integrado', name: 'Canonical_Catalog_Integrated' }]);
  ontologyAPI.listIndividuals.mockResolvedValue([]);
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  await act(async () => {
    root.render(<QueryClientProvider client={client}><OntologyExplorer /></QueryClientProvider>);
  });
});
afterEach(async () => { await act(async () => { root.unmount(); }); client.clear(); container.remove(); });

test('shows every root of the hierarchy returned by the API', async () => {
  ontologyAPI.getClassHierarchy.mockResolvedValue(hierarchy);
  await openIntegratedCatalog();
  expect(ontologyAPI.getClassHierarchy).toHaveBeenCalledWith('catalogo-integrado');
  for (const label of ['Enterprise', 'Process', 'Station']) expect(byText('span', label)).toBeDefined();
});

test('expands a class and shows its subclasses from the "subclasses" field', async () => {
  ontologyAPI.getClassHierarchy.mockResolvedValue(hierarchy);
  await openIntegratedCatalog();
  expect(byText('span', 'Proceso de manufactura')).toBeUndefined();
  const processRow = byText('span', 'Process').parentElement;
  await act(async () => { processRow.querySelector('button').click(); });
  expect(byText('span', 'Proceso de manufactura')).toBeDefined();
});

test('selecting a class shows the class detail, not the individual detail', async () => {
  ontologyAPI.getClassHierarchy.mockResolvedValue(hierarchy);
  await openIntegratedCatalog();
  const processRow = byText('span', 'Process').parentElement;
  await act(async () => { processRow.querySelector('button').click(); });
  await act(async () => { byText('span', 'Proceso de manufactura').click(); });
  await settle();
  expect(byText('h2', 'Proceso de manufactura')).toBeDefined();
  expect(container.textContent).toContain('http://w3id.org/protys/ontology/iso15531#ManufacturingProcess');
  const superBox = byText('p', 'Superclasses').parentElement;
  expect(superBox.textContent).toContain('1');
  expect(container.textContent).toContain('Process');
  expect(ontologyAPI.getIndividual).not.toHaveBeenCalled();
});

test('a single-object response (the contract before the fix) does not break the explorer', async () => {
  ontologyAPI.getClassHierarchy.mockResolvedValue(singleObject);
  await openIntegratedCatalog();
  expect(byText('h3', 'Class Hierarchy')).toBeDefined();
  expect(byText('span', singleObject.label)).toBeDefined();
});

// ---- Individuos de una clase, detalle, modificación, baja, alta y deshacer ----
const PROCESS = 'http://w3id.org/protys/ontology/iso15531#ManufacturingProcess';
const IND = 'http://example.org/individuos/proceso-1';
const consistent = { status: 'CONSISTENT', consistent: true, timeMs: 2100, violations: [] };
const edit = (operation, validation = consistent) => ({ operation, uri: IND, moduleName: 'Canonical_Catalog_Integrated', editId: 'e-1', validation });

async function openProcessClass() {
  ontologyAPI.getClassHierarchy.mockResolvedValue(hierarchy);
  await openIntegratedCatalog();
  const processRow = byText('span', 'Process').parentElement;
  await act(async () => { processRow.querySelector('button').click(); });
  await act(async () => { byText('span', 'Proceso de manufactura').click(); });
  await settle();
}
const clickButton = async (text) => {
  await act(async () => { byText('button', text).click(); });
  await settle();
};

test('the class detail lists its individuals and opens one by its IRI', async () => {
  ontologyAPI.listIndividuals.mockResolvedValue([{ uri: IND, label: 'Proceso uno', localName: 'proceso-1' }]);
  ontologyAPI.getIndividual.mockResolvedValue({ uri: IND, label: 'Proceso uno', types: [PROCESS], properties: { 'http://www.w3.org/2000/01/rdf-schema#label': ['Proceso uno'] } });
  await openProcessClass();
  expect(ontologyAPI.listIndividuals).toHaveBeenCalledWith(PROCESS);
  const item = container.querySelector('[data-testid=class-individuals] button');
  expect(item.textContent).toContain('Proceso uno');
  await act(async () => { item.click(); });
  await settle();
  expect(ontologyAPI.getIndividual).toHaveBeenCalledWith(IND);
  expect(byText('h2', 'Proceso uno')).toBeDefined();
  expect(container.textContent).toContain('ManufacturingProcess');
});

test('editing the label sends PUT with the IRI, shows one notice and the validation result', async () => {
  ontologyAPI.listIndividuals.mockResolvedValue([{ uri: IND, label: 'Proceso uno' }]);
  ontologyAPI.getIndividual.mockResolvedValue({ uri: IND, label: 'Proceso uno', types: [PROCESS], properties: {} });
  ontologyAPI.updateIndividual.mockResolvedValue(edit('UPDATE'));
  await openProcessClass();
  await act(async () => { container.querySelector('[data-testid=class-individuals] button').click(); });
  await settle();
  await clickButton('Edit');
  await act(async () => { Simulate.change(container.querySelector('input[aria-label=Label]'), { target: { value: 'Proceso uno bis' } }); });
  await act(async () => { Simulate.submit(container.querySelector('form[aria-label="Edit individual"]')); });
  await settle();
  expect(ontologyAPI.updateIndividual).toHaveBeenCalledWith(IND, { label: 'Proceso uno bis' });
  expect(toast.success).toHaveBeenCalledTimes(1);
  expect(toast.success.mock.calls[0][0]).toContain('network consistent (validated in 2.1 s)');
  expect(container.querySelector('[data-testid=edit-result]').textContent).toContain('network consistent');
});

test('delete asks for confirmation in the page (no browser dialog) and an inconsistent result offers undo', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm');
  ontologyAPI.listIndividuals.mockResolvedValue([{ uri: IND, label: 'Proceso uno' }]);
  ontologyAPI.getIndividual.mockResolvedValue({ uri: IND, label: 'Proceso uno', types: [PROCESS], properties: {} });
  ontologyAPI.deleteIndividual.mockResolvedValue(edit('DELETE', { status: 'INCONSISTENT', consistent: false, timeMs: 1800, violations: ['x'] }));
  ontologyAPI.undoIndividualEdit.mockResolvedValue({ ...edit('UNDO'), editId: null });
  await openProcessClass();
  await act(async () => { container.querySelector('[data-testid=class-individuals] button').click(); });
  await settle();
  await clickButton('Delete');
  expect(ontologyAPI.deleteIndividual).not.toHaveBeenCalled();
  await clickButton('Confirm delete');
  expect(ontologyAPI.deleteIndividual).toHaveBeenCalledWith(IND);
  expect(confirmSpy).not.toHaveBeenCalled();
  expect(toast.warning).toHaveBeenCalledTimes(1);
  const banner = container.querySelector('[data-testid=edit-result]');
  expect(banner.textContent).toContain('network INCONSISTENT');
  expect(banner.textContent).toContain('You can undo it');
  await clickButton('Undo');
  expect(ontologyAPI.undoIndividualEdit).toHaveBeenCalledWith('e-1');
  expect(container.querySelector('[data-testid=edit-result]').textContent).toContain('Edit undone');
  confirmSpy.mockRestore();
});

test('the add form sends the IRI as subject and the label as rdfs:label, with a single notice', async () => {
  ontologyAPI.createIndividual.mockResolvedValue(edit('CREATE'));
  await openProcessClass();
  await clickButton('Add Individual');
  const [iri, label] = container.querySelectorAll('.fixed input');
  await act(async () => { Simulate.change(iri, { target: { value: IND } }); });
  await act(async () => { Simulate.change(label, { target: { value: 'Proceso uno' } }); });
  await act(async () => { Simulate.submit(container.querySelector('.fixed form')); });
  await settle();
  expect(ontologyAPI.createIndividual).toHaveBeenCalledWith(PROCESS, { uri: IND, label: 'Proceso uno' });
  expect(toast.success).toHaveBeenCalledTimes(1);
});
