import { useQuery } from '@tanstack/react-query';
import { dashboardAPI } from '../services/api';

/**
 * Fetches dashboard statistics with 30-second refetch interval
 * Includes module counts, class counts, individual counts, triple counts, etc.
 */
export const useDashboardStats = (options = {}) => {
  return useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: async () => {
      return await dashboardAPI.getStats();
    },
    staleTime: 30 * 1000, // 30 seconds
    refetchInterval: 30 * 1000, // Refetch every 30 seconds
    ...options,
  });
};

/**
 * Fetches system health information with 60-second refetch interval
 * Includes service availability status
 */
export const useSystemHealth = (options = {}) => {
  return useQuery({
    queryKey: ['dashboard', 'health'],
    queryFn: async () => {
      return await dashboardAPI.getHealth();
    },
    staleTime: 60 * 1000, // 60 seconds
    refetchInterval: 60 * 1000, // Refetch every 60 seconds
    ...options,
  });
};

/**
 * Fetches recent system activity log
 */
export const useRecentActivity = (options = {}) => {
  return useQuery({
    queryKey: ['dashboard', 'activity'],
    queryFn: async () => {
      return await dashboardAPI.getRecentActivity();
    },
    staleTime: 15 * 1000, // 15 seconds
    refetchInterval: 15 * 1000, // Refetch every 15 seconds
    ...options,
  });
};
