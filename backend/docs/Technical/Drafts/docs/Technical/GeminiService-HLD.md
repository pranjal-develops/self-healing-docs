# GeminiService Design Document

## Overview
The GeminiService module provides a Spring-integrated implementation of the `LlmService` interface, specifically designed to interact with Google's Gemini AI API. It abstracts the `generateContent` and `embContent` REST endpoints, enabling LLM functionality within the docdebt application. Activation is conditional via the `docdebt.llm.provider` property, and the service is configured using Spring-driven properties for API key, model selection, and base URL.

## Architecture & Data Flow
- The `GeminiService` class implements `LlmService`, acting as a dedicated adapter for Gemini's REST APIs.
- Outbound HTTP requests to `generateContent` and `embContent` endpoints are executed via `RestTemplate`.
- Inbound and outbound JSON payloads are processed using `ObjectMapper`, forming an internal abstraction layer for reliable data mapping and serialization/deserialization.
- Spring configuration drives the API base URL, model name, and API key, injected and applied per request.
- Component activation is governed by `@ConditionalOnProperty`, ensuring the service is only instantiated when `docdebt.llm.provider` is set to the designated Gemini provider value.

## APIs & Data Models
- `generateContent`: REST endpoint for generating LLM responses. Request parameters and JSON payloads are mapped via `ObjectMapper` and transmitted through `RestTemplate`.
- `embContent`: REST endpoint for generating embeddings. Follows the same JSON mapping and HTTP execution pattern as `generateContent`, aligned with Gemini's API contract.
- Data models are structured to support Gemini's request/response JSON formats, serialized and deserialized via `ObjectMapper` within the service's internal abstraction layer.

## Dependencies
- **Spring Framework**: `@ConditionalOnProperty` annotation and Spring-driven configuration properties for API key, model selection, and base URL.
- **`RestTemplate`**: Synchronous HTTP client for invoking Gemini REST endpoints.
- **`ObjectMapper`** (Jackson): JSON processing for request/response mapping and the internal abstraction layer.
- **`LlmService`**: Domain interface implemented by `GeminiService`.
- **Conditional activation**: Controlled by the `docdebt.llm.provider` property.

## Key Behaviors
- Implements `LlmService` interface methods, delegating to Gemini's `generateContent` and `embContent` endpoints.
- Executes HTTP requests via `RestTemplate` with configured base URL and authentication using the injected API key.
- Serializes input parameters and deserializes response bodies using `ObjectMapper`.
- Respects Spring conditional activation: only active when `docdebt.llm.provider` matches the Gemini provider setting.
- Supports dynamic model selection and base URL configuration via Spring properties.