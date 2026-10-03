export default function CleaningContextStatus({ evaluation }) {
  if (!evaluation) return null;
  const messages = {
    NOT_APPLICABLE: 'No required cleaning pair is recorded in this snapshot.',
    NOT_EVALUABLE: 'Cleaning records cannot be evaluated: shared equipment or unique numeric, ordered interval endpoints are missing or contradictory.',
    MISSING_CLEANING_RECORD: 'At least one evaluable pair lacks its required cleaning record.',
    COMPLETE_CLEANING_RECORD: 'Required cleaning records are complete for the evaluable pairs in this snapshot.',
  };
  return <div className="card p-4" role="status">
    <p className="font-semibold">Last cleaning record review</p>
    <p>{messages[evaluation.status] || 'Cleaning record evaluation is unavailable.'}</p>
    <p className="text-sm text-gray-600">This record review does not approve physical cleanliness.</p>
  </div>;
}
