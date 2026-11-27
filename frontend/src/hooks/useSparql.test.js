import { useMutation } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { sparqlAPI } from '../services/api';
import { useSparqlExecute, useExportResults, getSparqlTable } from './useSparql';
import q06 from '../components/sparql/__fixtures__/q06.json';
jest.mock('@tanstack/react-query', () => ({ useMutation: jest.fn(), useQuery: jest.fn(), useQueryClient: jest.fn() }));
jest.mock('react-toastify', () => ({ toast: { success: jest.fn(), info: jest.fn(), error: jest.fn() } }));
jest.mock('../services/api', () => ({ sparqlAPI: { executeQuery: jest.fn(), exportResults: jest.fn() } }));
beforeEach(() => jest.clearAllMocks());
function executionOptions() { useSparqlExecute(); return useMutation.mock.calls[0][0]; }
test('Q06 notification reports three rows from the actual array DTO', () => {
  executionOptions().onSuccess(q06);
  expect(toast.success).toHaveBeenCalledWith('Query executed: 3 results');
  expect(toast.info).not.toHaveBeenCalled();
});
test('ASK false has a positive execution notification without claiming empty results', () => {
  executionOptions().onSuccess({ askResult: false });
  expect(toast.success).toHaveBeenCalledWith('Query executed: ASK returned false');
  expect(toast.info).not.toHaveBeenCalled();
});
test('empty SELECT has an explicit empty-result notification', () => {
  executionOptions().onSuccess({ results: [] });
  expect(toast.info).toHaveBeenCalledWith('Query executed: no results found');
});
test('legacy columns/rows preserve zero and missing cell positions', () => {
  expect(getSparqlTable({ columns: ['amount', 'lot'], rows: [{ amount: 0 }] })).toEqual({ columns: ['amount', 'lot'], rows: [{ amount: 0 }] });
});
test('typed bindings keep lexical precision and do not mutate the original DTO', () => {
  const before = JSON.stringify(q06);
  expect(getSparqlTable(q06).rows[0].liters).toBe('98.4');
  expect(JSON.stringify(q06)).toBe(before);
});
test('export sends the complete typed response to the published endpoint unchanged', async () => {
  const blob = new Blob(['test']); sparqlAPI.exportResults.mockResolvedValue(blob);
  useExportResults();
  const options = useMutation.mock.calls[0][0];
  expect(await options.mutationFn({ results: q06, format: 'JSON' })).toBe(blob);
  expect(sparqlAPI.exportResults).toHaveBeenCalledWith(q06, 'JSON');
});
