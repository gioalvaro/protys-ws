import React, { useState } from 'react';
import { toast } from 'react-toastify';
import {
  TrashIcon,
  PencilIcon,
  UserPlusIcon,
  ArrowLeftIcon,
  ArrowUturnLeftIcon,
} from '@heroicons/react/24/outline';
import {
  useModules,
  useClassHierarchy,
  useClassIndividuals,
  useIndividual,
  useCreateIndividual,
  useUpdateIndividual,
  useDeleteIndividual,
  useUndoIndividualEdit,
  validationSummary,
} from '../../hooks/useModules';
import ClassTree from './ClassTree';

const OPERATION_TEXT = { CREATE: 'Individual created', UPDATE: 'Individual updated', DELETE: 'Individual deleted', UNDO: 'Edit undone' };

/** Un único aviso por edición, con el resultado de la validación que la API ejecutó después. */
export function notifyEdit(result) {
  const text = `${OPERATION_TEXT[result.operation] || result.operation}: ${validationSummary(result.validation)}`;
  if (result.validation?.status === 'CONSISTENT') toast.success(text);
  else toast.warning(text);
}

function OntologyExplorer() {
  const [selectedModule, setSelectedModule] = useState(null);
  const [selectedNode, setSelectedNode] = useState(null);
  const [selectedIndividual, setSelectedIndividual] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showAddIndividual, setShowAddIndividual] = useState(false);
  const [lastEdit, setLastEdit] = useState(null);

  const { data: modules, isLoading: modulesLoading } = useModules();

  const { data: hierarchy, isLoading: hierarchyLoading } = useClassHierarchy(
    selectedModule?.id
  );

  const undoMutation = useUndoIndividualEdit();

  const recordEdit = (result) => {
    notifyEdit(result);
    setLastEdit(result);
  };

  const selectClass = (node) => {
    setSelectedNode(node);
    setSelectedIndividual(null);
  };

  const undoLastEdit = () => {
    undoMutation.mutate(lastEdit.editId, {
      onSuccess: (result) => {
        notifyEdit(result);
        setLastEdit(result);
        setSelectedIndividual(null);
      },
    });
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
                setSelectedIndividual(null);
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
              onSelect={selectClass}
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
        {lastEdit && (
          <EditResult result={lastEdit} undoing={undoMutation.isPending} onUndo={undoLastEdit} onClose={() => setLastEdit(null)} />
        )}
        {selectedNode ? (
          <>
            <button
              onClick={() => (selectedIndividual ? setSelectedIndividual(null) : setSelectedNode(null))}
              className="flex items-center gap-2 text-protys-600 hover:text-protys-700 text-sm font-medium"
            >
              <ArrowLeftIcon className="w-4 h-4" />
              {selectedIndividual ? `Back to ${selectedNode.label || 'class'}` : 'Back to hierarchy'}
            </button>

            <div className="card p-6 flex-1 overflow-y-auto">
              {selectedIndividual ? (
                <IndividualDetail
                  uri={selectedIndividual}
                  onEdited={recordEdit}
                  onDeleted={(result) => { recordEdit(result); setSelectedIndividual(null); }}
                />
              ) : (
                <ClassDetail
                  node={selectedNode}
                  onAddIndividual={() => setShowAddIndividual(true)}
                  onSelectIndividual={setSelectedIndividual}
                />
              )}
            </div>
          </>
        ) : (
          <div className="card p-6 min-h-[10rem] flex items-center justify-center text-gray-500 lg:sticky lg:top-0">
            <p className="text-sm">Select a class or individual to view details</p>
          </div>
        )}
      </div>

      {showAddIndividual && selectedNode && (
        <AddIndividualModal
          classUri={selectedNode.uri}
          classLabel={selectedNode.label || selectedNode.uri}
          onClose={() => setShowAddIndividual(false)}
          onSuccess={(result) => {
            setShowAddIndividual(false);
            recordEdit(result);
          }}
        />
      )}
    </div>
  );
}

/** Resultado de la última edición: validación posterior y opción de deshacer. */
export function EditResult({ result, undoing, onUndo, onClose }) {
  const status = result.validation?.status;
  const ok = status === 'CONSISTENT';
  const tone = ok ? 'border-green-200 bg-green-50' : status === 'INCONSISTENT' ? 'border-red-300 bg-red-50' : 'border-amber-300 bg-amber-50';
  return (
    <div className={`card p-4 border ${tone}`} role="status" data-testid="edit-result">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-gray-900">
            {OPERATION_TEXT[result.operation] || result.operation}
            {result.moduleName ? ` in ${result.moduleName}` : ''}
          </p>
          <p className="text-xs text-gray-600 font-mono break-all">{result.uri}</p>
          <p className="text-sm text-gray-800 mt-1">{validationSummary(result.validation)}</p>
          {!ok && result.validation?.violations?.length > 0 && (
            <ul className="mt-1 text-xs text-gray-700 list-disc pl-5">
              {result.validation.violations.slice(0, 5).map((v, i) => <li key={i} className="break-all">{v}</li>)}
            </ul>
          )}
          {!ok && result.editId && (
            <p className="text-xs text-gray-700 mt-1">The edit was saved. You can undo it to restore the previous state of the module.</p>
          )}
        </div>
        <div className="flex gap-2 shrink-0">
          {result.editId && (
            <button onClick={onUndo} disabled={undoing} className={`${ok ? 'btn-secondary' : 'btn-danger'} btn-sm flex items-center gap-1`}>
              <ArrowUturnLeftIcon className="w-4 h-4" />
              {undoing ? 'Undoing...' : 'Undo'}
            </button>
          )}
          <button onClick={onClose} className="btn-ghost btn-sm">Dismiss</button>
        </div>
      </div>
    </div>
  );
}

function ClassDetail({ node, onAddIndividual, onSelectIndividual }) {
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

      <ClassIndividuals classUri={node.uri} onSelect={onSelectIndividual} />

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

/** Individuos de la clase seleccionada; al elegir uno se abre su detalle. */
function ClassIndividuals({ classUri, onSelect }) {
  const { data: individuals, isLoading } = useClassIndividuals(classUri);
  return (
    <div>
      <h3 className="text-sm font-semibold text-gray-900 mb-2">
        Individuals{individuals ? ` (${individuals.length})` : ''}
      </h3>
      {isLoading ? (
        <p className="text-sm text-gray-500">Loading individuals...</p>
      ) : individuals && individuals.length > 0 ? (
        <ul className="space-y-1 max-h-64 overflow-y-auto" data-testid="class-individuals">
          {individuals.map((ind) => (
            <li key={ind.uri}>
              <button
                onClick={() => onSelect(ind.uri)}
                className="w-full text-left text-sm px-3 py-2 bg-gray-50 hover:bg-protys-50 rounded"
              >
                <span className="text-gray-900">{ind.label || ind.localName || ind.uri}</span>
                <span className="block text-xs text-gray-500 font-mono break-all">{ind.uri}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-gray-500">No individuals of this class.</p>
      )}
    </div>
  );
}

function shortName(iri) {
  const cut = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
  return cut >= 0 ? iri.slice(cut + 1) : iri;
}

/** Detalle de un individuo: ver, modificar la etiqueta y borrar (con confirmación en la página). */
function IndividualDetail({ uri, onEdited, onDeleted }) {
  const { data: individual, isLoading, isError } = useIndividual(uri);
  const updateMutation = useUpdateIndividual();
  const deleteMutation = useDeleteIndividual();
  const [editing, setEditing] = useState(false);
  const [label, setLabel] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (isLoading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3].map((i) => <div key={i} className="h-12 bg-gray-200 rounded skeleton"></div>)}
      </div>
    );
  }
  if (isError || !individual) return <p className="text-gray-500">Individual not found.</p>;

  const save = (e) => {
    e.preventDefault();
    updateMutation.mutate({ uri, body: { label } }, {
      onSuccess: (result) => { setEditing(false); onEdited(result); },
    });
  };
  const properties = Object.entries(individual.properties || {});

  return (
    <div className="space-y-6">
      <div>
        {editing ? (
          <form onSubmit={save} className="flex gap-2" aria-label="Edit individual">
            <input
              className="input flex-1"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              placeholder="Label (rdfs:label)"
              aria-label="Label"
              autoFocus
            />
            <button type="submit" disabled={updateMutation.isPending} className="btn-primary">
              {updateMutation.isPending ? 'Saving...' : 'Save'}
            </button>
            <button type="button" onClick={() => setEditing(false)} className="btn-secondary">Cancel</button>
          </form>
        ) : (
          <h2 className="text-2xl font-bold text-gray-900 mb-2">{individual.label || shortName(individual.uri)}</h2>
        )}
        <p className="text-sm text-gray-600 font-mono break-all mt-1">{individual.uri}</p>
      </div>

      {individual.types && individual.types.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Types</h3>
          <div className="flex flex-wrap gap-2">
            {individual.types.map((type) => (
              <span key={type} className="badge badge-primary" title={type}>{shortName(type)}</span>
            ))}
          </div>
        </div>
      )}

      {properties.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Properties</h3>
          <div className="space-y-2">
            {properties.map(([key, values]) => (
              <div key={key} className="px-3 py-2 bg-gray-50 rounded">
                <p className="text-xs font-medium text-gray-600 break-all" title={key}>{shortName(key)}</p>
                {values.map((value, idx) => (
                  <p key={idx} className="text-sm text-gray-900 break-all">{value}</p>
                ))}
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex gap-2 pt-4 border-t border-gray-200">
        {confirmDelete ? (
          <>
            <button
              onClick={() => deleteMutation.mutate(uri, { onSuccess: (result) => onDeleted(result) })}
              disabled={deleteMutation.isPending}
              className="btn-danger flex-1 flex justify-center gap-2"
            >
              <TrashIcon className="w-4 h-4" />
              {deleteMutation.isPending ? 'Deleting...' : 'Confirm delete'}
            </button>
            <button onClick={() => setConfirmDelete(false)} className="btn-secondary flex-1">Cancel</button>
          </>
        ) : (
          <>
            <button
              onClick={() => { setLabel(individual.label || ''); setEditing(true); }}
              className="btn-secondary flex-1 flex justify-center gap-2"
            >
              <PencilIcon className="w-4 h-4" />
              Edit
            </button>
            <button onClick={() => setConfirmDelete(true)} className="btn-danger flex-1 flex justify-center gap-2">
              <TrashIcon className="w-4 h-4" />
              Delete
            </button>
          </>
        )}
      </div>
    </div>
  );
}

function AddIndividualModal({ classUri, classLabel, onClose, onSuccess }) {
  const [formData, setFormData] = useState({ uri: '', label: '' });
  const createIndividualMutation = useCreateIndividual();

  const handleSubmit = (e) => {
    e.preventDefault();
    const body = { label: formData.label };
    if (formData.uri.trim()) body.uri = formData.uri.trim();
    createIndividualMutation.mutate({ classUri, body }, { onSuccess: (result) => onSuccess(result) });
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-lg p-6 w-96">
        <h2 className="text-lg font-bold text-gray-900 mb-4">Add Individual to {classLabel}</h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Individual IRI (optional)</label>
            <input
              type="text"
              value={formData.uri}
              onChange={(e) => setFormData({ ...formData, uri: e.target.value })}
              placeholder="http://example.org/individual/1"
              className="input"
            />
            <p className="text-xs text-gray-500 mt-1">Leave empty to generate one under http://w3id.org/protys/instances/.</p>
          </div>
          <div>
            <label className="label">Label (rdfs:label)</label>
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
              disabled={createIndividualMutation.isPending}
              className="btn-primary flex-1 disabled:opacity-50"
            >
              {createIndividualMutation.isPending ? 'Creating and validating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default OntologyExplorer;
