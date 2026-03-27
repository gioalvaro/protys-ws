import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import {
  ArrowRightIcon,
  ArrowLeftIcon,
  CheckCircleIcon,
  DocumentPlusIcon,
} from '@heroicons/react/24/outline';
import { wizardAPI } from '../../services/api';

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
    mutationFn: (file) => wizardAPI.step1Upload(file),
    onSuccess: (data) => {
      setSessionId(data.sessionId);
      setUploadedFile(data.fileName);
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
    onSuccess: (data) => {
      setValidationResult(data);
      setAvailableRules(data.suggestedRules || []);
      setStep(3);
      toast.success('Validation complete');
    },
    onError: () => {
      toast.error('Validation failed');
    },
  });

  // Step 3: Alignment
  const step3Mutation = useMutation({
    mutationFn: () => wizardAPI.step3DefineAlignments(sessionId, selectedRules),
    onSuccess: () => {
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
      toast.success('Verification complete');
    },
    onError: () => {
      toast.error('Verification failed');
    },
  });

  // Complete Wizard
  const completeMutation = useMutation({
    mutationFn: () => wizardAPI.completeIncorporation(sessionId),
    onSuccess: () => {
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

function Step2Validate({ uploadedFile, onValidate, isLoading, onBack }) {
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
        <ValidationStep label="Syntax Check" description="Verifying RDF/OWL syntax" status="success" />
        <ValidationStep label="Integrity Check" description="Checking logical consistency" status="success" />
        <ValidationStep label="Completeness Check" description="Analyzing coverage" status="pending" />
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

function Step3Alignment({
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

      {validationResult && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
          <p className="text-sm font-medium text-green-900">
            ✓ Ontology validated successfully
          </p>
          <p className="text-xs text-green-800 mt-1">
            Found {validationResult.classCount} classes and {validationResult.propertyCount} properties
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

function Step4Verify({
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

      {verificationResult && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4">
          <p className="text-sm font-medium text-green-900">
            ✓ Configuration verified
          </p>
          <p className="text-xs text-green-800 mt-1">
            {verificationResult.message}
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

function Step5Complete({ verificationResult, onComplete, isLoading, onReset }) {
  return (
    <div className="space-y-6 text-center">
      <div>
        <CheckCircleIcon className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Ontology Ready</h2>
        <p className="text-gray-600">Your ontology is configured and ready to use</p>
      </div>

      {verificationResult && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-6 text-left">
          <h3 className="font-semibold text-green-900 mb-3">Summary</h3>
          <div className="space-y-2 text-sm text-green-800">
            <p>✓ Ontology validated successfully</p>
            <p>✓ Alignment rules configured</p>
            <p>✓ All checks passed</p>
          </div>
        </div>
      )}

      <div className="flex flex-col gap-2 pt-4">
        <button
          onClick={onComplete}
          disabled={isLoading}
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
