import React, { useState } from 'react';
import { toast } from 'react-toastify';
import { useQueryClient } from '@tanstack/react-query';
import {
  PlusIcon,
  TrashIcon,
  CheckCircleIcon,
  XCircleIcon,
  MinusCircleIcon,
  ArrowPathIcon,
  Cog6ToothIcon,
  TableCellsIcon,
} from '@heroicons/react/24/outline';
import {
  useConnectors,
  useConnector,
  useRegisterConnector,
  useTestConnection,
  useIntrospectSchema,
  useMaterialize,
  useDeleteConnector,
} from '../../hooks/useERP';

// Contrato de la API (ERPConnectorResponse): status es REGISTERED, CONNECTED, MATERIALIZED o ERROR;
// type es ADEMPIERE, ODOO o CUSTOM; la contraseña es de sólo escritura y nunca vuelve.
export const STATUS_STYLE = {
  CONNECTED: { label: 'Connected', className: 'bg-green-100 text-green-700', textClass: 'text-green-600', Icon: CheckCircleIcon },
  MATERIALIZED: { label: 'Materialized', className: 'bg-green-100 text-green-700', textClass: 'text-green-600', Icon: CheckCircleIcon },
  REGISTERED: { label: 'Registered', className: 'bg-gray-100 text-gray-700', textClass: 'text-gray-700', Icon: MinusCircleIcon },
  ERROR: { label: 'Error', className: 'bg-red-100 text-red-700', textClass: 'text-red-600', Icon: XCircleIcon },
};
const UNKNOWN_STATUS = { label: 'Unknown', className: 'bg-gray-100 text-gray-700', textClass: 'text-gray-700', Icon: MinusCircleIcon };
export const statusStyle = (status) => STATUS_STYLE[status] || { ...UNKNOWN_STATUS, label: status || 'Unknown' };

const TYPE_LABEL = { ADEMPIERE: 'ADempiere', ODOO: 'Odoo', CUSTOM: 'Custom (JDBC)' };
const typeLabel = (type) => TYPE_LABEL[type] || type || '';

/**
 * Servidor, puerto y base de una URL JDBC, sin credenciales ni parámetros. Devuelve null si la URL no
 * tiene la forma jdbc:<driver>://<servidor>[:<puerto>]/<base>.
 */
export function describeJdbcUrl(jdbcUrl) {
  const match = /^jdbc:[a-z0-9]+:\/\/(?:[^@/?;]*@)?([^/?;:]+)(?::(\d+))?\/([^?;/]*)/i.exec(jdbcUrl || '');
  if (!match) return null;
  return { host: match[1], port: match[2] || null, database: match[3] || null };
}

// La API devuelve LocalDateTime sin zona (hora del servidor): se muestra tal cual, sin convertirla.
const formatServerTime = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : null);

const EMPTY_FORM = {
  name: '',
  type: 'CUSTOM',
  jdbcUrl: '',
  username: '',
  password: '',
  namedGraph: '',
  r2rmlMappingPath: '',
};

/** Cuerpo de POST /api/erp/connectors con los nombres del modelo ERPConnector. */
export function connectorRequestBody(form) {
  const body = {
    name: form.name.trim(),
    type: form.type,
    jdbcUrl: form.jdbcUrl.trim(),
    username: form.username.trim() || null,
    password: form.password || null,
    namedGraph: form.namedGraph.trim(),
    r2rmlMappingPath: form.r2rmlMappingPath.trim() || null,
  };
  const database = describeJdbcUrl(body.jdbcUrl)?.database;
  if (database) body.databaseName = database;
  return body;
}

function ConnectionSummary({ connector }) {
  const server = describeJdbcUrl(connector.jdbcUrl);
  return (
    <dl className="grid grid-cols-[auto,1fr] gap-x-3 gap-y-1 text-xs text-gray-600">
      <dt className="text-gray-500">Server</dt>
      <dd className="text-gray-800">{server ? `${server.host}${server.port ? `:${server.port}` : ''}` : '—'}</dd>
      <dt className="text-gray-500">Database</dt>
      <dd className="text-gray-800">{server?.database || connector.databaseName || '—'}</dd>
      <dt className="text-gray-500">User</dt>
      <dd className="text-gray-800">{connector.username || '—'}</dd>
      {connector.namedGraph && (
        <>
          <dt className="text-gray-500">Target graph</dt>
          <dd className="text-gray-800 break-all">{connector.namedGraph}</dd>
        </>
      )}
      {connector.lastMaterializationAt && (
        <>
          <dt className="text-gray-500">Last materialized</dt>
          <dd className="text-gray-800">
            {formatServerTime(connector.lastMaterializationAt)}
            {connector.tripleCount != null && ` · ${connector.tripleCount} triples`}
          </dd>
        </>
      )}
    </dl>
  );
}

function StatusBadge({ status }) {
  const style = statusStyle(status);
  return (
    <div
      data-testid="connector-status"
      data-status={status}
      className={`px-2 py-1 text-xs font-medium rounded-full flex items-center gap-1 ${style.className}`}
    >
      <style.Icon className="w-3 h-3" />
      {style.label}
    </div>
  );
}

function ErpIntegration() {
  const [showAddConnector, setShowAddConnector] = useState(false);
  const [selectedConnector, setSelectedConnector] = useState(null);
  const [formData, setFormData] = useState(EMPTY_FORM);
  const [actionResult, setActionResult] = useState(null);
  const queryClient = useQueryClient();

  const { data: connectors, isLoading: connectorsLoading } = useConnectors();
  const { data: selectedConnectorDetail, isLoading: connectorDetailLoading } = useConnector(
    selectedConnector?.id
  );

  const registerConnectorMutation = useRegisterConnector();
  const testConnectionMutation = useTestConnection();
  const introspectMutation = useIntrospectSchema();
  const materializeMutation = useMaterialize();
  const deleteConnectorMutation = useDeleteConnector();
  const busy = testConnectionMutation.isPending || introspectMutation.isPending
    || materializeMutation.isPending || deleteConnectorMutation.isPending;

  const closeConnectorForm = () => {
    if (registerConnectorMutation.isPending) return;
    setFormData((current) => ({ ...current, password: '' }));
    setShowAddConnector(false);
  };

  const handleCreateConnector = (e) => {
    e.preventDefault();
    if (!formData.name.trim() || !formData.jdbcUrl.trim() || !formData.namedGraph.trim()) {
      toast.error('Name, JDBC URL and target graph are required');
      return;
    }
    registerConnectorMutation.mutate(connectorRequestBody(formData), {
      onSuccess: () => {
        setShowAddConnector(false);
        setFormData(EMPTY_FORM);
        queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
      },
    });
  };

  const selectConnector = (connector) => {
    setSelectedConnector(connector);
    setActionResult(null);
  };

  const runAction = (mutation, label, describe) => {
    const id = selectedConnector.id;
    mutation.mutate(id, {
      onSuccess: (result) => {
        setActionResult({ ok: true, text: describe(result) });
        queryClient.invalidateQueries({ queryKey: ['erp', 'connectors'] });
      },
      onError: (error) => {
        setActionResult({ ok: false, text: `${label}: ${error.response?.data?.message || 'request failed'}` });
      },
    });
  };

  const detail = selectedConnectorDetail || selectedConnector;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 mb-1">ERP Integration</h1>
          <p className="text-sm text-gray-600">Connect and materialize external systems</p>
        </div>
        <button
          onClick={() => showAddConnector ? closeConnectorForm() : setShowAddConnector(true)}
          disabled={registerConnectorMutation.isPending}
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
                <label className="label" htmlFor="erp-name">Connector Name</label>
                <input
                  id="erp-name"
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="e.g., odoo-production"
                  className="input"
                  required
                />
              </div>
              <div>
                <label className="label" htmlFor="erp-type">ERP Type</label>
                <select
                  id="erp-type"
                  value={formData.type}
                  onChange={(e) => setFormData({ ...formData, type: e.target.value })}
                  className="input"
                >
                  <option value="ADEMPIERE">ADempiere</option>
                  <option value="ODOO">Odoo</option>
                  <option value="CUSTOM">Custom (JDBC)</option>
                </select>
              </div>
            </div>

            <div>
              <label className="label" htmlFor="erp-jdbc-url">JDBC URL</label>
              <input
                id="erp-jdbc-url"
                type="text"
                value={formData.jdbcUrl}
                onChange={(e) => setFormData({ ...formData, jdbcUrl: e.target.value })}
                placeholder="jdbc:postgresql://host:5432/database"
                className="input font-mono"
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="label" htmlFor="erp-username">Username</label>
                <input
                  id="erp-username"
                  type="text"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  placeholder="Read-only (SELECT-only) database user"
                  className="input"
                />
              </div>
              <div>
                <label className="label" htmlFor="erp-password">Password</label>
                <input
                  id="erp-password"
                  type="password"
                  autoComplete="new-password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  placeholder="Not shown after saving"
                  className="input"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="label" htmlFor="erp-graph">Target graph</label>
                <input
                  id="erp-graph"
                  type="text"
                  value={formData.namedGraph}
                  onChange={(e) => setFormData({ ...formData, namedGraph: e.target.value })}
                  placeholder="http://w3id.org/protys/erp/<source>"
                  className="input font-mono"
                  required
                />
              </div>
              <div>
                <label className="label" htmlFor="erp-mapping">R2RML mapping path (on the server)</label>
                <input
                  id="erp-mapping"
                  type="text"
                  value={formData.r2rmlMappingPath}
                  onChange={(e) => setFormData({ ...formData, r2rmlMappingPath: e.target.value })}
                  placeholder="/opt/protys/mappings/source.r2rml.ttl"
                  className="input font-mono"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-4">
              <button
                type="button"
                onClick={closeConnectorForm}
                disabled={registerConnectorMutation.isPending}
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
                  data-testid="connector-card"
                  onClick={() => selectConnector(connector)}
                  className={`p-4 border rounded-lg cursor-pointer transition-colors duration-150 ${
                    selectedConnector?.id === connector.id
                      ? 'border-protys-500 bg-protys-50'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-start justify-between mb-2">
                    <div>
                      <h3 className="font-semibold text-gray-900">{connector.name}</h3>
                      <p className="text-sm text-gray-600">{typeLabel(connector.type)}</p>
                    </div>
                    <StatusBadge status={connector.status} />
                  </div>
                  <ConnectionSummary connector={connector} />
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
            ) : detail ? (
              <>
                <div className="space-y-2 text-sm">
                  <div>
                    <p className="text-gray-600">Type</p>
                    <p className="font-medium text-gray-900">{typeLabel(detail.type)}</p>
                  </div>
                  <div>
                    <p className="text-gray-600">Status</p>
                    <p className={`font-medium ${statusStyle(detail.status).textClass}`}>{statusStyle(detail.status).label}</p>
                  </div>
                  {detail.r2rmlMappingPath && (
                    <div>
                      <p className="text-gray-600">Mapping</p>
                      <p className="font-medium text-gray-900 break-all">{detail.r2rmlMappingPath}</p>
                    </div>
                  )}
                  {detail.tripleCount != null && (
                    <div>
                      <p className="text-gray-600">Triples Materialized</p>
                      <p className="font-medium text-gray-900">{detail.tripleCount}</p>
                    </div>
                  )}
                </div>

                {actionResult && (
                  <p
                    role="status"
                    className={`text-sm rounded-md p-2 ${actionResult.ok ? 'bg-green-50 text-green-800' : 'bg-red-50 text-red-800'}`}
                  >
                    {actionResult.text}
                  </p>
                )}

                <div className="space-y-2 pt-4 border-t border-gray-200">
                  <button
                    onClick={() => runAction(testConnectionMutation, 'Connection test',
                      (r) => (r.connected ? 'Connection test passed' : 'Connection test failed'))}
                    disabled={busy}
                    className="btn-secondary w-full flex justify-center gap-2 disabled:opacity-50"
                  >
                    <ArrowPathIcon className="w-4 h-4" />
                    {testConnectionMutation.isPending ? 'Testing...' : 'Test Connection'}
                  </button>
                  <button
                    onClick={() => runAction(introspectMutation, 'Schema inspection',
                      (r) => `Schema inspected: ${Object.keys(r || {}).length} tables`)}
                    disabled={busy}
                    className="btn-secondary w-full flex justify-center gap-2 disabled:opacity-50"
                  >
                    <TableCellsIcon className="w-4 h-4" />
                    {introspectMutation.isPending ? 'Inspecting...' : 'Inspect Schema'}
                  </button>
                  <button
                    onClick={() => runAction(materializeMutation, 'Materialization',
                      (r) => (r.status === 'SUCCESS'
                        ? `Materialized: ${r.tripleCount ?? 0} triples in ${r.namedGraph || detail.namedGraph}`
                        : `Materialization ${String(r.status || 'not confirmed').toLowerCase()}: no data was published`))}
                    disabled={busy}
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
                            setActionResult(null);
                          },
                        });
                      }
                    }}
                    disabled={busy}
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
            <p className="text-sm text-gray-600">Register the ERP database with a read-only (SELECT-only) user</p>
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
