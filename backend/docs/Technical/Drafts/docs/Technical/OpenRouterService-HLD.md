# OpenRouterService Design Document

## Overview
The `OpenRouterService` is a new Spring-managed class that implements the `LlmService` interface. It serves as a REST client targeting two LLM endpoints: OpenRouter's `/chat/completions` for generative AI tasks and Gemini's `/models/{model}:embedContent` for embedding operations. The service uses Jackson `ObjectMapper` for JSON processing and `DualSummary` as the structured output data model. It enables AI-driven capabilities for PR diff analysis, technical documentation synthesis, and business document generation.

## Architecture & Data Flow
- The class is configured as a Spring component via `@ConditionalOnProperty`, activating based on configured property values.
- Dependencies injected via `@Value` annotations for endpoint URLs, model names, and API keys.
- REST calls flow through a Spring-managed client to the OpenRouter `/chat/completions` endpoint for generation requests and the Gemini `/models/{model}:embedContent` endpoint for embedding requests.
- JSON request/response bodies are serialized and deserialized using Jackson `ObjectMapper`.
- Output from both endpoints is mapped to the `DualSummary` structured data model, which serves as the canonical return type for LlmService methods.
- Initialization is triggered by `@PostConstruct`, ensuring configuration is ready before service operations begin.

## APIs & Data Models
- **OpenRouter API**: `POST /chat/completions` — used for AI-driven text generation tasks.
- **Gemini API**: `GET /models/{model}:embedContent` — used for generating vector embeddings.
- **LlmService interface**: implemented by `OpenRouterService`, defining the contract for LLM interactions.
- **DualSummary**: structured output data model representing consolidated AI response data.
- **Jackson ObjectMapper**: handles all JSON marshaling and unmarshaling for API requests and responses.

## Dependencies
- `jakarta.annotation.PostConstruct` — lifecycle initialization annotation.
- `Lombok Slf4j` — logging abstraction and utilities.
- `Spring @ConditionalOnProperty` — conditional bean activation based on configuration.
- `Spring @Value` — injection of configuration properties (e.g., API endpoints, keys).
- `Jackson ObjectMapper` — JSON processing for API integration.
- `LlmService` interface — core service contract.

## Key Behaviors
- Implements all methods defined in the `LlmService` interface.
- Executes PR diff analysis via OpenRouter generative endpoints.
- Synthesizes technical documentation using configured LLM models.
- Generates business documents by mapping AI responses to `DualSummary` structure.
- Logs operational flow via Lombok Slf4j.
- Starts up conditionally based on `@ConditionalOnProperty` settings, avoiding unnecessary initialization when LLM features are disabled.