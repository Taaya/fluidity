# Wordswith

Wordswith now runs as a small Java 17 application that serves both the frontend and a JSON API.

## Run locally

```powershell
.\gradlew.bat run
```

Open [http://localhost:8080](http://localhost:8080).

Without OpenAI configuration, the app uses a deterministic demo generator. This keeps the full
training flow usable while the production prompt is still being written.

## OpenAI configuration

Create a saved prompt in the OpenAI dashboard with these variables:

- `topics_json`
- `words_json`

The prompt must produce questions that match this structure:

```json
{
  "questions": [
    {
      "text": "Tell a story about a time you travelled with your cat.",
      "word": "cat",
      "topic": "travelling & places"
    }
  ]
}
```

Structured output is enforced by the API request, so the saved prompt only needs the generation
instructions and the two variables.

Configure the backend through environment variables:

```powershell
$env:WORDSMITH_GENERATOR = "openai"
$env:OPENAI_API_KEY = "..."
$env:OPENAI_MODEL = "gpt-5.4-nano"
$env:OPENAI_PROMPT_ID = "pmpt_..."
$env:OPENAI_PROMPT_VERSION = "1"
.\gradlew.bat run
```

The API key stays on the server and is never sent to the browser.

## API

`POST /api/training-sessions`

```json
{
  "topics": ["travelling & places", "pets"],
  "words": ["cat", "wisdom", "serenity"]
}
```

The response contains a fresh session ID and a generated or cached question set.

## Caching

There are two independent layers:

1. The Responses API receives a saved prompt ID plus variables instead of the full prompt text.
2. Generated question sets are persisted under `data/question-cache`.

The local cache key includes normalized topics, normalized words, model, prompt ID, and prompt
version. Changing the prompt version or model automatically creates a new cache namespace.

## Project boundaries

- `api` — HTTP and static-file delivery
- `service` — validation, cache orchestration, and session creation
- `generation` — interchangeable demo and OpenAI generators
- `persistence` — persistent question-set cache
- `domain` — API-independent records

The current browser request supplies the hardcoded vocabulary deck. A future user dictionary can
replace that source without changing the generator or cache layers.
