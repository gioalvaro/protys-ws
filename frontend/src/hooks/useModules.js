import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { ontologyAPI } from '../services/api';

/**
 * Fetches all ontology modules
 */
export const useModules = (options = {}) => {
  return useQuery({
    queryKey: ['modules'],
    queryFn: async () => {
      return await ontologyAPI.getModules();
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
};

/**
 * Fetches the class hierarchy/tree for a specific module
 */
export const useClassHierarchy = (moduleId, options = {}) => {
  return useQuery({
    queryKey: ['modules', moduleId, 'classes'],
    queryFn: async () => {
      return await ontologyAPI.getClassHierarchy(moduleId);
    },
    enabled: !!moduleId,
    staleTime: 10 * 60 * 1000, // 10 minutes
    ...options,
  });
};

/**
 * Mutation for uploading an ontology module file
 */
export const useUploadModule = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ file, name }) => {
      return await ontologyAPI.uploadModule(file, name);
    },
    onSuccess: (data) => {
      // Invalidate modules list to refetch
      queryClient.invalidateQueries({ queryKey: ['modules'] });
      toast.success(`Module "${data.name}" uploaded successfully`);
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to upload module';
      toast.error(message);
    },
  });
};

/**
 * Mutation for validating module consistency
 */
export const useValidateModule = () => {
  return useMutation({
    mutationFn: async (moduleId) => {
      return await ontologyAPI.validateModule(moduleId);
    },
    onSuccess: (result) => {
      if (result.status === 'CONSISTENT') {
        toast.success('Module is consistent');
      } else if (result.status === 'INCONSISTENT') {
        toast.warning('Module is inconsistent');
      } else {
        toast.error('Consistency could not be evaluated: ' + (result.error || 'Reasoning unavailable'));
      }
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Validation failed';
      toast.error(message);
    },
  });
};

/** Mensaje de la validación que la API ejecuta después de cada edición. */
export function validationSummary(validation) {
  if (!validation) return 'validation not reported';
  const seconds = (validation.timeMs / 1000).toFixed(1);
  if (validation.status === 'CONSISTENT') return `network consistent (validated in ${seconds} s)`;
  if (validation.status === 'INCONSISTENT') return `network INCONSISTENT (validated in ${seconds} s)`;
  return `validation not evaluated: ${validation.message || 'see details'} (${seconds} s)`;
}

function errorMessage(error, fallback) {
  return error.response?.data?.message || fallback;
}

/** Individuos de una clase (rdf:type exacto), en todos los módulos registrados. */
export const useClassIndividuals = (classUri, options = {}) => {
  return useQuery({
    queryKey: ['individuals', 'class', classUri],
    queryFn: async () => ontologyAPI.listIndividuals(classUri),
    enabled: !!classUri,
    ...options,
  });
};

/** Detalle de un individuo. */
export const useIndividual = (uri, options = {}) => {
  return useQuery({
    queryKey: ['individuals', 'detail', uri],
    queryFn: async () => ontologyAPI.getIndividual(uri),
    enabled: !!uri,
    ...options,
  });
};

// Las mutaciones no muestran avisos: el componente muestra un único aviso con el resultado de la validación.
function useIndividualMutation(mutationFn, fallback) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['individuals'] }),
    onError: (error) => toast.error(errorMessage(error, fallback)),
  });
}

export const useCreateIndividual = () =>
  useIndividualMutation(({ classUri, body }) => ontologyAPI.createIndividual(classUri, body), 'Failed to create individual');

export const useUpdateIndividual = () =>
  useIndividualMutation(({ uri, body }) => ontologyAPI.updateIndividual(uri, body), 'Failed to update individual');

export const useDeleteIndividual = () =>
  useIndividualMutation((uri) => ontologyAPI.deleteIndividual(uri), 'Failed to delete individual');

export const useUndoIndividualEdit = () =>
  useIndividualMutation((editId) => ontologyAPI.undoIndividualEdit(editId), 'Failed to undo the edit');
