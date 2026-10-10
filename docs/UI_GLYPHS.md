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
- (Superseded, see "Menu glyphs" below.) Seven screens drew NO non-ASCII glyph at all: Shop, Tome, Weapon, Merchant, Shrine, RunEnd, ChestReveal. On those, a square box cannot come from a glyph in our strings.

**Not established:** why the owner sees a square on those screens. It is not an unverified glyph in the source. Either he means those screens should now USE glyphs (the P6 wording), or the box has another cause that a headless check cannot see. See the INBOX question.

**Guard:** `tools/testbot/relic_math/GlyphGuardCheck.java` (13 checks, runs in the existing `math-checks` CI job). It fails on any glyph outside the verified 64 and on any section sign not followed by a colour-code character. Shown red on the real tree by injecting an airplane (U+2708) into `ShrineScreen` and a stray section sign into `RunEndScreen`: both caught with file and line, exit 1.

## Menu glyphs (Vesper, Drop 2 Task A, 2026-10-09 CT): one icon per row on every expedition menu
**Tested headless, look unverified.** The owner's "square boxes" are the stat icons in the stats panel (`RunHud.STAT_ICONS`); he liked them and wants the same style on every expedition menu. This is not a missing-glyph bug (the P6 sweep above stands: 0 unverified glyphs). So each menu row now gets one verified glyph, drawn like the stats panel: measured with `font.width`, centred in a marker column from `HudLayout.markerColumn/markerOffset`, in a fixed colour.

**One table.** The glyph of every row lives in `relic/MenuGlyphs` (pure, no Minecraft types). The screens call `MenuGlyphs.glyph(menu, "meaning")` and hold no glyph of their own; `tools/testbot/relic_math/MenuGlyphCheck.java` reads the same table, so it cannot test a copy.

**Rules.** (1) Only the 64 bitmap glyphs above, never the Unifont section. (2) A glyph must end on row 7 or above (the text line), so nothing hangs below it: `U+2611` and the other rows 2..10 glyphs are out. (3) A glyph the stats panel already uses keeps that meaning and is used for nothing else: star = level, diamond = gold, envelope = chest, white diamond = silver, skull = kills. (4) Glyphs are chosen by the KIND of row, never by an item's name, because weapon, upgrade, tome and relic names are open-ended data. (5) Vanilla buttons keep their widget type; the glyph is drawn on top at a fixed left inset (Koda, 2026-10-09 13:17 CT).

**Shared glyph, accepted:** crossed swords U+2694 means weapon, challenge shrine and bosses defeated (all "fighting things"). Koda accepted this on 2026-10-09 13:17 CT.

| Screen | Row (meaning) | Glyph | Code | Width | Rows | Why | Status |
|---|---|---|---|---|---|---|---|
| ShopScreen | weapon | ⚔ | U+2694 | 7 | 0..6 | crossed swords: a weapon row | VERIFIED glyph |
| ShopScreen | upgrade | ♯ | U+266F | 5 | 0..6 | sharp: raised a level; no verified glyph says upgrade | PROPOSAL |
| ShopScreen | silver | ♢ | U+2662 | 5 | 1..6 | white diamond: the shop is priced in Silver, same glyph as the stats panel | VERIFIED glyph |
| TomeChoiceScreen | tome | ✎ | U+270E | 7 | 0..6 | pencil: a tome is written knowledge | VERIFIED glyph |
| TomeChoiceScreen | banish | ✂ | U+2702 | 7 | 0..6 | scissors: cut the offer away | VERIFIED glyph |
| TomeChoiceScreen | reroll | ↔ | U+2194 | 8 | 1..5 | left-right arrow: swap for another set | VERIFIED glyph |
| WeaponChoiceScreen | weapon | ⚔ | U+2694 | 7 | 0..6 | crossed swords: a weapon card | VERIFIED glyph |
| MerchantScreen | relic | ♥ | U+2665 | 5 | 1..6 | heart suit: a relic is something prized; no verified glyph says relic | PROPOSAL |
| MerchantScreen | gold | ♦ | U+2666 | 5 | 1..6 | diamond: prices and your balance are gold, same glyph as the stats panel | VERIFIED glyph |
| MerchantScreen | time | ⌛ | U+231B | 7 | 0..6 | hourglass: the merchant leaves in N seconds | VERIFIED glyph |
| ShrineScreen | challenge | ⚔ | U+2694 | 7 | 0..6 | crossed swords: a trial to fight | VERIFIED glyph |
| ShrineScreen | curse | ☄ | U+2604 | 7 | 0..6 | comet: a curse falling on the boss; no verified glyph says curse | PROPOSAL |
| ShrineScreen | greed | ♣ | U+2663 | 5 | 1..6 | club suit: greed; no verified glyph says greed | PROPOSAL |
| RunEndScreen | time | ⌛ | U+231B | 7 | 0..6 | hourglass: time survived | VERIFIED glyph |
| RunEndScreen | level | ★ | U+2605 | 7 | 0..6 | star: level reached, the stats panel level glyph | VERIFIED glyph |
| RunEndScreen | kills | ☠ | U+2620 | 7 | 0..6 | skull: enemies defeated, the stats panel kills glyph | VERIFIED glyph |
| RunEndScreen | gold | ♦ | U+2666 | 5 | 1..6 | diamond: gold collected, the stats panel gold glyph | VERIFIED glyph |
| RunEndScreen | bosses | ⚔ | U+2694 | 7 | 0..6 | crossed swords: bosses defeated | VERIFIED glyph |
| RunEndScreen | silver | ♢ | U+2662 | 5 | 1..6 | white diamond: silver earned, the stats panel silver glyph | VERIFIED glyph |
| ChestRevealScreen | chest | ✉ | U+2709 | 7 | 1..6 | envelope: the chest, the stats panel chest glyph | VERIFIED glyph |
| ChestRevealScreen | relic | ♥ | U+2665 | 5 | 1..6 | heart suit: the relic that dropped, same glyph as the merchant's relic row | PROPOSAL |

**PROPOSAL picks (no verified glyph clearly means these; a taste call for the owner, not a fact):** upgrade = sharp, relic = heart suit, curse = comet, greed = club suit.

**What the check proves (VERIFIED, headless):** every glyph is in the verified set and none is from the Unifont section (G1, G2); none hangs below the text line (G3); none is wider than 9 px (G4); one meaning per glyph inside a menu (G7); the five stats-panel glyphs keep their meaning (G8, G9, G10); every one of the seven screens draws from the table, every meaning a screen asks for exists, and no screen holds a glyph of its own (G11 to G13); and a button's label starts clear of its glyph, using per-character widths measured from the client jar's `ascii.png` (G14).
**What it does NOT prove (UNSEEN):** how any menu looks; whether 4 px between the glyph and the label on the 70 px Banish button reads as cramped; whether the colours read well; whether a glyph over a disabled button looks right.
