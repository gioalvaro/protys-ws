import React, { useState } from 'react';
import { Routes, Route, Link, useLocation } from 'react-router-dom';
import {
  Bars3Icon,
  XMarkIcon,
  HomeIcon,
  SparklesIcon,
  CommandLineIcon,
  ArrowRightIcon,
  CircleStackIcon,
  WrenchIcon,
} from '@heroicons/react/24/outline';
import Dashboard from './components/dashboard/Dashboard';
import OntologyExplorer from './components/explorer/OntologyExplorer';
import SparqlConsole from './components/sparql/SparqlConsole';
import Alignment from './components/alignment/Alignment';
import ErpIntegration from './components/erp/ErpIntegration';
import Wizard from './components/wizard/Wizard';

const NAVIGATION = [
  {
    name: 'Dashboard',
    href: '/',
    icon: HomeIcon,
    description: 'Overview and system health',
  },
  {
    name: 'Ontology Explorer',
    href: '/explorer',
    icon: SparklesIcon,
    description: 'Browse and manage ontologies',
  },
  {
    name: 'SPARQL Console',
    href: '/sparql',
    icon: CommandLineIcon,
    description: 'Execute semantic queries',
  },
  {
    name: 'Alignment',
    href: '/alignment',
    icon: ArrowRightIcon,
    description: 'Manage alignment rules',
  },
  {
    name: 'ERP Integration',
    href: '/erp',
    icon: CircleStackIcon,
    description: 'Connect external systems',
  },
  {
    name: 'Wizard',
    href: '/wizard',
    icon: WrenchIcon,
    description: 'Guided ontology setup',
  },
];

function App() {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const location = useLocation();

  const getCurrentPath = () => {
    return NAVIGATION.find(nav => nav.href === location.pathname)?.name || 'Dashboard';
  };

  const isActive = (href) => location.pathname === href;

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 bg-white border-r border-gray-200 transform transition-transform duration-300 ease-in-out overflow-y-auto ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Logo Section */}
        <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 z-10">
          <Link to="/" className="flex items-center gap-3 group">
            <div className="w-10 h-10 bg-gradient-to-br from-protys-500 to-semantic-500 rounded-lg flex items-center justify-center text-white font-bold">
              PW
            </div>
            <div>
              <div className="text-sm font-bold text-gray-900">PROTYS-WS</div>
              <div className="text-xs text-gray-500">Semantic Web</div>
            </div>
          </Link>
        </div>

        {/* Navigation */}
        <nav className="space-y-1 px-3 py-6">
          {NAVIGATION.map((item) => {
            const Icon = item.icon;
            const active = isActive(item.href);
            return (
              <Link
                key={item.href}
                to={item.href}
                className={`flex items-start gap-3 px-4 py-3 rounded-lg transition-colors duration-150 group ${
                  active
                    ? 'bg-protys-50 text-protys-600 border border-protys-200'
                    : 'text-gray-700 hover:bg-gray-50 border border-transparent'
                }`}
              >
                <Icon
                  className={`w-5 h-5 flex-shrink-0 mt-0.5 ${
                    active ? 'text-protys-600' : 'text-gray-400 group-hover:text-gray-600'
                  }`}
                />
                <div className="flex-1">
                  <div className="text-sm font-medium">{item.name}</div>
                  <div
                    className={`text-xs ${
                      active ? 'text-protys-500' : 'text-gray-500 group-hover:text-gray-600'
                    }`}
                  >
                    {item.description}
                  </div>
                </div>
              </Link>
            );
          })}
        </nav>

        {/* Footer Info */}
        <div className="absolute bottom-0 left-0 right-0 border-t border-gray-200 bg-gray-50 p-4">
          <div className="text-xs text-gray-600 space-y-1">
            <div className="font-medium">Status</div>
            <div className="flex items-center gap-2">
              <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
              <span>Connected</span>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <div className={`flex-1 flex flex-col transition-all duration-300 ${sidebarOpen ? 'ml-64' : 'ml-0'}`}>
        {/* Top Bar */}
        <header className="sticky top-0 z-40 bg-white border-b border-gray-200 shadow-sm">
          <div className="px-6 py-4 flex items-center justify-between">
            <div className="flex items-center gap-4">
              <button
                onClick={() => setSidebarOpen(!sidebarOpen)}
                className="p-2 hover:bg-gray-100 rounded-lg transition-colors duration-150"
              >
                {sidebarOpen ? (
                  <XMarkIcon className="w-5 h-5 text-gray-600" />
                ) : (
                  <Bars3Icon className="w-5 h-5 text-gray-600" />
                )}
              </button>
              <div className="flex items-center gap-2">
                <h1 className="text-lg font-semibold text-gray-900">{getCurrentPath()}</h1>
              </div>
            </div>

            {/* Right side actions */}
            <div className="flex items-center gap-4">
              <div className="px-3 py-1 bg-protys-50 text-protys-700 rounded-full text-xs font-medium">
                v1.0.0
              </div>
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-auto">
          <div className="p-6">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/explorer" element={<OntologyExplorer />} />
              <Route path="/sparql" element={<SparqlConsole />} />
              <Route path="/alignment" element={<Alignment />} />
              <Route path="/erp" element={<ErpIntegration />} />
              <Route path="/wizard" element={<Wizard />} />
              <Route path="*" element={<NotFound />} />
            </Routes>
          </div>
        </main>
      </div>
    </div>
  );
}

function NotFound() {
  return (
    <div className="flex items-center justify-center h-96">
      <div className="text-center">
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Page not found</h2>
        <p className="text-gray-600 mb-4">The page you're looking for doesn't exist.</p>
        <Link to="/" className="btn-primary">
          Back to Dashboard
        </Link>
      </div>
    </div>
  );
}

export default App;
