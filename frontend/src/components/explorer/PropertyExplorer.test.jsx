import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { Simulate } from 'react-dom/test-utils';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import OntologyExplorer from './OntologyExplorer';
import { ontologyAPI } from '../../services/api';
import hierarchy from './__fixtures__/class-hierarchy.json';
import properties from './__fixtures__/module-properties.json';

// Vista de propiedades del explorador (RF-02). module-properties.json tiene la forma de
// GET /api/ontology/modules/{id}/properties: dos árboles (objectProperties y dataProperties) con
// hijos en "subproperties", dominios y rangos como {uri, operator, members} y los conteos.
jest.mock('../../services/api', () => ({
  ontologyAPI: {
    getModules: jest.fn(),
    getClassHierarchy: jest.fn(),
    getPropertyHierarchy: jest.fn(),
    listIndividuals: jest.fn(),
    getIndividual: jest.fn(),
  },
}));
jest.mock('react-toastify', () => ({ toast: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } }));

const C = 'http://w3id.org/protys/ontology/core#';
let container, root, client;
const settle = async () => { await act(async () => { await new Promise(resolve => setTimeout(resolve, 0)); }); };
const byText = (selector, text) =>
  Array.from(container.querySelectorAll(selector)).find(node => node.textContent.trim() === text);
const click = async (node) => { await act(async () => { node.click(); }); await settle(); };

// Jerarquía de clases con las propiedades cuyo dominio declarado incluye cada clase, como las llena el servicio.
const flat = (p) => ({ ...p, subproperties: null });
const [hasPart, locatedAt, partOf] = properties.objectProperties;
const [identifier] = properties.dataProperties;
const withProperties = hierarchy.map(c =>
  c.uri === C + 'Process' ? { ...c, objectProperties: [flat(hasPart), flat(partOf)], dataProperties: [flat(identifier)], propertyCount: 3 }
    : c.uri === C + 'Enterprise' ? { ...c, objectProperties: [flat(locatedAt)], dataProperties: [flat(identifier)], propertyCount: 2 }
      : { ...c, objectProperties: [], dataProperties: [], propertyCount: 0 });

async function openModule() {
  await settle();
  await act(async () => { Simulate.change(container.querySelector('select'), { target: { value: 'catalogo-integrado' } }); });
  await settle();
}
const showProperties = async () => click(byText('[role=tab]', 'Properties'));

beforeEach(async () => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.clearAllMocks();
  ontologyAPI.getModules.mockResolvedValue([{ id: 'catalogo-integrado', name: 'Canonical_Catalog_Integrated' }]);
  ontologyAPI.getClassHierarchy.mockResolvedValue(withProperties);
  ontologyAPI.getPropertyHierarchy.mockResolvedValue(properties);
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

test('the selector switches between classes and properties; properties are requested only when opened', async () => {
  await openModule();
  expect(byText('[role=tab]', 'Classes').getAttribute('aria-selected')).toBe('true');
  expect(byText('h3', 'Class Hierarchy')).toBeDefined();
  expect(ontologyAPI.getPropertyHierarchy).not.toHaveBeenCalled();
  await showProperties();
  expect(ontologyAPI.getPropertyHierarchy).toHaveBeenCalledWith('catalogo-integrado');
  expect(byText('h3', 'Property Hierarchy')).toBeDefined();
  expect(byText('h3', 'Class Hierarchy')).toBeUndefined();
  expect(byText('label', 'Search Properties')).toBeDefined();
  await click(byText('[role=tab]', 'Classes'));
  expect(byText('h3', 'Class Hierarchy')).toBeDefined();
});

test('the property tree has an object group and a data group, with counts, and folds like the class tree', async () => {
  await openModule();
  await showProperties();
  const objects = container.querySelector('[data-testid=property-group-object]');
  const datas = container.querySelector('[data-testid=property-group-data]');
  expect(objects.textContent).toContain('Object properties');
  expect(objects.querySelector('button').textContent).toContain('4');
  for (const label of ['has part', 'located at', 'part of']) expect(byText('span', label)).toBeDefined();
  expect(datas.textContent).toContain('identifier');
  expect(byText('span', 'has operation')).toBeUndefined();
  await click(byText('span', 'has part').parentElement.querySelector('button'));
  expect(byText('span', 'has operation')).toBeDefined();
  await click(objects.querySelector('button'));
  expect(byText('span', 'has part')).toBeUndefined();
  expect(datas.textContent).toContain('identifier');
});

test('the property detail shows type, domain, range, super and subproperties, inverse and characteristics', async () => {
  await openModule();
  await showProperties();
  await click(byText('span', 'has part'));
  const detail = container.querySelector('[data-testid=property-detail]');
  expect(byText('h2', 'has part')).toBeDefined();
  expect(detail.textContent).toContain('Object property');
  expect(detail.querySelector('[data-testid=property-domains]').textContent).toContain('Process');
  expect(detail.querySelector('[data-testid=property-sub]').textContent).toContain('has operation');
  expect(detail.querySelector('[data-testid=property-inverse]').textContent).toContain('part of');
  expect(detail.querySelector('[data-testid=property-characteristics]').textContent).toContain('Transitive');
  // La subpropiedad lleva a su detalle, con la superpropiedad.
  await click(byText('[data-testid=property-sub] button', 'has operation'));
  expect(byText('h2', 'has operation')).toBeDefined();
  expect(container.querySelector('[data-testid=property-super]').textContent).toContain('has part');
  // Un rango que no es clase del módulo no es enlace.
  expect(container.querySelector('[data-testid=property-ranges] button')).toBeNull();
});

test('a union domain is shown as A ∪ B, several domains as declared ones, and a domain class opens its detail', async () => {
  await openModule();
  await showProperties();
  await click(byText('span', 'located at'));
  expect(container.querySelector('[data-testid=property-domains]').textContent).toContain('Enterprise ∪ Station');
  await click(byText('span', 'identifier'));
  const domains = container.querySelector('[data-testid=property-domains]');
  expect(domains.textContent).toContain('2 declared (each one applies)');
  expect(domains.querySelectorAll('li')).toHaveLength(2);
  expect(container.querySelector('[data-testid=property-ranges]').textContent).toContain('string');
  expect(container.querySelector('[data-testid=property-characteristics]').textContent).toContain('Functional');
  await click(byText('[data-testid=property-domains] button', 'Process'));
  expect(byText('[role=tab]', 'Classes').getAttribute('aria-selected')).toBe('true');
  expect(byText('h2', 'Process')).toBeDefined();
});

test('the class detail lists the properties whose declared domain includes the class, with the full domain', async () => {
  await openModule();
  await click(byText('span', 'Enterprise'));
  const list = container.querySelector('[data-testid=class-properties]');
  expect(container.textContent).toContain('Properties whose declared domain includes this class (2)');
  const items = list.querySelectorAll(':scope > li');
  expect(items).toHaveLength(2);
  expect(items[0].textContent).toContain('located at');
  expect(items[0].textContent).toContain('Declared domain: Enterprise ∪ Station');
  expect(items[1].textContent).toContain('identifier');
  expect(items[1].textContent).toContain('Declared domains (2, each one applies)');
  // La propiedad lleva a su detalle en la vista de propiedades.
  await click(byText('[data-testid=class-properties] button', 'located at'));
  expect(byText('[role=tab]', 'Properties').getAttribute('aria-selected')).toBe('true');
  expect(byText('h2', 'located at')).toBeDefined();
});

test('a module without properties shows the empty state', async () => {
  ontologyAPI.getPropertyHierarchy.mockResolvedValue({ objectProperties: [], dataProperties: [], objectPropertyCount: 0, dataPropertyCount: 0, annotationPropertyCount: 0 });
  await openModule();
  await showProperties();
  expect(container.textContent).toContain('No properties found');
});

test('an error from the properties service shows the error state', async () => {
  ontologyAPI.getPropertyHierarchy.mockRejectedValue(new Error('500'));
  await openModule();
  await showProperties();
  expect(container.querySelector('[role=alert]').textContent).toContain('Could not load the properties');
});

test('an error from the classes service shows the error state, not the empty state', async () => {
  ontologyAPI.getClassHierarchy.mockRejectedValue(new Error('500'));
  await openModule();
  expect(container.querySelector('[role=alert]').textContent).toContain('Could not load the class hierarchy');
  expect(container.textContent).not.toContain('No classes found');
});
