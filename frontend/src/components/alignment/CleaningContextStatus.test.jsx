import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import CleaningContextStatus from './CleaningContextStatus';

test('an incomplete cleaning pair never renders a completed record review', () => {
  const html=renderToStaticMarkup(<CleaningContextStatus evaluation={{status:'NOT_EVALUABLE'}} />);
  expect(html).toContain('cannot be evaluated');
  expect(html).not.toContain('records are complete');
  expect(html).toContain('does not approve physical cleanliness');
});
test('missing records and an absent required pair remain distinct from completed records', () => {
  const missing=renderToStaticMarkup(<CleaningContextStatus evaluation={{status:'MISSING_CLEANING_RECORD'}} />);
  const absent=renderToStaticMarkup(<CleaningContextStatus evaluation={{status:'NOT_APPLICABLE'}} />);
  expect(missing).toContain('lacks its required cleaning record');
  expect(absent).toContain('No required cleaning pair');
  expect(missing+absent).not.toContain('records are complete');
});
