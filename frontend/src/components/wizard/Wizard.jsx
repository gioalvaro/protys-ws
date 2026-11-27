import React, { useState } from 'react';
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

function Wizard() {
  const [step, setStep] = useState(1);
  const [sessionId, setSessionId] = useState(null);
  const [uploadedFile, setUploadedFile] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [selectedRules, setSelectedRules] = useState([]);
  const [availableRules, setAvailableRules] = useState([]);
  const [validationResult, setValidationResult] = useState(null);
  const [verificationResult, setVerificationResult] = useState(null);

  // Step 1: Upload
  const step1Mutation = useMutation({
    mutationFn: (file) => wizardAPI.step1Upload(file, file.name.replace(/\.[^.]+$/, '').replace(/[^a-zA-Z0-9_-]/g, '_')),
    onSuccess: (data) => {
      if (!data.tempModuleId || data.step1_parsed !== true || data.step1_error) { toast.error('Upload was not verified'); return; }
      setSessionId(data.tempModuleId);
      setUploadedFile(selectedFile?.name || data.standardName);
      setValidationResult(null);
      setVerificationResult(null);
      toast.success('File uploaded successfully');
      setStep(2);
    },
    onError: () => {
      toast.error('Failed to upload file');
    },
  });

  // Step 2: Validate
  const step2Mutation = useMutation({
    mutationFn: () => wizardAPI.step2Validate(sessionId),
    onSuccess: async (data) => {
      setValidationResult(data);
      if (canProceedValidation(data)) {
        try { setAvailableRules(await alignmentAPI.getRules()); }
        catch { setAvailableRules([]); }
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
    mutationFn: () => wizardAPI.step3DefineAlignments(sessionId, availableRules.filter((rule) => selectedRules.includes(rule.id))),
    onSuccess: (data) => {
      if (data.step3_error) { toast.error(data.step3_message || 'Alignment definition failed'); return; }
      setVerificationResult(null);
      setStep(4);
      toast.success('Alignments configured');
    },
    onError: () => {
      toast.error('Failed to configure alignments');
    },
  });

  // Step 4: Verify
  const step4Mutation = useMutation({
    mutationFn: () => wizardAPI.step4VerifyInferences(sessionId),
    onSuccess: (data) => {
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
      if (data.status !== 'COMPLETED' || !data.finalModuleId) { toast.error(data.message || 'Incorporation could not be completed'); return; }
      toast.success('Ontology successfully created!');
      resetWizard();
    },
    onError: () => {
      toast.error('Failed to complete wizard');
    },
  });

  const handleUpload = (e) => {
    e.preventDefault();
    if (!selectedFile) {
      toast.error('Please select a file');
      return;
    }
    step1Mutation.mutate(selectedFile);
  };

  const handleProceedToValidate = () => {
    step2Mutation.mutate();
  };

  const handleProceedToAlignment = () => {
    step3Mutation.mutate();
  };

  const handleProceedToVerify = () => {
    step4Mutation.mutate();
  };

  const handleComplete = () => {
    if (!canProceedValidation(validationResult) || !canProceedVerification(verificationResult)) { toast.error('A completed, consistent verification is required'); return; }
    completeMutation.mutate();
  };

  const resetWizard = () => {
    setStep(1);
    setSessionId(null);
    setUploadedFile(null);
    setSelectedFile(null);
    setSelectedRules([]);
    setAvailableRules([]);
    setValidationResult(null);
    setVerificationResult(null);
  };

  const toggleRule = (ruleId) => {
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

      {/* Step Content */}
      <div className="card p-8">
        {step === 1 && (
          <Step1Upload
            onUpload={handleUpload}
            selectedFile={selectedFile}
            setSelectedFile={setSelectedFile}
            isLoading={step1Mutation.isPending}
          />
        )}

        {step === 2 && (
          <Step2Validate
            uploadedFile={uploadedFile}
            validationResult={validationResult}
            onValidate={handleProceedToValidate}
            isLoading={step2Mutation.isPending}
            onBack={() => setStep(1)}
          />
        )}

        {step === 3 && (
          <Step3Alignment
            availableRules={availableRules}
            selectedRules={selectedRules}
            toggleRule={toggleRule}
            onNext={handleProceedToAlignment}
            isLoading={step3Mutation.isPending}
            onBack={() => setStep(2)}
            validationResult={validationResult}
          />
        )}

        {step === 4 && (
          <Step4Verify
            validationResult={validationResult}
            selectedRules={selectedRules}
            onVerify={handleProceedToVerify}
            isLoading={step4Mutation.isPending}
            onBack={() => setStep(3)}
            verificationResult={verificationResult}
          />
        )}

        {step === 5 && (
          <Step5Complete
            verificationResult={verificationResult}
            onComplete={handleComplete}
            isLoading={completeMutation.isPending}
            onReset={resetWizard}
          />
        )}
      </div>
    </div>
  );
}

function Step1Upload({ onUpload, selectedFile, setSelectedFile, isLoading }) {
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

        <div className="flex justify-end gap-2 pt-4">
          <button
            type="submit"
            disabled={!selectedFile || isLoading}
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

export function Step2Validate({ uploadedFile, onValidate, isLoading, onBack, validationResult }) {
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
        <button onClick={onBack} className="btn-secondary flex items-center gap-2">
          <ArrowLeftIcon className="w-4 h-4" />
          Back
        </button>
        <button
          onClick={onValidate}
          disabled={isLoading}
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
  onNext,
  isLoading,
  onBack,
  validationResult,
}) {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Step 3: Configure Alignments</h2>
        <p className="text-gray-600">Select alignment rules to apply to your ontology</p>
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
                checked={selectedRules.includes(rule.id)}
                onChange={() => toggleRule(rule.id)}
                className="mt-1"
              />
              <div className="flex-1">
                <p className="font-medium text-gray-900">{rule.name}</p>
                <p className="text-sm text-gray-600">{rule.description}</p>
              </div>
            </label>
          ))
        ) : (
          <p className="text-gray-500 text-sm">No suggested rules available</p>
        )}
      </div>

      <div className="flex justify-between gap-2 pt-4">
        <button onClick={onBack} className="btn-secondary flex items-center gap-2">
          <ArrowLeftIcon className="w-4 h-4" />
          Back
        </button>
        <button
          onClick={onNext}
          disabled={isLoading}
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
        <p className="font-semibold text-gray-900 mb-3">Applied Rules</p>
        {selectedRules.length > 0 ? (
          <ul className="space-y-1">
            {selectedRules.map((ruleId) => (
              <li key={ruleId} className="text-sm text-gray-700">
                ✓ Rule {ruleId}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-gray-600">No rules selected</p>
        )}
      </div>

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
        <button onClick={onBack} className="btn-secondary flex items-center gap-2">
          <ArrowLeftIcon className="w-4 h-4" />
          Back
        </button>
        <button
          onClick={onVerify}
          disabled={isLoading}
          className="btn-primary flex items-center gap-2 disabled:opacity-50"
        >
          {isLoading ? 'Verifying...' : 'Verify & Continue'}
          <ArrowRightIcon className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}

export function Step5Complete({ verificationResult, onComplete, isLoading, onReset }) {
  const verified = canProceedVerification(verificationResult);
  const recordWarning = verificationResult?.numericEvaluation?.status === 'NOT_EVALUABLE' || verificationResult?.contextEvaluation?.status === 'CONTEXT_INTEGRITY_ERROR' || ['NOT_EVALUABLE', 'MISSING_CLEANING_RECORD'].includes(verificationResult?.cleaningEvaluation?.status);
  return (
    <div className="space-y-6 text-center">
      <div>
        {verified && !recordWarning && <CheckCircleIcon className="w-16 h-16 text-green-500 mx-auto mb-4" />}
        <h2 className="text-2xl font-bold text-gray-900 mb-2">{verified ? recordWarning ? 'OWL checks complete; record review required' : 'OWL reasoning checks complete' : 'Verification required'}</h2>
        <p className="text-gray-600">{verified ? 'The configured OWL profile, consistency and inference checks completed. Review the separate record statuses below.' : stateMessage(verificationResult)}</p>
      </div>

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
          disabled={isLoading || !canProceedVerification(verificationResult)}
          className="btn-success w-full disabled:opacity-50"
        >
          {isLoading ? 'Creating...' : 'Create Ontology'}
        </button>
        <button onClick={onReset} className="btn-secondary w-full">
          Start Over
        </button>
      </div>
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
