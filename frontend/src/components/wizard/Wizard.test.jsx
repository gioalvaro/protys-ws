import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Step2Validate, Step3Alignment, Step4Verify, Step5Complete } from './Wizard';
jest.mock('../../services/api', () => ({wizardAPI:{},alignmentAPI:{}}));
test('validation screen does not display successful integrity before evaluation', () => {
  const html=renderToStaticMarkup(<Step2Validate uploadedFile="case.owl" />);
  expect(html).toContain('Pending evaluation');
  expect(html).not.toContain('✓');
});
test('a failed response cannot render any validated or verified success panel', () => {
  const failure={status:'NOT_EVALUATED',step2_consistent:true,step4_verified:true};
  expect(renderToStaticMarkup(<Step3Alignment availableRules={[]} selectedRules={[]} validationResult={failure}/>)).not.toContain('OWL profile and consistency checked');
  expect(renderToStaticMarkup(<Step4Verify selectedRules={[]} verificationResult={failure}/>)).not.toContain('OWL reasoning configuration checked');
  const final=renderToStaticMarkup(<Step5Complete verificationResult={failure}/>);
  expect(final).toContain('Verification required');
  expect(final).not.toContain('Ontology Ready');
  expect(final).toContain('disabled');
});

test('a context conflict remains visible at the final incorporation screen without a global approval', () => {
  const result={status:'CONSISTENT',step4_verified:true,contextEvaluation:{status:'CONTEXT_INTEGRITY_ERROR'}};
  const html=renderToStaticMarkup(<Step5Complete verificationResult={result}/>);
  expect(html).toContain('record review required');
  expect(html).toContain('role="alert"');
  expect(html).not.toContain('Ontology Ready');
  expect(html).not.toContain('Your ontology passed');
});
test('an ambiguous numerical response blocks final incorporation and explains the separate OWL result', () => {
  const result={status:'NOT_EVALUATED',step4_verified:true,owlValidationStatus:'CONSISTENT',numericEvaluation:{status:'AMBIGUOUS_INPUT'}};
  const html=renderToStaticMarkup(<Step5Complete verificationResult={result}/>);
  expect(html).toContain('Verification required');
  expect(html).toContain('queries are blocked');
  expect(html).toContain('OWL consistency was checked separately');
  expect(html).toContain('disabled');
  expect(html).not.toContain('✓ OWL consistency and configured inference execution verified');
});
