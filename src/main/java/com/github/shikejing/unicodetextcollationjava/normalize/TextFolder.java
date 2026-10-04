package com.github.shikejing.unicodetextcollationjava.normalize;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Normalizer2;

import java.util.Arrays;

/**
 * Case folding service with full-width/half-width and compatibility handling.
 * <p>
 * The fold pipeline is <b>NFKC compatibility normalization followed by full Unicode
 * case folding</b>, which is the standard "compatibility case fold" used for
 * case-insensitive retrieval and de-duplication:
 * <ul>
 *   <li>full-width / half-width forms are folded to their ASCII equivalents
 *       ({@code Ａ} → {@code a}, {@code ｶﾞ} → {@code ガ});</li>
 *   <li>compatibility characters are folded ({@code ﬁ} → {@code fi},
 *       {@code ㌀} → {@code アパート});</li>
 *   <li>special case mappings are applied ({@code ß} → {@code ss},
 *       {@code ǅ} → {@code ǆ}).</li>
 * </ul>
 * The result is deterministic and idempotent. Every folded output code point can be
 * traced back to the source code point it was derived from via
 * {@link FoldResult#sourceIndex()}.
 */
public class TextFolder {

    private final TextNormalizer normalizer = new TextNormalizer();

    /**
     * Folds the given text.
     *
     * @param text the text to fold; {@code null} is treated as empty
     * @return the fold result with traceability mapping (never {@code null})
     */
    public FoldResult fold(String text) {
        String sanitized = normalizer.sanitize(text);
        int[] sourceCodePoints = sanitized.codePoints().toArray();

        Normalizer2 nfkc = Normalizer2.getNFKCInstance();
        String nfkcWhole = nfkc.normalize(sanitized);
        String folded = UCharacter.foldCase(nfkcWhole, 0);

        // Per-source-code-point contributions, used to build the traceability mapping.
        String[] contributions = new String[sourceCodePoints.length];
        for (int i = 0; i < sourceCodePoints.length; i++) {
            String asString = String.valueOf(Character.toChars(sourceCodePoints[i]));
            contributions[i] = UCharacter.foldCase(nfkc.normalize(asString), 0);
        }

        int[] sourceIndex = buildMapping(folded, sourceCodePoints.length, contributions);
        return new FoldResult(text == null ? "" : text, folded, sourceIndex);
    }

    /**
     * Builds the mapping from folded output code points to source code point indices.
     * <p>
     * In the common case the concatenation of per-source contributions equals the
     * whole-string folded output, so each output code point maps exactly to its
     * source. Cross-code-point compatibility composition (e.g. half-width dakuten
     * merging with the preceding kana) can make adjacent contributions merge; in that
     * case the merged output code points are attributed to the first involved source
     * index, which still traces the result back to the original text region.
     */
    private static int[] buildMapping(String folded, int sourceCount, String[] contributions) {
        int[] map = new int[folded.length()];
        Arrays.fill(map, -1);
        int op = 0;
        for (int i = 0; i < sourceCount; i++) {
            String contribution = contributions[i];
            if (!contribution.isEmpty()
                    && op + contribution.length() <= folded.length()
                    && folded.startsWith(contribution, op)) {
                for (int k = 0; k < contribution.length(); k++) {
                    map[op + k] = i;
                }
                op += contribution.length();
            } else {
                // Merged / reordered contribution: attribute any still unassigned output
                // positions to this source index so the mapping stays total and monotone.
                while (op < folded.length() && map[op] == -1) {
                    map[op] = i;
                    op++;
                }
            }
        }
        // Any trailing positions (should not normally happen) map to the last source.
        int fallback = sourceCount == 0 ? 0 : sourceCount - 1;
        while (op < folded.length()) {
            map[op] = fallback;
            op++;
        }
        return map;
    }
}
