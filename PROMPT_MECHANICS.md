# Speaking prompt mechanics

Updated 2026-09-08. Shared context: `PRODUCT_STRATEGY.md` and `PRODUCT_FLOW_REVIEW.md`.

## Current product requirements

Generate from the vocabulary item's meaning and natural use to a focused speaking task. The
question should directly elicit the thought expressed by the target itself. A word that appears
once before a long discussion of the surrounding context is not enough.

The latest learner feedback supersedes the earlier three-or-more-sentence requirement:

- Aim for a short, meaningful answer, usually 1–3 sentences. One complete sentence is acceptable.
- Ask one direct question, normally in 12–35 words, with essential details integrated into it.
- Do not show a `Context:` prefix, topic label, separate scenario introduction, or instructions
  to a teacher such as “Ask how…”. The `context` field is internal metadata for novelty checks.
- Keep the requested answer centred on the target's meaning or grammatical relationship.
- Remove secondary requests that send the learner away from that thought: apologies, repair
  choices, artistic intentions, unrelated plans, general reasons or stories.
- Add a follow-up only when it elicits a directly relevant use of the same meaning or relationship.
- Natural use of the target once is sufficient. Do not impose a repetition quota or require it
  in every sentence. Pronouns may refer back to a named place while that place remains the subject.
- Allow appropriate inflections in the intended sense, e.g. crack, cracks, cracked, cracking.
- Keep the target and its answer forms out of the question and optional starter; the word is
  previewed before practice and hidden behind a reveal button during each task. The starter should lead directly towards a target-bearing sentence.
- Use ordinary experience or easy imagination. Avoid definitions, translation, vocabulary-learning
  advice, research, specialised knowledge or a complicated invented situation.
- Adjust language difficulty to level without making higher-level answers longer by default.

These are requested product requirements. Their learning effectiveness is not yet validated.

## Editorial examples

### Danube

Prompt:

> What does the river you are looking at from a bridge at sunset look like?

Starter: “From the bridge, I can see…”

Possible answer:

> From the bridge, I can see the Danube reflecting the evening light. Its surface looks orange
> near the bank and darker in the middle.

The river remains the subject. Asking what the photographer wanted to capture would shift the
answer towards their artistic intentions, so that extra task has been removed.

### cracked

Prompt:

> How would you describe the damage to a borrowed bowl that hit the sink and now has a thin line near the rim?

Starter: “The bowl…”

Possible answer:

> The bowl cracked near the rim when it hit the sink. The crack runs down one side.

The task centres on the damage and how it happened. Apologising and negotiating a replacement
would extend the conversation beyond the target-bearing thought.

### among

Prompt:

> How would you divide the picnic preparation jobs within a group of four friends?

Starter: “I'd divide the jobs…”

Possible answer:

> I'd divide the jobs among the four of us. Two could prepare food, while the others handle
> transport and drinks.

Both sentences describe distribution within the group. Resolving a task nobody wants is an
unnecessary additional problem.

Sample answers are private editorial evidence, not answers to display during practice.

## Variation between training sessions

On returning to a word, the learner should receive a different speaking opportunity. Vary the
relevant event, relation, description or perspective while preserving natural use of the intended
meaning. Renaming people, swapping an object in an otherwise identical task, or attaching an
unrelated follow-up does not establish substantive novelty.

A concise question may remain within a narrow domain. Do not inflate scenarios or jump to unrelated
senses to manufacture variety. Never reset history to make a pool look fresh.

The app records what was shown separately from learning success. Skipped and interrupted tasks
count as seen. Merely prepared tasks do not. Editorially revised versions of unseen questions can
remain eligible, but an edited version of an already seen scenario is not a new encounter.

## Review criteria

For each candidate, check:

1. **Natural target use:** Does a short plausible answer use the target centrally and naturally?
2. **Focus:** After using the target, do remaining instructions send the learner into another
   conversation? Remove those requests. A sample must not hide drift by ignoring part of the task.
3. **Brevity:** Is every clause needed for the speaking task? One meaningful sentence is enough;
   neither a bare label nor padding to hit a sentence count is the goal.
4. **Starter:** Does it lead directly into natural target use without revealing or replacing it?
5. **Accessibility:** Can the learner respond without special knowledge or a complex backstory?
6. **Novelty:** Does the requested target-bearing thought differ from previous shown tasks and the
   other candidates? Different labels alone are not evidence of novelty.

## Implementation and limits

- Actual generation and separate review rules live in `src/main/resources/prompts`. A failed
  review triggers one revision with feedback; unapproved sets are not served.
- The generator supplies private 1–3-sentence samples for review. Samples are discarded before
  tasks reach the browser. The UI asks for a short answer without a minimum sentence count.
- Generation and semantic review receive up to 30 recent shown tasks. Local exact/near-text and
  stable-context checks cover all shown history. This does not guarantee unlimited semantic novelty.
- The durable ledger retains every issued ID for late exposure acknowledgments, independently
  of level, model and prompt version. Eligibility is based on shown history, not mere preparation.
  Cached tasks are checked against seen contexts as well as IDs across revisions and levels.
- Curated demo scenario identities stay stable across wording edits so a shorter version does
  not reintroduce a previously shown situation. Its pool version changes to bypass old wording.
- The offline preview has ten tasks each for Danube, among, cracked and steeple, plus five each
  for the original ten expressions, and ten each for chat someone up, hit on someone and engage in.
  The generic fallback has been removed.
- A word without fresh tasks does not block other available words from the library; unavailable
  words are reported and remain in the library. If none are available, the app explains the limit.
- Live generation for arbitrary vocabulary requires a server-side OpenAI key, currently absent
  in the development environment. Deterministic tests verify the pipeline, not live model quality.

See `README.md` for configuration and verification. Still to evaluate: actual model outputs under
these rules, learner focus during answers, the semantic history window, and ambiguous meanings.

## Session preview and recall (2026-09-08)

Practice opens a separate preview with exactly the available session words as shuffled chips.
Ready starts the questions. The preview order and practice order are shuffled independently.
Targets are absent from visible and accessibility text until Reveal word is selected. A new
prompt closes the spoiler. Preview alone does not mark a prompt as seen.

Each assessment is saved in the vocabulary item's `practiceHistory` in browser localStorage:
`sessionId`, `taskId`, `practicedAt`, `result`, and boolean `wordRevealed`. This is saved for
Used it and Still learning. If an earlier prompt for the same word was revealed in this session,
the flag stays true even after Try another prompt. The flag starts fresh for the next word/session.
Old attempts have no inferred reveal history. This data does not yet change status calculations,
selection or any learning coefficient. Persistence is local to this browser, as with existing progress.

This requested flow supersedes earlier recommendations to avoid a vocabulary preview. Its
learning benefit remains a hypothesis to validate with delayed, unprompted use in new contexts.

MVP UI update: sentence starters are not displayed. Revealing a word replaces the reveal button
with a green vocabulary chip; there is no hide action. Starter fields remain in generated data
for future revision. The extra answer instruction below each prompt has been removed.

Leaving an active practice via Back, the brand or Escape asks for confirmation. Assessments and
reveal flags are staged until the full session completes; confirmed exit discards these pending
results. Previously completed progress is preserved. Shown-prompt history is still saved immediately
so interrupted tasks are not repeated. The preview screen can be left without confirmation.
