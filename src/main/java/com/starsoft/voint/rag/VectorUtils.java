package com.starsoft.voint.rag;

/** Formats a raw embedding as a Postgres/pgvector literal, e.g. {@code [0.01,0.02,...]}. */
public final class VectorUtils {

    private VectorUtils() {
    }

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
