# ============================================================================
# PROTYS-WS FRONTEND - REACT APPLICATION
# Multi-stage Docker build for UI
# ============================================================================

# Stage 1: Build React application
FROM node:24.18.0-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS builder

WORKDIR /app

# Copy package files
COPY package.json package-lock.json .npmrc ./

# Install dependencies
RUN test "$(node --version)" = "v24.18.0" \
    && test "$(npm --version)" = "11.16.0" \
    && npm ci --no-audit --no-fund

# Copy source code
COPY . .

# Build application
ARG REACT_APP_API_URL=/api
ENV REACT_APP_API_URL=${REACT_APP_API_URL}
ENV CI=true
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:1.30.5-alpine@sha256:0985e772fb9f729e6fa0980da05fca5d9c468e870eed43071545afa9d2e27d94

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Copy built application from builder stage
COPY --from=builder /app/build /usr/share/nginx/html

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:80/health || exit 1

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
