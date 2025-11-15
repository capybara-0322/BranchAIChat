# MyAIProject

MyAIProject is a full‑stack conversational AI playground. The backend (`demo/`) is a Spring Boot 3.5 application on Java 17 that orchestrates chat sessions, tree‑structured turns, streaming SSE responses, Redis caching, and optional RAG enrichment. The frontend (`frontend/`) is a Vue powered client (bundled for nginx inside `spring-ai-nginx/`). Supporting folders host research docs, logs, and vector store snapshots for knowledge-grounded conversations.

## Repository Layout

.
├─demo/ # Spring Boot backend (MyBatis, Redis, Spring AI)
│ ├─src/main/java/com/example/ai
│ │ ├─controller/ # Auth, sessions, turns, SSE chat endpoints
│ │ ├─service/ # Chat, Session, Turn, User services
│ │ ├─mapper/ + entity/ # MyBatis mappers & models
│ │ ├─advisor/ # Conversation/RAG advisors for Spring AI
│ │ ├─security/ + util/ # JWT, interceptors, helpers
│ │ └─common/ # Result wrappers, pagination helpers
│ ├─src/main/resources/
│ │ ├─application.yml # Datasource, Redis, AI model, JWT, vectorstore config
│ │ └─com/example/ai/mapper/*.xml
│ └─src/test/ # JUnit + H2 fixtures
├─frontend/ # Vue client (components, pages, api, dist, etc.)
├─spring-ai-nginx/ # Packaging & nginx templates for deployment
├─github_docs/ # Design notes & API research
├─runLog/ # Timestamped backend logs
└─vectorstoreData/ # Persisted embedding stores (chat PDFs, etc.)


## Features

- **Authentication & Authorization**: JWT login plus `@RequireAuth`-guarded REST/SSE endpoints enforced by `AuthInterceptor`.
- **Session & Turn Management**: Tree-structured conversations with pagination, subtree deletion, “latest chain” traversal, Redis-backed caches for children/heights, and UUID/tid dual identifiers.
- **Streaming Chat APIs**: SSE endpoints for standard and RAG-enhanced chats, emitting chunk/done events compatible with the web client.
- **RAG & Vector Store**: Spring AI `ChatClient` advisors enrich prompts from external RAG services (`RagEnhancementAdvisor`) or a persisted `SimpleVectorStore` loaded from `vectorstore.path`.
- **Observability**: Structured logging via Logback (console WARN+, file INFO+) with rolling archives in `runLog/`.
- **Tests & Fixtures**: H2-backed integration and regression suites (`DeleteTurnWithLastActiveTidTest`, `HeightServiceTest`, mapper smoke tests).

## Tech Stack

- Java 17, Spring Boot 3.5.5, Spring AI 1.0.x
- MyBatis + MySQL (prod), H2 (tests)
- Redis (Lettuce) for session metadata caches
- Reactor (Flux/Mono) for SSE streaming
- JWT (jjwt) for auth tokens
- Vue + Vite for the frontend, Pinia stores
- Nginx (spring-ai-nginx) for packaging static assets

## Prerequisites

- JDK 17+
- Maven Wrapper (bundled)
- Node.js 18+ & npm
- MySQL & Redis instances (matching `application.yml`)
- Optional: external RAG service at `http://127.0.0.1:7000` and DashScope credentials for Spring AI

## Backend Setup

```bash
# 1. Configure env vars or override application-local.yml (never commit secrets)
# 2. Install dependencies & build
cd demo
./mvnw clean package
# 3. Run (default port 8080, override with --server.port=8090)
./mvnw spring-boot:run
Key configuration knobs live in demo/src/main/resources/application.yml:

spring.datasource.* for MySQL
spring.data.redis.* for Redis
spring.ai.* for DashScope/OpenAI/Ollama
jwt.* for token secrets/TTL
vectorstore.path for the JSON store used by pdfChatClient
