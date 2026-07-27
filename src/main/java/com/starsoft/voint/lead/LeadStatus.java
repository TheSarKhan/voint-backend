package com.starsoft.voint.lead;

/** Where a pilot request stands in the manual follow-up process. */
public enum LeadStatus {

    /** Just arrived from the landing form, nobody has looked at it yet. */
    NEW,

    /** Someone has called or written back. */
    CONTACTED,

    /** Became a tenant. The tenant row is created separately - this is only the outcome flag. */
    CONVERTED,

    /** Not a fit, or never reachable. */
    REJECTED
}
