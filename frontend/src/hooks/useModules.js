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
      if (result.consistent) {
        toast.success('Module is consistent');
      } else {
        toast.warning('Module has consistency issues');
      }
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Validation failed';
      toast.error(message);
    },
  });
};

/**
 * Fetches individual details
 */
export const useIndividual = (uri, options = {}) => {
  return useQuery({
    queryKey: ['individuals', uri],
    queryFn: async () => {
      return await ontologyAPI.getIndividual(uri);
    },
    enabled: !!uri,
    staleTime: 5 * 60 * 1000,
    ...options,
  });
};

/**
 * Mutation for creating a new individual
 */
export const useCreateIndividual = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ classUri, properties }) => {
      return await ontologyAPI.createIndividual(classUri, properties);
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['individuals'] });
      toast.success('Individual created successfully');
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to create individual';
      toast.error(message);
    },
  });
};

/**
 * Mutation for updating an individual
 */
export const useUpdateIndividual = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ uri, properties }) => {
      return await ontologyAPI.updateIndividual(uri, properties);
    },
    onSuccess: (_, { uri }) => {
      queryClient.invalidateQueries({ queryKey: ['individuals', uri] });
      toast.success('Individual updated successfully');
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to update individual';
      toast.error(message);
    },
  });
};

/**
 * Mutation for deleting an individual
 */
export const useDeleteIndividual = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (uri) => {
      await ontologyAPI.deleteIndividual(uri);
      return uri;
    },
    onSuccess: (uri) => {
      queryClient.invalidateQueries({ queryKey: ['individuals'] });
      queryClient.removeQueries({ queryKey: ['individuals', uri] });
      toast.success('Individual deleted successfully');
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to delete individual';
      toast.error(message);
    },
  });
};
