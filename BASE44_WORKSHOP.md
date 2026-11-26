# Base44 workshop: Add and Share

## Goal

Share the words entered on the main page with another learner.
This is a small experiment from PRODUCT_STRATEGY.md.

## Requirements

1. Add an **Add and Share** button next to **Add words** on the main page input form.
   Use the existing form; do not add a separate word-entry dialog.
2. Explain validation errors the same way as when just adding words.
   Reuse the existing parsing, validation, duplicate handling, limits and feedback.
3. **Add and Share** adds the entered words to the sender's library as **Add words** does,
   and generates a same-app URL containing a versioned, URL-encoded vocabulary payload
   in the fragment. Use the current origin/path; preserve case and phrase spelling.
   No server-side link storage is necessary for this experiment.
   Share the parsed input list, including words already in the sender's library;
   do not share the whole library or only the newly added items.
   Capture the input list before the existing add action clears the field.
4. Copy the link. If clipboard access fails, show the link in a selectable text field
   with a short instruction to copy it manually.
5. When the recipient opens the link, open the main page with the shared words
   already filled into the existing input field.
6. The recipient can edit the list and press the existing **Add words** button.
   Opening the link alone does not add words. Existing library items and their progress
   stay intact, and existing duplicate handling prevents duplicate additions.

No additional review screen or teacher account is needed. Keep the existing design,
Practice flow and Share progress image feature.

## Quick check

- Paste the five sample items and press Add and Share: they are added locally and a link is copied.
- Open that link in a private window: the same list is in the input, ready to edit or add.
- Press Add words: the accepted items appear in the recipient's library.
- Try a recipient who already has one of those words: its progress stays intact.
- Invalid input uses the existing form feedback; a clipboard failure still leaves a copyable link.
- An invalid shared payload displays a clear message and does not change the library.

## Repository and setup

Starting branch: workshop/base44
Import link: https://github.com/Taaya/fluidity/tree/workshop/base44

This branch includes the previous Practice fix from PR #1.
Create the workshop working branch/session from workshop/base44.
Confirm this file is present before making changes.

Read AGENTS.md, PRODUCT_STRATEGY.md, the latest updates in PRODUCT_FLOW_REVIEW.md,
DESIGN_SYSTEM.md, script.js, index.html and styles.css.

The app uses Java 17 + Gradle and vanilla HTML/CSS/JavaScript.
Run the preview without an OpenAI key:

    WORDSMITH_GENERATOR=demo ./gradlew run

Port: 8080. GET /api/health should report generator=demo.
Use examples/base44-workshop-words.txt for the demonstration.
Restart the Gradle run after changing web assets, then reload the preview.
The generated link needs a reachable app/preview address; localhost on a different computer
does not point to the sender's server.

Base Code entry: https://app.base44.com/base-code
Instructions: https://docs.base44.com/base-code/getting-started

## Prompt to paste into Base Code

Read BASE44_WORKSHOP.md and implement its six requirements in the existing app.
Reuse the main-page input and Add words behavior. Add and Share should add the input words
and copy a link. Opening the link should prefill that same input on the recipient's main page;
the recipient adds the words using Add words. Keep the existing stack, visual style and progress.
Use demo mode and the sample vocabulary. Check the listed cases and save the result in a
working branch with a PR targeting workshop/base44.

## One-hour plan

Before the session: connect GitHub and get the unchanged preview running.
First 10 minutes: inspect the existing input/add behavior.
Next 25 minutes: implement Add and Share and recipient input prefilling.
Next 15 minutes: check the scenarios above and mobile layout.
Last 10 minutes: fix issues and save the work in GitHub.

Relevant checks after implementation:

    node --test src/test/js/*.test.cjs tests/share-progress.test.cjs
    node --check script.js
    ./gradlew test

If only the ordinary Base44 builder is available, prototype this same six-step flow as a
separate app with sample vocabulary. That prototype is separate from the existing Fluidity code.
