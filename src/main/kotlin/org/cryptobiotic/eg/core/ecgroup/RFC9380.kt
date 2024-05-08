package org.cryptobiotic.eg.core.ecgroup

// import jdk.vm.ci.code.CodeUtil.log2
import kotlin.math.ceil

// https://www.rfc-editor.org/rfc/rfc9380.pdf

class RFC9380(val DST: ByteArray, p: Int, k: Int, val m: Int) {
   // val L = ceil((ceil(log2(p)) + k) / 8)

    // hash_to_field(msg, count)
    //Parameters:
    //- DST, a domain separation tag (see Section 3.1).
    //- F, a finite field of characteristic p and order q = p^m.
    //- p, the characteristic of F (see immediately above).
    //- m, the extension degree of F, m >= 1 (see immediately above).
    //- L = ceil((ceil(log2(p)) + k) / 8), where k is the security
    //      parameter of the suite (e.g., k = 128).
    //
    //- expand_message, a function that expands a byte string and
    //  domain separation tag into a uniformly random byte string (see Section 5.3).
    //Input:
    //- msg, a byte string containing the message to hash.
    //- count, the number of elements of F to output.
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

    /*
    fun hash_to_field(msg: ByteArray, count: Int) : ByteArray {
        //Steps:
        val len_in_bytes = count * m * L
        val uniform_bytes = expand_message(msg, DST, len_in_bytes)
        //3. for i in (0, ..., count - 1):
        //4. for j in (0, ..., m - 1):
        //5. elm_offset = L * (j + i * m)
        //6. tv = substr(uniform_bytes, elm_offset, L)
        //7. e_j = OS2IP(tv) mod p
        //8. u_i = (e_0, ..., e_(m - 1))
        //9. return (u_0, ..., u_(count - 1))
    }

     */

// expand_message_xmd(msg, DST, len_in_bytes)
// Parameters:
// - H, a hash function (see requirements above).
// - b_in_bytes, b / 8 for b the output size of H in bits.
//  For example, for b = 256, b_in_bytes = 32.
// - s_in_bytes, the input block size of H, measured in bytes (see
//  discussion above). For example, for SHA-256, s_in_bytes = 64.

// Input:
// - msg, a byte string.
// - DST, a byte string of at most 255 bytes.
//  See below for information on using longer DSTs.
// - len_in_bytes, the length of the requested output in bytes,
//   not greater than the lesser of (255 * b_in_bytes) or 2^16-1.
// Output:
// - uniform_bytes, a byte string.

// Steps:
// 1. ell = ceil(len_in_bytes / b_in_bytes)
// 2. ABORT if ell > 255 or len_in_bytes > 65535 or len(DST) > 255
// 3. DST_prime = DST || I2OSP(len(DST), 1)
// 4. Z_pad = I2OSP(0, s_in_bytes)
// 5. l_i_b_str = I2OSP(len_in_bytes, 2)
// 6. msg_prime = Z_pad || msg || l_i_b_str || I2OSP(0, 1) || DST_prime
// 7. b_0 = H(msg_prime)
// 8. b_1 = H(b_0 || I2OSP(1, 1) || DST_prime)
// 9. for i in (2, ..., ell):
// 10. b_i = H(strxor(b_0, b_(i - 1)) || I2OSP(i, 1) || DST_prime)
// 11. uniform_bytes = b_1 || ... || b_ell
// 12. return substr(uniform_bytes, 0, len_in_bytes)

    /*
    fun expand_message(msg: ByteArray, DST: ByteArray, len_in_bytes: Int) {
        val ell = ceil(len_in_bytes / b_in_bytes)
        require (ell < 255 && len_in_bytes < 65535 && DST.size < 255)

        val DST_prime = DST || I2OSP(len(DST), 1)
        val Z_pad = I2OSP(0, s_in_bytes)
        val l_i_b_str = I2OSP(len_in_bytes, 2)
        val msg_prime = Z_pad || msg || l_i_b_str || I2OSP(0, 1) || DST_prime
        val b_0 = H(msg_prime)
        val b_1 = H(b_0 || I2OSP(1, 1) || DST_prime)
        for (i in (2..ell)) {
            val b_i = H(strxor(b_0, b_(i - 1)) || I2OSP(i, 1) || DST_prime)
        }
        val uniform_bytes = b_1 || ... || b_ell
        return substr(uniform_bytes, 0, len_in_bytes)
    }

     */


}