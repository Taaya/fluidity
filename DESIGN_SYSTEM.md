# Wordswith visual direction

Updated September 12, 2026. Implemented visual direction based on the supplied references;
this is a design choice, not evidence of improved learner outcomes. Product behavior continues
to follow PRODUCT_STRATEGY.md and PRODUCT_FLOW_REVIEW.md.

## Character

A warm, illustrated notebook for speaking practice. Cream paper, vivid ink, soft shapes,
a little hand lettering, and generous space. Use the supplied studio and editorial references
for their warmth and expressive typography. Keep the working app compact and readable.

## Palette

The canonical tokens live in styles.css.

- Paper: #fbf7ed; input surface: #fffdf7.
- Text: #292d28; secondary text: #66695f.
- Cobalt: #234bcc, for the brand, display headings, and primary actions.
- Green: #39775b, for encouragement and successful assessment.
- Sun yellow: #ffdb59, for small decorative accents.
- Pale blue #e9edff / yellow #faf0c7 / green #e6eedf distinguish New / Building /
  Getting comfortable. Always include the written status; color never carries meaning alone.
- Clay #a34230 on pale clay #f8e8df is reserved for errors and destructive hover feedback.

## Typography

All fonts are hosted locally under assets/fonts, with their SIL Open Font Licenses.

- Fraunces: brand, compact display headings, section titles, vocabulary preview chips, counts.
  Use 450–550 weight; SOFT 80 and WONK 1 for the main brand and heading treatment.
  Main title: at most 54px desktop / 39px narrow mobile; avoid long uppercase headings.
- DM Sans: questions, vocabulary editing, labels, instructions, and buttons.
  Practice questions use 23–30px and a generous line height. Mobile editing fields use 16px.
- Caveat: a few short, nonessential handwritten notes, at 22–24px.
  Never use it for a task, status, error, or necessary instruction.

## Composition and components

Keep supporting copy purposeful. Do not add decorative slogans to the header, library caption,
input footer, or page footer; these were removed at the user’s request on September 13, 2026.

- Desktop canvas is 1180px maximum with 36px side padding; the home columns have an 80px gap.
- At 760px, home becomes a single column. Main input and practice action precede the library.
- Library rows use compact 64px spacing and textual status badges, with no horizontal dividers.
  Avoid enclosing every row in a card.
- Filled cobalt primary buttons, green successful assessment, outlined secondary actions.
- Slightly irregular corners and small rotations suggest handwork. Do not rotate reading text.
- Keep focus rings, disabled states, reduced-motion support, and native controls.
- Apply the same palette and font pair to the downloaded progress image in assets/share-progress.js.

## Future illustrations

The UI already includes small, replaceable vector accents: assets/word-garden.svg and the brand
speech mark. The garden is referenced by the library empty state's pseudo-element and by the
summary's .illustration-slot. These are natural anchors for the next illustration pass.

Suggested small set:

1. Empty library: a little notebook or character collecting words.
2. Session preview: a small character getting ready to speak, if extra art proves useful.
3. Session complete: a growing sprout or quietly celebrating character.

Use a loose ink or pencil outline, uneven human strokes, simple rounded silhouettes, and one or
two flat accent fills. A gentle picture-book / Japanese stationery feeling fits; avoid decorative
scripts or cultural symbols unrelated to the product. Use transparent backgrounds or the paper
color. No text baked into the artwork and no detailed scene behind a question.

Keep art secondary to the learner's next action: roughly 100–140px in the empty library and
96px in the desktop summary, shrinking to 54–64px on phones. Reserve its dimensions to prevent
layout jumps and give decorative images an empty alt attribute. Replace the existing garden in
both locations when a final illustration is chosen. Large hero artwork is unnecessary here.

## Verification

The September 12 pass exercised add words → preview → speak/reveal → assess → summary →
return → progress image, and checked persistence after reload. Browser captures in
outputs/ui-review cover 1440px desktop, 390px mobile, and a 320px narrow screen.
All 26 existing JavaScript tests passed. Backend behavior was not changed.
