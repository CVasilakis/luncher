package com.luncher.launcher.outside

import java.io.IOException

/**
 * A wait on another app that ended without its answer, for [reason]. An [IOException], as a read
 * that fails is: an adapter that handles a failed read handles this too.
 */
class OutsideCallEnded(val reason: Reason) : IOException(reason.name) {

    enum class Reason {
        /** The caller's [Stop] was set. */
        STOPPED,

        /** The other app answered nothing for the time limit. */
        NO_ANSWER,

        /** [OutsideCalls.MOST] outside threads were alive already: none was started. */
        TOO_MANY,
    }
}
