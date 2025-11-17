# Multi-stage build for Bootie application

# Stage 1: Build stage
FROM eclipse-temurin:25-jdk-noble AS builder

# Install sbt
RUN apt-get update && \
    apt-get install -y curl gnupg && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add && \
    apt-get update && \
    apt-get install -y sbt && \
    rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy build files
COPY build.sbt .
COPY project ./project

# Download dependencies (cached layer)
RUN sbt update

# Copy source code
COPY src ./src

# Build the application
RUN sbt clean compile stage

# Stage 2: Runtime stage
FROM eclipse-temurin:25-jre-noble

# Create app user
RUN groupadd -r bootie && useradd -r -g bootie bootie

# Set working directory
WORKDIR /app

# Copy the built application from builder stage
COPY --from=builder /app/target/universal/stage /app

# Change ownership
RUN chown -R bootie:bootie /app

# Switch to app user
USER bootie

# Expose port (if needed in the future for metrics/health checks)
EXPOSE 8080

# Set environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
CMD ["/bin/bash", "-c", "/app/bin/bootie $JAVA_OPTS"]
