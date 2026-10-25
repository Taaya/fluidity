# Product Strategy

> Living document. This is the shared product context for decisions about positioning,
> features, user flows, experiments, and priorities.

The current feature classification and target learner flow are documented in
[`PRODUCT_FLOW_REVIEW.md`](PRODUCT_FLOW_REVIEW.md).

## Product idea

The product helps learners turn English vocabulary they already recognize into words and
phrases they can confidently use in conversation.

It is not primarily a tool for memorizing definitions or translating unfamiliar words. Its job
is to close the gap between knowing a word and being able to retrieve and use it naturally while
speaking.

**Core promise:**

> Turn vocabulary you already know into words you can actually use in conversation.

## The problem

Learners regularly encounter useful vocabulary in lessons, books, videos, work, and everyday
life. They may understand it when they see or hear it, but fail to recall it when speaking.

Traditional vocabulary tools are usually optimized for recognition and memorization. They help
the learner remember what a word means, but provide little practice retrieving it, fitting it into
a sentence, and using it to express a real thought.

The product focuses on that final step: active spoken use.

## Core product loop

1. The learner encounters vocabulary that is personally relevant.
2. They add it to the product with minimal effort.
3. The product creates short, contextual speaking prompts around that vocabulary.
4. The learner answers aloud and repeatedly retrieves the words in different situations.
5. The vocabulary becomes easier to use in spontaneous conversation.

The loop should feel like speaking practice, not a flashcard review with a microphone attached.

## Initial audience

The primary user is an English learner who:

- already studies English and regularly encounters new vocabulary;
- understands more words than they can actively use;
- wants to speak more fluently and with greater range;
- needs short, structured practice between longer lessons or conversations.

The strongest initial context may be a learner who works with an independent English teacher.
Vocabulary from a recent lesson is timely, relevant, and already partially understood, making it
well suited for activation practice.

## Teacher use case

Independent English teachers may serve three roles at once:

- a trusted acquisition channel;
- a source of relevant vocabulary and recurring practice triggers;
- an external structure that supports retention between lessons.

The intended learning loop is:

> lesson -> new vocabulary -> speaking practice between lessons -> more active use in the next lesson

The teacher's core problem is not simply whether a student remembers a definition. It is whether
the student can independently use the vocabulary later:

> "We covered this word in class" does not mean "I can use this word in conversation next week."

**Potential teacher positioning:**

> Speaking practice between lessons, using the vocabulary you actually learned with your teacher.

The teacher's primary reason to recommend the product should be better learning outcomes and more
productive lessons. Referral rewards can reinforce an already useful behavior, but should not be
the main value proposition.

## Current product hypothesis

> Independent English tutors can be both a high-intent acquisition channel and a recurring usage
> driver, because the product can become the speaking-practice layer between lessons for vocabulary
> introduced during class.

This is a hypothesis to validate, not yet a commitment to build a teacher platform.

## Implications for features and user flows

Near-term product decisions should strengthen the learner's activation loop first.

### Vocabulary input

- Adding vocabulary from a lesson should be fast enough to become part of homework.
- The product should work with words, phrases, and collocations, not only single words.
- Each word should remain an independent learning item even when several words are imported from
  the same lesson.
- The source or context of a word may be retained for teacher workflows and reporting, but should
  not turn lesson vocabulary into a mandatory practice deck or make input cumbersome.
- Teacher-provided vocabulary is an important path, but manual learner input must remain useful on
  its own.

### Speaking practice

- Practice should require the learner to say something meaningful aloud.
- Prompts should make the target vocabulary useful or necessary in context.
- Sessions should be short enough to fit naturally between lessons.
- Sessions may intentionally mix suitable vocabulary from different lessons and sources. This
  makes recall less predictable and closer to spontaneous conversation.
- Repeated practice should vary the situation rather than repeat the same recall question.
- Completion should communicate progress toward active use, not merely task completion.

### Return loop

- The product should give the learner a clear reason to return before the next lesson.
- Recently learned and not-yet-active vocabulary should be easy to resume.
- Progress should eventually distinguish between exposure, attempted use, and confident use.
- Reminders and teacher involvement are possible retention mechanisms, but the practice itself
  must provide enough value to support repeated use.

### Teacher involvement

- The first version does not require a teacher account or dashboard.
- Teacher-to-student vocabulary sharing can initially be tested manually or with a lightweight
  link/import flow that adds individual items to the learner's existing vocabulary.
- Reporting should be built only after teachers demonstrate a real need for it.
- The smallest useful report should answer a concrete teaching question, such as what the student
  practiced and what may need attention in the next lesson.

## Feature decision filter

A feature is strategically strong when it improves at least one of these outcomes:

1. The learner can bring in personally relevant vocabulary with less friction.
2. The learner retrieves and uses that vocabulary in meaningful speech.
3. The learner returns and practices it again at an appropriate time.
4. A teacher can naturally incorporate the practice into an existing lesson workflow.
5. The team learns whether teacher-led acquisition and retention actually work.

Features that mainly improve passive recognition, generic conversation practice, or teacher
administration without strengthening this loop should have a lower priority.

## What not to build yet

Until the teacher hypothesis is validated, avoid committing to:

- a full teacher dashboard;
- class and student management;
- detailed reporting infrastructure;
- a complex assignment system;
- a complete referral and payout system;
- school or institutional administration features.

These may become valuable later, but each adds a new product surface before the core behavior is
proven.

## First validation experiment

Start with one English teacher and a small number of existing students.

1. Review the product together from the teacher's perspective.
2. Let students add and practice vocabulary from their real lessons.
3. Observe whether the teacher includes the product in homework without repeated prompting.
4. Observe whether students start, complete, and repeat speaking practice.
5. Ask how the practice affects the following lesson.
6. Watch for an unsolicited need to assign vocabulary or view student progress.

The experiment should rely on lightweight manual support where needed. Its goal is to learn which
workflow deserves productization, not to simulate a finished teacher platform.

## Evidence to look for

Strong signals include:

- a teacher recommends the product to additional students;
- the teacher repeatedly includes it in homework;
- students practice vocabulary from actual lessons rather than only trying sample words;
- students return between lessons without individual reminders from the product team;
- practiced vocabulary appears more readily in the next lesson;
- the teacher independently asks for assignments, reports, or visibility into practice;
- students continue using the product when teacher prompting becomes lighter.

Weak or ambiguous signals include registrations, one-time trials, positive interview feedback, or
teacher interest that does not lead to recurring student practice.

## Discovery questions for teachers

- How do you currently capture and share vocabulary from a lesson?
- What vocabulary homework do you assign, and how do you check it?
- Which parts of that homework do students regularly skip?
- How do you know whether a student can actively use a word?
- What do you use Quizlet, Anki, shared documents, or other tools for today?
- What is missing from those tools?
- At what point in the lesson workflow could short speaking practice fit naturally?
- Would you recommend a third-party product to students? What would make that easy or risky?
- What information about practice would change what you do in the next lesson?

## Possible future model

If the hypothesis is validated, the product may develop into a B2B2C loop:

> Teacher -> Student -> Product

Possible later capabilities include teacher-assigned vocabulary, progress summaries, practiced / skipped / parked states, speaking-practice volume, and recommendations before the next lesson.

A referral model may complement this loop. A student could receive a benefit, while a teacher is
rewarded for a retained or paying learner rather than a registration. The economics and mechanics
should be designed only after organic recommendation behavior is observed.

## Strategic guardrail

The product should remain valuable to an individual learner without a teacher. Teacher involvement
is a promising distribution and retention layer, not a requirement for the core experience.
