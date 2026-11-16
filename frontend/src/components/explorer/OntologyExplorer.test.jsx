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
    getIndividual: jest.fn(),
    createIndividual: jest.fn(),
    deleteIndividual: jest.fn(),
  },
}));
jest.mock('react-toastify', () => ({ toast: { success: jest.fn(), error: jest.fn() } }));

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
