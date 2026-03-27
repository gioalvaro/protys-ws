import React, { useState } from 'react';
import { toast } from 'react-toastify';
import { useQueryClient } from '@tanstack/react-query';
import {
  PlusIcon,
  TrashIcon,
  CheckCircleIcon,
  XCircleIcon,
  ArrowPathIcon,
  Cog6ToothIcon,
} from '@heroicons/react/24/outline';
import {
  useConnectors,
  useConnector,
  useRegisterConnector,
  useTestConnection,
  useMaterialize,
  useDeleteConnector,
} from '../../hooks/useERP';

function ErpIntegration() {
  const [showAddConnector, setShowAddConnector] = useState(false);
  const [selectedConnector, setSelectedConnector] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    type: 'sap',
    connectionString: '',
    username: '',
    password: '',
  });
  const queryClient = useQueryClient();

  const { data: connectors, isLoading: connectorsLoading } = useConnectors();
  const { data: selectedConnectorDetail, isLoading: connectorDetailLoading } = useConnector(
    selectedConnector?.id
  );

  const registerConnectorMutation = useRegisterConnector();
  const testConnectionMutation = useTestConnection();
  const materializeMutation = useMaterialize();
  const deleteConnectorMutation = useDeleteConnector();

  const handleCreateConnector = (e) => {
    e.preventDefault();
    if (!formData.name || !formData.connectionString) {
      toast.error('Please fill in all required fields');
      return;
    }
    registerConnectorMutation.mutate(formData, {
      onSuccess: () => {
        toast.success('Connector created successfully');
        setShowAddConnector(false);
        setFormData({
          name: '',
          type: 'sap',
          connectionString: '',
          username: '',
          password: '',
        });
        queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
      },
    });
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 mb-1">ERP Integration</h1>
          <p className="text-sm text-gray-600">Connect and materialize external systems</p>
        </div>
        <button
          onClick={() => setShowAddConnector(!showAddConnector)}
          className="btn-primary flex items-center gap-2"
        >
          <PlusIcon className="w-4 h-4" />
          New Connector
        </button>
      </div>

      {/* Add Connector Form */}
      {showAddConnector && (
        <div className="card p-6 space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Create ERP Connector</h2>
          <form onSubmit={handleCreateConnector} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="label">Connector Name</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g., SAP Production"
                  className="input"
                  required
                />
              </div>
              <div>
                <label className="label">ERP Type</label>
                <select
                  value={formData.type}
                  onChange={(e) => setFormData({ ...formData, type: e.target.value })}
                  className="input"
                >
                  <option value="sap">SAP</option>
                  <option value="oracle">Oracle EBS</option>
                  <option value="dynamics">Microsoft Dynamics</option>
                  <option value="salesforce">Salesforce</option>
                  <option value="custom">Custom SQL</option>
                </select>
              </div>
            </div>

            <div>
              <label className="label">Connection String</label>
              <input
                type="text"
                value={formData.connectionString}
                onChange={(e) => setFormData({ ...formData, connectionString: e.target.value })}
                placeholder="jdbc:mysql://host:3306/database or http://endpoint"
                className="input"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="label">Username</label>
                <input
                  type="text"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  placeholder="Database user"
                  className="input"
                />
              </div>
              <div>
                <label className="label">Password</label>
                <input
                  type="password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  placeholder="Database password"
                  className="input"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-4">
              <button
                type="button"
                onClick={() => setShowAddConnector(false)}
                className="btn-secondary flex-1"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={registerConnectorMutation.isPending}
                className="btn-primary flex-1 disabled:opacity-50"
              >
                {registerConnectorMutation.isPending ? 'Creating...' : 'Create'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Connectors List */}
        <div className="lg:col-span-2 card p-6 space-y-4">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Connected Systems</h2>

          {connectorsLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 bg-gray-200 rounded skeleton"></div>
              ))}
            </div>
          ) : connectors && connectors.length > 0 ? (
            <div className="space-y-3">
              {connectors.map((connector) => (
                <div
                  key={connector.id}
                  onClick={() => setSelectedConnector(connector)}
                  className={`p-4 border rounded-lg cursor-pointer transition-colors duration-150 ${
                    selectedConnector?.id === connector.id
                      ? 'border-protys-500 bg-protys-50'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-start justify-between mb-2">
                    <div>
                      <h3 className="font-semibold text-gray-900">{connector.name}</h3>
                      <p className="text-sm text-gray-600">{connector.type.toUpperCase()}</p>
                    </div>
                    <div
                      className={`px-2 py-1 text-xs font-medium rounded-full flex items-center gap-1 ${
                        connector.status === 'connected'
                          ? 'bg-green-100 text-green-700'
                          : 'bg-red-100 text-red-700'
                      }`}
                    >
                      {connector.status === 'connected' ? (
                        <CheckCircleIcon className="w-3 h-3" />
                      ) : (
                        <XCircleIcon className="w-3 h-3" />
                      )}
                      {connector.status}
                    </div>
                  </div>

                  <div className="text-xs text-gray-500">
                    <p>Connection: {connector.connectionString}</p>
                    {connector.lastMaterialized && (
                      <p>Last materialized: {new Date(connector.lastMaterialized).toLocaleDateString()}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center py-8 text-gray-500">
              <p className="text-sm">No connectors configured</p>
            </div>
          )}
        </div>

        {/* Connector Details */}
        {selectedConnector && (
          <div className="card p-6 space-y-4 h-fit">
            <h2 className="text-lg font-semibold text-gray-900">Details</h2>

            {connectorDetailLoading ? (
              <div className="space-y-3">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="h-8 bg-gray-200 rounded skeleton"></div>
                ))}
              </div>
            ) : selectedConnectorDetail ? (
              <>
                <div className="space-y-2 text-sm">
                  <div>
                    <p className="text-gray-600">Type</p>
                    <p className="font-medium text-gray-900">{selectedConnectorDetail.type.toUpperCase()}</p>
                  </div>
                  <div>
                    <p className="text-gray-600">Status</p>
                    <p
                      className={`font-medium ${
                        selectedConnectorDetail.status === 'connected'
                          ? 'text-green-600'
                          : 'text-red-600'
                      }`}
                    >
                      {selectedConnectorDetail.status}
                    </p>
                  </div>
                  {selectedConnectorDetail.statistics && (
                    <>
                      <div>
                        <p className="text-gray-600">Tables</p>
                        <p className="font-medium text-gray-900">
                          {selectedConnectorDetail.statistics.tableCount}
                        </p>
                      </div>
                      <div>
                        <p className="text-gray-600">Records</p>
                        <p className="font-medium text-gray-900">
                          {selectedConnectorDetail.statistics.recordCount}
                        </p>
                      </div>
                      <div>
                        <p className="text-gray-600">Triples Materialized</p>
                        <p className="font-medium text-gray-900">
                          {selectedConnectorDetail.statistics.tripleCount}
                        </p>
                      </div>
                    </>
                  )}
                </div>

                <div className="space-y-2 pt-4 border-t border-gray-200">
                  <button
                    onClick={() => testConnectionMutation.mutate(selectedConnector.id)}
                    disabled={testConnectionMutation.isPending}
                    className="btn-secondary w-full flex justify-center gap-2 disabled:opacity-50"
                  >
                    <ArrowPathIcon className="w-4 h-4" />
                    {testConnectionMutation.isPending ? 'Testing...' : 'Test Connection'}
                  </button>
                  <button
                    onClick={() =>
                      materializeMutation.mutate(selectedConnector.id, {
                        onSuccess: () => {
                          queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
                        },
                      })
                    }
                    disabled={materializeMutation.isPending}
                    className="btn-success w-full flex justify-center gap-2 disabled:opacity-50"
                  >
                    <Cog6ToothIcon className="w-4 h-4" />
                    {materializeMutation.isPending ? 'Materializing...' : 'Materialize'}
                  </button>
                  <button
                    onClick={() => {
                      if (window.confirm('Delete this connector?')) {
                        deleteConnectorMutation.mutate(selectedConnector.id, {
                          onSuccess: () => {
                            setSelectedConnector(null);
                          },
                        });
                      }
                    }}
                    disabled={deleteConnectorMutation.isPending}
                    className="btn-danger w-full flex justify-center gap-2 disabled:opacity-50"
                  >
                    <TrashIcon className="w-4 h-4" />
                    Delete
                  </button>
                </div>
              </>
            ) : null}
          </div>
        )}
      </div>

      {/* Integration Guide */}
      <div className="card p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Integration Guide</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <h3 className="font-medium text-gray-900 mb-2">1. Connect System</h3>
            <p className="text-sm text-gray-600">Create a new connector with your ERP credentials</p>
          </div>
          <div>
            <h3 className="font-medium text-gray-900 mb-2">2. Test Connection</h3>
            <p className="text-sm text-gray-600">Verify the connection to your external system</p>
          </div>
          <div>
            <h3 className="font-medium text-gray-900 mb-2">3. Materialize Data</h3>
            <p className="text-sm text-gray-600">Convert relational data to semantic triples</p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ErpIntegration;
