package com.github.shikejing.collation;

import org.springframework.stereotype.Service;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Locale-aware, strength-configured collation of multilingual text.
 *
 * <p>Strength levels map to {@link Collator} strengths: accent and case
 * differences are distinguished at progressively higher levels
 * (PRIMARY ignores both, SECONDARY distinguishes accents, TERTIARY also
 * distinguishes case, IDENTICAL distinguishes everything).</p>
 *
 * <p>Determinism: for a fixed (locale, strength) pair the comparison is a
 * pure function of the input; the same inputs always produce the same order
 * in any execution. Ties under the configured strength are broken by a
 * stronger comparison so that {@link #sort} results are fully deterministic
 * even for inputs that differ only in case or accents.</p>
 *
 * <p>Collator instances are cached per (locale, strength) in a bounded map;
 * all methods are thread-safe.</p>
 */
@Service
public class CollationService {

    private static final int MAX_COLLATOR_CACHE = 64;

    private final Map<String, Collator> collatorCache = new ConcurrentHashMap<>();

    /**
     * Returns a comparator for the given locale and strength. Two strings
     * compare equal (0) iff they are equivalent at the configured strength;
     * inputs are NFC-normalized first so canonically equivalent strings
     * always compare equal.
     */
    public Comparator<String> comparator(Locale locale, CollationStrength strength) {
        Collator collator = collator(locale, strength);
        return (a, b) -> collator.compare(normalize(a), normalize(b));
    }

    /**
     * Returns a fully deterministic comparator: strings equivalent at the
     * configured strength are further ordered by IDENTICAL-strength
     * comparison and finally by code-point order, so no two distinct
     * strings ever compare equal.
     */
    public Comparator<String> deterministicComparator(Locale locale, CollationStrength strength) {
        Comparator<String> base = comparator(locale, strength);
        Collator identical = collator(locale, CollationStrength.IDENTICAL);
        return (a, b) -> {
            int cmp = base.compare(a, b);
            if (cmp != 0) {
                return cmp;
            }
            String na = normalize(a);
            String nb = normalize(b);
            cmp = identical.compare(na, nb);
            if (cmp != 0) {
                return cmp;
            }
            return na.compareTo(nb);
        };
    }

    /**
     * Sorts a copy of {@code inputs}; the argument list is not modified.
     * Null elements are sorted first. The result is fully deterministic:
     * ties at the configured strength are broken deterministically.
     */
    public List<String> sort(List<String> inputs, Locale locale, CollationStrength strength) {
        List<String> copy = new ArrayList<>(inputs);
        Comparator<String> cmp = deterministicComparator(locale, strength);
        copy.sort((a, b) -> {
            if (a == null) {
                return b == null ? 0 : -1;
            }
            if (b == null) {
                return 1;
            }
            return cmp.compare(a, b);
        });
        return copy;
    }

    /**
     * Returns the raw collation sort key bytes for {@code input} under the
     * given locale and strength. The input is NFC-normalized first.
     */
    public byte[] sortKey(String input, Locale locale, CollationStrength strength) {
        return collator(locale, strength).getCollationKey(normalize(input)).toByteArray();
    }

    private Collator collator(Locale locale, CollationStrength strength) {
        Locale effective = locale != null ? locale : Locale.ROOT;
        String cacheKey = effective.toLanguageTag() + "#" + strength;
        return collatorCache.computeIfAbsent(cacheKey, k -> {
            if (collatorCache.size() >= MAX_COLLATOR_CACHE) {
                collatorCache.clear();
            }
            Collator c = Collator.getInstance(effective);
            c.setStrength(toJdkStrength(strength));
            c.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
            return c;
        });
    }

    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    private int toJdkStrength(CollationStrength strength) {
        return switch (strength) {
            case PRIMARY -> Collator.PRIMARY;
            case SECONDARY -> Collator.SECONDARY;
            case TERTIARY -> Collator.TERTIARY;
            case IDENTICAL -> Collator.IDENTICAL;
        };
    }
}
