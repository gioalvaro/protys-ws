// Only explicit results from the genuine validation pipeline permit progression.
export const validationState = (result) => {
  if (!result) return 'PENDING';
  if (result.numericEvaluation?.status === 'AMBIGUOUS_INPUT') return 'NOT_EVALUATED';
  const status = result.validationStatus || result.status;
  return ['CONSISTENT', 'INCONSISTENT', 'NOT_EVALUATED'].includes(status)
    ? status : 'NOT_EVALUATED';
};
export const canProceedValidation = (result) => validationState(result) === 'CONSISTENT'
  && result.step2_consistent === true && result.step2_error !== true;
export const canProceedVerification = (result) => validationState(result) === 'CONSISTENT'
  && result.step4_verified === true && result.step4_error !== true;
export const reasoningSucceeded = (result) => validationState(result) === 'CONSISTENT'
  && result.status === 'SUCCESS';
export const stateMessage = (result) => {
  const status = validationState(result);
  if (status === 'PENDING') return 'Pending evaluation';
  if (result?.numericEvaluation?.status === 'AMBIGUOUS_INPUT') return 'Evaluation blocked by ambiguous numerical records';
  if (status === 'CONSISTENT') return 'Consistency verified';
  if (status === 'INCONSISTENT') return 'Ontology is inconsistent';
  return 'Evaluation could not be completed';
};
