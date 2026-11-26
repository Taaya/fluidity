# Base44 workshop: share vocabulary with a learner

Prepared for a one-hour building session. This is an experiment from PRODUCT_STRATEGY.md,
not a commitment to a teacher platform.

## Start here

Repository: https://github.com/Taaya/fluidity
Starting branch: workshop/base44

This branch includes the Practice candidate-filtering fix from commit
d7791be61b7dfd4d1526de4afc5bdebce48dab3e. PR #1 is still open separately.
It also contains this brief and examples/base44-workshop-words.txt.
Do not begin from main accidentally: confirm this file exists before editing.

Use Base Code's existing-repository workflow:
https://app.base44.com/base-code
Official instructions: https://docs.base44.com/base-code/getting-started

Connect GitHub with access to Taaya/fluidity. When importing by link, use:
https://github.com/Taaya/fluidity/tree/workshop/base44

Create the working branch/session from workshop/base44. Base Code's generic new-task flow
may start from the default branch; choose the starting branch explicitly in the sidebar.
Availability and event coupon coverage must be checked in the user's Base44 workspace.

## Product outcome

A teacher pastes five lesson words, copies a link, and sends it to a learner.
The learner opens it, reviews the items, and explicitly adds them to their existing library.
The learner can then start the app's existing mixed speaking practice.

The teacher workflow is a hypothesis to test. Success today is a working end-to-end link,
not evidence of better learning or teacher adoption.

## Read before editing

- AGENTS.md
- PRODUCT_STRATEGY.md
- The latest updates at the top of PRODUCT_FLOW_REVIEW.md
- DESIGN_SYSTEM.md
- script.js, index.html, styles.css

The existing Share progress action exports a progress image. Keep its purpose and behavior.
The vocabulary importer and localStorage-backed library already exist; reuse them.

## Run without an OpenAI key

This is Java 17 + Gradle, serving vanilla HTML/CSS/JavaScript. It is not a React project.
Install/use a JDK 17 in the development environment. The Gradle wrapper is checked in.

    WORDSMITH_GENERATOR=demo ./gradlew run

Server port: 8080
Health endpoint: GET /api/health (generator should be demo)

Use the exact five supported preview items in examples/base44-workshop-words.txt for the demo.
Arbitrary vocabulary needs the separate OpenAI setup, outside this workshop's scope.
Do not request or copy the owner's .env or API key for this task.

Web assets are copied to the Java classpath by processResources. Restart the Gradle run
after HTML/JS/CSS edits, then reload the preview so it uses the latest assets.
Base Code provides a development preview; a permanent shared deployment is a separate task.
For today's demonstration, open the generated link using the same reachable preview origin
in a second browser profile/private window. Neither the teacher's browser storage nor a
localhost address on a different computer is a shared service.

## Scope for the hour

1. Add a small Share word list action and dialog, separate from Share progress.
2. The dialog accepts one word or phrase per line. Trim whitespace, remove blank lines,
   and deduplicate case-insensitively. Support 1-20 items, each at most 80 characters.
   Explain validation errors rather than silently truncating.
3. Generate a same-app URL containing a versioned, URL-encoded vocabulary payload in the
   fragment. Use the current origin/path; preserve case and phrase spelling.
   No server-side link storage is necessary for this experiment.
4. Copy link, with a selectable text fallback if clipboard access fails.
5. On opening the link, validate its format/version/limits and show a review dialog.
   Render imported text as text, never HTML. Opening the link must not save anything.
6. Show how many entries are new and how many already exist. Confirm adds only new entries
   using the existing normalization, ID creation, migration, and save helpers.
   Keep existing item IDs, practice history, statuses, learner ID, and exposure outbox.
7. Cancel leaves the library untouched. Successful import removes only the consumed sharing
   fragment with history.replaceState, so reload does not reopen the same import.
   An unrelated fragment should be ignored.
8. Show a compact confirmation and return to the existing library/Practice action.
   No automatic practice launch or change to session selection rules.

No teacher accounts, dashboards, login, cloud sync, progress sharing, AI generation changes,
new framework, or full redesign are needed for this task. Sharing vocabulary never transfers
the sender's practice history or marks items as practiced.

## Demo and acceptance checks

- Teacher: paste the five lines from examples/base44-workshop-words.txt and copy the link.
- Recipient in a private window: open link -> see five editable/removable or selectable items
  using the existing review affordances -> confirm -> library shows the accepted items.
- Recipient with an existing practiced "among" and an unrelated item: import all five ->
  only four additions; the existing item's ID/history/status and unrelated item survive.
- Cancel and malformed/unsupported/oversized links leave the library untouched.
- The same link opened twice never creates duplicates.
- "to bridge the gap" remains a single phrase; mixed-case duplicates count as one entry.
- HTML-looking input displays literally and never executes.
- Demo-mode Practice with the supported vocabulary still uses the existing preview/Ready flow.
- Existing Share progress still opens its image-export dialog.
- Check a narrow mobile viewport and keyboard dialog navigation.

Checks relevant to any changed logic:

    node --test src/test/js/*.test.cjs tests/share-progress.test.cjs
    node --check script.js
    ./gradlew test

Add a focused regression test for link parsing and non-destructive merge if implementing helpers.
Do not spend the hour writing tests that merely duplicate markup.

## Time box

Before the event: connect the repository and get the unchanged app preview running.
0-10 minutes: inspect the app and agree on the three-step flow.
10-35 minutes: implement compose/copy and recipient review/import.
35-50 minutes: test fresh/existing libraries, cancellation, duplicates, malformed input, mobile.
50-60 minutes: make small fixes, save/push changes, and leave a PR targeting workshop/base44.
Leave the feature on its working branch for later review; do not merge during the workshop.

If setup still fails after 10 minutes, use the fallback below instead of spending the hour
porting the Java app or debugging credentials.

## Prompt to paste into Base Code

Read BASE44_WORKSHOP.md and the repository instructions. Implement the scoped teacher-to-learner
vocabulary-sharing experiment in this existing app: compose a short list, copy a link, review
and confirm importing it into the recipient's library. Keep the Java 17 + vanilla JS stack,
existing visual design, learning data and Practice flow. Use demo mode and the provided sample
words; no OpenAI key is needed. Check the acceptance cases and leave the work in a PR targeting
workshop/base44. Before changing files, confirm that your starting branch contains this brief
and summarize the minimal implementation you will make.

## Fallback if existing-repo access is unavailable

In the ordinary Base44 app builder, build a separate clickable prototype of the same flow:
compose a list -> copy a share link -> recipient review -> add to a small persistent library.
Use synthetic words/progress and keep the English UI. Link handling, duplicate detection,
confirmation and preservation of existing entries should work, not just look clickable.
Use the same limits and acceptance cases. No Java migration, AI API, teacher dashboard,
authentication, or integration with the current Fluidity backend is required.

This fallback is a separate prototype, not an update to Fluidity. Save its URL and preserve its
code if the event plan allows export. We can later evaluate and port the useful interaction.
