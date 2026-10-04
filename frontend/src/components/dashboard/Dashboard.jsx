import React from 'react';
import { Link } from 'react-router-dom';
import {
  PieChart,
  Pie,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import {
  ArrowRightIcon,
  DocumentPlusIcon,
  CommandLineIcon,
  SparklesIcon,
} from '@heroicons/react/24/outline';
import {
  useDashboardStats,
  useSystemHealth,
  useRecentActivity,
} from '../../hooks/useDashboard';

const CHART_COLORS = ['#0ea5e9', '#14b8a6', '#a855f7', '#f59e0b', '#ef4444', '#06b6d4'];

function Dashboard() {
  const { data: stats, isLoading: statsLoading, error: statsError } = useDashboardStats();

  const { data: health, isLoading: healthLoading, error: healthError } = useSystemHealth();

  const { data: activity, isLoading: activityLoading } = useRecentActivity();

  // Derive chart data from stats
  const chartData = stats
    ? {
        moduleComposition: [
          { name: 'Modules', value: stats.totalModules || 0 },
        ],
        tripleDistribution: [
          { name: 'Triples', count: stats.totalTriples || 0 },
        ],
      }
    : null;

  const chartLoading = statsLoading;

  if (statsError) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-6 text-red-700">
        <h3 className="font-semibold mb-2">Error loading dashboard</h3>
        <p className="text-sm">Failed to connect to the backend API. Please ensure the server is running.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard
          label="Total Modules"
          value={stats?.totalModules ?? '-'}
          icon="📦"
          loading={statsLoading}
          trend={stats?.modulesTrend}
        />
        <StatCard
          label="Total Classes"
          value={stats?.totalClasses ?? '-'}
          icon="📋"
          loading={statsLoading}
          trend={stats?.classesTrend}
        />
        <StatCard
          label="Total Individuals"
          value={stats?.totalIndividuals ?? '-'}
          icon="👥"
          loading={statsLoading}
          trend={stats?.individualsTrend}
        />
        <StatCard
          label="Total Triples"
          value={stats?.totalTriples ?? '-'}
          icon="🔗"
          loading={statsLoading}
          trend={stats?.triplesTrend}
        />
        <StatCard
          label="Active Rules"
          value={stats?.activeAlignmentRules ?? '-'}
          icon="⚡"
          loading={statsLoading}
          trend={stats?.rulesTrend}
        />
        <StatCard
          label="Connected ERPs"
          value={stats?.connectedERPs ?? '-'}
          icon="🔌"
          loading={statsLoading}
        />
      </div>

      {/* System Health */}
      <div className="card p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">API Status</h2>
        {healthLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-4 bg-gray-200 rounded skeleton"></div>
            ))}
          </div>
        ) : (
          <ApiStatus health={health} error={healthError} />
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Module Composition Chart */}
        <div className="card p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Module Composition</h2>
          {chartLoading ? (
            <div className="h-64 bg-gray-200 rounded animate-pulse"></div>
          ) : chartData?.moduleComposition ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={chartData.moduleComposition}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={({ name, value }) => `${name}: ${value}`}
                  outerRadius={80}
                  fill="#0ea5e9"
                  dataKey="value"
                >
                  {chartData.moduleComposition.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="text-center text-gray-500 py-8">No data available</div>
          )}
        </div>

        {/* Triple Distribution Chart */}
        <div className="card p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Triple Distribution</h2>
          {chartLoading ? (
            <div className="h-64 bg-gray-200 rounded animate-pulse"></div>
          ) : chartData?.tripleDistribution ? (
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={chartData.tripleDistribution}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Bar dataKey="count" fill="#0ea5e9" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="text-center text-gray-500 py-8">No data available</div>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Activity */}
        <div className="lg:col-span-2 card p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Activity Snapshot</h2>
          {activityLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-12 bg-gray-200 rounded skeleton"></div>
              ))}
            </div>
          ) : (
            <ActivitySnapshot activity={activity} />
          )}
        </div>

        {/* Quick Actions */}
        <div className="card p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
          <div className="space-y-3">
            <Link
              to="/explorer"
              className="flex items-center justify-between p-3 rounded-lg border border-gray-200 hover:border-protys-300 hover:bg-protys-50 transition-colors duration-150"
            >
              <div className="flex items-center gap-3">
                <DocumentPlusIcon className="w-5 h-5 text-protys-500" />
                <span className="text-sm font-medium text-gray-900">Load Module</span>
              </div>
              <ArrowRightIcon className="w-4 h-4 text-gray-400" />
            </Link>
            <Link
              to="/sparql"
              className="flex items-center justify-between p-3 rounded-lg border border-gray-200 hover:border-semantic-300 hover:bg-semantic-50 transition-colors duration-150"
            >
              <div className="flex items-center gap-3">
                <CommandLineIcon className="w-5 h-5 text-semantic-500" />
                <span className="text-sm font-medium text-gray-900">Run SPARQL</span>
              </div>
              <ArrowRightIcon className="w-4 h-4 text-gray-400" />
            </Link>
            <Link
              to="/alignment"
              className="flex items-center justify-between p-3 rounded-lg border border-gray-200 hover:border-ontology-300 hover:bg-ontology-50 transition-colors duration-150"
            >
              <div className="flex items-center gap-3">
                <SparklesIcon className="w-5 h-5 text-ontology-500" />
                <span className="text-sm font-medium text-gray-900">Execute Reasoning</span>
              </div>
              <ArrowRightIcon className="w-4 h-4 text-gray-400" />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, icon, loading, trend }) {
  return (
    <div className="card p-6">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-gray-600 mb-1">{label}</p>
          {loading ? (
            <div className="h-8 w-24 bg-gray-200 rounded skeleton"></div>
          ) : (
            <p className="text-3xl font-bold text-gray-900">{value}</p>
          )}
          {trend && (
            <p className={`text-xs mt-2 ${trend > 0 ? 'text-green-600' : 'text-red-600'}`}>
              {trend > 0 ? '↑' : '↓'} {Math.abs(trend)}% from last week
            </p>
          )}
        </div>
        <div className="text-3xl">{icon}</div>
      </div>
    </div>
  );
}

export function ApiStatus({ health, error }) {
  const status = error ? 'Request failed' : health?.status ?? 'Not reported';
  const services = !error && health?.services ? Object.entries(health.services) : [];
  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between py-2">
        <span className="text-sm text-gray-700">API response</span>
        <span className={`px-3 py-1 rounded-full text-xs font-medium ${error ? 'bg-red-100 text-red-700' : 'bg-gray-100 text-gray-700'}`}>
          {status}
        </span>
      </div>
      <h3 className="text-sm font-semibold text-gray-900">Registered services</h3>
      <p className="text-xs text-gray-600">The API declares these services. Availability of the triple store, reasoning engine and ERP connections requires separate checks.</p>
      {services.length > 0 ? (
        <ul className="space-y-2">
          {services.map(([name, declaredStatus]) => (
            <li key={name} className="flex flex-wrap items-center justify-between gap-2 text-sm text-gray-700">
              <span>{name}</span>
              <span className="text-xs text-gray-500">Declared: {declaredStatus}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-gray-500">No service declaration received</p>
      )}
    </div>
  );
}

export function ActivitySnapshot({ activity }) {
  if (!activity) return <p className="text-sm text-gray-500">No activity snapshot received</p>;
  const entries = [
    ['Snapshot timestamp', activity.timestamp],
    ['Last updated', activity.lastUpdated],
    ['Loaded modules', activity.loadedModules],
    ['Classes', activity.totalClasses],
    ['Individuals', activity.totalIndividuals],
    ['Triples', activity.totalTriples],
  ];
  return (
    <dl className="space-y-3">
      {entries.map(([label, value]) => (
        <div key={label} className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-200 pb-2">
          <dt className="text-sm text-gray-600">{label}</dt>
          <dd className="text-sm text-gray-900 break-all">{value ?? 'Not recorded'}</dd>
        </div>
      ))}
    </dl>
  );
}

export default Dashboard;
