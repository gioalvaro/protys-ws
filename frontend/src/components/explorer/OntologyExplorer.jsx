import React, { useState } from 'react';
import { toast } from 'react-toastify';
import {
  TrashIcon,
  PencilIcon,
  UserPlusIcon,
  ArrowLeftIcon,
} from '@heroicons/react/24/outline';
import {
  useModules,
  useClassHierarchy,
  useIndividual,
  useDeleteIndividual,
  useCreateIndividual,
} from '../../hooks/useModules';
import ClassTree from './ClassTree';

function OntologyExplorer() {
  const [selectedModule, setSelectedModule] = useState(null);
  const [selectedNode, setSelectedNode] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showAddIndividual, setShowAddIndividual] = useState(false);

  const { data: modules, isLoading: modulesLoading } = useModules();

  const { data: hierarchy, isLoading: hierarchyLoading } = useClassHierarchy(
    selectedModule?.id
  );

  const { data: individual, isLoading: individualLoading } = useIndividual(
    selectedNode?.type === 'individual' ? selectedNode.uri : null
  );

  const deleteIndividualMutation = useDeleteIndividual();

  const handleDeleteIndividual = async (uri) => {
    if (!window.confirm('Are you sure you want to delete this individual?')) return;
    try {
      deleteIndividualMutation.mutate(uri, {
        onSuccess: () => {
          setSelectedNode(null);
        },
      });
    } catch (error) {
      toast.error('Failed to delete individual');
    }
  };

  return (
    <div className="h-full flex gap-6">
      {/* Left Panel */}
      <div className="w-80 flex flex-col gap-4">
        {/* Module Selector */}
        <div className="card p-4">
          <label className="label">Select Ontology Module</label>
          {modulesLoading ? (
            <div className="h-10 bg-gray-200 rounded skeleton"></div>
          ) : (
            <select
              value={selectedModule?.id || ''}
              onChange={(e) => {
                const module = modules?.find(m => m.id === e.target.value);
                setSelectedModule(module);
                setSelectedNode(null);
              }}
              className="input"
            >
              <option value="">-- Select a module --</option>
              {modules?.map((module) => (
                <option key={module.id} value={module.id}>
                  {module.name}
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Search */}
        {selectedModule && (
          <div className="card p-4">
            <label className="label">Search Classes</label>
            <input
              type="text"
              placeholder="Filter class hierarchy..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="input"
            />
          </div>
        )}

        {/* Class Tree */}
        {selectedModule && (
          <div className="card p-4 flex-1 overflow-y-auto">
            <h3 className="text-sm font-semibold text-gray-900 mb-3">Class Hierarchy</h3>
            <ClassTree
              data={hierarchy}
              onSelect={setSelectedNode}
              selectedUri={selectedNode?.uri}
              loading={hierarchyLoading}
              searchQuery={searchQuery}
            />
          </div>
        )}

        {!selectedModule && (
          <div className="card p-4 flex-1 flex items-center justify-center text-gray-500">
            <p className="text-sm">Select a module to browse classes</p>
          </div>
        )}
      </div>

      {/* Right Panel */}
      <div className="flex-1 flex flex-col gap-4">
        {selectedNode ? (
          <>
            {/* Back Button */}
            <button
              onClick={() => setSelectedNode(null)}
              className="flex items-center gap-2 text-protys-600 hover:text-protys-700 text-sm font-medium"
            >
              <ArrowLeftIcon className="w-4 h-4" />
              Back to hierarchy
            </button>

            {/* Detail Panel */}
            <div className="card p-6 flex-1 overflow-y-auto">
              {selectedNode.type === 'class' ? (
                <ClassDetail
                  node={selectedNode}
                  onAddIndividual={() => setShowAddIndividual(true)}
                />
              ) : (
                <IndividualDetail
                  individual={individual}
                  loading={individualLoading}
                  onDelete={() => handleDeleteIndividual(selectedNode.uri)}
                />
              )}
            </div>
          </>
        ) : (
          <div className="card p-6 flex-1 flex items-center justify-center text-gray-500">
            <p className="text-sm">Select a class or individual to view details</p>
          </div>
        )}
      </div>

      {/* Add Individual Modal */}
      {showAddIndividual && selectedNode && (
        <AddIndividualModal
          classUri={selectedNode.uri}
          classLabel={selectedNode.label || selectedNode.uri}
          onClose={() => setShowAddIndividual(false)}
          onSuccess={() => {
            setShowAddIndividual(false);
            toast.success('Individual created successfully');
          }}
        />
      )}
    </div>
  );
}

function ClassDetail({ node, onAddIndividual }) {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">{node.label || 'Unnamed Class'}</h2>
        <p className="text-sm text-gray-600 font-mono break-all">{node.uri}</p>
      </div>

      {node.description && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Description</h3>
          <p className="text-sm text-gray-700">{node.description}</p>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-protys-50 rounded-lg p-4">
          <p className="text-xs text-protys-600 uppercase tracking-wide font-semibold">Superclasses</p>
          <p className="text-2xl font-bold text-protys-700 mt-2">{node.superclasses?.length || 0}</p>
        </div>
        <div className="bg-semantic-50 rounded-lg p-4">
          <p className="text-xs text-semantic-600 uppercase tracking-wide font-semibold">Subclasses</p>
          <p className="text-2xl font-bold text-semantic-700 mt-2">{node.subclasses?.length || 0}</p>
        </div>
      </div>

      {node.superclasses && node.superclasses.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Superclasses</h3>
          <div className="space-y-1">
            {node.superclasses.map((sc, idx) => (
              <div key={idx} className="text-sm text-gray-700 px-3 py-2 bg-gray-50 rounded">
                {sc.label || sc.uri.split('/').pop()}
              </div>
            ))}
          </div>
        </div>
      )}

      {node.properties && node.properties.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Properties</h3>
          <div className="space-y-1">
            {node.properties.map((prop, idx) => (
              <div key={idx} className="text-sm text-gray-700 px-3 py-2 bg-gray-50 rounded flex justify-between">
                <span>{prop.label || prop.name}</span>
                <span className="text-xs text-gray-500">{prop.type}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <button
        onClick={onAddIndividual}
        className="btn-primary w-full flex justify-center gap-2"
      >
        <UserPlusIcon className="w-4 h-4" />
        Add Individual
      </button>
    </div>
  );
}

function IndividualDetail({ individual, loading, onDelete }) {
  if (loading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-12 bg-gray-200 rounded skeleton"></div>
        ))}
      </div>
    );
  }

  if (!individual) {
    return <p className="text-gray-500">Loading individual details...</p>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">
          {individual.label || 'Unnamed Individual'}
        </h2>
        <p className="text-sm text-gray-600 font-mono break-all">{individual.uri}</p>
      </div>

      {individual.types && individual.types.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Types</h3>
          <div className="flex flex-wrap gap-2">
            {individual.types.map((type, idx) => (
              <span key={idx} className="badge badge-primary">
                {type.label || type.uri.split('/').pop()}
              </span>
            ))}
          </div>
        </div>
      )}

      {individual.dataProperties && Object.keys(individual.dataProperties).length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Data Properties</h3>
          <div className="space-y-2">
            {Object.entries(individual.dataProperties).map(([key, values]) => (
              <div key={key} className="px-3 py-2 bg-gray-50 rounded">
                <p className="text-xs font-medium text-gray-600">{key}</p>
                <div className="mt-1 space-y-1">
                  {Array.isArray(values) ? (
                    values.map((val, idx) => (
                      <p key={idx} className="text-sm text-gray-900">
                        {String(val)}
                      </p>
                    ))
                  ) : (
                    <p className="text-sm text-gray-900">{String(values)}</p>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {individual.objectProperties && Object.keys(individual.objectProperties).length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Object Properties</h3>
          <div className="space-y-2">
            {Object.entries(individual.objectProperties).map(([key, values]) => (
              <div key={key} className="px-3 py-2 bg-gray-50 rounded">
                <p className="text-xs font-medium text-gray-600">{key}</p>
                <div className="mt-1 space-y-1">
                  {Array.isArray(values) ? (
                    values.map((val, idx) => (
                      <p key={idx} className="text-sm text-protys-600 cursor-pointer hover:underline">
                        {val.label || val.uri.split('/').pop()}
                      </p>
                    ))
                  ) : (
                    <p className="text-sm text-protys-600 cursor-pointer hover:underline">
                      {values.label || values.uri.split('/').pop()}
                    </p>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex gap-2 pt-4 border-t border-gray-200">
        <button className="btn-secondary flex-1 flex justify-center gap-2">
          <PencilIcon className="w-4 h-4" />
          Edit
        </button>
        <button onClick={onDelete} className="btn-danger flex-1 flex justify-center gap-2">
          <TrashIcon className="w-4 h-4" />
          Delete
        </button>
      </div>
    </div>
  );
}

function AddIndividualModal({ classUri, classLabel, onClose, onSuccess }) {
  const [formData, setFormData] = useState({
    uri: '',
    label: '',
  });
  const createIndividualMutation = useCreateIndividual();

  const handleSubmit = async (e) => {
    e.preventDefault();
    createIndividualMutation.mutate(
      {
        classUri,
        properties: {
          uri: formData.uri,
          label: formData.label,
        },
      },
      {
        onSuccess: () => {
          onSuccess();
        },
      }
    );
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-lg p-6 w-96">
        <h2 className="text-lg font-bold text-gray-900 mb-4">Add Individual to {classLabel}</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Individual URI</label>
            <input
              type="text"
              value={formData.uri}
              onChange={(e) => setFormData({ ...formData, uri: e.target.value })}
              placeholder="http://example.org/individual/1"
              className="input"
              required
            />
          </div>
          <div>
            <label className="label">Label (Optional)</label>
            <input
              type="text"
              value={formData.label}
              onChange={(e) => setFormData({ ...formData, label: e.target.value })}
              placeholder="Display name"
              className="input"
            />
          </div>
          <div className="flex gap-3 pt-4">
            <button type="button" onClick={onClose} className="btn-secondary flex-1">
              Cancel
            </button>
            <button
              type="submit"
              disabled={createIndividualMutation.isPending || !formData.uri}
              className="btn-primary flex-1 disabled:opacity-50"
            >
              {createIndividualMutation.isPending ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default OntologyExplorer;
