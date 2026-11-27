import { validationState, canProceedValidation, canProceedVerification, reasoningSucceeded } from './validationState';
test('pending and failed evaluations cannot advance even if a stale boolean is true', () => {
  expect(validationState(null)).toBe('PENDING');
  for (const status of ['INCONSISTENT','NOT_EVALUATED','unknown',undefined]) {
    const result={status,step2_consistent:true,step4_verified:true};
    expect(canProceedValidation(result)).toBe(false);
    expect(canProceedVerification(result)).toBe(false);
  }
});
test('consistency without the completed stage does not permit completing the wizard', () => {
  expect(canProceedValidation({status:'CONSISTENT',step2_consistent:true})).toBe(true);
  expect(canProceedVerification({status:'CONSISTENT',step2_consistent:true})).toBe(false);
  expect(canProceedVerification({status:'CONSISTENT',step4_verified:true})).toBe(true);
  expect(canProceedVerification({status:'CONSISTENT',step4_verified:true,step4_error:true})).toBe(false);
});
test('a successful HTTP response, missing status or cached number cannot masquerade as reasoning', () => {
  expect(reasoningSucceeded({status:'SUCCESS',inferredTripleCount:42})).toBe(false);
  expect(reasoningSucceeded({status:'SUCCESS',validationStatus:'NOT_EVALUATED'})).toBe(false);
  expect(reasoningSucceeded({status:'SUCCESS',validationStatus:'CONSISTENT',cacheHit:true,reasoningTimeMs:0})).toBe(true);
});
test('numeric ambiguity overrides stale success flags and never permits wizard completion or reasoning success', () => {
  const result={status:'CONSISTENT',validationStatus:'CONSISTENT',step2_consistent:true,step4_verified:true,owlValidationStatus:'CONSISTENT',numericEvaluation:{status:'AMBIGUOUS_INPUT'}};
  expect(validationState(result)).toBe('NOT_EVALUATED');
  expect(canProceedValidation(result)).toBe(false);
  expect(canProceedVerification(result)).toBe(false);
  expect(reasoningSucceeded({...result,status:'SUCCESS'})).toBe(false);
});
