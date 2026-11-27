import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ContextIntegrityStatus from './ContextIntegrityStatus';

test('an incompatible declared link is visibly a scoped data warning, never a completed approval', () => {
  const html=renderToStaticMarkup(<ContextIntegrityStatus evaluation={{status:'CONTEXT_INTEGRITY_ERROR'}} />);
  expect(html).toContain('role="alert"');
  expect(html).toContain('These links require review');
  expect(html).toContain('separate from OWL consistency');
  expect(html).not.toContain('links match their lot');
});

test('valid and inapplicable contexts do not certify missing or global data', () => {
  const valid=renderToStaticMarkup(<ContextIntegrityStatus evaluation={{status:'CONTEXT_VALID'}} />);
  const absent=renderToStaticMarkup(<ContextIntegrityStatus evaluation={{status:'NOT_APPLICABLE'}} />);
  expect(valid).toContain('does not certify completeness');
  expect(absent).toContain('No lot activity');
  expect(absent).not.toContain('links match their lot');
});
