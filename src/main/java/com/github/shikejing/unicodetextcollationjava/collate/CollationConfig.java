package com.github.shikejing.unicodetextcollationjava.collate;

import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;

/**
 * Immutable collation configuration.
 * <p>
 * Instances are value-based and safe to share across threads. The configuration
 * participates in the identity of every generated {@link SortKey}: keys produced with
 * different locale, strength or version tags are never silently compared as equal.
 *
 * @param localeTag  BCP-47 locale tag, e.g. {@code zh-CN}, {@code de-DE}; empty for the
 *                   root locale (language-neutral ordering)
 * @param strength   collation strength controlling which differences are significant
 * @param versionTag configuration version tag; bump it when collation rules change so
 *                   previously generated keys are detected as incompatible
 */
public record CollationConfig(String localeTag, CollationStrength strength, String versionTag) {

    /** Current configuration version. Bump when collation rules change. */
    public static final String VERSION = "1";

    public CollationConfig {
        localeTag = localeTag == null ? "" : localeTag;
        strength = strength == null ? CollationStrength.TERTIARY : strength;
        versionTag = versionTag == null ? VERSION : versionTag;
    }

    /** Root-locale, tertiary-strength, current-version configuration. */
    public static CollationConfig defaultConfig() {
        return new CollationConfig("", CollationStrength.TERTIARY, VERSION);
    }

    /**
     * Returns the ICU locale for this configuration.
     */
    public ULocale toULocale() {
        return localeTag.isEmpty() ? ULocale.ROOT : ULocale.forLanguageTag(localeTag);
    }

    /**
     * Returns the ICU strength constant.
     */
    public int icuStrength() {
        return switch (strength) {
            case PRIMARY -> Collator.PRIMARY;
            case SECONDARY -> Collator.SECONDARY;
            case IDENTICAL -> Collator.IDENTICAL;
            case TERTIARY -> Collator.TERTIARY;
        };
    }

    /**
     * Returns a stable 32-bit identity hash derived from the locale tag, strength
     * ordinal and version tag. Unlike {@link Object#hashCode()}, the strength ordinal
     * and string hashes used here are deterministic across JVM executions.
     */
    public int identityHash() {
        int h = localeTag.hashCode();
        h = 31 * h + strength.ordinal();
        h = 31 * h + versionTag.hashCode();
        return h;
    }
}
