# PROTYS-WS Frontend

A modern React 18 frontend for PROTYS-WS - a semantic web application for managing manufacturing ontologies.

## Features

- **Dashboard**: Overview of your ontology statistics, system health, and quick actions
- **Ontology Explorer**: Browse and manage classes and individuals in your ontologies
- **SPARQL Console**: Execute semantic queries with templates and competency questions
- **Alignment & Reasoning**: Manage alignment rules and execute OWL reasoning
- **ERP Integration**: Connect and materialize data from external systems
- **Guided Wizard**: Step-by-step ontology creation and configuration

## Tech Stack

- **React 18.2** - UI framework
- **React Router 6** - Client-side routing
- **Tailwind CSS 3** - Styling
- **Axios** - HTTP client
- **React Query** - Server state management
- **Recharts** - Data visualization
- **CodeMirror** - Code editors
- **React Toastify** - Notifications
- **Heroicons** - Icons

## Getting Started

### Prerequisites

- Node.js 16+ and npm
- Backend API running at `http://localhost:8080/api`

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm start

# Build for production
npm build

# Run tests
npm test
```

The app will open at `http://localhost:3000`

## Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── dashboard/
│   │   ├── explorer/
│   │   ├── sparql/
│   │   ├── alignment/
│   │   ├── erp/
│   │   └── wizard/
│   ├── services/
│   │   └── api.js          # API client and endpoints
│   ├── App.jsx             # Main app component
│   ├── index.js            # Entry point
│   └── index.css           # Global styles
├── public/
│   └── index.html
├── package.json
├── tailwind.config.js
├── postcss.config.js
└── README.md
```

## API Integration

The frontend communicates with the backend REST API at `http://localhost:8080/api`. All endpoints are documented in `src/services/api.js`:

- `/dashboard` - Dashboard statistics and health
- `/ontology` - Module and class management
- `/sparql` - Query execution and templates
- `/alignment` - Rule management and reasoning
- `/erp` - ERP connectors and materialization
- `/wizard` - Guided setup workflow

## Component Overview

### Dashboard
- System statistics cards
- Health indicators
- Module and triple distribution charts
- Recent activity timeline
- Quick action buttons

### Ontology Explorer
- Module selector
- Hierarchical class tree with search
- Class and individual details
- Add/edit/delete individuals
- Property display and navigation

### SPARQL Console
- Query editor with syntax support
- Query templates and competency questions
- Result table with export options
- Query validation

### Alignment
- Rule management (upload, activate, delete)
- Statistics display
- Reasoning execution
- Rule examples and documentation

### ERP Integration
- Connector management
- Connection testing
- Schema introspection
- Data materialization
- Integration guide

### Wizard
- 5-step guided process
- File upload
- Validation
- Alignment rule selection
- Configuration verification
- Completion

## Styling

Uses Tailwind CSS with custom PROTYS theme colors:

- **Primary**: Protys Blue (`#0ea5e9`)
- **Semantic**: Teal accent (`#14b8a6`)
- **Ontology**: Purple accent (`#a855f7`)

Custom components are defined in `src/index.css` for consistent styling:
- `.card` - Card containers
- `.btn-*` - Button variants
- `.input` - Form inputs
- `.badge` - Badge components
- `.label` - Form labels

## State Management

- **Server State**: React Query for API data
- **Client State**: React hooks (useState)
- **Form State**: Local component state with mutations

## Error Handling

All API requests include error handling with toast notifications. Network errors are automatically caught and displayed to the user.

## Development

```bash
# Start with hot reload
npm start

# Build optimized production bundle
npm build

# Run tests
npm test -- --watch
```

## Browser Support

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## License

Proprietary - PROTYS Team
