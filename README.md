# 🧠 RAG Dokumentum Keresőrendszer

Production-ready Retrieval-Augmented Generation (RAG) alkalmazás **Spring Boot 3** + **Java 21** alapokon, **Oracle 23ai** vektor adatbázissal és teljesen **lokális AI** modellekkel.

## ✨ Funkciók

- **📄 Dokumentum feltöltés**: PDF, TXT, DOCX, MD, CSV fájlok támogatása
- **✂️ Intelligens darabolás**: Token-alapú szövegdarabolás átfedéssel
- **🔢 Lokális embedding**: Ollama `mxbai-embed-large` modell (1024 dimenzió)
- **💾 Vector Store**: Oracle 23ai beépített VECTOR típus, IVF index, cosine hasonlóság
- **🔍 Szemantikus keresés**: Két mód:
  - **Chunk keresés**: Releváns szövegrészletek visszaadása
  - **RAG keresés**: LLM-alapú válaszgenerálás (Ollama `llama3.2`)
- **🖥️ Modern UI**: Thymeleaf + HTMX, dark tema, reszponzív dizájn
- **🔒 Teljesen lokális**: Nincs külső API hívás, minden helyben fut

## 🏗️ Architektúra

```
┌─────────────────────────────────────────────────────┐
│                    Web UI (HTMX)                    │
├─────────────────────────────────────────────────────┤
│                 Spring Boot 3.4.x                   │
│  ┌──────────────┐  ┌──────────────┐                 │
│  │  Document     │  │   Search     │                 │
│  │  Controller   │  │   Controller │                 │
│  └──────┬───────┘  └──────┬───────┘                 │
│         ▼                  ▼                         │
│  ┌──────────────┐  ┌──────────────┐                 │
│  │  Document     │  │   Search     │                 │
│  │  Service      │  │   Service    │                 │
│  └──────┬───────┘  └──────┬───────┘                 │
│         │                  │                         │
│  ┌──────▼───────┐  ┌──────▼───────┐                 │
│  │  Chunking    │  │   RAG        │                 │
│  │  Service     │  │   (ChatClient)│                │
│  └──────┬───────┘  └──────────────┘                 │
├─────────┼───────────────────────────────────────────┤
│         ▼                                           │
│  ┌──────────────────────────────────────┐           │
│  │     Spring AI OracleVectorStore      │           │
│  │   (embedding + vector search)        │           │
│  └──────────────┬───────────────────────┘           │
├─────────────────┼───────────────────────────────────┤
│                 ▼                                   │
│  ┌─────────────────┐  ┌────────────────┐           │
│  │  Oracle 23ai     │  │  Ollama        │           │
│  │  (VECTOR store)  │  │  (local LLM)   │           │
│  └─────────────────┘  └────────────────┘           │
└─────────────────────────────────────────────────────┘
```

## 🚀 Gyors indítás

### Előfeltételek

- **Docker** és **Docker Compose**
- Legalább **8GB RAM** (Ollama modelleknek)
- ~10GB szabad lemezterület

### 1. Teljes stack indítása Docker Compose-zal

```bash
# Klónozás
git clone <repo-url>
cd springai-rag

# Indítás (Oracle 23ai + Ollama + App)
docker compose up -d

# Modellek letöltése automatikusan megtörténik
# Monitorozás:
docker compose logs -f ollama-setup
```

Az alkalmazás elérhető: **http://localhost:8080**

### 2. Fejlesztői mód (külön komponensek)

#### Oracle 23ai indítása:
```bash
docker run -d \
  --name oracle-23ai \
  -p 1521:1521 \
  -e ORACLE_PASSWORD=SysPassword123 \
  -e APP_USER=raguser \
  -e APP_USER_PASSWORD=ragpassword \
  gvenzl/oracle-free:23-slim
```

#### Ollama telepítése és modellek letöltése:
```bash
# Ollama telepítése (Linux)
curl -fsSL https://ollama.com/install.sh | sh

# Modellek letöltése
ollama pull mxbai-embed-large    # Embedding modell (1024 dim)
ollama pull llama3.2            # Chat modell
```

#### Alkalmazás indítása:
```bash
./mvnw spring-boot:run
```

## ⚙️ Konfiguráció

### Főbb beállítások (`application.yml`)

| Beállítás | Alapértelmezés | Leírás |
|-----------|---------------|--------|
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Ollama szerver URL |
| `spring.ai.ollama.chat.options.model` | `llama3.2` | Chat modell |
| `spring.ai.ollama.embedding.options.model` | `mxbai-embed-large` | Embedding modell |
| `app.chunking.chunk-size` | `800` | Chunk méret (token) |
| `app.chunking.chunk-overlap` | `200` | Chunk átfedés (token) |
| `spring.servlet.multipart.max-file-size` | `50MB` | Max fájlméret |

### Környezeti változók (production)

```bash
ORACLE_JDBC_URL=jdbc:oracle:thin:@<host>:1521/<service>
ORACLE_USER=raguser
ORACLE_PASSWORD=<password>
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_CHAT_MODEL=llama3.2
OLLAMA_EMBEDDING_MODEL=mxbai-embed-large
```

## 📊 API Végpontok

| Metódus | URL | Leírás |
|---------|-----|--------|
| `GET` | `/` | Dashboard |
| `GET` | `/documents` | Feltöltési oldal |
| `POST` | `/documents/upload` | Fájl feltöltés |
| `GET` | `/documents/{id}/status` | Feldolgozási állapot |
| `DELETE` | `/documents/{id}` | Dokumentum törlés |
| `GET` | `/search` | Keresési oldal |
| `POST` | `/search` | Keresés (HTMX) |
| `POST` | `/search/api` | Keresés REST API |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrikák |

### REST API keresés

```bash
curl -X POST http://localhost:8080/search/api \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Mi az Oracle 23ai?",
    "mode": "RAG",
    "topK": 5
  }'
```

## 🔧 Technológiai stack

| Komponens | Technológia |
|-----------|-------------|
| Backend | Java 21 + Spring Boot 3.4.x |
| AI Framework | Spring AI 1.0.3 |
| Embedding | Ollama + mxbai-embed-large |
| LLM | Ollama + llama3.2 |
| Vector DB | Oracle 23ai (VECTOR típus) |
| Dokumentum parser | Apache Tika |
| Frontend | Thymeleaf + HTMX |
| DB migráció | Flyway |
| Monitoring | Spring Actuator + Prometheus |
| Container | Docker + Docker Compose |

## 📁 Projekt struktúra

```
springai-rag/
├── pom.xml                          # Maven konfiguráció
├── docker-compose.yml               # Teljes stack
├── Dockerfile                       # Multi-stage build
├── src/main/java/com/example/rag/
│   ├── RagApplication.java          # Belépési pont
│   ├── config/                      # Konfigurációk
│   │   ├── AsyncConfig.java         # Aszinkron feldolgozás
│   │   ├── VectorStoreConfig.java   # Oracle Vector Store
│   │   └── WebConfig.java           # Web MVC
│   ├── controller/
│   │   ├── HomeController.java      # Dashboard
│   │   ├── DocumentController.java  # Feltöltés
│   │   └── SearchController.java    # Keresés
│   ├── service/
│   │   ├── DocumentService.java     # Dokumentum feldolgozás
│   │   ├── ChunkingService.java     # Szöveg darabolás
│   │   └── SearchService.java       # Keresés + RAG
│   ├── model/                       # DTO-k és entitások
│   ├── repository/                  # JPA repository-k
│   └── exception/                   # Hibakezelés
├── src/main/resources/
│   ├── application.yml              # Fő konfiguráció
│   ├── application-prod.yml         # Production profil
│   ├── db/migration/                # Flyway migrációk
│   ├── templates/                   # Thymeleaf sablonok
│   └── static/                      # CSS + JS
```

## 🩺 Monitoring

- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **Prometheus**: `GET /actuator/prometheus`

## 📝 Licenc

MIT
