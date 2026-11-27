import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { toast } from 'react-toastify';
import { erpAPI } from '../services/api';

/**
 * Fetches all ERP connectors
 */
export const useConnectors = (options = {}) => {
  return useQuery({
    queryKey: ['erp', 'connectors'],
    queryFn: async () => {
      return await erpAPI.getConnectors();
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
};

/**
 * Fetches a single ERP connector by ID
 */
export const useConnector = (connectorId, options = {}) => {
  return useQuery({
    queryKey: ['erp', 'connectors', connectorId],
    queryFn: async () => {
      return await erpAPI.getConnector(connectorId);
    },
    enabled: !!connectorId,
    staleTime: 5 * 60 * 1000,
    ...options,
  });
};

/**
 * Mutation for registering a new ERP connector
 */
export const useRegisterConnector = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (connector) => {
      return await erpAPI.registerConnector(connector);
    },
    onSuccess: (connector) => {
      queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
      toast.success(`Connector "${connector.name}" registered successfully`);
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to register connector';
      toast.error(message);
    },
  });
};

/**
 * Mutation for updating an ERP connector
 */
export const useUpdateConnector = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ connectorId, config }) => {
      return await erpAPI.updateConnector(connectorId, config);
    },
    onSuccess: (connector) => {
      queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
      queryClient.invalidateQueries({ queryKey: ['erp', 'connectors', connector.id] });
      toast.success(`Connector "${connector.name}" updated successfully`);
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to update connector';
      toast.error(message);
    },
  });
};

/**
 * Mutation for deleting an ERP connector
 */
export const useDeleteConnector = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (connectorId) => {
      await erpAPI.deleteConnector(connectorId);
      return connectorId;
    },
    onSuccess: (connectorId) => {
      queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
      queryClient.removeQueries({ queryKey: ['erp', 'connectors', connectorId] });
      toast.success('Connector deleted successfully');
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to delete connector';
      toast.error(message);
    },
  });
};

/**
 * Mutation for testing ERP JDBC connection
 */
export const useTestConnection = () => {
  return useMutation({
    mutationFn: async (connectorId) => {
      return await erpAPI.testConnection(connectorId);
    },
    onSuccess: (result) => {
      if (result.connected) {
        toast.success('Connection test passed');
      } else {
        toast.warning('Connection test failed');
      }
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Connection test failed';
      toast.error(message);
    },
  });
};

/**
 * Mutation for introspecting ERP database schema
 * Discovers available tables and columns
 */
export const useIntrospectSchema = () => {
  return useMutation({
    mutationFn: async (connectorId) => {
      return await erpAPI.introspectSchema(connectorId);
    },
    onSuccess: () => {
      toast.success('Schema introspection completed');
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Schema introspection failed';
      toast.error(message);
    },
  });
};

/**
 * Mutation for materializing ERP data into RDF
 * Tracks progress state during materialization
 */
export const useMaterialize = () => {
  const queryClient = useQueryClient();
  const [progress, setProgress] = useState({
    isRunning: false,
    currentStep: '',
    percentage: 0,
    tripleCount: 0,
  });

  const mutation = useMutation({
    mutationFn: async (connectorId) => {
      setProgress({
        isRunning: true,
        currentStep: 'Starting materialization...',
        percentage: 0,
        tripleCount: 0,
      });

      try {
        return await erpAPI.materialize(connectorId);
      } catch (error) {
        setProgress((prev) => ({
          ...prev,
          isRunning: false,
        }));
        throw error;
      }
    },
    onSuccess: (result) => {
      setProgress({
        isRunning: false,
        currentStep: 'Materialization completed',
        percentage: 100,
        tripleCount: result.tripleCount || 0,
      });
      queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
      toast.success(
        `Materialization completed: ${result.tripleCount || 0} triples generated`
      );
    },
    onError: (error) => {
      setProgress((prev) => ({
        ...prev,
        isRunning: false,
      }));
      const message = error.response?.data?.message || 'Materialization failed';
      toast.error(message);
    },
  });

  return {
    ...mutation,
    progress,
    setProgress,
  };
};

/**
 * Fetches materialization history for a connector
 */
export const useMaterializationHistory = (connectorId, options = {}) => {
  return useQuery({
    queryKey: ['erp', 'connectors', connectorId, 'history'],
    queryFn: async () => {
      return await erpAPI.getMaterializationHistory(connectorId);
    },
    enabled: !!connectorId,
    staleTime: 2 * 60 * 1000,
    ...options,
  });
};
