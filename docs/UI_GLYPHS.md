# Minecraft font glyphs usable in Emberfall UI text

Source list: the owner's `allowed-minecraft-emojis.md` (156 entries, pasted 2026-10-08). Checked by Vesper against the **real Minecraft 1.21.11 client jar**, not from memory. **Tested headless, look unverified:** no glyph here has been drawn on a real client.

## How to read this

- **Bitmap** = the character is in the game's own crisp font sheets (`ascii.png`, `accented.png`, `nonlatin_european.png`). Measured width and rows below are in GUI pixels, so a layout can reserve exact space. **Safe to use.**
- **Unifont fallback** = the character is not in a bitmap sheet. The game draws it from Unifont, whose `.hex` data is NOT in the jar and was not on the machine this was checked on. It normally renders, but blurrier, at a different size, and **its width is not verified**. Use only after a real-client check.
- Row budget in `RunHud` is 9 px per row (`font.lineHeight + 4` minus padding). Glyphs reaching row 10 or beyond do not fit a stat row.
- **Hypixel caveat (the owner's warning, confirmed as a risk):** Hypixel Skyblock now ships a resource pack with its own glyph textures, so a glyph that looks a certain way there is not guaranteed to look that way in plain Minecraft. Emberfall ships no such pack, so it gets the stock game font only.

**Does not fit a 9 px stat row:** `☑` (U+2611) is 9 px wide and its rows run 2..10, so it hangs below the row. Do not use it as a row marker.

## Bitmap, verified in the jar (64)

| Glyph | Code point | Name | Width px | Rows |
|---|---|---|---|---|
| ☺ | U+263A | Smiling Face | 5 | 1..5 |
| ☹ | U+2639 | Frowning Face | 5 | 1..5 |
| ☠ | U+2620 | Skull And Crossbones | 7 | 0..6 |
| ❣ | U+2763 | Heart Exclamation | 5 | 0..6 |
| ❤ | U+2764 | Red Heart | 7 | 0..6 |
| ⌛ | U+231B | Hourglass Done | 7 | 0..6 |
| ⌚ | U+231A | Watch | 7 | 0..6 |
| ☀ | U+2600 | Sun | 8 | 0..7 |
| ☁ | U+2601 | Cloud | 8 | 0..5 |
| ☂ | U+2602 | Umbrella | 7 | 0..7 |
| ❄ | U+2744 | Snowflake | 7 | 0..6 |
| ☃ | U+2603 | Snowman | 7 | 0..7 |
| ☄ | U+2604 | Comet | 7 | 0..6 |
| ♠ | U+2660 | Spade Suit | 5 | 1..6 |
| ♥ | U+2665 | Heart Suit | 5 | 1..6 |
| ♦ | U+2666 | Diamond Suit | 5 | 1..6 |
| ♣ | U+2663 | Club Suit | 5 | 1..6 |
| ✉ | U+2709 | Envelope | 7 | 1..6 |
| ✂ | U+2702 | Scissors | 7 | 0..6 |
| ↔ | U+2194 | Left-Right Arrow | 8 | 1..5 |
| ☯ | U+262F | Yin Yang | 9 | 2..10 |
| ☮ | U+262E | Peace Symbol | 9 | 2..10 |
| ▶ | U+25B6 | Play Button | 6 | 1..5 |
| ◀ | U+25C0 | Reverse Button | 6 | 1..5 |
| ♀ | U+2640 | Female Sign | 3 | 1..6 |
| ♂ | U+2642 | Male Sign | 5 | 1..5 |
| ‼ | U+203C | Double Exclamation Mark | 3 | 0..6 |
| ☑ | U+2611 | Check Box With Check | 9 | 2..10 |
| ✔ | U+2714 | Check Mark | 6 | 0..6 |
| © | U+00A9 | Copyright | 7 | 0..6 |
| ® | U+00AE | Registered | 7 | 0..6 |
| ™ | U+2122 | Trade Mark | 8 | 0..3 |
| Ⓜ | U+24C2 | Circled M | 9 | 2..10 |
| ☷ | U+2637 | Trigram For Earth | 5 | 1..5 |
| ☵ | U+2635 | Trigram For Water | 5 | 1..5 |
| ☶ | U+2636 | Trigram For Mountain | 5 | 1..5 |
| ♡ | U+2661 | White Heart Suit | 5 | 1..6 |
| ♬ | U+266C | Beamed Sixteenth Notes | 7 | 0..6 |
| ♮ | U+266E | Music Natural Sign | 3 | 0..6 |
| ♯ | U+266F | Sharp | 5 | 0..6 |
| ☴ | U+2634 | Trigram For Wind | 5 | 1..5 |
| ♭ | U+266D | Flat | 3 | 0..6 |
| ♢ | U+2662 | White Diamond Suit | 5 | 1..6 |
| ☈ | U+2608 | Thunderstorm | 7 | 0..6 |
| ☒ | U+2612 | Ballot Box With X | 9 | 2..10 |
| ★ | U+2605 | Black Star | 7 | 0..6 |
| ✎ | U+270E | Lower Right Pencil | 7 | 0..6 |
| ♪ | U+266A | Eighth Note | 5 | 0..6 |
| ☰ | U+2630 | Trigram For Heaven | 5 | 1..5 |
| ☽ | U+263D | First Quarter Moon | 8 | 0..7 |
| ☐ | U+2610 | Ballot Box | 9 | 2..10 |
| ♧ | U+2667 | White Club Suit | 5 | 1..6 |
| ☱ | U+2631 | Trigram For Lake | 5 | 1..5 |
| ☻ | U+263B | Black Smiling Face | 7 | 0..6 |
| ♤ | U+2664 | White Spade Suit | 5 | 1..6 |
| # | U+0023 | Hash Sign | 5 | 0..6 |
| ♩ | U+2669 | Quarter Note | 3 | 0..6 |
| ☞ | U+261E | White Right Pointing Index | 9 | 4..9 |
| ♫ | U+266B | Beamed Eighth Notes | 7 | 0..6 |
| ☜ | U+261C | White Left Pointing Index | 9 | 4..9 |
| ☲ | U+2632 | Trigram For Fire | 5 | 1..5 |
| ☳ | U+2633 | Trigram For Thunder | 5 | 1..5 |
| ⚔ | U+2694 | Crossed Swords | 7 | 0..6 |
| ⚀ | U+2680 | Dice One | 7 | 0..6 |

## Unifont fallback, width NOT verified (92)

| Glyph | Code point | Name |
|---|---|---|
| ✌ | U+270C | Victory Hand |
| ☝ | U+261D | Index Pointing Up |
| ✍ | U+270D | Writing Hand |
| ♨ | U+2668 | Hot Springs |
| ✈ | U+2708 | Airplane |
| ♟ | U+265F | Chess Pawn |
| ☎ | U+260E | Telephone |
| ⌨ | U+2328 | Keyboard |
| ✏ | U+270F | Pencil |
| ✒ | U+2712 | Black Nib |
| ☢ | U+2622 | Radioactive |
| ☣ | U+2623 | Biohazard |
| ⬆ | U+2B06 | Up Arrow |
| ⬇ | U+2B07 | Down Arrow |
| ➡ | U+27A1 | Right Arrow |
| ⬅ | U+2B05 | Left Arrow |
| ↗ | U+2197 | Up-Right Arrow |
| ↘ | U+2198 | Down-Right Arrow |
| ↙ | U+2199 | Down-Left Arrow |
| ↖ | U+2196 | Up-Left Arrow |
| ↕ | U+2195 | Up-Down Arrow |
| ↩ | U+21A9 | Right Arrow Curving Left |
| ↪ | U+21AA | Left Arrow Curving Right |
| ✡ | U+2721 | Star Of David |
| ☸ | U+2638 | Wheel Of Dharma |
| ✝ | U+271D | Latin Cross |
| ☦ | U+2626 | Orthodox Cross |
| ☪ | U+262A | Star And Crescent |
| ♈ | U+2648 | Aries |
| ♉ | U+2649 | Taurus |
| ♊ | U+264A | Gemini |
| ♋ | U+264B | Cancer |
| ♌ | U+264C | Leo |
| ♍ | U+264D | Virgo |
| ♎ | U+264E | Libra |
| ♏ | U+264F | Scorpio |
| ♐ | U+2650 | Sagittarius |
| ♑ | U+2651 | Capricorn |
| ♒ | U+2652 | Aquarius |
| ♓ | U+2653 | Pisces |
| ✖ | U+2716 | Multiply |
| 〰 | U+3030 | Wavy Dash |
| ✳ | U+2733 | Eight-Spoked Asterisk |
| ✴ | U+2734 | Eight-Pointed Star |
| ❇ | U+2747 | Sparkle |
| ㊗ | U+3297 | Japanese “Congratulations” Button |
| ㊙ | U+3299 | Japanese “Secret” Button |
| ▪ | U+25AA | Black Small Square |
| ▫ | U+25AB | White Small Square |
| ☋ | U+260B | Descending Node |
| ☌ | U+260C | Conjunction |
| ♜ | U+265C | Black Chess Rook |
| ♕ | U+2655 | White Chess Queen |
| ☚ | U+261A | Black Left Pointing Index |
| ♝ | U+265D | Black Chess Bishop |
| ☓ | U+2613 | Saltire |
| ☛ | U+261B | Black Right Pointing Index |
| ☭ | U+262D | Hammer And Sickle |
| ✐ | U+2710 | Upper Right Pencil |
| ♖ | U+2656 | White Chess Rook |
| ♚ | U+265A | Black Chess King |
| ♛ | U+265B | Black Chess Queen |
| ☡ | U+2621 | Caution Sign |
| ☼ | U+263C | White Sun With Rays |
| ♅ | U+2645 | Uranus |
| ☟ | U+261F | White Down Pointing Index |
| ❦ | U+2766 | Floral Heart |
| ☊ | U+260A | Ascending Node |
| ☍ | U+260D | Opposition |
| ☬ | U+262C | Adi Shakti |
| ☫ | U+262B | Farsi Symbol |
| ☾ | U+263E | Last Quarter Moon |
| ☤ | U+2624 | Caduceus |
| ❧ | U+2767 | Rotated Floral Heart Bullet |
| ♄ | U+2644 | Saturn |
| ♁ | U+2641 | Earth |
| ♔ | U+2654 | White Chess King |
| ❥ | U+2765 | Rotated Heavy Black Heart Bullet |
| ☥ | U+2625 | Ankh |
| ♞ | U+265E | Black Chess Knight |
| ♆ | U+2646 | Neptune |
| ♃ | U+2643 | Jupiter |
| ☇ | U+2607 | Lightning |
| ☏ | U+260F | White Telephone |
| ♘ | U+2658 | White Chess Knight |
| ☧ | U+2627 | Chi Rho |
| ☉ | U+2609 | Sun |
| ♇ | U+2647 | Pluto |
| ☩ | U+2629 | Cross Of Jerusalem |
| ♙ | U+2659 | White Chess Pawn |
| ☨ | U+2628 | Cross Of Lorraine |
| ♗ | U+2657 | White Chess Bishop |

## Not in the owner's list

The owner asked me to look for more approved glyphs. I did not add any outside his list, because the only checkable source here is the jar, and every bitmap glyph in it is already covered by the 156-entry cross-check above. Any further candidates would need a real-client test first.

## P6 sweep result (Vesper, 2026-10-09 CT): every glyph the code draws is already verified

**Tested headless, look unverified.** Nothing here was drawn on a real client.

Method: a script walked every `.java` and `.json` under `src/client` and `src/main` (386 files), read literal characters AND `\uXXXX` escapes above U+007F, and compared each to the 64-glyph `bitmap` array in `docs/ui_glyphs.json`. Independently, each glyph found was looked up in the real 1.21.11 client jar (`font/nonlatin_european.png`) and its inked columns and rows measured from the pixels; they match this document for all six.

| Screen | File:line | String / use | Glyph | Code point | Verified bitmap | Width px | Rows | Replaced with |
|---|---|---|---|---|---|---|---|---|
| RunHud (stats panel) | `RunHud.java:32` | STAT_ICONS[0] | ★ | U+2605 | Y | 7 | 0..6 | none needed |
| RunHud (stats panel) | `RunHud.java:32` | STAT_ICONS[1] | ♦ | U+2666 | Y | 5 | 1..6 | none needed |
| RunHud (stats panel) | `RunHud.java:32` | STAT_ICONS[2] | ✉ | U+2709 | Y | 7 | 1..6 | none needed |
| RunHud (stats panel) | `RunHud.java:32` | STAT_ICONS[3] | ♢ | U+2662 | Y | 5 | 1..6 | none needed |
| RunHud (stats panel) | `RunHud.java:32` | STAT_ICONS[4] | ☠ | U+2620 | Y | 7 | 0..6 | none needed |
| CharacterSelectScreen | (removed) | the play marker was dropped when the screen was rebuilt as cards (no non-ASCII character is drawn now) | none | none | n/a | n/a | n/a | none needed |
| Hub bust sign (in world) | (removed) | the hub was removed (the Rift replaces it) | none | none | n/a | n/a | n/a | none needed |

- **Unverified (Unifont) glyphs in the source: 0.** Nothing to replace.
- `U+00A7` (section sign) appears 116 times in 18 files (literal characters, counted by script). It is the colour-code prefix, consumed by the game and never drawn, so it is not a glyph. One use, `EmberfallCommands.java:450`, is `replaceAll("§.", "")`, a regex that STRIPS codes; it is never drawn.
- `lang/en_us.json` has no non-ASCII character. No font override (`withFont`) exists anywhere.
- Seven screens draw NO non-ASCII glyph at all: Shop, Tome, Weapon, Merchant, Shrine, RunEnd, ChestReveal. On those, a square box cannot come from a glyph in our strings.

**Not established:** why the owner sees a square on those screens. It is not an unverified glyph in the source. Either he means those screens should now USE glyphs (the P6 wording), or the box has another cause that a headless check cannot see. See the INBOX question.

**Guard:** `tools/testbot/relic_math/GlyphGuardCheck.java` (13 checks, runs in the existing `math-checks` CI job). It fails on any glyph outside the verified 64 and on any section sign not followed by a colour-code character. Shown red on the real tree by injecting an airplane (U+2708) into `ShrineScreen` and a stray section sign into `RunEndScreen`: both caught with file and line, exit 1.
