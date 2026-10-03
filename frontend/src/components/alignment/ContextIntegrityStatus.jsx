export default function ContextIntegrityStatus({ evaluation }) {
  if (!evaluation) return null;
  const messages = {
    NOT_APPLICABLE: 'No lot activity, contribution or plan-operation link is declared in this snapshot.',
    CONTEXT_VALID: 'Declared activity, contribution and plan-operation links match their lot manufacturing process.',
    CONTEXT_INTEGRITY_ERROR: 'Some declared activity, contribution or plan-operation link belongs to a different or missing lot manufacturing process. These links require review.',
  };
  const invalid = evaluation.status === 'CONTEXT_INTEGRITY_ERROR';
  return <div className={invalid ? 'card p-4 border border-amber-400' : 'card p-4'} role={invalid ? 'alert' : 'status'}>
    <p className="font-semibold">Declared context link review</p>
    <p>{messages[evaluation.status] || 'Declared context links have not been evaluated.'}</p>
    <p className="text-sm text-gray-600">This scoped record check is separate from OWL consistency and does not certify completeness of all data.</p>
  </div>;
}
