import React, { useRef, useState } from 'react';
import CleaningContextStatus from '../alignment/CleaningContextStatus';
import ContextIntegrityStatus from '../alignment/ContextIntegrityStatus';
import NumericIntegrityStatus from '../alignment/NumericIntegrityStatus';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import {
  ArrowRightIcon,
  ArrowLeftIcon,
  CheckCircleIcon,
  DocumentPlusIcon,
} from '@heroicons/react/24/outline';
import { wizardAPI, alignmentAPI } from '../../services/api';
import { validationState, canProceedValidation, canProceedVerification, stateMessage } from '../../services/validationState';

const SESSION_KEY = 'protys.wizard.session';
function readSession() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY));
    return saved && typeof saved.id === 'string' && saved.id ? saved : null;
  } catch { return null; }
}
function saveSession(session) {
  try {
    if (session) sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    else sessionStorage.removeItem(SESSION_KEY);
  } catch { /* A blocked browser store does not change server session ownership. */ }
}
function sameRuleIds(expected, actual) {
  return Array.isArray(actual) && JSON.stringify([...expected].sort()) === JSON.stringify([...actual].sort());
}
// Las reglas registradas llegan con id; las reglas de la sesión, sin id hasta completar.
function executionMatchesSelection(result, selectedIds, sessionRuleIds = []) {
  if (!sameRuleIds(selectedIds, result?.selectedRuleIds) || !Array.isArray(result?.executedRules)) return false;
  const registered = result.executedRules.filter(rule => rule && typeof rule.id === 'string');
  const sessionRules = result.executedRules.filter(rule => rule && rule.id == null && typeof rule.name === 'string');
  return registered.length + sessionRules.length === result.executedRules.length
    && sameRuleIds(selectedIds, registered.map(rule => rule.id))
    && sameRuleIds(sessionRuleIds, sessionRules.map(rule => rule.name));
}

function Wizard() {
  const [initialSession] = useState(readSession);
  const [step, setStep] = useState(initialSession ? 2 : 1);
  const [sessionId, setSessionId] = useState(initialSession?.id || null);
  const [uploadedFile, setUploadedFile] = useState(initialSession?.name || null);
  const [cancellationError, setCancellationError] = useState(initialSession?.cleanupPending
    ? 'A previous completion reported pending cleanup. Retrieve its current receipt before starting again.' : null);
  const [uploadNeedsCleanup, setUploadNeedsCleanup] = useState(initialSession?.requiresCleanup === true);
  const [completionRecoveryAvailable, setCompletionRecoveryAvailable] = useState(initialSession?.cleanupPending === true);
  const [completionReceipt, setCompletionReceipt] = useState(null);
  const actionInFlight = useRef(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [selectedRules, setSelectedRules] = useState([]);
  const [availableRules, setAvailableRules] = useState([]);
  const [validationResult, setValidationResult] = useState(null);
  const [verificationResult, setVerificationResult] = useState(null);
  const [dataFile, setDataFile] = useState(null);
  const [sessionRuleIds, setSessionRuleIds] = useState([]);
  const [lastCompletion, setLastCompletion] = useState(null);

  const rememberSession = (data, requiresCleanup = false) => {
    if (!data?.tempModuleId) return;
    const name = selectedFile?.name || data.standardName;
    setSessionId(data.tempModuleId);
    setUploadedFile(name);
    setUploadNeedsCleanup(requiresCleanup);
    saveSession({ id: data.tempModuleId, name, requiresCleanup });
    setValidationResult(null);
    setVerificationResult(null);
    setStep(2);
  };

  // Step 1: Upload
  const step1Mutation = useMutation({
    mutationFn: async (file) => {
      const data = await wizardAPI.step1Upload(file, file.name.replace(/\.[^.]+$/, '').replace(/[^a-zA-Z0-9_-]/g, '_'));
      if (!dataFile || !data?.tempModuleId || data.step1_parsed !== true) return data;
      // Datos de instancia opcionales: van a un grafo propio de la misma sesión.
      const instanceData = await wizardAPI.step1UploadData(data.tempModuleId, dataFile).catch(error => error.response?.data || { step1_parsed: false });
      return { ...data, instanceData };
    },
    onSuccess: (data) => {
      if (!data.tempModuleId || data.step1_parsed !== true || data.step1_error) {
        rememberSession(data, true);
        toast.error('Upload was not verified'); return;
      }
      if (dataFile && data.instanceData?.step1_parsed !== true) {
        rememberSession(data, true);
        toast.error(data.instanceData?.step1_message || 'Instance data was not loaded'); return;
      }
      rememberSession(data);
      toast.success('File uploaded successfully');
    },
    onError: (error) => {
      // A failed graph write can still leave a registered session requiring cleanup.
      rememberSession(error.response?.data, true);
      toast.error('Failed to upload file');
    },
  });

  // Step 2: Validate
  const step2Mutation = useMutation({
    mutationFn: () => wizardAPI.step2Validate(sessionId),
    onSuccess: async (data) => {
      setValidationResult(data);
      if (canProceedValidation(data)) {
        try {
          const rules = await alignmentAPI.getRules();
          if (!Array.isArray(rules)) throw new Error('Unconfirmed rule catalog');
          setAvailableRules(rules);
          setSelectedRules((current) => current.filter(id => rules.some(rule => rule.id === id)));
        } catch {
          toast.error('The rule catalog could not be loaded. Retry validation before configuring this session.');
          return;
        }
      }
      if (canProceedValidation(data)) { setStep(3); if (data.contextEvaluation?.status === 'CONTEXT_INTEGRITY_ERROR') toast.warning('OWL consistency checked; declared context links require review'); else toast.success('OWL consistency checked'); }
      else { toast.error(data.step2_message || stateMessage(data)); }
    },
    onError: () => {
      toast.error('Validation failed');
    },
  });

  // Step 3: Alignment
  const step3Mutation = useMutation({
    mutationFn: () => wizardAPI.step3DefineAlignments(sessionId, selectedRules.map((id) => ({ id }))),
    onSuccess: (data) => {
      if (data.step3_error || data.status !== 'SELECTED' || !sameRuleIds(selectedRules, data.selectedRuleIds)) {
        toast.error(data.step3_message || 'Rule selection was not confirmed'); return;
      }
      setVerificationResult(null);
      setStep(4);
      toast.success('Alignments configured');
    },
    onError: () => {
      toast.error('Failed to configure alignments');
    },
  });

  // Step 3b: reglas SWRL nuevas de la sesión
  const step3RulesMutation = useMutation({
    mutationFn: (file) => wizardAPI.step3UploadRules(sessionId, file),
    onSuccess: (data) => {
      setVerificationResult(null);
      if (data.step3_error || !Array.isArray(data.sessionRuleIds)) {
        toast.error(data.step3_message || 'Session rules were not loaded'); return;
      }
      setSessionRuleIds(data.sessionRuleIds);
      toast.success(`Session rules loaded: ${data.sessionRuleIds.join(', ')}`);
    },
    onError: (error) => {
      toast.error(error.response?.data?.step3_message || 'Failed to load session rules');
    },
  });

  // Step 4: Verify
  const step4Mutation = useMutation({
    mutationFn: () => wizardAPI.step4VerifyInferences(sessionId),
    onSuccess: (data) => {
      if (canProceedVerification(data) && !executionMatchesSelection(data, selectedRules, sessionRuleIds)) {
        setVerificationResult(null);
        toast.error('The executed rules could not be confirmed for this selection');
        return;
      }
      setVerificationResult(data);
      if (canProceedVerification(data)) { setStep(5); if (data.contextEvaluation?.status === 'CONTEXT_INTEGRITY_ERROR') toast.warning('OWL reasoning checked; declared context links require review'); else toast.success('OWL reasoning configuration checked'); }
      else { toast.error(data.step4_message || stateMessage(data)); }
    },
    onError: () => {
      toast.error('Verification failed');
    },
  });

  // Complete Wizard
  const completeMutation = useMutation({
    mutationFn: () => wizardAPI.completeIncorporation(sessionId),
    onSuccess: (data) => {
      if (data.status !== 'COMPLETED' || !data.finalModuleId) {
        toast.error(completionReceipt
          ? 'Temporary cleanup could not be confirmed; ontology incorporation remains complete.'
          : data.message || 'Ontology incorporation could not be confirmed');
        return;
      }
      const warnings = Array.isArray(data.warnings) ? data.warnings : [];
      const cleanupPending = data.cleanupPending === true || (data.cleanupPending == null
        && warnings.some(warning => typeof warning === 'string' && warning.includes('temporary graph cleanup')));
      if (cleanupPending) {
        setCompletionReceipt(data);
        setCompletionRecoveryAvailable(true);
        setCancellationError(null);
        saveSession({ id: sessionId, name: uploadedFile, cleanupPending: true });
        setStep(5);
        toast.warning('Ontology incorporation is complete; temporary cleanup is pending.');
        return;
      }
      warnings.forEach(warning => toast.warning(warning));
      toast.success('Ontology incorporation confirmed');
      clearWizard();
      setLastCompletion(data);
    },
    onError: () => {
      toast.error(completionReceipt
        ? 'Temporary cleanup could not be confirmed; ontology incorporation remains complete.'
        : 'Ontology incorporation could not be confirmed');
    },
  });

  const cancelMutation = useMutation({
    mutationFn: () => wizardAPI.cancelIncorporation(sessionId),
    onSuccess: (data) => {
      if (data.status !== 'CANCELLED' || data.tempModuleId !== sessionId) {
        setCancellationError('Cancellation was not confirmed. Retry before uploading another file.');
        return;
      }
      clearWizard();
    },
    onError: (error) => {
      const result = error.response?.data;
      if (error.response?.status === 409 && result?.status === 'COMPLETED' && result.tempModuleId === sessionId) {
        setCompletionRecoveryAvailable(true);
        setCancellationError('The server reports this session was completed. Retrieve its completion receipt before starting again.');
      } else {
        setCancellationError('Cancellation could not be confirmed. Retry before uploading another file.');
      }
    },
  });
  const busy = [step1Mutation, step2Mutation, step3Mutation, step3RulesMutation, step4Mutation, completeMutation, cancelMutation]
    .some(mutation => mutation.isPending);

  const runAction = (mutation, variables) => {
    // Guard synchronously as well as disabling buttons after React renders.
    if (actionInFlight.current || busy) return;
    actionInFlight.current = true;
    mutation.mutate(variables, { onSettled: () => { actionInFlight.current = false; } });
  };
  const handleUpload = (e) => {
    e.preventDefault();
    if (sessionId) { toast.error('Cancel the current session before uploading another file'); return; }
    if (!selectedFile) { toast.error('Please select a file'); return; }
    setLastCompletion(null);
    runAction(step1Mutation, selectedFile);
  };
  const handleProceedToValidate = () => { if (!uploadNeedsCleanup && !completionRecoveryAvailable) runAction(step2Mutation); };
  const handleProceedToAlignment = () => runAction(step3Mutation);
  const handleProceedToVerify = () => runAction(step4Mutation);
  const handleComplete = () => {
    if (!canProceedValidation(validationResult) || !canProceedVerification(verificationResult)
      || !executionMatchesSelection(verificationResult, selectedRules, sessionRuleIds)) {
      toast.error('A completed verification of the selected rules is required'); return;
    }
    runAction(completeMutation);
  };
  const clearWizard = () => {
    saveSession(null);
    setStep(1);
    setSessionId(null);
    setUploadedFile(null);
    setSelectedFile(null);
    setDataFile(null);
    setSessionRuleIds([]);
    setSelectedRules([]);
    setAvailableRules([]);
    setValidationResult(null);
    setVerificationResult(null);
    setCancellationError(null);
    setUploadNeedsCleanup(false);
    setCompletionRecoveryAvailable(false);
    setCompletionReceipt(null);
  };
  const cancelAndReset = () => {
    if (actionInFlight.current || busy) return;
    if (!sessionId) { clearWizard(); return; }
    setCancellationError(null);
    runAction(cancelMutation);
  };
  const goBack = (targetStep) => {
    if (actionInFlight.current || busy) return;
    setVerificationResult(null);
    setStep(targetStep);
  };

  const toggleRule = (ruleId) => {
    if (actionInFlight.current || busy) return;
    setVerificationResult(null);
    setSelectedRules((prev) =>
      prev.includes(ruleId)
        ? prev.filter((id) => id !== ruleId)
        : [...prev, ruleId]
    );
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Progress Indicator */}
      <div className="card p-6">
        <div className="flex justify-between items-center">
          {[1, 2, 3, 4, 5].map((s) => (
            <React.Fragment key={s}>
              <div
                className={`w-10 h-10 rounded-full flex items-center justify-center font-semibold transition-colors duration-200 ${
                  s < step
                    ? 'bg-green-500 text-white'
                    : s === step
                    ? 'bg-protys-500 text-white'
                    : 'bg-gray-200 text-gray-600'
                }`}
              >
                {s < step ? '✓' : s}
              </div>
              {s < 5 && (
                <div
                  className={`flex-1 h-1 mx-2 transition-colors duration-200 ${
                    s < step ? 'bg-green-500' : 'bg-gray-200'
                  }`}
                ></div>
              )}
            </React.Fragment>
          ))}
        </div>
        <div className="flex justify-between text-xs text-gray-600 mt-3">
          <span>Upload</span>
          <span>Validate</span>
          <span>Align</span>
          <span>Verify</span>
          <span>Complete</span>
        </div>
      </div>

      {cancellationError && <div role="alert" className="bg-red-50 border border-red-200 rounded-lg p-4">{cancellationError}</div>}
      {uploadNeedsCleanup && <div role="alert" className="bg-red-50 border border-red-200 rounded-lg p-4">Upload incomplete. Use Back to cancel this session before uploading another file.</div>}
      {completionRecoveryAvailable && <button className="btn-secondary" disabled={busy} onClick={() => runAction(completeMutation)}>{completionReceipt ? 'Retry temporary cleanup' : 'Retrieve completion receipt'}</button>}
      {cancelMutation.isPending && <p role="status">Cancelling the current session...</p>}
      {lastCompletion && step === 1 && <CompletionSummary result={lastCompletion} />}
      {/* Step Content */}
      <div className="card p-8">
        {step === 1 && (
          <Step1Upload
            onUpload={handleUpload}
            selectedFile={selectedFile}
            setSelectedFile={setSelectedFile}
            dataFile={dataFile}
            setDataFile={setDataFile}
            isLoading={step1Mutation.isPending}
            busy={busy}
          />
        )}

        {step === 2 && (
          <Step2Validate
            uploadedFile={uploadedFile}
            validationResult={validationResult}
            onValidate={handleProceedToValidate}
            isLoading={step2Mutation.isPending}
            busy={busy}
            onBack={cancelAndReset}
            validationBlocked={uploadNeedsCleanup || completionRecoveryAvailable}
          />
        )}

        {step === 3 && (
          <Step3Alignment
            availableRules={availableRules}
            selectedRules={selectedRules}
            toggleRule={toggleRule}
            sessionRuleIds={sessionRuleIds}
            onUploadRules={(file) => runAction(step3RulesMutation, file)}
            rulesLoading={step3RulesMutation.isPending}
            onNext={handleProceedToAlignment}
            isLoading={step3Mutation.isPending}
            busy={busy}
            onBack={() => goBack(2)}
            validationResult={validationResult}
          />
        )}

        {step === 4 && (
          <Step4Verify
            validationResult={validationResult}
            selectedRules={selectedRules}
            onVerify={handleProceedToVerify}
            isLoading={step4Mutation.isPending}
            busy={busy}
            onBack={() => goBack(3)}
            verificationResult={verificationResult}
          />
        )}

        {step === 5 && completionReceipt && (
          <div className="space-y-4">
            <h2 className="text-2xl font-bold text-gray-900">Ontology incorporated</h2>
            <div role="alert" className="bg-yellow-50 border border-yellow-200 rounded-lg p-4">
              <p>Temporary cleanup is pending. Retry cleanup before starting another session.</p>
              {(completionReceipt.warnings || []).map((warning, index) => <p key={index}>{warning}</p>)}
            </div>
            <ExecutedRules result={completionReceipt} />
          </div>
        )}
        {step === 5 && !completionReceipt && (
          <Step5Complete
            verificationResult={verificationResult}
            onComplete={handleComplete}
            isLoading={completeMutation.isPending}
            busy={busy}
            onReset={cancelAndReset}
          />
        )}
      </div>
    </div>
  );
}

function Step1Upload({ onUpload, selectedFile, setSelectedFile, dataFile, setDataFile, isLoading, busy }) {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Step 1: Upload Ontology</h2>
        <p className="text-gray-600">Select an ontology file to get started</p>
      </div>

      <form onSubmit={onUpload} className="space-y-6">
        <div className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center">
          <DocumentPlusIcon className="w-12 h-12 mx-auto mb-3 text-gray-400" />
          <label className="block cursor-pointer">
            <input
              type="file"
              disabled={busy}
              onChange={(e) => setSelectedFile(e.target.files?.[0])}
              accept=".rdf,.owl,.ttl,.n3,.xml"
              className="hidden"
            />
            <span className="text-protys-600 hover:text-protys-700 font-medium">
              Click to upload
            </span>
            {' '}or drag and drop
          </label>
          <p className="text-sm text-gray-500 mt-2">
            RDF, OWL, TTL, N3, or XML files (max 100MB)
          </p>
        </div>

        {selectedFile && (
          <div className="bg-protys-50 border border-protys-200 rounded-lg p-4">
            <p className="text-sm text-gray-600">Selected file:</p>
            <p className="font-medium text-gray-900">{selectedFile.name}</p>
            <p className="text-xs text-gray-500">
              {(selectedFile.size / 1024 / 1024).toFixed(2)} MB
            </p>
          </div>
        )}

        <div className="border border-gray-200 rounded-lg p-4">
          <label className="block text-sm font-medium text-gray-900">
            Instance data (optional)
            <input
              type="file"
              aria-label="Instance data (optional)"
              disabled={busy}
              onChange={(e) => setDataFile(e.target.files?.[0] || null)}
              accept=".rdf,.owl,.ttl,.n3,.nt,.xml"
              className="block mt-2 text-sm"
            />
          </label>
          <p className="text-xs text-gray-500 mt-1">Individuals validated together with the standard and registered as their own module on completion.</p>
          {dataFile && <p className="text-sm text-gray-700 mt-1">Selected data file: {dataFile.name}</p>}
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <button
            type="submit"
            disabled={!selectedFile || isLoading || busy}
            className="btn-primary flex items-center gap-2 disabled:opacity-50"
          >
            {isLoading ? 'Uploading...' : 'Upload & Continue'}
            <ArrowRightIcon className="w-4 h-4" />
          </button>
        </div>
      </form>
    </div>
  );
}

export function Step2Validate({ uploadedFile, onValidate, isLoading, busy, onBack, validationResult, validationBlocked }) {
  const status = validationState(validationResult);
  const indicator = status === 'CONSISTENT' ? 'success' : status === 'PENDING' ? 'pending' : 'error';
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Step 2: Validate Ontology</h2>
        <p className="text-gray-600">Checking ontology structure and consistency</p>
      </div>

      <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
        <p className="text-sm text-gray-600 mb-2">Uploaded file:</p>
        <p className="font-semibold text-gray-900">{uploadedFile}</p>
      </div>

      <div className="space-y-3">
        <ValidationStep label="Syntax Check" description="RDF parsed during upload; OWL profile evaluated on validation" status={indicator} />
        <ValidationStep label="Integrity Check" description={stateMessage(validationResult)} status={indicator} />
        <ValidationStep label="Coverage evaluation" description="Domain coverage requires the separate competency-query fixtures" status="pending" />
      </div>

      <div className="flex justify-between gap-2 pt-4">
        <button onClick={onBack} disabled={busy || isLoading} className="btn-secondary flex items-center gap-2">
          <ArrowLeftIcon className="w-4 h-4" />
          Back
        </button>
        <button
          onClick={onValidate}
          disabled={isLoading || busy || validationBlocked}
          className="btn-primary flex items-center gap-2 disabled:opacity-50"
        >
          {isLoading ? 'Validating...' : 'Validate & Continue'}
          <ArrowRightIcon className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

export function Step3Alignment({
  availableRules,
  selectedRules,
  toggleRule,
  sessionRuleIds = [],
  onUploadRules,
  rulesLoading,
  onNext,
  isLoading,
  busy,
  onBack,
  validationResult,
}) {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Step 3: Configure Alignments</h2>
        <p className="text-gray-600">Select alignment rules to apply to your ontology</p>
        <p className="text-sm text-gray-600">This selection applies only to this session. Registered inactive rules can be selected without changing global activation.</p>
      </div>

      <CleaningContextStatus evaluation={validationResult?.cleaningEvaluation} />
      <ContextIntegrityStatus evaluation={validationResult?.contextEvaluation} />
      <NumericIntegrityStatus evaluation={validationResult?.numericEvaluation} owlStatus={validationResult?.owlValidationStatus} />
      {canProceedValidation(validationResult) && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
          <p className="text-sm font-medium text-green-900">
            ✓ OWL profile and consistency checked
          </p>
          <p className="text-xs text-green-800 mt-1">
            {validationResult.step2_message}
          </p>
        </div>
      )}

      <div className="space-y-3">
        <h3 className="font-semibold text-gray-900">Available Alignment Rules</h3>
        {availableRules.length > 0 ? (
          availableRules.map((rule) => (
            <label
              key={rule.id}
              className="flex items-start gap-3 p-4 border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer"
            >
              <input
                type="checkbox"
                disabled={busy || isLoading}
                checked={selectedRules.includes(rule.id)}
                onChange={() => toggleRule(rule.id)}
                className="mt-1"
              />
              <div className="flex-1">
                <p className="font-medium text-gray-900">{rule.name}</p>
                <p className="text-sm text-gray-600">{rule.description}</p>
                {rule.active === false && <p className="text-xs text-gray-600">Globally inactive; available for this session</p>}
              </div>
            </label>
          ))
        ) : (
          <p className="text-gray-500 text-sm">No suggested rules available</p>
        )}
      </div>

      <div className="space-y-2 border border-gray-200 rounded-lg p-4">
        <h3 className="font-semibold text-gray-900">Session rules (SWRL)</h3>
        <p className="text-sm text-gray-600">New rules brought by this standard. They are verified in step 4 and, on completion, registered and activated.</p>
        <input
          type="file"
          aria-label="Session rules (SWRL)"
          disabled={busy || isLoading || rulesLoading}
          onChange={(e) => { const file = e.target.files?.[0]; if (file && onUploadRules) onUploadRules(file); }}
          accept=".rdf,.owl,.ttl,.n3,.nt,.xml"
          className="block text-sm"
        />
        {rulesLoading && <p role="status" className="text-sm">Loading session rules...</p>}
        {sessionRuleIds.length > 0 && <p className="text-sm text-gray-700">Loaded session rules: {sessionRuleIds.join(', ')}</p>}
      </div>

      <div className="flex justify-between gap-2 pt-4">
        <button onClick={onBack} disabled={busy || isLoading} className="btn-secondary flex items-center gap-2">
          <ArrowLeftIcon className="w-4 h-4" />
          Back
        </button>
        <button
          onClick={onNext}
          disabled={isLoading || busy}
          className="btn-primary flex items-center gap-2 disabled:opacity-50"
        >
          {isLoading ? 'Configuring...' : 'Configure & Continue'}
          <ArrowRightIcon className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

export function Step4Verify({
  validationResult,
  selectedRules,
  onVerify,
  isLoading,
  busy,
  onBack,
  verificationResult,
}) {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Step 4: Verify Configuration</h2>
        <p className="text-gray-600">Review your configuration before creating the ontology</p>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-protys-50 border border-protys-200 rounded-lg p-4">
          <p className="text-xs text-protys-600 uppercase font-semibold mb-2">Classes</p>
          <p className="text-2xl font-bold text-protys-700">{validationResult?.classCount || 0}</p>
        </div>
        <div className="bg-semantic-50 border border-semantic-200 rounded-lg p-4">
          <p className="text-xs text-semantic-600 uppercase font-semibold mb-2">Properties</p>
          <p className="text-2xl font-bold text-semantic-700">
            {validationResult?.propertyCount || 0}
          </p>
        </div>
      </div>

      <div className="bg-gray-50 rounded-lg p-4">
        <p className="font-semibold text-gray-900 mb-3">Selected Rules</p>
        {selectedRules.length > 0 ? (
          <ul className="space-y-1">
            {selectedRules.map((ruleId) => (
              <li key={ruleId} className="text-sm text-gray-700">
                Rule {ruleId}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-600">No rules selected</p>
        )}
      </div>

      <ExecutedRules result={verificationResult} />
      <CleaningContextStatus evaluation={verificationResult?.cleaningEvaluation} />
      <ContextIntegrityStatus evaluation={verificationResult?.contextEvaluation} />
      <NumericIntegrityStatus evaluation={verificationResult?.numericEvaluation} owlStatus={verificationResult?.owlValidationStatus} />
      {canProceedVerification(verificationResult) && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
          <p className="text-sm font-medium text-green-900">
            ✓ OWL reasoning configuration checked
          </p>
          <p className="text-xs text-green-800 mt-1">
            {verificationResult.step4_message}
          </p>
        </div>
      )}

      <div className="flex justify-between gap-2 pt-4">
        <button onClick={onBack} disabled={busy || isLoading} className="btn-secondary flex items-center gap-2">
          <ArrowLeftIcon className="w-4 h-4" />
          Back
        </button>
        <button
          onClick={onVerify}
          disabled={isLoading || busy}
          className="btn-primary flex items-center gap-2 disabled:opacity-50"
        >
          {isLoading ? 'Verifying...' : 'Verify & Continue'}
          <ArrowRightIcon className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

export function Step5Complete({ verificationResult, onComplete, isLoading, busy, onReset }) {
  const verified = canProceedVerification(verificationResult);
  const recordWarning = verificationResult?.numericEvaluation?.status === 'NOT_EVALUABLE' || verificationResult?.contextEvaluation?.status === 'CONTEXT_INTEGRITY_ERROR' || ['NOT_EVALUABLE', 'MISSING_CLEANING_RECORD'].includes(verificationResult?.cleaningEvaluation?.status);
  return (
    <div className="space-y-6 text-center">
      <div>
        {verified && !recordWarning && <CheckCircleIcon className="w-16 h-16 text-green-500 mx-auto mb-4" />}
        <h2 className="text-2xl font-bold text-gray-900 mb-2">{verified ? recordWarning ? 'OWL checks complete; record review required' : 'OWL reasoning checks complete' : 'Verification required'}</h2>
        <p className="text-gray-600">{verified ? 'The configured OWL profile, consistency and inference checks completed. Review the separate record statuses below.' : stateMessage(verificationResult)}</p>
      </div>

      <ExecutedRules result={verificationResult} />
      {canProceedVerification(verificationResult) && Array.isArray(verificationResult.executedRules) && verificationResult.executedRules.length > 0 && (
        <p className="text-sm text-gray-700 text-left">Active after completion: {verificationResult.executedRules.map(rule => rule.name).join(', ')}</p>
      )}
      <CleaningContextStatus evaluation={verificationResult?.cleaningEvaluation} />
      <ContextIntegrityStatus evaluation={verificationResult?.contextEvaluation} />
      <NumericIntegrityStatus evaluation={verificationResult?.numericEvaluation} owlStatus={verificationResult?.owlValidationStatus} />
      {canProceedVerification(verificationResult) && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-6 text-left">
          <h3 className="font-semibold text-green-900 mb-3">Summary</h3>
          <div className="space-y-2 text-sm text-green-800">
            <p>✓ OWL profile and consistency checked</p>
            <p>✓ Alignment rules configured</p>
            <p>✓ OWL consistency and configured inference execution verified</p>
          </div>
        </div>
      )}

      <div className="flex flex-col gap-2 pt-4">
        <button
          onClick={onComplete}
          disabled={isLoading || busy || !canProceedVerification(verificationResult)}
          className="btn-success w-full disabled:opacity-50"
        >
          {isLoading ? 'Creating...' : 'Create Ontology'}
        </button>
        <button onClick={onReset} disabled={busy || isLoading} className="btn-secondary w-full">
          Start Over
        </button>
      </div>
    </div>
  );
}

function ExecutedRules({ result }) {
  const completed = result?.status === 'COMPLETED' && !!result.finalModuleId;
  if ((!completed && !canProceedVerification(result)) || !Array.isArray(result?.executedRules)) return null;
  return (
    <div className="bg-gray-50 rounded-lg p-4 text-left">
      <h3 className="font-semibold text-gray-900">Executed Rules</h3>
      {result.executedRules.length ? <ul>{result.executedRules.map(rule => (
        <li key={rule.id || rule.name}>{rule.name || rule.id} ({rule.id || 'session rule'})</li>
      ))}</ul> : <p>No alignment rules were executed for this session.</p>}
      {typeof result.ruleDefinitionsSha256 === 'string' && result.ruleDefinitionsSha256 && (
        <details className="mt-3 text-sm">
          <summary className="cursor-pointer">Rule snapshot traceability</summary>
          <p className="mt-2">Saved rule definitions (SHA-256)</p>
          <code className="block break-all">{result.ruleDefinitionsSha256}</code>
        </details>
      )}
    </div>
  );
}

function CompletionSummary({ result }) {
  const active = Array.isArray(result?.activatedRules) ? result.activatedRules : [];
  return (
    <div role="status" className="bg-green-50 border border-green-200 rounded-lg p-4">
      <p className="font-medium text-green-900">Last incorporation: {result.standardName || result.finalModuleId}</p>
      <p className="text-sm text-green-800">Rules active after completion ({active.length}): {active.length ? active.join(', ') : 'none'}</p>
    </div>
  );
}

function ValidationStep({ label, description, status }) {
  const statusColor = {
    success: 'bg-green-100 text-green-700',
    pending: 'bg-yellow-100 text-yellow-700',
    error: 'bg-red-100 text-red-700',
  }[status];

  const statusIcon = {
    success: '✓',
    pending: '⏳',
    error: '✕',
  }[status];

  return (
    <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
      <span className={`px-2 py-1 rounded font-medium text-xs ${statusColor}`}>
        {statusIcon}
      </span>
      <div className="flex-1">
        <p className="font-medium text-gray-900">{label}</p>
        <p className="text-sm text-gray-600">{description}</p>
      </div>
    </div>
  );
}

export default Wizard;
