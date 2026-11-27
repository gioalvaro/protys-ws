import React, { useState, useRef } from 'react';
import {
  PlayIcon,
  ArrowDownTrayIcon,
  TrashIcon,
  CommandLineIcon,
} from '@heroicons/react/24/outline';
import {
  useSparqlTemplates,
  useCompetencyQueries,
  useSparqlExecute,
  useValidateQuery,
  useExportResults,
} from '../../hooks/useSparql';

function SparqlConsole() {
  const [query, setQuery] = useState(getSampleQuery());
  const [results, setResults] = useState(null);
  const [showTemplates, setShowTemplates] = useState(false);
  const resultsRef = useRef(null);

  const { data: templates, isLoading: templatesLoading } = useSparqlTemplates();
  const { data: cqs } = useCompetencyQueries();
  const executeQueryMutation = useSparqlExecute();
  const validateQueryMutation = useValidateQuery();
  const exportResultsMutation = useExportResults();

  const handleLoadTemplate = (template) => {
    setQuery(template.queryText);
    setShowTemplates(false);
  };

  const handleClearResults = () => {
    setResults(null);
  };

  const handleExportResults = async (format) => {
    if (!results) return;
    exportResultsMutation.mutate(
      { results, format: format.toUpperCase() },
      {
        onSuccess: (blob) => {
          downloadFile(blob, `sparql-results.${format}`);
        },
      }
    );
  };

  return (
    <div className="space-y-6">
      {/* Header with Quick Actions */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 mb-1">SPARQL Query Console</h1>
          <p className="text-sm text-gray-600">Execute semantic queries on your ontologies</p>
        </div>
        <button
          onClick={() => setShowTemplates(!showTemplates)}
          className="btn-secondary"
        >
          📋 Templates
        </button>
      </div>

      {/* Templates Panel */}
      {showTemplates && (
        <div className="card p-4 space-y-3">
          <h3 className="font-semibold text-gray-900">Query Templates</h3>
          <div className="grid grid-cols-2 gap-3 max-h-64 overflow-y-auto">
            {templatesLoading ? (
              <p className="text-sm text-gray-500">Loading templates...</p>
            ) : templates && templates.length > 0 ? (
              templates.map((template) => (
                <button
                  key={template.id}
                  onClick={() => handleLoadTemplate(template)}
                  className="text-left p-3 border border-gray-200 rounded-lg hover:bg-protys-50 hover:border-protys-300 transition-colors duration-150"
                >
                  <p className="font-medium text-sm text-gray-900">{template.name}</p>
                  <p className="text-xs text-gray-600 mt-1">{template.description}</p>
                </button>
              ))
            ) : (
              <p className="text-sm text-gray-500">No templates available</p>
            )}
          </div>
        </div>
      )}

      {/* Query Editor */}
      <div className="card p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-3">Query</h2>
        <textarea
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="input font-mono text-sm h-64 mb-4 p-4"
          placeholder="Enter SPARQL query here..."
        />

        <div className="flex gap-2">
          <button
            onClick={() =>
              executeQueryMutation.mutate(
                { query },
                {
                  onSuccess: (data) => {
                    setResults(data);
                    setTimeout(() => resultsRef.current?.scrollIntoView({ behavior: 'smooth' }), 100);
                  },
                }
              )
            }
            disabled={executeQueryMutation.isPending || !query.trim()}
            className="btn-primary flex items-center gap-2"
          >
            <PlayIcon className="w-4 h-4" />
            {executeQueryMutation.isPending ? 'Executing...' : 'Execute'}
          </button>
          <button
            onClick={() => validateQueryMutation.mutate(query)}
            disabled={validateQueryMutation.isPending || !query.trim()}
            className="btn-secondary"
          >
            ✓ Validate
          </button>
          <button
            onClick={() => setQuery('')}
            className="btn-ghost"
          >
            <TrashIcon className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Competency Questions */}
      {cqs && cqs.length > 0 && (
        <div className="card p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-3">Competency Questions</h2>
          <div className="space-y-2">
            {cqs.map((cq) => (
              <button
                key={cq.id}
                onClick={() => setQuery(cq.queryText)}
                className="text-left w-full p-3 border border-gray-200 rounded-lg hover:bg-semantic-50 hover:border-semantic-300 transition-colors duration-150"
              >
                <p className="font-medium text-sm text-gray-900">{cq.question}</p>
                <p className="text-xs text-gray-500 mt-1">{cq.description}</p>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Results */}
      {results && (
        <div ref={resultsRef} className="card p-6 space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-semibold text-gray-900">
              Results ({results.results?.length || 0})
            </h2>
            <div className="flex gap-2">
              <button
                onClick={() => handleExportResults('csv')}
                className="btn-secondary btn-sm flex items-center gap-1"
              >
                <ArrowDownTrayIcon className="w-4 h-4" />
                CSV
              </button>
              <button
                onClick={() => handleExportResults('json')}
                className="btn-secondary btn-sm flex items-center gap-1"
              >
                <ArrowDownTrayIcon className="w-4 h-4" />
                JSON
              </button>
              <button
                onClick={handleClearResults}
                className="btn-ghost btn-sm"
              >
                Clear
              </button>
            </div>
          </div>

          {results.results && results.results.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-200 bg-gray-50">
                    {results.headers?.map((header) => (
                      <th key={header} className="text-left px-4 py-2 font-medium text-gray-900">
                        {header}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {results.results.map((row, idx) => (
                    <tr key={idx} className="border-b border-gray-200 hover:bg-gray-50">
                      {results.headers?.map((header) => (
                        <td key={header} className="px-4 py-2 text-gray-700">
                          <ResultCell value={row[header]} />
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-center py-8 text-gray-500">
              <p className="text-sm">No results found</p>
            </div>
          )}

          {results.executionTime && (
            <p className="text-xs text-gray-600 text-right">
              Executed in {results.executionTime}ms
            </p>
          )}
        </div>
      )}

      {/* Empty State */}
      {!results && (
        <div className="card p-12 flex items-center justify-center text-gray-500">
          <div className="text-center">
            <CommandLineIcon className="w-12 h-12 mx-auto mb-3 text-gray-400" />
            <p className="text-sm">Enter a query and click Execute to see results</p>
          </div>
        </div>
      )}
    </div>
  );
}

function ResultCell({ value }) {
  if (value === null || value === undefined) {
    return <span className="text-gray-400 italic">null</span>;
  }

  if (typeof value === 'object') {
    return (
      <a href={value.uri} target="_blank" rel="noopener noreferrer" className="text-protys-600 hover:underline truncate inline-block max-w-xs">
        {value.label || value.uri.split('/').pop()}
      </a>
    );
  }

  return <span className="truncate">{String(value)}</span>;
}

function getSampleQuery() {
  return `PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX owl: <http://www.w3.org/2002/07/owl#>

SELECT ?class ?label ?description
WHERE {
  ?class a owl:Class .
  OPTIONAL { ?class rdfs:label ?label . }
  OPTIONAL { ?class rdfs:comment ?description . }
}
LIMIT 10`;
}

function downloadFile(blob, filename) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
}

export default SparqlConsole;
