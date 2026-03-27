import React, { useState } from 'react';
import { toast } from 'react-toastify';
import {
  ArrowPathIcon,
  DocumentPlusIcon,
  EyeIcon,
  EyeSlashIcon,
} from '@heroicons/react/24/outline';
import {
  useAlignmentRules,
  useUploadRules,
  useToggleRule,
  useExecuteReasoning,
  useInferenceStats,
} from '../../hooks/useAlignment';
import { useQueryClient } from '@tanstack/react-query';

function Alignment() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [ruleName, setRuleName] = useState('');
  const [ruleDescription, setRuleDescription] = useState('');
  const [showUploadForm, setShowUploadForm] = useState(false);
  const queryClient = useQueryClient();

  const { data: rules, isLoading: rulesLoading } = useAlignmentRules();
  const { data: stats } = useInferenceStats();
  const uploadRulesMutation = useUploadRules();
  const toggleRuleMutation = useToggleRule();
  const executeReasoningMutation = useExecuteReasoning();

  const handleUploadSuccess = () => {
    toast.success('Rules uploaded successfully');
    setShowUploadForm(false);
    setSelectedFile(null);
    setRuleName('');
    setRuleDescription('');
    queryClient.invalidateQueries({ queryKey: ['alignment', 'rules'] });
  };

  const handleUploadSubmit = (e) => {
    e.preventDefault();
    if (!selectedFile || !ruleName) {
      toast.error('Please select a file and enter a name');
      return;
    }
    uploadRulesMutation.mutate(selectedFile, {
      onSuccess: handleUploadSuccess,
    });
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 mb-1">Alignment & Reasoning</h1>
          <p className="text-sm text-gray-600">Manage alignment rules and execute reasoning</p>
        </div>
        <button
          onClick={() => setShowUploadForm(!showUploadForm)}
          className="btn-primary flex items-center gap-2"
        >
          <DocumentPlusIcon className="w-4 h-4" />
          Upload Rules
        </button>
      </div>

      {/* Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard
          label="Total Rules"
          value={rules?.length ?? 0}
          icon="📋"
        />
        <StatCard
          label="Active Rules"
          value={stats?.activeRules ?? 0}
          icon="⚡"
        />
        <StatCard
          label="Last Inferences"
          value={stats?.lastInferences ?? 0}
          icon="🧠"
        />
        <StatCard
          label="Total Inferences"
          value={stats?.totalInferences ?? 0}
          icon="📊"
        />
      </div>

      {/* Upload Form */}
      {showUploadForm && (
        <div className="card p-6 space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Upload Alignment Rules</h2>
          <form onSubmit={handleUploadSubmit} className="space-y-4">
            <div>
              <label className="label">Rule Name</label>
              <input
                type="text"
                value={ruleName}
                onChange={(e) => setRuleName(e.target.value)}
                placeholder="e.g., ERP to Ontology Mapping"
                className="input"
                required
              />
            </div>
            <div>
              <label className="label">Description</label>
              <textarea
                value={ruleDescription}
                onChange={(e) => setRuleDescription(e.target.value)}
                placeholder="Describe the purpose of these rules..."
                className="input h-24"
              />
            </div>
            <div>
              <label className="label">Rule File (RDF/SWRL)</label>
              <input
                type="file"
                onChange={(e) => setSelectedFile(e.target.files?.[0])}
                accept=".rdf,.owl,.swrl,.ttl,.n3"
                className="input cursor-pointer"
                required
              />
            </div>
            <div className="flex gap-2 pt-4">
              <button
                type="button"
                onClick={() => {
                  setShowUploadForm(false);
                  setSelectedFile(null);
                  setRuleName('');
                  setRuleDescription('');
                }}
                className="btn-secondary flex-1"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={uploadRulesMutation.isPending}
                className="btn-primary flex-1 disabled:opacity-50"
              >
                {uploadRulesMutation.isPending ? 'Uploading...' : 'Upload'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Execute Reasoning Button */}
      <button
        onClick={() => {
          executeReasoningMutation.mutate(undefined, {
            onSuccess: () => {
              queryClient.invalidateQueries({ queryKey: ['alignment', 'stats'] });
            },
          });
        }}
        disabled={executeReasoningMutation.isPending || (rules?.filter(r => r.active).length || 0) === 0}
        className="btn-success w-full flex items-center justify-center gap-2 disabled:opacity-50"
      >
        <ArrowPathIcon className="w-4 h-4" />
        {executeReasoningMutation.isPending ? 'Executing...' : 'Execute Reasoning'}
      </button>

      {/* Rules List */}
      <div className="card p-6 space-y-4">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Alignment Rules</h2>

        {rulesLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-20 bg-gray-200 rounded skeleton"></div>
            ))}
          </div>
        ) : rules && rules.length > 0 ? (
          <div className="space-y-3">
            {rules.map((rule) => (
              <div
                key={rule.id}
                className="flex items-start justify-between p-4 border border-gray-200 rounded-lg hover:border-gray-300 transition-colors duration-150"
              >
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-1">
                    <h3 className="font-semibold text-gray-900">{rule.name}</h3>
                    <span
                      className={`px-2 py-1 text-xs font-medium rounded-full ${
                        rule.active
                          ? 'bg-green-100 text-green-700'
                          : 'bg-gray-100 text-gray-700'
                      }`}
                    >
                      {rule.active ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                  {rule.description && (
                    <p className="text-sm text-gray-600 mb-2">{rule.description}</p>
                  )}
                  <div className="flex gap-4 text-xs text-gray-500">
                    <span>Uploaded: {new Date(rule.uploadedAt).toLocaleDateString()}</span>
                    {rule.lastExecuted && (
                      <span>Last executed: {new Date(rule.lastExecuted).toLocaleDateString()}</span>
                    )}
                  </div>
                </div>

                <div className="flex gap-2 ml-4">
                  <button
                    onClick={() =>
                      toggleRuleMutation.mutate({
                        ruleId: rule.id,
                        active: !rule.active,
                      })
                    }
                    disabled={toggleRuleMutation.isPending}
                    className="btn-ghost btn-sm p-2"
                    title={rule.active ? 'Deactivate' : 'Activate'}
                  >
                    {rule.active ? (
                      <EyeIcon className="w-4 h-4 text-protys-600" />
                    ) : (
                      <EyeSlashIcon className="w-4 h-4 text-gray-400" />
                    )}
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-8 text-gray-500">
            <p className="text-sm">No rules uploaded yet</p>
          </div>
        )}
      </div>

      {/* Inference Rules Preview */}
      <div className="card p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Rule Examples</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="bg-gray-50 p-4 rounded-lg font-mono text-xs space-y-2">
            <p className="font-semibold text-gray-900">SWRL Rule Example</p>
            <pre className="text-gray-700 overflow-x-auto">{`Class(?c) ^
Class(?d) ^
subClassOf(?c, ?d) ->
subClassOf(?d, ?c)`}</pre>
          </div>
          <div className="bg-gray-50 p-4 rounded-lg font-mono text-xs space-y-2">
            <p className="font-semibold text-gray-900">OWL Reasoning Example</p>
            <pre className="text-gray-700 overflow-x-auto">{`ObjectProperty(?p) ^
inverseOf(?p, ?q) ^
ObjectPropertyAssertion(?p, ?x, ?y) ->
ObjectPropertyAssertion(?q, ?y, ?x)`}</pre>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, icon }) {
  return (
    <div className="card p-6">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-gray-600 mb-1">{label}</p>
          <p className="text-3xl font-bold text-gray-900">{value}</p>
        </div>
        <div className="text-3xl">{icon}</div>
      </div>
    </div>
  );
}

export default Alignment;
