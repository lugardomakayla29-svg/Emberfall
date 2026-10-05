package com.solme.emberfall.tome;

/**
 * Design doc 7.1. WEAPON tomes are the per-weapon capstone mechanics (see
 * TomePool - all 8 weapons now have one). PASSIVE covers everything else:
 * flat stat grants, on-hit/on-kill status procs, and companion summons.
 *
 * SYNERGY existed in v1 as a third category for Tomes that carried a
 * SynergyTag but did nothing on their own ("does nothing alone - shares
 * the tag"). The tome-refinement pass removed every one of those - each
 * tag family's Tomes are now all genuinely working PASSIVE effects, so
 * SYNERGY has no remaining users and was retired rather than kept as dead
 * code. A tag's synergy bonus (SynergyEffects) does not depend on this
 * category at all - it's driven purely by SynergyTag + PlayerBuild.tagCount.
 */
public enum TomeCategory {
    WEAPON,
    PASSIVE
}
