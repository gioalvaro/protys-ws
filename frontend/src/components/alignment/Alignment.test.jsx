import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Alignment, { RuleDates } from './Alignment';
import rules from './__fixtures__/alignment-rules.json';
import stats from './__fixtures__/alignment-stats.json';
import {
  useAlignmentRules, useUploadRules, useToggleRule, useExecuteReasoning, useInferenceStats,
} from '../../hooks/useAlignment';

jest.mock('../../hooks/useAlignment', () => ({
  useAlignmentRules: jest.fn(),
  useUploadRules: jest.fn(),
  useToggleRule: jest.fn(),
  useExecuteReasoning: jest.fn(),
  useInferenceStats: jest.fn(),
}));

function containerFor(element) {
  const container = document.createElement('div');
  container.innerHTML = renderToStaticMarkup(element);
  return container;
}

beforeEach(() => {
  useAlignmentRules.mockReturnValue({ data: rules, isLoading: false });
  useInferenceStats.mockReturnValue({ data: stats });
  useUploadRules.mockReturnValue({ isPending: false });
  useToggleRule.mockReturnValue({ isPending: false });
  useExecuteReasoning.mockReturnValue({ isPending: false });
});

test('real rule model renders createdAt and updatedAt instead of uploadedAt', () => {
  const view = containerFor(<RuleDates rule={rules[0]} />);
  expect(view.querySelectorAll('time')).toHaveLength(2);
  expect(view.querySelectorAll('time')[0].getAttribute('datetime')).toBe(rules[0].createdAt);
  expect(view.querySelectorAll('time')[1].getAttribute('datetime')).toBe(rules[0].updatedAt);
  expect(view.textContent).toContain('Created:');
  expect(view.textContent).toContain('Updated:');
  expect(view.textContent).not.toMatch(/Invalid Date|Uploaded:|Last executed:/);
});

test('missing or malformed dates are unrecorded rather than Invalid Date', () => {
  const view = containerFor(<RuleDates rule={{ createdAt: null, updatedAt: 'invalid timestamp' }} />);
  expect(view.querySelectorAll('time')).toHaveLength(0);
  expect(view.textContent.match(/Not recorded/g)).toHaveLength(2);
  expect(view.textContent).not.toContain('Invalid Date');
});

test('statistics use materialized and input triple counts from the real DTO', () => {
  const client = new QueryClient();
  const view = containerFor(<QueryClientProvider client={client}><Alignment /></QueryClientProvider>);
  function cardValue(label) {
    const cardLabel = Array.from(view.querySelectorAll('p')).find(p => p.textContent === label);
    return cardLabel.nextElementSibling.textContent;
  }
  expect(cardValue('Total Rules')).toBe('22');
  expect(cardValue('Active Rules')).toBe('22');
  expect(cardValue('Materialized Inferred Triples')).toBe('1132');
  expect(cardValue('Input Triples')).toBe('4316');
  expect(view.textContent).not.toMatch(/Last Inferences|Total Inferences|Invalid Date/);
});

test('missing inference statistics do not become measured zeros', () => {
  useInferenceStats.mockReturnValue({ data: undefined });
  const client = new QueryClient();
  const view = containerFor(<QueryClientProvider client={client}><Alignment /></QueryClientProvider>);
  for (const label of ['Active Rules', 'Materialized Inferred Triples', 'Input Triples']) {
    const cardLabel = Array.from(view.querySelectorAll('p')).find(p => p.textContent === label);
    expect(cardLabel.nextElementSibling.textContent).toBe('-');
  }
});
