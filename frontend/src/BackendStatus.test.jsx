import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { BackendStatus } from './App';
import { useSystemHealth } from './hooks/useDashboard';

jest.mock('./hooks/useDashboard', () => ({ useSystemHealth: jest.fn() }));

const text = () => {
  const container = document.createElement('div');
  container.innerHTML = renderToStaticMarkup(<BackendStatus />);
  return container.textContent;
};

test('the sidebar reports the backend health instead of a fixed "Connected"', () => {
  useSystemHealth.mockReturnValue({ data: { status: 'UP' }, isLoading: false, isError: false });
  expect(text()).toContain('Backend connected');
  useSystemHealth.mockReturnValue({ data: undefined, isLoading: false, isError: true });
  expect(text()).toContain('Backend unreachable');
  useSystemHealth.mockReturnValue({ data: undefined, isLoading: true, isError: false });
  expect(text()).toContain('Checking backend');
});
