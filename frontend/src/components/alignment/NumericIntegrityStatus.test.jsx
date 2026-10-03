import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import NumericIntegrityStatus from './NumericIntegrityStatus';
test('ambiguous numerical inputs remain blocked despite separately consistent OWL', () => {
  const html=renderToStaticMarkup(<NumericIntegrityStatus evaluation={{status:'AMBIGUOUS_INPUT'}} owlStatus="CONSISTENT"/>);
  expect(html).toContain('role="alert"');
  expect(html).toContain('queries are blocked');
  expect(html).toContain('does not resolve ambiguous numerical records');
  expect(html).not.toContain('numerically coherent');
});
test('incomplete numerical records and untested output never render a complete numeric approval', () => {
  const incomplete=renderToStaticMarkup(<NumericIntegrityStatus evaluation={{status:'NOT_EVALUABLE'}}/>);
  const inputOnly=renderToStaticMarkup(<NumericIntegrityStatus evaluation={{status:'VALID',output_checked:false}}/>);
  expect(incomplete).toContain('partial lot total');
  expect(incomplete).not.toContain('numerically coherent');
  expect(inputOnly).toContain('GHG output has not yet been checked');
  expect(inputOnly).not.toContain('numerically coherent');
});
