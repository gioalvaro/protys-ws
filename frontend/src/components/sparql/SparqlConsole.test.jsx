import React, { act } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createRoot } from 'react-dom/client';
import SparqlConsole, { SparqlResults, competencyTitle } from './SparqlConsole';
import q06 from './__fixtures__/q06.json';
import competency from './__fixtures__/competency.json';
import { useSparqlTemplates, useCompetencyQueries, useSparqlExecute, useValidateQuery, useExportResults } from '../../hooks/useSparql';

jest.mock('../../hooks/useSparql', () => ({
  ...jest.requireActual('../../hooks/useSparql'),
  useSparqlTemplates: jest.fn(), useCompetencyQueries: jest.fn(), useSparqlExecute: jest.fn(),
  useValidateQuery: jest.fn(), useExportResults: jest.fn(),
}));
function view(element) {
  const div = document.createElement('div');
  div.innerHTML = renderToStaticMarkup(element);
  return div;
}
beforeEach(() => {
  useSparqlTemplates.mockReturnValue({ data: [], isLoading: false });
  useCompetencyQueries.mockReturnValue({ data: competency });
  useSparqlExecute.mockReturnValue({ mutate: jest.fn(), isPending: false });
  useValidateQuery.mockReturnValue({ mutate: jest.fn(), isPending: false });
  useExportResults.mockReturnValue({ mutate: jest.fn(), isPending: false });
});
test('captured Q06 DTO renders five ordered columns and all three rows', () => {
  expect(q06.columns).toBeNull();
  expect(q06.rows).toBeNull();
  const div = view(<SparqlResults results={q06} />);
  expect(Array.from(div.querySelectorAll('th')).map(x => x.textContent)).toEqual(['product', 'lot', 'liters', 'ghgKgCO2e', 'kgCO2ePerL']);
  expect(div.querySelectorAll('tbody tr')).toHaveLength(3);
  expect(div.querySelectorAll('tbody td')).toHaveLength(15);
  expect(div.textContent).toContain('0.586043358739837398373984');
  expect(div.textContent).not.toContain('No results found');
  expect(Array.from(div.querySelectorAll('tbody tr')).map(x => x.children[1].textContent)).toEqual(['http://w3id.org/protys/fixture#BatchA', 'http://w3id.org/protys/fixture#BatchB', 'http://w3id.org/protys/fixture#BatchC']);
});
test('competency buttons show real P identifiers and names and load exact query text', () => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  const div = document.createElement('div');
  const root = createRoot(div);
  act(() => root.render(<SparqlConsole />));
  const label = 'P2, P3, P5: Q02 - Trazabilidad lote, plan, producto, recursos requeridos e insumos';
  expect(competencyTitle(competency[0])).toBe(label);
  const button = Array.from(div.querySelectorAll('button')).find(x => x.textContent.includes(label));
  expect(button).toBeDefined();
  act(() => button.dispatchEvent(new MouseEvent('click', { bubbles: true })));
  expect(div.querySelector('textarea').value).toBe(competency[0].queryText);
  expect(div.textContent).toContain('P1: Q04');
  expect(div.textContent).toContain('P4: Q18');
  act(() => root.unmount());
});
test('ASK false is displayed as an answer and not as an empty SELECT', () => {
  const div = view(<SparqlResults results={{ askResult: false }} />);
  expect(div.textContent).toBe('ASK result: false');
  expect(div.textContent).not.toContain('No results found');
});
test('executing a captured response updates the console count, cells and execution metadata', () => {
  global.IS_REACT_ACT_ENVIRONMENT = true;
  jest.useFakeTimers();
  const mutate = jest.fn((request, callbacks) => callbacks.onSuccess(q06));
  useSparqlExecute.mockReturnValue({ mutate, isPending: false });
  const div = document.createElement('div');
  const root = createRoot(div);
  const previousScroll = HTMLElement.prototype.scrollIntoView;
  HTMLElement.prototype.scrollIntoView = jest.fn();
  act(() => root.render(<SparqlConsole />));
  const button = Array.from(div.querySelectorAll('button')).find(x => x.textContent === 'Execute');
  act(() => button.dispatchEvent(new MouseEvent('click', { bubbles: true })));
  act(() => jest.runOnlyPendingTimers());
  expect(mutate.mock.calls[0][0].query).toBe(div.querySelector('textarea').value);
  expect(div.textContent).toContain('Results (3)');
  expect(div.textContent).toContain('Executed in 81ms');
  expect(div.querySelectorAll('tbody td')).toHaveLength(15);
  act(() => root.unmount());
  HTMLElement.prototype.scrollIntoView = previousScroll;
  jest.useRealTimers();
});
test('empty SELECT keeps its column headings and explicit absence message', () => {
  const div = view(<SparqlResults results={{ sparqlJson: { head: { vars: ['lot'] }, results: { bindings: [] } } }} />);
  expect(div.querySelector('th').textContent).toBe('lot');
  expect(div.textContent).toContain('No results found');
});
