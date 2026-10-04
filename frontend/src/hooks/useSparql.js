import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { sparqlAPI } from '../services/api';

/**
 * Mutation for executing SPARQL queries
 * Tracks loading state and results
 */
export const useSparqlExecute = () => {
  return useMutation({
    mutationFn: async ({ query }) => {
      return await sparqlAPI.executeQuery({ query });
    },
    onSuccess: (result) => {
      if (typeof result.askResult === 'boolean') {
        toast.success(`Query executed: ASK returned ${result.askResult}`);
      } else if (result.constructResult != null) {
        toast.success('Query executed: graph returned');
      } else if (getSparqlTable(result).rows.length > 0) {
        toast.success(`Query executed: ${getSparqlTable(result).rows.length} results`);
      } else {
        toast.info('Query executed: no results found');
      }
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Query execution failed';
      toast.error(message);
    },
  });
};

/**
 * Fetches SPARQL query templates
 */
export const useSparqlTemplates = (options = {}) => {
  return useQuery({
    queryKey: ['sparql', 'templates'],
    queryFn: async () => {
      return await sparqlAPI.getTemplates();
    },
    staleTime: 15 * 60 * 1000, // 15 minutes
    ...options,
  });
};

/**
 * Fetches saved query templates associated with competency identifiers P1-P5.
 */
export const useCompetencyQueries = (options = {}) => {
  return useQuery({
    queryKey: ['sparql', 'competency'],
    queryFn: async () => {
      return await sparqlAPI.getCompetencyQueries();
    },
    staleTime: 15 * 60 * 1000,
    ...options,
  });
};

/** Read the execution DTO without rewriting its typed SPARQL JSON or query. */
export function getSparqlTable(response) {
  const bindings = response?.sparqlJson?.results?.bindings;
  const fallback = Array.isArray(response?.rows) ? response.rows
    : Array.isArray(response?.results) ? response.results : [];
  const columns = response?.columns ?? response?.sparqlJson?.head?.vars
    ?? [...new Set(fallback.flatMap(row => Object.keys(row)))];
  const rows = Array.isArray(bindings)
    ? bindings.map(binding => Object.fromEntries(columns.map(column => [
      column, binding[column]?.value ?? null,
    ]))) : fallback;
  return { columns, rows };
}

/**
 * Mutation for saving a new SPARQL template
 */
export const useSaveTemplate = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (template) => {
      return await sparqlAPI.saveTemplate(template);
    },
    onSuccess: (template) => {
      queryClient.invalidateQueries({ queryKey: ['sparql', 'templates'] });
      toast.success(`Template "${template.name}" saved successfully`);
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to save template';
      toast.error(message);
    },
  });
};

/**
 * Mutation for validating SPARQL query syntax
 */
export const useValidateQuery = () => {
  return useMutation({
    mutationFn: async (queryText) => {
      return await sparqlAPI.validateQuery(queryText);
    },
    onSuccess: (result) => {
      if (result.valid) {
        toast.success('Query syntax is valid');
      } else {
        toast.warning(`Query validation failed: ${result.message || 'Invalid query'}`);
      }
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Validation failed';
      toast.error(message);
    },
  });
};

/**
 * Mutation for exporting SPARQL results
 */
export const useExportResults = () => {
  return useMutation({
    mutationFn: async ({ results, format = 'CSV' }) => {
      return await sparqlAPI.exportResults(results, format);
    },
    onSuccess: () => {
      toast.success('Results exported successfully');
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Export failed';
      toast.error(message);
    },
  });
};
