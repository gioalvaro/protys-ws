import React, { useMemo, useState } from 'react';
import {
  ChevronRightIcon,
  ChevronDownIcon,
  SparklesIcon,
  CircleStackIcon,
  ExclamationTriangleIcon,
} from '@heroicons/react/24/outline';

const shortName = uri => (uri || '').split(/[#/]/).filter(Boolean).pop() || uri;

/**
 * Normaliza la respuesta de GET /api/ontology/modules/{id}/classes: una lista de raíces con hijos
 * en "subclasses". Tolera un objeto único (contrato anterior) y nodos con "children". Cada nodo
 * queda marcado como clase y con sus superclases como objetos, que es lo que usa el detalle.
 */
export function normalizeHierarchy(data) {
  const roots = Array.isArray(data) ? data : data && typeof data === 'object' && data.uri ? [data] : [];
  const normalize = node => ({
    ...node,
    type: 'class',
    label: node.label || node.localName || shortName(node.uri),
    children: (node.subclasses || node.children || []).map(normalize),
    superclasses: (node.superClassUris || []).map(uri => ({ uri, label: shortName(uri) })),
  });
  return roots.map(normalize);
}

function ClassTree({
  data,
  onSelect,
  selectedUri,
  loading = false,
  error = false,
  searchQuery = '',
}) {
  const filterTree = (node, query) => {
    if (!query.trim()) return true;
    const searchLower = query.toLowerCase();
    const labelMatch =
      (node.label || node.uri)
        .toLowerCase()
        .includes(searchLower);
    const childrenMatch =
      node.children && node.children.some(child => filterTree(child, query));
    return labelMatch || childrenMatch;
  };

  const nodes = useMemo(() => normalizeHierarchy(data), [data]);
  const visibleData = nodes.filter(node => filterTree(node, searchQuery));

  if (loading) {
    return (
      <div className="space-y-2 p-4">
        {[1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="h-8 bg-gray-200 rounded skeleton"></div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-8 text-red-600" role="alert">
        <ExclamationTriangleIcon className="w-8 h-8 mx-auto mb-2 text-red-400" />
        <p className="text-sm">Could not load the class hierarchy</p>
      </div>
    );
  }

  if (nodes.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        <CircleStackIcon className="w-8 h-8 mx-auto mb-2 text-gray-400" />
        <p className="text-sm">No classes found</p>
      </div>
    );
  }

  return (
    <div className="space-y-1">
      {visibleData.map((node) => (
        <TreeNode
          key={node.uri}
          node={node}
          level={0}
          onSelect={onSelect}
          selectedUri={selectedUri}
          searchQuery={searchQuery}
        />
      ))}
    </div>
  );
}

function TreeNode({
  node,
  level,
  onSelect,
  selectedUri,
  searchQuery,
}) {
  const [expanded, setExpanded] = useState(false);
  const hasChildren = node.children && node.children.length > 0;
  const isSelected = selectedUri === node.uri;
  const indentPx = level * 20;

  const filterTree = (n, query) => {
    if (!query.trim()) return true;
    const searchLower = query.toLowerCase();
    const labelMatch =
      (n.label || n.uri)
        .toLowerCase()
        .includes(searchLower);
    const childrenMatch =
      n.children && n.children.some(child => filterTree(child, query));
    return labelMatch || childrenMatch;
  };

  const visibleChildren = node.children?.filter(child => filterTree(child, searchQuery)) || [];

  return (
    <div>
      <div
        className={`flex items-center gap-1 px-2 py-1.5 rounded cursor-pointer transition-colors duration-150 ${
          isSelected
            ? 'bg-protys-100 text-protys-700 border border-protys-300'
            : 'hover:bg-gray-100 text-gray-700'
        }`}
        style={{ paddingLeft: `${indentPx + 8}px` }}
        onClick={() => {
          onSelect(node);
          if (hasChildren && visibleChildren.length > 0) {
            setExpanded(!expanded);
          }
        }}
      >
        {hasChildren && visibleChildren.length > 0 ? (
          <button
            onClick={(e) => {
              e.stopPropagation();
              setExpanded(!expanded);
            }}
            className="flex-shrink-0 p-0.5"
          >
            {expanded ? (
              <ChevronDownIcon className="w-4 h-4" />
            ) : (
              <ChevronRightIcon className="w-4 h-4" />
            )}
          </button>
        ) : (
          <div className="w-4" />
        )}

        <SparklesIcon className="w-4 h-4 flex-shrink-0 text-protys-500" />
        <span className="flex-1 text-sm font-medium truncate">
          {node.label || shortName(node.uri)}
        </span>
        {node.individualCount !== undefined && (
          <span className="flex-shrink-0 px-2 py-0.5 text-xs bg-gray-200 text-gray-700 rounded-full">
            {node.individualCount}
          </span>
        )}
      </div>

      {expanded && visibleChildren.length > 0 && (
        <div>
          {visibleChildren.map((child) => (
            <TreeNode
              key={child.uri}
              node={child}
              level={level + 1}
              onSelect={onSelect}
              selectedUri={selectedUri}
              searchQuery={searchQuery}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default ClassTree;
