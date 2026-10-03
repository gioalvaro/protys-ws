export default function NumericIntegrityStatus({ evaluation, owlStatus }) {
  if (!evaluation) return null;
  const blocked = evaluation.status === 'AMBIGUOUS_INPUT';
  const incomplete = evaluation.status === 'NOT_EVALUABLE';
  const messages = {
    NOT_APPLICABLE: 'Electrical-inventory records are outside the scope of this source configuration or no operation is selected.',
    VALID: evaluation.output_checked ? 'Selected electrical inputs and recorded GHG outputs are numerically coherent.' : 'Selected electrical inputs meet the numerical contract; GHG output has not yet been checked.',
    NOT_EVALUABLE: 'Some selected electrical inputs or GHG outputs are missing or incompatible. A partial lot total cannot be presented as complete.',
    AMBIGUOUS_INPUT: 'Selected operations have contradictory numerical values or multiple selected emission factors. Reasoning and requested queries are blocked until the records are corrected.',
  };
  return <div className={blocked || incomplete ? 'card p-4 border border-amber-400' : 'card p-4'} role={blocked ? 'alert' : 'status'}>
    <p className="font-semibold">Numerical record review</p>
    <p>{messages[evaluation.status] || 'Numerical records have not been evaluated.'}</p>
    {owlStatus === 'CONSISTENT' && blocked && <p>OWL consistency was checked separately; it does not resolve ambiguous numerical records.</p>}
    <p className="text-sm text-gray-600">This review does not certify physical measurements or completeness of all data.</p>
  </div>;
}
