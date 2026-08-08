package com.starsoft.voint.rag;

/** Formats a raw embedding as a Postgres/pgvector literal, e.g. {@code [0.01,0.02,...]}. */
public final class VectorUtils {

    private VectorUtils() {
    }

    /**
     * How dissimilar a nearest-neighbour match is allowed to be before it's dropped instead of
     * forced into the results. pgvector's {@code <=>} on {@code vector} is cosine distance: 0 is
     * identical direction, 2 is opposite. Without this cutoff, a search always returns its top-K
     * regardless of how weak the match actually is - a tenant whose knowledge base doesn't cover
     * the caller's question still got 4 unrelated chunks stuffed into the prompt.
     *
     * <p>0.5 is a starting point, not a measured constant: with Gemini's text embeddings, genuinely
     * related short FAQ pairs land well under this in practice and unrelated pairs comfortably
     * above it, but it deserves tuning against real call transcripts once there's enough traffic to
     * see false positives (relevant chunk dropped) vs. false negatives (junk chunk kept).
     */
    public static final double MAX_COSINE_DISTANCE = 0.5;

    public static String toPgVector(float[] values) {
        StringBuilder sb = new StringBuilder(values.length * 9 + 2);
        sb.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
