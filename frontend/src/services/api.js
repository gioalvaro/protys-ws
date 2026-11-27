import axios from 'axios';

const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

// Create axios instance with base URL
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor for auth token and error handling
apiClient.interceptors.request.use(
  (config) => {
    // Add auth token if available
    const token = localStorage.getItem('authToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for global error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Handle authentication errors
    if (error.response?.status === 401) {
      localStorage.removeItem('authToken');
      window.location.href = '/login';
    }
    console.error('API Error:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

// ==================== DASHBOARD ====================
export const dashboardAPI = {
  // GET /api/dashboard
  getStats: async () => {
    const response = await apiClient.get('/dashboard');
    return response.data;
  },

  // GET /api/dashboard/health
  getHealth: async () => {
    const response = await apiClient.get('/dashboard/health');
    return response.data;
  },

  // GET /api/dashboard/activity
  getRecentActivity: async () => {
    const response = await apiClient.get('/dashboard/activity');
    return response.data;
  },
};

// ==================== ONTOLOGY ====================
export const ontologyAPI = {
  // GET /api/ontology/modules
  getModules: async () => {
    const response = await apiClient.get('/ontology/modules');
    return response.data;
  },

  // POST /api/ontology/modules/upload
  uploadModule: async (file, name) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('name', name);

    const response = await apiClient.post('/ontology/modules/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  // GET /api/ontology/modules/{id}/classes
  getClassHierarchy: async (moduleId) => {
    const response = await apiClient.get(`/ontology/modules/${moduleId}/classes`);
    return response.data;
  },

  // GET /api/ontology/individuals/{uri}
  getIndividual: async (uri) => {
    const response = await apiClient.get(`/ontology/individuals/${uri}`);
    return response.data;
  },

  // POST /api/ontology/individuals
  createIndividual: async (classUri, properties) => {
    const response = await apiClient.post('/ontology/individuals', properties, {
      params: { classUri },
    });
    return response.data;
  },

  // PUT /api/ontology/individuals/{uri}
  updateIndividual: async (uri, properties) => {
    const response = await apiClient.put(`/ontology/individuals/${uri}`, properties);
    return response.data;
  },

  // DELETE /api/ontology/individuals/{uri}
  deleteIndividual: async (uri) => {
    const response = await apiClient.delete(`/ontology/individuals/${uri}`);
    return response.data;
  },

  // POST /api/ontology/modules/{id}/validate
  validateModule: async (moduleId) => {
    const response = await apiClient.post(`/ontology/modules/${moduleId}/validate`);
    return response.data;
  },
};

// ==================== SPARQL ====================
export const sparqlAPI = {
  // POST /api/sparql/execute
  executeQuery: async (request) => {
    const response = await apiClient.post('/sparql/execute', request);
    return response.data;
  },

  // POST /api/sparql/validate
  validateQuery: async (queryText) => {
    const response = await apiClient.post('/sparql/validate', { query: queryText });
    return response.data;
  },

  // GET /api/sparql/templates
  getTemplates: async () => {
    const response = await apiClient.get('/sparql/templates');
    return response.data;
  },

  // GET /api/sparql/templates/competency
  getCompetencyQueries: async () => {
    const response = await apiClient.get('/sparql/templates/competency');
    return response.data;
  },

  // POST /api/sparql/templates
  saveTemplate: async (template) => {
    const response = await apiClient.post('/sparql/templates', template);
    return response.data;
  },

  // POST /api/sparql/export
  exportResults: async (response, format = 'CSV') => {
    const exportResponse = await apiClient.post('/sparql/export', response, {
      params: { format },
      responseType: 'blob',
    });
    return exportResponse.data;
  },
};

// ==================== ALIGNMENT ====================
export const alignmentAPI = {
  // GET /api/alignment/rules
  getRules: async () => {
    const response = await apiClient.get('/alignment/rules');
    return response.data;
  },

  // GET /api/alignment/rules/active
  getActiveRules: async () => {
    const response = await apiClient.get('/alignment/rules/active');
    return response.data;
  },

  // POST /api/alignment/rules/upload
  uploadRules: async (file) => {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post('/alignment/rules/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  // PUT /api/alignment/rules/{id}/toggle
  toggleRule: async (ruleId, active) => {
    const response = await apiClient.put(
      `/alignment/rules/${ruleId}/toggle`,
      {},
      { params: { active } }
    );
    return response.data;
  },

  // POST /api/alignment/rules/{id}/validate
  validateRule: async (ruleId) => {
    const response = await apiClient.post(`/alignment/rules/${ruleId}/validate`);
    return response.data;
  },

  // POST /api/alignment/reasoning/execute
  executeReasoning: async () => {
    const response = await apiClient.post('/alignment/reasoning/execute');
    return response.data;
  },

  // GET /api/alignment/reasoning/stats
  getInferenceStats: async () => {
    const response = await apiClient.get('/alignment/reasoning/stats');
    return response.data;
  },
};

// ==================== ERP ====================
export const erpAPI = {
  // GET /api/erp/connectors
  getConnectors: async () => {
    const response = await apiClient.get('/erp/connectors');
    return response.data;
  },

  // GET /api/erp/connectors/{id}
  getConnector: async (connectorId) => {
    const response = await apiClient.get(`/erp/connectors/${connectorId}`);
    return response.data;
  },

  // POST /api/erp/connectors
  registerConnector: async (connector) => {
    const response = await apiClient.post('/erp/connectors', connector);
    return response.data;
  },

  // PUT /api/erp/connectors/{id}
  updateConnector: async (connectorId, connectorConfig) => {
    const response = await apiClient.put(`/erp/connectors/${connectorId}`, connectorConfig);
    return response.data;
  },

  // DELETE /api/erp/connectors/{id}
  deleteConnector: async (connectorId) => {
    const response = await apiClient.delete(`/erp/connectors/${connectorId}`);
    return response.data;
  },

  // POST /api/erp/connectors/{id}/test
  testConnection: async (connectorId) => {
    const response = await apiClient.post(`/erp/connectors/${connectorId}/test`);
    return response.data;
  },

  // POST /api/erp/connectors/{id}/introspect
  introspectSchema: async (connectorId) => {
    const response = await apiClient.post(`/erp/connectors/${connectorId}/introspect`);
    return response.data;
  },

  // POST /api/erp/connectors/{id}/materialize
  materialize: async (connectorId) => {
    const response = await apiClient.post(`/erp/connectors/${connectorId}/materialize`);
    return response.data;
  },

  // GET /api/erp/connectors/{id}/history
  getMaterializationHistory: async (connectorId) => {
    const response = await apiClient.get(`/erp/connectors/${connectorId}/history`);
    return response.data;
  },
};

// ==================== WIZARD ====================
export const wizardAPI = {
  // POST /api/wizard/step1/upload
  step1Upload: async (file, standardName) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('standardName', standardName);

    const response = await apiClient.post('/wizard/step1/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  // POST /api/wizard/step2/validate/{tempId}
  step2Validate: async (tempId) => {
    const response = await apiClient.post(`/wizard/step2/validate/${tempId}`);
    return response.data;
  },

  // POST /api/wizard/step3/alignments/{tempId}
  step3DefineAlignments: async (tempId, rules) => {
    const response = await apiClient.post(`/wizard/step3/alignments/${tempId}`, rules);
    return response.data;
  },

  // POST /api/wizard/step4/verify/{tempId}
  step4VerifyInferences: async (tempId) => {
    const response = await apiClient.post(`/wizard/step4/verify/${tempId}`);
    return response.data;
  },

  // POST /api/wizard/complete/{tempId}
  completeIncorporation: async (tempId) => {
    const response = await apiClient.post(`/wizard/complete/${tempId}`);
    return response.data;
  },
};

export default apiClient;
