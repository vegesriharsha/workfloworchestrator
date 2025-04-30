# Workflow Orchestrator Project Guide

## Project Overview
The Workflow Orchestrator is a robust, extensible platform for defining, executing, and managing complex workflows. It enables organizations to automate complex business processes through configurable workflows with sophisticated execution strategies.

## Key Components
- **Core Engine**: Central workflow execution management with multiple execution strategies
- **Task Execution**: Multiple executor types for different environments (REST, RabbitMQ, Sub-workflows)
- **Event System**: Event-driven architecture for asynchronous communication
- **Human Interaction**: User review points that pause workflows for approval

## Development Commands

### Setup and Build
```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test
```

### Workflow Execution
- Sequential, Parallel, Conditional, and Graph-based execution strategies
- Support for sub-workflows
- Configurable retry mechanisms
- Human approval integration

### Useful Files
- `WorkflowEngine.java`: Core workflow orchestration logic
- `ExecutionStrategy.java`: Interface for workflow execution patterns
- `TaskExecutor.java`: Task execution interface
- `WorkflowDefinition.java`: Schema for workflow definitions

## /init Command Output
Use `/init` to get an overview of the project structure and primary files to understand the system architecture.

## Development Tips
- Review execution strategies in `engine/strategy` to understand workflow patterns
- Examine task executor implementations to understand extension points
- Use the sample package for implementation examples
- Ensure proper transaction management for workflow state persistence