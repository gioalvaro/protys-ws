import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { StaticRouter } from 'react-router-dom/server';
import Dashboard, { ApiStatus, ActivitySnapshot, formatTimestamp, moduleChartData } from './Dashboard';
import health from './__fixtures__/dashboard-health.json';
import activity from './__fixtures__/dashboard-activity.json';
import stats from './__fixtures__/dashboard-stats.json';
import { useDashboardStats, useSystemHealth, useRecentActivity } from '../../hooks/useDashboard';

jest.mock('../../hooks/useDashboard', () => ({
  useDashboardStats: jest.fn(),
  useSystemHealth: jest.fn(),
  useRecentActivity: jest.fn(),
}));

function containerFor(element) {
  const container = document.createElement('div');
  container.innerHTML = renderToStaticMarkup(element);
  return container;
}

test('real health payload declares services without claiming dependency checks', () => {
  const view = containerFor(<ApiStatus health={health} />);
  expect(view.textContent).toContain('API responseUP');
  expect(view.textContent).toContain('Registered services');
  expect(view.textContent).toContain('ERPConnectorService');
  expect(view.querySelectorAll('li')).toHaveLength(5);
  expect(view.textContent.match(/Declared: AVAILABLE/g)).toHaveLength(5);
  expect(view.textContent).toContain('requires separate checks');
  expect(view.textContent).not.toMatch(/healthy|API Server|Triple Store/);
});

test('a failed health request does not present stale UP or service declarations', () => {
  const view = containerFor(<ApiStatus health={health} error={new Error('network failure')} />);
  expect(view.textContent).toContain('Request failed');
  expect(view.textContent).not.toContain('UP');
  expect(view.querySelectorAll('li')).toHaveLength(0);
  expect(view.textContent).toContain('No service declaration received');
});

test('a missing health response stays unreported', () => {
  const view = containerFor(<ApiStatus />);
  expect(view.textContent).toContain('Not reported');
  expect(view.querySelectorAll('li')).toHaveLength(0);
});

test('real activity object renders a snapshot and its recorded counts', () => {
  const view = containerFor(<ActivitySnapshot activity={activity} />);
  const entries = Object.fromEntries(Array.from(view.querySelectorAll('dl > div')).map(entry => [
    entry.querySelector('dt').textContent, entry.querySelector('dd').textContent,
  ]));
  expect(entries).toEqual({
    'Snapshot timestamp': formatTimestamp(activity.timestamp),
    'Last updated': formatTimestamp(activity.lastUpdated),
    'Loaded modules': '1',
    Classes: '128',
    Individuals: '621',
    Triples: '4316',
  });
});

test('activity snapshot retains null lastUpdated as unrecorded', () => {
  const view = containerFor(<ActivitySnapshot activity={{ ...activity, lastUpdated: null }} />);
  const entry = Array.from(view.querySelectorAll('dl > div')).find(
    item => item.querySelector('dt').textContent === 'Last updated'
  );
  expect(entry.querySelector('dd').textContent).toBe('Not recorded');
});

test('dashboard consumes the real DTO names instead of absent rule/ERP fields', () => {
  useDashboardStats.mockReturnValue({ data: stats, isLoading: false });
  useSystemHealth.mockReturnValue({ data: health, isLoading: false });
  useRecentActivity.mockReturnValue({ data: activity, isLoading: false });
  const view = containerFor(<StaticRouter><Dashboard /></StaticRouter>);
  function cardValue(label) {
    const cardLabel = Array.from(view.querySelectorAll('p')).find(p => p.textContent === label);
    return cardLabel.nextElementSibling.textContent;
  }
  // The captured DTO reports zero; the test does not replace it with the rule-list count.
  expect(cardValue('Active Rules')).toBe('0');
  expect(cardValue('Connected ERPs')).toBe('0');
  expect(view.textContent).toContain('Activity Snapshot');
  expect(view.textContent).toContain('Last updated');
  expect(view.textContent).not.toContain('No recent activity');
});

test('timestamps drop fractional seconds and keep the server date and time', () => {
  expect(formatTimestamp('2026-11-17T14:54:05.353452769')).toBe('2026-11-17 14:54:05');
  expect(formatTimestamp(null)).toBeNull();
  expect(formatTimestamp('not a date')).toBe('not a date');
});

test('charts break the totals down per module instead of a single slice', () => {
  const rows = moduleChartData({
    moduleStats: [
      { moduleName: 'iso10303-ap242-fragment', tripleCount: 46, classCount: 6, individualCount: 0 },
      { moduleName: 'Canonical_Catalog_Integrated', tripleCount: 4318, classCount: 128, individualCount: 165 },
      { moduleName: 'iso10303-ap242-fragment — datos de instancia', tripleCount: 203, classCount: null, individualCount: 53 },
    ],
  });
  expect(rows.map(row => row.triples)).toEqual([4318, 203, 46]);
  expect(rows[1].classes).toBe(0);
  expect(rows[1].label.length).toBeLessThanOrEqual(28);
  expect(rows[1].name).toBe('iso10303-ap242-fragment — datos de instancia');
  expect(moduleChartData(undefined)).toEqual([]);
});
