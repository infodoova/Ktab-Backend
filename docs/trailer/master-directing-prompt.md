# Cinematic Book Trailer — Master Directing Prompt (source)

Supplied by the product owner on 2026-09-30. This is the source of the directing language in
`ops/trailer-agent/system-prompt.md` (§5, §7.C) and of the fixed `DIRECTING LANGUAGE` block that opens every
Seedance 2.5 prompt. Ktab adaptations are listed at the end; change the system prompt and this file together.

---

CINEMATIC BOOK TRAILER — MASTER PROMPT

INPUTS
Book content or supplied synopsis: [BOOK_CONTENT]
Duration: [DURATION — default: 30 seconds]
Aspect ratio: [ASPECT_RATIO — default: 16:9]

CREATIVE DIRECTION
Create a hyper-realistic cinematic trailer drawn from the supplied book. Make the viewer feel that a compelling world is opening and something significant is about to happen.
Preserve the book's genre, emotional tone, setting, period and central themes. Select specific, meaningful imagery from the supplied content.
For fiction, build anticipation around its characters and unresolved conflict. For nonfiction, build curiosity around its central question, ideas and human significance.
Do not invent major events, misrepresent claims or reveal the ending. When the supplied content is limited, develop its supported details rather than adding unsupported plot developments.
Maintain the directing language below across all books. Adapt the depicted world to each book. Any reference image guides only its assigned role; do not import unrelated subjects from it.

NARRATIVE FOCUS
Before selecting shots, identify one central dramatic question, one emotional anchor and up to three recurring visual motifs supported by the supplied content.
The emotional anchor may be a character, a relationship, a human consequence, or a central idea. Build the trailer around it so the imagery creates a coherent emotional progression.
Introduce motifs clearly before fragmenting or revisiting them. Each return should deepen their meaning or increase anticipation. Withhold the answer to the central question.

RHYTHM AND DRAMATIC STRUCTURE
Build the trailer through contrasting waves of energy:
Slow anticipation → fast fragments → sustained reveal → deeper discovery → rising momentum → emotional pause → stronger acceleration → final burst → stillness.
Preserve this emotional order while adapting its intensity to the genre. Escalation may come from emotional recognition, revelation or visual association rather than physical danger or faster action. Do not force every book into thriller pacing.
Use rapid montage shots of approximately 0.2–0.5 seconds and sustained hero shots of approximately 1.5–2 seconds. Include intermediate shot lengths to connect these extremes. Reserve occasional shorter flashes for the final burst, using already established imagery.
Suggested 30-second structure:
00:00–00:02 — Open with a restrained, intriguing reveal. Establish one visual question through a slow, deliberate camera move appropriate to the genre.
00:02–00:03.5 — First burst: short, connected details that awaken curiosity.
00:03.5–00:05.5 — First hero shot: reveal the larger context behind those details.
00:05.5–00:10 — Move deeper into the world through a motivated transition, brief discoveries and one slower meaningful detail.
00:10–00:14.5 — Expand the world and build momentum through setting, action and human presence where relevant.
00:14.5–00:17 — Move closer emotionally, then hold one quiet image for approximately 1.5 seconds. Let its significance register.
00:17–00:23 — Begin a stronger escalation. Connect increasingly charged fragments while expanding the story's stakes or central question.
00:23–00:25 — Reveal one defining image, character or motif that concentrates the trailer's meaning.
00:25–00:26.5 — Final rapid burst. Revisit selected established images with greater intensity.
00:26.5–00:28 — Cut abruptly to near-stillness. Leave the central question unresolved.
00:28–00:30 — Finish with a clean dark frame or restrained negative space, held for an editorial title to be added in post-production. Do not generate the title.
For other durations, scale the narrative sections while preserving their emotional order. When an editorial title is required, reserve approximately 1.5–2 seconds for it and fit the narrative into the remaining time. Reduce the number of shots when needed so important images remain readable.

CAMERA LANGUAGE
Make every camera move serve discovery, scale, tension or emotional proximity.
Treat the following techniques as a selective vocabulary, not a checklist. Use only the movements that serve this book and this sequence:
— Wide and low-angle tracking to establish space and physical presence.
— Lateral movement and foreground parallax to create depth.
— Controlled push-ins for emotional emphasis.
— Pullbacks or rising reveals to uncover context and scale.
— Partial arcs to reveal a subject's relationship to its surroundings.
— Restrained handheld movement for intimate or urgent human moments.
— Close-ups and extreme macro inserts for meaningful details.
— Near-locked framing for moments of emotional weight.
Use one dominant camera move per shot. Combine movements only when the shot has enough time to read clearly. Define a clear starting composition, movement direction and ending composition. Avoid repeating the same push-in across consecutive shots.
Choose lens perspective intentionally: wide for spatial depth, normal for human proximity, longer lenses for isolation and compression, macro for small details. Use selective focus with purpose; keep essential action readable.
Use whip pans, crash zooms and short speed ramps sparingly at specific energy changes. Fast camera travel must preserve coherent geography, perspective and parallax. Keep rapid inserts simple enough to register at their intended duration.

SUBJECT AND ENVIRONMENT MOTION
Every narrative shot must feel like moving photographic footage. Maintain natural breathing, blinking, gaze shifts, believable body weight and physical interaction. Keep environmental movement appropriate to the setting.
Slow camera movement does not automatically mean slow-motion action. Use slow motion only for selected dramatic emphasis.
Near-stillness means a nearly locked camera with subtle live subject or environmental motion, never a frozen frame. Quiet shots should retain subtle life without unnecessary activity. The final editorial background may be fully static.
Preserve character identity, anatomy, wardrobe, object count, scale, lighting continuity and spatial relationships.
Fantasy elements may appear when supported by the book; render them with convincing materials, weight and interaction.

EDITING AND TRANSITIONS
Use clean hard cuts as the foundation. Link selected shots through:
— Movement continuing in a compatible screen direction.
— Matching shapes, composition, textures or highlights.
— A foreground object physically covering the lens.
— A brief passage through darkness.
— Motivated whip pans or focus changes.
Give each designed transition a clear outgoing cue and incoming match. Keep physical wipes and match cuts visually legible. Avoid arbitrary morphing, excessive dissolves and slideshow transitions.
Fast passages must remain connected by story, motif or visual logic. Each burst should add information, strengthen a connection or increase anticipation. Do not introduce essential new information in flashes too brief to understand.

VISUAL QUALITY
Use cinematic photographic realism: convincing skin, fabric, surfaces, reflections and natural imperfections. Maintain coherent lighting, controlled contrast, soft highlight roll-off, realistic motion blur and restrained film grain.
Derive the palette, lighting and atmosphere from the book. Keep a consistent visual identity across the trailer without forcing every location into identical lighting.
Use haze, dust, flares or other effects only when motivated by the scene.

SHOT PLANNING
Before generation, translate this structure into a book-specific shot plan. Assign each shot an exact time range, narrative purpose, subject and action, framing and lens perspective, dominant camera move, and cut or transition cue.
Keep the total duration exact. Give each shot one clear visual idea, with action and camera movement achievable within its allotted time. Treat the suggested sections as dramatic beats, not as single shots.
Use this plan to guide generation and editing. If producing individual clips, preserve continuity and assemble their final timing in post-production.

OUTPUT RULES
Generate the picture only. Leave music, voiceover and sound design for post-production. Build visual beats that can support rising sound, impacts and a deliberate silence.
Do not generate typography, captions, subtitles, logos, watermarks or readable interface graphics. Add accurate titles and any essential readable text in editing. When a clue depends on readable text, compose a stable, unobstructed area for that editorial addition.
Avoid frozen-image animation, plastic skin, unstable faces, duplicated objects, unmotivated camera movement, excessive shake, random stock-style imagery and effects unrelated to the book.
The final trailer must reveal enough to create desire, preserve enough to create curiosity, and leave one strong image in the viewer's mind.

---

## Ktab adaptations (applied in the system prompt)

- **End-card window is 2.5 s (27.5–30.0), not 2 s** (confirmed by the product owner, 2026-09-30). The Ktab card has four elements (cover, title, author, logo). Following the master's own scaling rule, the beats after 00:23 are compressed: defining image 23–24.5, final burst 24.5–26, near-stillness 26–27.5 (1.5 s, as in the master), then a dark low-detail negative-space plate 27.5–30 with nothing bright or detailed in the centre, left or bottom (the cover is on the left, the text on the right, the logo at the bottom centre).
- **Frame rate:** the Seedance source rate (24 fps if it outputs 24); never converted.
- **One generation, not individual clips.** The "If producing individual clips…" line does not apply; the whole picture is one Seedance 2.5 generation.
- **Real people:** identifiable real people (including the author) are never depicted. Fictional characters may be shown, but only when the book is fiction. Nonfiction about real persons uses anonymous figures, hands, silhouettes and places.
- **Text-dependent clues** are not added in editing. The only editorial text is the Ktab end card.
- **Dissolves:** at most one in-camera dissolve, for a reflective beat.
