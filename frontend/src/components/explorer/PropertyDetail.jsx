import React from 'react';

const shortName = uri => (uri || '').split(/[#/]/).filter(Boolean).pop() || uri;

const TYPE_TEXT = { OBJECT: 'Object property', DATA: 'Data property' };
const CHARACTERISTIC_TEXT = {
  FUNCTIONAL: 'Functional',
  INVERSE_FUNCTIONAL: 'Inverse functional',
  TRANSITIVE: 'Transitive',
  SYMMETRIC: 'Symmetric',
};
const OPERATOR_SYMBOL = { unionOf: ' ∪ ', intersectionOf: ' ∩ ' };

/** Una clase (o tipo de datos): enlace al detalle si es una clase del módulo; si no, su nombre corto. */
function ClassRef({ uri, classes, onSelectClass }) {
  const cls = classes?.get(uri);
  if (cls && onSelectClass) {
    return (
      <button
        onClick={() => onSelectClass(cls)}
        className="text-protys-600 hover:text-protys-800 hover:underline"
        title={uri}
      >
        {cls.label || shortName(uri)}
      </button>
    );
  }
  return <span title={uri}>{cls?.label || shortName(uri)}</span>;
}

/**
 * Un dominio o rango tal como está declarado: nombrado (su clase), o anónimo (las clases que lo
 * forman unidas por ∪ o ∩). Una expresión anónima de otro tipo se muestra como tal.
 */
export function DomainRange({ term, classes, onSelectClass }) {
  if (term.uri) return <ClassRef uri={term.uri} classes={classes} onSelectClass={onSelectClass} />;
  const symbol = OPERATOR_SYMBOL[term.operator];
  if (!symbol || !term.members?.length) return <span className="italic text-gray-500">anonymous expression</span>;
  return (
    <span>
      {term.members.map((uri, i) => (
        <React.Fragment key={uri}>
          {i > 0 && symbol}
          <ClassRef uri={uri} classes={classes} onSelectClass={onSelectClass} />
        </React.Fragment>
      ))}
    </span>
  );
}

/** Texto plano de un dominio o rango, para listas compactas. */
export function domainRangeText(term, classes) {
  const name = uri => classes?.get(uri)?.label || shortName(uri);
  if (term.uri) return name(term.uri);
  const symbol = OPERATOR_SYMBOL[term.operator];
  return symbol && term.members?.length ? term.members.map(name).join(symbol) : 'anonymous expression';
}

function TermList({ title, terms, classes, onSelectClass, testId }) {
  return (
    <div data-testid={testId}>
      <h3 className="text-sm font-semibold text-gray-900 mb-1">
        {title}
        {terms.length > 1 && (
          <span className="ml-2 text-xs font-normal text-gray-500">{terms.length} declared (each one applies)</span>
        )}
      </h3>
      {terms.length > 0 ? (
        <ul className="space-y-1">
          {terms.map((term, i) => (
            <li key={i} className="text-sm text-gray-700 px-3 py-2 bg-gray-50 rounded">
              <DomainRange term={term} classes={classes} onSelectClass={onSelectClass} />
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-gray-500">None declared</p>
      )}
    </div>
  );
}

function NameList({ title, uris, properties, onSelectProperty, testId }) {
  return (
    <div data-testid={testId}>
      <h3 className="text-sm font-semibold text-gray-900 mb-1">{title}</h3>
      {uris.length > 0 ? (
        <ul className="space-y-1">
          {uris.map(uri => {
            const prop = properties?.get(uri);
            return (
              <li key={uri} className="text-sm text-gray-700 px-3 py-2 bg-gray-50 rounded">
                {prop && onSelectProperty ? (
                  <button onClick={() => onSelectProperty(prop)} className="text-protys-600 hover:underline" title={uri}>
                    {prop.label}
                  </button>
                ) : (
                  <span title={uri}>{shortName(uri)}</span>
                )}
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="text-sm text-gray-500">None</p>
      )}
    </div>
  );
}

/**
 * Detalle de una propiedad: tipo, dominios, rangos, superpropiedades, subpropiedades, inversa y
 * características declaradas. {@code classes} y {@code properties} son mapas IRI → nodo del módulo.
 */
function PropertyDetail({ property, classes, properties, onSelectClass, onSelectProperty }) {
  const characteristics = property.characteristics || [];
  return (
    <div className="space-y-6" data-testid="property-detail">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">{property.label}</h2>
        <p className="text-sm text-gray-600 font-mono break-all">{property.uri}</p>
        <span className="inline-block mt-2 px-2 py-0.5 text-xs bg-semantic-50 text-semantic-700 rounded-full">
          {TYPE_TEXT[property.type] || property.type}
        </span>
      </div>

      {property.comment && (
        <div>
          <h3 className="text-sm font-semibold text-gray-900 mb-2">Description</h3>
          <p className="text-sm text-gray-700">{property.comment}</p>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <TermList title="Domain" terms={property.domains || []} classes={classes} onSelectClass={onSelectClass} testId="property-domains" />
        <TermList title="Range" terms={property.ranges || []} classes={classes} onSelectClass={onSelectClass} testId="property-ranges" />
        <NameList title="Superproperties" uris={property.superPropertyUris || []} properties={properties} onSelectProperty={onSelectProperty} testId="property-super" />
        <NameList title="Subproperties" uris={(property.children || property.subproperties || []).map(p => p.uri)} properties={properties} onSelectProperty={onSelectProperty} testId="property-sub" />
        {property.type === 'OBJECT' && (
          <NameList title="Inverse" uris={property.inverseOf || []} properties={properties} onSelectProperty={onSelectProperty} testId="property-inverse" />
        )}
        <div data-testid="property-characteristics">
          <h3 className="text-sm font-semibold text-gray-900 mb-1">Characteristics</h3>
          {characteristics.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {characteristics.map(c => (
                <span key={c} className="px-2 py-1 text-xs bg-protys-50 text-protys-700 rounded">{CHARACTERISTIC_TEXT[c] || c}</span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-gray-500">None declared</p>
          )}
        </div>
      </div>
    </div>
  );
}

export default PropertyDetail;
