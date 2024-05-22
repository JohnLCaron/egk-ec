package org.cryptobiotic.eg.core.ecgroup

// implement what is in Section 5 of RFC 9380: Hashing to Elliptic Curves (rfc-editor.org).
// This produces a larger output before it is reduced modulo Q.
// You’ll need that algorithms expand_message in 5.3 and hash_to_field in 5.2, which uses expand_message.
// Note that you only need to do this for m=1 (and then their q = their p) and count = 1.

// This implementation is only for P-256, and its only doing hash_to_field() not hash_to_curve().

import java.math.BigInteger
import java.security.MessageDigest
import kotlin.experimental.xor

// https://www.rfc-editor.org/rfc/rfc9380.pdf

//- DST, a domain separation tag (see Section 3.1).
//- F, a finite field of characteristic p and order q = p^m.
//- p, the characteristic of F (see immediately above).
//- m, the extension degree of F, m >= 1 (see immediately above). m == 1
//- count, the number of elements of F to output. count == 1
//- k is the security parameter of the suite (e.g., k = 128, kBytes = 16).
class RFC9380(val group: EcGroupContext, val DST: ByteArray, kBytes: Int) {

    companion object {
        val b_in_bytes = 32 //  output of hashFunction in bytes = 32
        val s_in_bytes = 64 //  the input block size of H, measured in bytes.
    }

    val len_in_bytes = group.vecGroup.pbyteLength + kBytes

    // hash_to_field(msg)
    //- expand_message, a function that expands a byte string and domain separation tag into a uniformly random byte
    //  string (see Section 5.3).
    //Input:
    //- msg, a byte string containing the message to hash.
    //Output:
    //- (u_0, ..., u_(count - 1)), a list of field elements.
    //Steps:
    //1. len_in_bytes = count * m * L
    //2. uniform_bytes = expand_message(msg, DST, len_in_bytes)
    //3. for i in (0, ..., count - 1):
    //4. for j in (0, ..., m - 1):
    //5. elm_offset = L * (j + i * m)
    //6. tv = substr(uniform_bytes, elm_offset, L)
    //7. e_j = OS2IP(tv) mod p
    //8. u_i = (e_0, ..., e_(m - 1))
    //9. return (u_0, ..., u_(count - 1))

    fun hash_to_field(msg: ByteArray): EcElementModQ {
        //Steps:
        val uniform_bytes = expand_message(msg)
        val bi = BigInteger(1, uniform_bytes) // OS2IP equiv
        return EcElementModQ(group, bi.mod(group.vecGroup.primeModulus)) // note that p == q for P-256
    }

    // expand_message_xmd(msg, DST, len_in_bytes)
    // Parameters:
    // - H, a hash function (see requirements above).
    // - b_in_bytes, b / 8 for b the output size of H in bits.
    //  For example, for b = 256, b_in_bytes = 32.
    // - s_in_bytes, the input block size of H, measured in bytes. For example, for SHA-256, s_in_bytes = 64.
    // Input:
    // - msg, a byte string.
    // - DST, a byte string of at most 255 bytes. See below for information on using longer DSTs.
    // - len_in_bytes, the length of the requested output in bytes, not greater than the lesser of (255 * b_in_bytes) or 2^16-1.
    // - b_in_bytes: output of hashFunction in bytes = 32
    // - s_in_bytes: output of hashFunction in bytes = 64
    // Output:
    // - uniform_bytes, a byte string.
    fun expand_message(msg: ByteArray): ByteArray {
        // 1. ell = ceil(len_in_bytes / b_in_bytes)
        val ell = (len_in_bytes + b_in_bytes - 1)/ b_in_bytes
        // 2. ABORT if ell > 255 or len_in_bytes > 65535 or len(DST) > 255
        require (ell < 256 && len_in_bytes < 65536 && DST.size < 256)

        // 3. DST_prime = DST || I2OSP(len(DST), 1)
        val DST_prime = DST + I2OSP(DST.size, 1)
        // 4. Z_pad = I2OSP(0, s_in_bytes)
        val Z_pad = I2OSP(0, s_in_bytes)
        // 5. l_i_b_str = I2OSP(len_in_bytes, 2)
        val l_i_b_str = I2OSP(len_in_bytes, 2)
        // 6. msg_prime = Z_pad || msg || l_i_b_str || I2OSP(0, 1) || DST_prime
        val msg_prime = Z_pad + msg + l_i_b_str + I2OSP(0, 1) + DST_prime
        // 7. b_0 = H(msg_prime)
        val b_0 = H(msg_prime)
        // 8. b_1 = H(b_0 || I2OSP(1, 1) || DST_prime)
        val b_1 = H(b_0 + I2OSP(1, 1) + DST_prime)
        var prev = b_1
        var uniform_bytes = b_1

        // 9. for i in (2, ..., ell):
        for (i in (2..ell)) {
            // 10. b_i = H(strxor(b_0, b_(i - 1)) || I2OSP(i, 1) || DST_prime)
            val b_i = H(strxor(b_0, prev) + I2OSP(i, 1) + DST_prime)
            prev = b_i
            // 11. uniform_bytes = b_1 || ... || b_ell
            uniform_bytes += b_i
        }
        // 12. return substr(uniform_bytes, 0, len_in_bytes)
        return ByteArray(len_in_bytes) { uniform_bytes[it] }
    }

    fun H(ba: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(ba)
    }

    fun strxor(b0: ByteArray, bi: ByteArray): ByteArray {
        require(b0.size == bi.size)
        return ByteArray(b0.size) { b0[it].xor(bi[it]) }
    }

}

// https://github.com/rackerlabs/atlas-lb/blob/master/common/ca/bouncycastle/src/main/java/org/bouncycastle/pqc/math/linearalgebra/BigEndianConversions.java

@Throws(ArithmeticException::class)
fun I2OSP(x: Int, oLen: Int): ByteArray {
    if (x < 0) {
        throw RuntimeException("x must be unsigned")
    }
    val octL: Int = ceilLog256(x)
    if (octL > oLen) {
        throw ArithmeticException("Cannot encode given integer into specified number of octets.")
    }
    val result = ByteArray(oLen)
    for (i in oLen - 1 downTo oLen - octL) {
        result[i] = (x ushr (8 * (oLen - 1 - i))).toByte()
    }
    return result
}

/**
 * Compute <tt>ceil(log_256 n)</tt>, the number of bytes needed to encode
 * the integer <tt>n</tt>.
 *
 * @param n the integer
 * @return the number of bytes needed to encode <tt>n</tt>
 */
fun ceilLog256(n: Int): Int {
    if (n == 0) {
        return 1
    }
    var m: Int = if (n < 0) -n else n
    var d = 0
    while (m > 0) {
        d++
        m = m ushr 8
    }
    return d
}