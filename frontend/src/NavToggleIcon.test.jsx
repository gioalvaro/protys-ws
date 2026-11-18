import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { NavToggleIcon } from './App';

jest.mock('./hooks/useDashboard', () => ({ useSystemHealth: jest.fn() }));

const render = (open) => {
  const view = document.createElement('div');
  view.innerHTML = renderToStaticMarkup(<NavToggleIcon open={open} />);
  return view;
};

test('with the sidebar open, narrow screens show a close icon and wide screens keep the menu icon', () => {
  const open = render(true);
  expect(open.querySelector('[data-testid="nav-close"]').getAttribute('class')).toContain('lg:hidden');
  expect(open.querySelector('[data-testid="nav-menu"]').getAttribute('class')).toContain('hidden lg:block');
  const closed = render(false);
  expect(closed.querySelector('[data-testid="nav-close"]')).toBeNull();
  expect(closed.querySelectorAll('svg')).toHaveLength(1);
});
