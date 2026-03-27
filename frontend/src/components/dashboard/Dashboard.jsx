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

  const { data: health, isLoading: healthLoading } = useSystemHealth();

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
          value={stats?.activeRules ?? '-'}
          icon="⚡"
          loading={statsLoading}
          trend={stats?.rulesTrend}
        />
        <StatCard
          label="Connected ERPs"
          value={stats?.connectedErps ?? '-'}
          icon="🔌"
          loading={statsLoading}
        />
      </div>

      {/* System Health */}
      <div className="card p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">System Health</h2>
        {healthLoading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-4 bg-gray-200 rounded skeleton"></div>
            ))}
          </div>
        ) : (
          <div className="space-y-3">
            <HealthIndicator label="API Server" status={health?.apiStatus} />
            <HealthIndicator label="Triple Store" status={health?.tripleStoreStatus} />
            <HealthIndicator label="Reasoning Engine" status={health?.reasoningStatus} />
            <HealthIndicator label="ERP Connectors" status={health?.erpStatus} />
          </div>
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
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Recent Activity</h2>
          {activityLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-12 bg-gray-200 rounded skeleton"></div>
              ))}
            </div>
          ) : activity?.length > 0 ? (
            <div className="space-y-3">
              {activity.slice(0, 5).map((item, idx) => (
                <div
                  key={idx}
                  className="flex items-start gap-3 pb-3 border-b border-gray-200 last:border-0"
                >
                  <div className="w-2 h-2 bg-protys-500 rounded-full mt-2 flex-shrink-0"></div>
                  <div className="flex-1">
                    <p className="text-sm text-gray-900">{item.description}</p>
                    <p className="text-xs text-gray-500 mt-1">{item.timestamp}</p>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-center text-gray-500 py-8">No recent activity</div>
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

function HealthIndicator({ label, status }) {
  const statusColor = {
    'healthy': 'bg-green-100 text-green-700',
    'warning': 'bg-yellow-100 text-yellow-700',
    'error': 'bg-red-100 text-red-700',
  }[status] || 'bg-gray-100 text-gray-700';

  const statusIcon = {
    'healthy': '✓',
    'warning': '⚠',
    'error': '✕',
  }[status] || '?';

  return (
    <div className="flex items-center justify-between py-2">
      <span className="text-sm text-gray-700">{label}</span>
      <span className={`px-3 py-1 rounded-full text-xs font-medium flex items-center gap-1 ${statusColor}`}>
        <span>{statusIcon}</span>
        <span className="capitalize">{status || 'unknown'}</span>
      </span>
    </div>
  );
}

export default Dashboard;
