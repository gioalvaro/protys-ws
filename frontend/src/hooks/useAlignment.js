import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { alignmentAPI } from '../services/api';

/**
 * Fetches all alignment rules
 */
export const useAlignmentRules = (options = {}) => {
  return useQuery({
    queryKey: ['alignment', 'rules'],
    queryFn: async () => {
      return await alignmentAPI.getRules();
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...options,
  });
};

/**
 * Fetches only active (enabled) alignment rules
 */
export const useActiveRules = (options = {}) => {
  return useQuery({
    queryKey: ['alignment', 'rules', 'active'],
    queryFn: async () => {
      return await alignmentAPI.getActiveRules();
    },
    staleTime: 5 * 60 * 1000,
    ...options,
  });
};

/**
 * Mutation for uploading SWRL rules file
 */
export const useUploadRules = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (file) => {
      return await alignmentAPI.uploadRules(file);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alignment', 'rules'] });
      toast.success('Rules file uploaded successfully');
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to upload rules';
      toast.error(message);
    },
  });
};

/**
 * Mutation for toggling rule activation state
 */
export const useToggleRule = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ ruleId, active }) => {
      return await alignmentAPI.toggleRule(ruleId, active);
    },
    onSuccess: (rule) => {
      queryClient.invalidateQueries({ queryKey: ['alignment', 'rules'] });
      queryClient.invalidateQueries({ queryKey: ['alignment', 'rules', 'active'] });
      const action = rule.active ? 'enabled' : 'disabled';
      toast.success(`Rule ${action}`);
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Failed to toggle rule';
      toast.error(message);
    },
  });
};

/**
 * Mutation for validating alignment rule
 */
export const useValidateRule = () => {
  return useMutation({
    mutationFn: async (ruleId) => {
      return await alignmentAPI.validateRule(ruleId);
    },
    onSuccess: (result) => {
      if (result.valid) {
        toast.success('Rule is valid');
      } else {
        toast.warning('Rule validation failed');
      }
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Rule validation failed';
      toast.error(message);
    },
  });
};

/**
 * Mutation for executing reasoning
 */
export const useExecuteReasoning = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      return await alignmentAPI.executeReasoning();
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['alignment', 'stats'] });
      toast.success(
        `Reasoning completed: ${result.newInferences || 0} new inferences generated`
      );
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Reasoning execution failed';
      toast.error(message);
    },
  });
};

/**
 * Fetches inference statistics
 */
export const useInferenceStats = (options = {}) => {
  return useQuery({
    queryKey: ['alignment', 'stats'],
    queryFn: async () => {
      return await alignmentAPI.getInferenceStats();
    },
    staleTime: 2 * 60 * 1000, // 2 minutes
    ...options,
  });
};
