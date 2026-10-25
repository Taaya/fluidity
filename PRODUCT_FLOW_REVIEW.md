# Product Features and User Flow Review

> Product decisions derived from `PRODUCT_STRATEGY.md` and a walkthrough of the current app.

## Executive decision

### Practice preparation update — September 19, 2026

- `Practice` immediately shows up to five selected words on `Before you begin`.
  Questions are prepared while the learner reviews this fixed selection, reusing cached pools.
- `Ready` starts practice immediately if preparation is complete. Otherwise it shows a
  spinner and `Preparing…`, then starts automatically when all selected words are ready.
- Preparation errors stay on the preview with `Try again` for the same selection and
  a back action. Unavailable words are not silently replaced or removed from the session.
- Going back cancels the intent to start; a late response cannot reopen practice.
  The server may finish an already submitted request and cache its questions for later.

### MVP update — September 12, 2026

The following implemented rules supersede the earlier status and selection proposals below.
They are product hypotheses to validate with learners, not validated measures of mastery.

- Library statuses are **New**, **Building**, and **Getting comfortable**. Home shows the
  count of each; word rows show the status without an attempt or success counter.
- An assessed attempt moves New to Building. Three `Used it` answers in the last four
  assessments (including three successes in the first three) move Building to Getting comfortable.
- Two consecutive `Still practicing` answers move Getting comfortable back to Building.
  One such answer and elapsed time alone do not demote a word. Regaining the status uses
  the same three-of-four rule; lifetime totals do not determine status.
- `Try another question` records exposure only, not an assessment. Each word is assessed
  once per session. Existing completion/discard behavior for session results is retained.
- Default sessions reserve two places for New words and three for previously assessed
  words (Building and Getting comfortable together). Within each group, choose oldest
  first by date added or last saved assessment. If a group has too few words, fill its
  places from the other group. The preview fixes the selection before preparation starts.
- Every session summary has exactly two actions: **Practice again** starts a regular
  mixed session from the whole library; **Done for now** returns home. There is no
  automatic subset retry or sharing button on the summary; sharing remains in the library.
- Stored assessment history is replayed to calculate statuses. Legacy practiced words
  without detailed history start at Building; their totals and last known result are retained,
  but missing attempts are not invented.

The current product already contains the beginning of the right experience: learners bring their
own vocabulary and receive contextual prompts that invite them to speak.

The next version should stop behaving like a one-off prompt generator and start behaving like an
activation loop for a small, timely selection from the learner's vocabulary.

The central product object should be an individual **vocabulary item**, not a lesson deck. Each
item should move through a simple learner-visible journey:

> added -> attempted in speech -> used successfully / needs more practice -> returned to later

An import may contain many words from one lesson, but that is an intake event rather than a
permanent practice unit. The product may retain optional source information for teacher workflows
and reporting while mixing suitable words from different lessons in practice. This variety reduces
contextual hints and makes retrieval less predictable, which is closer to real conversation.

## Current user flow

1. The learner opens a product introduction page.
2. They continue to a separate add-words page.
3. They type a word, paste a list or notes, or drop a text file.
4. The app extracts vocabulary and shows editable cards.
5. The learner selects an English level through a settings icon.
6. They start a session using every word currently stored in the browser.
7. The app cycles through generated speaking prompts indefinitely.
8. `Next` immediately counts the current word as practiced; `Skip` counts it as skipped.
9. The learner must stop the session manually.
10. A summary lists practiced and skipped words, then offers to train everything again or return
    to the input screen.

## What is already strong

- Learners use personally relevant vocabulary rather than a generic course deck.
- Words, phrases, and collocations are all accepted.
- Bulk input is possible and imported items can be reviewed before practice.
- Prompts are contextual and oriented toward producing meaningful speech.
- Sentence starters provide optional scaffolding without giving away the answer immediately.
- The practice interface is focused and visually distinct from a flashcard quiz.
- English level influences prompt generation without adding friction to every question.

## Main gaps

### `Next` is not evidence of active use

The interface records a word as practiced when the learner presses `Next`. It does not create a
clear moment when the learner speaks, nor ask whether they managed to use the target vocabulary.
The resulting progress signal is closer to prompt consumption than vocabulary activation.

### A session has no natural end

All generated questions enter a queue that refills indefinitely. The learner has no expectation of
how long practice will take, no visible progress toward completion, and no satisfying finish unless
they choose `Stop`.

### Vocabulary has no learning state

Words persist in one flat browser list. There is no distinction between new vocabulary, words
already used confidently, words that need another attempt, and words that have not appeared for a
while. The product also cannot retain where a word came from without turning that source into a
practice deck.

### There is no return loop

The summary describes what happened in the current session but does not tell the learner what to
practice next or when to return. Session results are not preserved as learner progress.

### The teacher workflow is invisible

A learner can paste lesson vocabulary, but the product does not retain optional source information
or create a natural path from teacher to student. This makes the teacher hypothesis hard to test
through observed behavior.

## Feature classification

### Core now

These capabilities directly support the vocabulary activation loop and should define the next
usable version.

| Capability | Current state | Product decision |
| --- | --- | --- |
| Add personal words and phrases | Exists | Keep as the primary vocabulary source. |
| Paste a clean vocabulary list | Exists | Keep and make the default bulk-input path. |
| Review, edit, and remove imported items | Exists | Keep, with a compact confirmation step. |
| Contextual speaking prompts | Exists | Keep as the heart of practice. |
| Optional sentence starter | Exists | Keep as learner-controlled scaffolding. |
| Learner level | Exists | Keep, but set it during lightweight onboarding or in clear settings rather than making it part of every flow. |
| Small, finite practice session | Missing | Add a default session of about five words or five minutes. |
| Explicit speak moment | Weak | Tell the learner to answer aloud and make completion an intentional action. |
| Self-assessment after speaking | Missing | Replace `Next` with `Used it` and `Need more practice`; keep task skipping separate. |
| Persistent word state | Missing | Store at least new, attempted, needs practice, and used confidently. |
| Return path | Missing | End with a clear next step and surface words ready for another attempt on return. |

### Experiments

These should be tested in the smallest form that can produce behavioral evidence.

| Experiment | Smallest useful version | What it tests |
| --- | --- | --- |
| Optional source metadata | Quietly retain that items came from a particular lesson, teacher, or import without presenting them as a deck. | Whether provenance is useful for teacher workflows and reporting. |
| Teacher vocabulary link | A teacher shares a link that adds prefilled items to the student's existing vocabulary after confirmation. | Whether teachers naturally send vocabulary through the product. |
| Mixed practice selection | Build each session from individual items across different lessons, balancing new, needs-practice, and older vocabulary. | Whether variety improves engagement and more spontaneous retrieval. |
| Five-word session | Select a small mixed group automatically from the full vocabulary library. | Whether a clear finish improves completion and repeat use. |
| Self-assessment labels | Test `Used it` / `Need more practice` against language such as `Easy` / `Try again`. | Which labels learners understand and use honestly. |
| Lightweight teacher summary | Manually send or expose a simple practiced / needs-practice result for pilot students. | Whether teachers use the information in the next lesson. |
| Pre-lesson return cue | Remind pilot learners to practice words that are ready for another attempt before their next class. | Whether lesson cadence can drive retention. |
| Notes extraction | Offer automatic extraction only after paste, with a clear review step. | Whether teachers and learners actually import messy lesson notes. |

### Later

These may be valuable after the core learner loop and teacher behavior are validated.

- Teacher accounts and dashboards.
- Classes, rosters, and student management.
- Formal vocabulary assignments and due dates.
- Automated weekly teacher reports.
- Referral codes, payouts, and retained-user attribution.
- Cross-device accounts and synchronization.
- Adaptive spaced-repetition scheduling.
- Audio recording and playback history.
- Speech recognition, pronunciation scoring, or AI feedback on the spoken answer.
- Integrations with lesson notes, LMS products, Quizlet, or shared documents.
- Institutional and school administration.

### Remove or de-emphasize

These current behaviors either add friction or reinforce the wrong mental model.

| Current behavior | Decision | Reason |
| --- | --- | --- |
| Separate product introduction page | Remove from the main app flow. | First-time value can be explained inside the empty vocabulary state; returning learners should reach practice immediately. |
| Repeated large brand block on the add-words page | De-emphasize. | The primary task and current vocabulary should own the screen. |
| `Vocabulary speaking studio` as the main explanation | Replace in key product copy. | It names a category but does not express the concrete outcome. |
| Automatic part-of-speech guessing | Remove from the visible flow for now. | It is unreliable and does not currently improve speaking practice. |
| Full prose extraction as the default input model | De-emphasize. | A clean pasted list is more predictable for the first teacher experiment. |
| Automatically practicing every stored word | Remove. | Select a small, varied group across the vocabulary library instead. |
| Infinite question queue | Remove. | It prevents completion and obscures the learning unit. |
| `Next` means practiced | Remove. | Moving forward is not the same as successfully using a word. |
| Practiced / skipped counters as the main progress model | Replace. | Progress should describe vocabulary state and finite session completion. |
| `Train again` for the entire unchanged deck | Replace. | The follow-up should prioritize words that need another attempt. |

## Target learner flow

### First visit

```text
Open product
    |
    v
Add or paste recently learned vocabulary
    |
    v
Review the individual items being added
    |
    v
Merge them into the vocabulary library
    |
    v
Start a short, mixed session with a clear size
    |
    v
For each word: read prompt -> answer aloud -> self-assess
    |
    v
See activated words and words to try again
    |
    v
Finish with a clear return cue
```

The first screen should contain the product promise and the input action together. A useful empty
state could say:

> Use the words you recently learned in real conversation.
>
> Add a few words or paste the list from your English lesson.

The product should choose a good default session automatically. On the first visit it will
naturally use the newly added vocabulary. On later visits it should mix items from different
lessons, prioritizing words that are new, need more practice, or have not appeared recently.
Session configuration should not become a form the learner must complete before understanding the
value.

### Practice loop

Each task should have four moments:

1. **Preview and context:** First show the session words as shuffled chips on a separate screen.
   After Ready, show each speaking situation with the target hidden behind an optional spoiler.
2. **Production:** Ask the learner to answer aloud; optionally reveal a sentence starter.
3. **Reflection:** Let the learner mark `Used it` or `Still learning`; save whether the target
   was revealed with each attempt, without changing any learning coefficient yet.
4. **Progress:** Move visibly toward the end of a finite session, for example `2 of 5`.

`Skip this prompt` may remain available as a secondary action. It should mean that the task was a
poor fit or the learner did not want to answer it, not that they failed to use the word.

### Session summary

The summary should answer three questions:

1. Which words did I manage to use?
2. Which words need another attempt?
3. What should I do next?

Actions for every result:

- `Practice again`: start a regular mixed session from the whole library.
- `Done for now`: return home.

The summary should persist so that returning to the product feels like continuing a learning
process rather than reopening a generator.

### Return visit

The first screen for a returning learner should prioritize action:

- `5 words ready to practice`;
- a varied selection from the learner's vocabulary, followed by a preview of the selected words before practice starts;
- a single primary `Practice now` action;
- a secondary way to add vocabulary or view the vocabulary library.

## Teacher-led pilot flow

The smallest teacher flow does not need a teacher account.

```text
Teacher shares vocabulary list or prefilled link
    |
    v
Student confirms the items; they join the existing vocabulary library
    |
    v
Student completes finite sessions that may mix words from different sources
    |
    v
Result: used successfully / needs more practice
    |
    v
Teacher learns the result manually during the pilot
```

For the first experiment, operational work can remain manual. The product only needs enough
structure to identify which items a teacher contributed, the student's practice attempts, and the
resulting word states. The teacher may receive progress for vocabulary they introduced even when
the student's session also contained unrelated words. A dashboard is justified only if teachers
repeatedly ask for visibility and use it to change the next lesson.

## Recommended implementation order

1. Replace the infinite queue with a five-item finite session selected across the vocabulary library.
2. Replace `Next` with explicit post-speaking self-assessment.
3. Persist the result, recency, and optional source for each vocabulary item.
4. Collapse the separate introduction page into a useful first-time empty state.
5. Mix new, needs-practice, and older items when assembling return sessions.
6. Pilot a teacher link that adds individual items to the student's library.
7. Add only the smallest reporting surface requested by pilot teachers.

## Success criteria for the next version

The next version is strategically stronger if a new learner can:

- understand the outcome without reading a separate explainer page;
- add real vocabulary from a recent lesson in under a minute;
- begin speaking without configuring a complex session;
- know how many tasks remain;
- distinguish words they used from words that need another attempt;
- encounter a varied mix without knowing the exact practice words in advance;
- leave with a clear sense of progress and a reason to return.

For the teacher hypothesis, success is behavioral: teachers assign or share vocabulary repeatedly,
students complete the practice, and teachers use the outcome in a later lesson.
