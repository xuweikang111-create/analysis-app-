# Kimi 3.2.18 adaptation map

Generated from a real macOS GitHub Actions comparison of the official Kimi 3.2.17 and 3.2.18 DMGs.

## Verified package identity

| Item | 3.2.17 | 3.2.18 |
|---|---|---|
| DMG SHA256 | 9da2a4fc17d1ddaeb5044e1fea4e89679d36a6a011b2f5c0d44593d516b24423 | c445530633814332df9542195ba5b08ded6647e55df6f588355f3d080783861b |
| app.asar SHA256 | 68419e9575719c69d1ada1ffc6db0cad258a53503d9e727c20b7809fb17846fd | 6eb027db9d23b1c7f5246c1cc8f15bd92340febb422bc09e5d7309449469d6d0 |
| out/main/index.js SHA256 | 6a37077974207f7b501add47d9756eaff8d41eb87347e05b11f59f7b2386c439 | 4b20660fdc04d6c9de50bf252ccadb3fb9502f5be3065126529a83dcd5f98408 |
| main JS bytes | 8,615,823 | 8,674,804 |

Kimi 3.2.18 bundle version: 3.2.18.6

Electron: 43.7.3

## Confirmed patch-point mapping

These are direct 3.2.17 -> 3.2.18 pairs confirmed by the unified diff, not guesses.

1. App Translocation check

3.2.17:
    function _0xa7af7d() {

3.2.18:
    function _0x31c1a0() {

Fuzzy structural score: 0.947040. The unified diff places the function bodies directly opposite each other.

2. App Translocation dialog / move flow

3.2.17:
    async function _0x227e1f() {

3.2.18:
    async function _0x1cd112() {

Fuzzy structural score: 0.922068. The unified diff places the function bodies directly opposite each other.

3. Kimi Code / Daimon credential + baseUrl writer

3.2.17:
    async function _0x77ccf3(_0x2c62cd) {

3.2.18:
    async function _0x4318a2(_0x34f147) {

Fuzzy structural score: 0.717460. Confirmed by direct function-body diff and by the provisioning call site:

3.2.17:
    await _0x77ccf3({ "apiKey": ..., "baseUrl": ..., "userAgent": ..., "timezone": ..., "desktopVersion": ... })

3.2.18:
    await _0x4318a2({ "apiKey": ..., "baseUrl": ..., "userAgent": ..., "timezone": ..., "desktopVersion": ... })

4. Work gateway base URL resolver

3.2.17:
    return this.#opts[_0x500c30(5016)]?.()?.[_0x500c30(6736)]() || _0x56b828;

3.2.18:
    return this.#opts[_0x5ab4de(7084)]?.()?.[_0x5ab4de(3178)]() || _0x3607cb;

The structural resolver expression occurs exactly once in 3.2.18.

## Patch.cs changes required

Add:

    private const string MainSha3218 =
        "4b20660fdc04d6c9de50bf252ccadb3fb9502f5be3065126529a83dcd5f98408";

Update Supports(version, hash) and VersionFor(hash) for 3.2.18.

Do NOT route 3.2.18 through the existing 3.2.16 fallback branch.

Add a dedicated 3.2.18 patch branch using the four confirmed mappings above.

Reuse the existing 3.2.17 MOZHE injection semantics, but retarget every obfuscated identifier referenced by its replacement body to the 3.2.18 equivalents before enabling the branch.

Each patch point must be exactly-one-match guarded. If any expected point is missing or ambiguous, abort and keep the original app.asar.

## Important

This mapping identifies where the existing MOZHE 3.2.17 patch must move. It does not by itself reproduce the private MOZHE replacement bodies from source/Patch.cs.
