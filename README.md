# Wordswith / Fluidity

A Java 17 app that serves a vocabulary speaking-practice interface and a JSON API.

## Run locally

On macOS/Linux, run `./start.sh` from the project directory. You can also run
the script by its full path from any directory. It selects the local Java 17
installation when available and starts the server; press `Ctrl+C` to stop it.
The existing `.env` configuration is loaded by Gradle.

```sh
./gradlew run
```

Open [http://localhost:8080](http://localhost:8080). On Windows, use `gradlew.bat run`.

Without an API key, the app runs a finite offline preview. It contains ten distinct tasks each for
`Danube`, `among`, `steeple`, and `cracked` (also accepting crack/cracks/cracking), plus five tasks each for the
ten original sample expressions. It never returns generic tasks for unsupported words or silently
recycles exhausted examples. Those cases produce an explicit message.

## Enable personalized generation

Export the API key in the server's shell or IDE run configuration, then restart the app:

```sh
export OPENAI_API_KEY="..."
export WORDSMITH_GENERATOR=openai
export OPENAI_MODEL=gpt-5.4-nano
./gradlew run
```

The API key stays on the server. Do not put it in frontend files or browser storage. For local
`./gradlew run` launches, copy `.env.example` to `.env`; Gradle loads this ignored local file.

`auto` (the default) selects OpenAI when a key is available, otherwise the offline preview.
Explicit `openai` mode fails at startup if the key is missing. API failures never silently switch
an OpenAI session to generic or demo prompts.

A dashboard saved prompt is no longer required. `OPENAI_PROMPT_ID` and `OPENAI_PROMPT_VERSION` are
no longer used. The actual instructions are checked into the project:

- [Generation rules](src/main/resources/prompts/generate-speaking-tasks.txt)
- [Review rules](src/main/resources/prompts/review-speaking-tasks.txt)
- [Curated preview examples](src/main/resources/prompts/demo-speaking-tasks.json)
- [Product mechanics and rationale](PROMPT_MECHANICS.md)

Generation uses the [Responses API structured output format](https://developers.openai.com/api/docs/guides/structured-outputs).
The configured model must support that API and strict JSON-schema output.

## Prompt generation and review

For each word, the generator receives its spelling, learner level and up to 30 recent shown
tasks. It creates five candidates with:

- one concise question eliciting a target-centred answer, usually 1–3 sentences;
- an optional compatible sentence starter;
- a speaking action and an internal description of the context;
- a private 1–3-sentence sample answer demonstrating focused, natural target use.

An independent review request checks semantic fit, target focus, brevity, learner level, starter quality
and substantive difference from previous tasks and the other candidates. A failed review triggers
one revision with feedback and the rejected candidates. Two unsuccessful attempts return an
error; unapproved candidates are not served or cached. Private sample answers are discarded after
review and are not sent to the browser.

Local validation additionally rejects malformed tasks, target strings in questions/starters,
exact normalized repeats, identical context labels and near-identical wording. Text checks use
word boundaries, so `cat` does not incorrectly match `vacation`. These checks are not a complete
semantic judge. Quality and semantic novelty still require live-model and learner evaluation.

Each API request has a 60-second timeout. Fresh generation normally uses two requests per word;
a revision uses up to four. Unseen cached tasks are reused to avoid generating a new batch for
every individual practice attempt.

## Exposure history and caching

The browser maintains an anonymous learner ID in local storage and supplies `X-Learner-Id` on API
requests. This is a browser profile identifier, not authentication or an account system. Requests
without the header use a shared `local` profile for backward compatibility.

Before displaying a prompt, the browser saves an exposure event in a durable local outbox and
sends it to the server. This is separate from learning self-assessment: seeing a prompt counts
whether the learner answers, skips it, or leaves the session. Failed or unacknowledged writes stay
in the outbox and must be replayed before the next session request.

The server stores two kinds of records under `data/question-cache`:

1. A task pool keyed by learner, normalized word, level, generator/model and rules revision.
2. A history ledger keyed only by learner and normalized word, retaining issued tasks and whether
   they have been seen. It survives pool replacement, level changes and prompt/model changes.

Unseen tasks remain in the pool until exhausted. Generating more tasks consults the ledger;
changing IDs does not make shown text eligible again. Prepared but unseen tasks may be revised
or reused across rule changes. Cached candidates are also checked against seen contexts from
other pools. Late exposure events still resolve against
issued task IDs after a pool or level change. Replayed events are idempotent. Writes for the same
learner and word are serialized within the server process, and files are replaced atomically.

OpenAI pool versions include a hash of both instruction files. Editing those rules invalidates
cached pools automatically, without discarding exposure history. Older pre-history demo caches
are bypassed; old versions did not retain enough information to reconstruct every past display.

Exact/near-text checks cover the full shown history. Model semantic review is limited to the
30-task window. It is not a guarantee of unlimited semantic novelty across all historical tasks.
The current storage is designed for one application process and one browser profile per learner;
account synchronization and multi-server storage are outside this change.

## API

All endpoints accept an optional `X-Learner-Id` header (1–80 letters, digits, underscores or hyphens).

`POST /api/training-sessions`

```json
{"words": ["Danube", "among", "cracked"], "level": "Intermediate"}
```

Returns a fresh session ID and available unseen tasks for each prepared word. Optional `sessionSize` limits the number of words prepared; the request may supply more ranked candidates. Words without available prompts are reported in `unavailableWords` and do not block other candidates. If none are available, the API returns an explanatory error. A pool may contain fewer than
five tasks as it is consumed. Merely preparing a session does not mark every returned task seen.

`POST /api/dictionary-items/tasks`

```json
{"word": "cracked", "level": "Intermediate"}
```

Returns the available unseen tasks for one item, generating a new pool only when necessary.

`PATCH /api/dictionary-items/tasks`

```json
{"word": "cracked", "taskId": "...", "level": "Intermediate"}
```

Records that a previously issued task was shown to this learner. Returns `{"recorded": true}`.
The legacy endpoint name is preserved; this no longer means successful vocabulary use. Learning
self-assessment remains in the browser's vocabulary state.

`GET /api/health` reports the active generator. Unsupported or exhausted preview vocabulary and
failed quality review return HTTP 503 with a user-facing explanation. Invalid input returns 400;
other generation failures return 502.

## Configuration

| Variable | Default |
| --- | --- |
| `WORDSMITH_GENERATOR` | `auto` (`auto`, `openai`, `demo`) |
| `OPENAI_API_KEY` | unset |
| `OPENAI_MODEL` | `gpt-5.4-nano` |
| `WORDSMITH_LEARNER_LEVEL` | `Upper-Intermediate` |
| `WORDSMITH_CACHE_DIR` | `data/question-cache` |
| `PORT` | `8080` |

## Verification

```sh
./gradlew test
node --test src/test/js/prompt-history.test.cjs
node --check script.js
```

Java tests cover pool reuse/exhaustion, persistent exposure, learner isolation, settings changes,
late and concurrent writes, duplicate rejection, all curated preview packs, and HTTP API flows.
A local fake Responses endpoint verifies the real instruction payload, review/revision protocol,
history window, response parsing, incomplete responses and failures without spending API credits.
Browser-history tests cover reload, failed acknowledgment, overlapping writes and outbox replay.

These are deterministic integration checks. They do not establish the quality of an actual model's
outputs. A live evaluation with a configured API key and review of repeated sessions is still
required before making quality claims about arbitrary vocabulary.
