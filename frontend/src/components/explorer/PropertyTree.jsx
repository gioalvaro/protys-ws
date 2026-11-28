import React, { useMemo, useState } from 'react';
import {
  ChevronRightIcon,
  ChevronDownIcon,
  ArrowsRightLeftIcon,
  TagIcon,
  CircleStackIcon,
  ExclamationTriangleIcon,
} from '@heroicons/react/24/outline';

const shortName = uri => (uri || '').split(/[#/]/).filter(Boolean).pop() || uri;

/**
 * Normaliza la respuesta de GET /api/ontology/modules/{id}/properties: dos listas de raíces
 * (objectProperties y dataProperties) con hijos en "subproperties".
 */
export function normalizeProperties(data) {
  const normalize = node => ({
    ...node,
    label: node.label || node.localName || shortName(node.uri),
    children: (node.subproperties || []).map(normalize),
  });
  return {
    objectProperties: (data?.objectProperties || []).map(normalize),
    dataProperties: (data?.dataProperties || []).map(normalize),
    objectPropertyCount: data?.objectPropertyCount ?? 0,
    dataPropertyCount: data?.dataPropertyCount ?? 0,
    annotationPropertyCount: data?.annotationPropertyCount ?? 0,
  };
}

const matches = (node, query) => {
  if (!query.trim()) return true;
  const q = query.toLowerCase();
  return (node.label || node.uri).toLowerCase().includes(q) || node.children.some(child => matches(child, query));
};

/** Árbol de propiedades en dos grupos plegables: de objeto y de datos. */
function PropertyTree({ data, onSelect, selectedUri, loading = false, error = false, searchQuery = '' }) {
  const tree = useMemo(() => normalizeProperties(data), [data]);

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
        <p className="text-sm">Could not load the properties</p>
      </div>
    );
  }

  if (tree.objectProperties.length === 0 && tree.dataProperties.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        <CircleStackIcon className="w-8 h-8 mx-auto mb-2 text-gray-400" />
        <p className="text-sm">No properties found</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <PropertyGroup
        title="Object properties"
        count={tree.objectPropertyCount}
        roots={tree.objectProperties}
        icon={ArrowsRightLeftIcon}
        onSelect={onSelect}
        selectedUri={selectedUri}
        searchQuery={searchQuery}
      />
      <PropertyGroup
        title="Data properties"
        count={tree.dataPropertyCount}
        roots={tree.dataProperties}
        icon={TagIcon}
        onSelect={onSelect}
        selectedUri={selectedUri}
        searchQuery={searchQuery}
      />
      {tree.annotationPropertyCount > 0 && (
        <p className="text-xs text-gray-500 px-2">
          {tree.annotationPropertyCount} annotation properties (not shown in the tree)
        </p>
      )}
    </div>
  );
}

function PropertyGroup({ title, count, roots, icon, onSelect, selectedUri, searchQuery }) {
  const [open, setOpen] = useState(true);
  const visible = roots.filter(node => matches(node, searchQuery));
  return (
    <div data-testid={`property-group-${title.split(' ')[0].toLowerCase()}`}>
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-1 px-1 py-1 text-xs font-semibold uppercase tracking-wide text-gray-600 hover:text-gray-900"
        aria-expanded={open}
      >
        {open ? <ChevronDownIcon className="w-4 h-4" /> : <ChevronRightIcon className="w-4 h-4" />}
        <span className="flex-1 text-left">{title}</span>
        <span className="px-2 py-0.5 bg-gray-200 text-gray-700 rounded-full normal-case">{count}</span>
      </button>
      {open && (
        visible.length > 0 ? (
          <div className="space-y-1 mt-1">
            {visible.map(node => (
              <PropertyNode
                key={node.uri}
                node={node}
                level={0}
                icon={icon}
                onSelect={onSelect}
                selectedUri={selectedUri}
                searchQuery={searchQuery}
              />
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-500 px-2 py-1">None</p>
        )
      )}
    </div>
  );
}

function PropertyNode({ node, level, icon: Icon, onSelect, selectedUri, searchQuery }) {
  const [expanded, setExpanded] = useState(false);
  const visibleChildren = node.children.filter(child => matches(child, searchQuery));
  const hasChildren = visibleChildren.length > 0;
  const isSelected = selectedUri === node.uri;
  return (
    <div>
      <div
        className={`flex items-center gap-1 px-2 py-1.5 rounded cursor-pointer transition-colors duration-150 ${
          isSelected ? 'bg-protys-100 text-protys-700 border border-protys-300' : 'hover:bg-gray-100 text-gray-700'
        }`}
        style={{ paddingLeft: `${level * 20 + 8}px` }}
        title={node.uri}
        onClick={() => {
          onSelect(node);
          if (hasChildren) setExpanded(!expanded);
        }}
      >
        {hasChildren ? (
          <button
            onClick={(e) => { e.stopPropagation(); setExpanded(!expanded); }}
            className="flex-shrink-0 p-0.5"
          >
            {expanded ? <ChevronDownIcon className="w-4 h-4" /> : <ChevronRightIcon className="w-4 h-4" />}
          </button>
        ) : (
          <div className="w-4" />
        )}
        <Icon className="w-4 h-4 flex-shrink-0 text-semantic-500" />
        <span className="flex-1 text-sm font-medium truncate">{node.label}</span>
      </div>
      {expanded && hasChildren && (
        <div>
          {visibleChildren.map(child => (
            <PropertyNode
              key={child.uri}
              node={child}
              level={level + 1}
              icon={Icon}
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

export default PropertyTree;
