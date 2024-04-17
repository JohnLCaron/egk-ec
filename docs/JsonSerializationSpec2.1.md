# Egk Election Record JSON version 2.1 serialization (proposed specification)

draft 04/12/2024

<!-- TOC -->
* [Egk Election Record JSON version 2.1 serialization (proposed specification)](#egk-election-record-json-version-21-serialization-proposed-specification)
    * [Constants](#constants)
    * [Elements](#elements)
      * [UInt256Json](#uint256json)
      * [ElementModQJson](#elementmodqjson)
      * [ElementModPJson](#elementmodpjson)
    * [Manifest](#manifest)
    * [ElectionConfig](#electionconfig)
    * [ElectionInitialized](#electioninitialized)
    * [PlaintextBallot](#plaintextballot)
    * [EncryptedBallot](#encryptedballot)
    * [EncryptedTally](#encryptedtally)
    * [DecryptedTallyOrBallot](#decryptedtallyorballot)
<!-- TOC -->

### Constants

For the P-256 Group:
````
{
    "name": "P-256",
    "type": "EllipticCurve",
    "protocolVersion": "v2.1.0",
    "constants": {
        "a": "ffffffff00000001000000000000000000000000fffffffffffffffffffffffc",
        "b": "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b",
        "primeModulus": "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",
        "order": "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
        "g.x": "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296",
        "g.y": "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5",
        "h": "1"
    }
}
````
For the Integer4096 Group:
````
{
    "name": "Integer4096 (low memory use)",
    "type": "IntegerGroup",
    "protocolVersion": "v2.0.0",
    "constants": {
        "largePrime": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffb17217f7d1cf79abc9e3b39803f2f6af40f343267298b62d8a0d175b8baafa2be7b876206debac98559552fb4afa1b10ed2eae35c138214427573b291169b8253e96ca16224ae8c51acbda11317c387eb9ea9bc3b136603b256fa0ec7657f74b72ce87b19d6548caf5dfa6bd38303248655fa1872f20e3a2da2d97c50f3fd5c607f4ca11fb5bfb90610d30f88fe551a2ee569d6dfc1efa157d2e23de1400b39617460775db8990e5c943e732b479cd33cccc4e659393514c4c1a1e0bd1d6095d25669b333564a3376a9c7f8a5e148e82074db6015cfe7aa30c480a5417350d2c955d5179b1e17b9dae313cdb6c606cb1078f735d1b2db31b5f50b5185064c18b4d162db3b365853d7598a1951ae273ee5570b6c68f96983496d4e6d330af889b44a02554731cdc8ea17293d1228a4ef98d6f5177fbcf0755268a5c1f9538b98261affd446b1ca3cf5e9222b88c66d3c5422183edc99421090bbb16faf3d949f236e02b20cee886b905c128d53d0bd2f9621363196af503020060e49908391a0c57339ba2beba7d052ac5b61cc4e9207cef2f0ce2d7373958d762265890445744fb5f2da4b751005892d356890defe9cad9b9d4b713e06162a2d8fdd0df2fd608ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        "smallPrime": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff43",
        "cofactor": "100000000000000000000000000000000000000000000000000000000000000bcb17217f7d1cf79abc9e3b39803f2f6af40f343267298b62d8a0d175b8bab857ae8f428165418806c62b0ea36355a3a73e0c741985bf6a0e3130179bf2f0b43e33ad862923861b8c9f768c4169519600bad06093f964b27e02d86831231a9160de48f4da53d8ab5e69e386b694bec1ae722d47579249d5424767c5c33b9151e07c5c11d106ac446d330b47db59d352e47a53157de04461900f6fe360db897df5316d87c94ae71dad0be84b647c4bcf818c23a2d4ebb53c702a5c8062d19f5e9b5033a94f7ff732f54129712869d97b8c96c412921a9d8679770f499a041c297cff79d4c9149eb6caf67b9ea3dc563d965f3aad1377ff22de9c3e62068dd0ed6151c37b4f74634c2bd09da912fd599f4333a8d2cc005627dca37bad43e64a3963119c0bfe34810a21ee7cfc421d53398cbc7a95b3bf585e5a04b790e2fe1fe9bc264fda8109f6454a082f5efb2f37ea237aa29df320d6ea860c41a9054ccd24876c6253f667bfb0139b5531ff30189961202fd2b0d55a75272c7fd73343f7899bca0b36a4c470a64a009244c84e77cebc92417d5bb13bf18167d8033eb6c4dd7879fd4a7f529fd4a7f529fd4a7f529fd4a7f529fd4a7f529fd4a7f529fd4a7f52a",
        "generator": "36036fed214f3b50dc566d3a312fe4131fee1c2bce6d02ea39b477ac05f7f885f38cfe77a7e45acf4029114c4d7a9bfe058bf2f995d2479d3dda618ffd910d3c4236ab2cfdd783a5016f7465cf59bbf45d24a22f130f2d04fe93b2d58bb9c1d1d27fc9a17d2af49a779f3ffbdca22900c14202ee6c99616034be35cbcdd3e7bb7996adfe534b63cca41e21ff5dc778ebb1b86c53bfbe99987d7aea0756237fb40922139f90a62f2aa8d9ad34dff799e33c857a6468d001acf3b681db87dc4242755e2ac5a5027db81984f033c4d178371f273dbb4fcea1e628c23e52759bc7765728035cea26b44c49a65666889820a45c33dd37ea4a1d00cb62305cd541be1e8a92685a07012b1a20a746c3591a2db3815000d2aaccfe43dc49e828c1ed7387466afd8e4bf1935593b2a442eec271c50ad39f733797a1ea11802a2557916534662a6b7e9a9e449a24c8cff809e79a4d806eb681119330e6c57985e39b200b4893639fdfdea49f76ad1acd997eba13657541e79ec57437e504eda9dd011061516c643fb30d6d58afccd28b73feda29ec12b01a5eb86399a593a9d5f450de39cb92962c5ec6925348db54d128fd99c14b457f883ec20112a75a6a0581d3d80a3b4ef09ec86f9552ffda1653f133aa2534983a6f31b0ee4697935a6b1ea2f75b85e7eba151ba486094d68722b054633fec51ca3f29b31e77e317b178b6b9d8ae0f"
    }
}
````

Also see _org.cryptobiotic.eg.election.ElectionConstants_


### Elements

All elements are byte arrays encoded in JSON as base64 Strings. 
They have leading zeros (if needed) to make them exactly nbytes, where nbytes depends on the Element type and the Group, 
as follows.

#### UInt256Json

UInt256 is the output of the SHA-256 hash function. It is 32 bytes.

#### ElementModQJson

ElementModQ is an element of the Z_q modular group. 
For both "Integer4096" and "P-256", its serialization is a byte array of 32 bytes.

#### ElementModPJson

ElementModP is an element of the Z_p modular group. 

For group "Integer4096", its serialization is a byte array of 512 bytes. 

For group "P-256", its serialization is a byte array of 33 bytes, encoded as a point-compressed elliptic curve coordinate as specified in
[The Elliptic Curve Digital Signature Algorithm (ECDSA)](https://safecurves.cr.yp.to/grouper.ieee.org/groups/1363/private/x9-62-09-20-98.pdf), section 4.2.1.


### Manifest

The current manifest.json is some version of common standards developed by NIST and others, possibly captured 
at [Election Results Reporting Common Data Format (CDF) Specification](https://github.com/usnistgov/ElectionResultsReporting).
For our purposes, we only need a small subset of this information, described by the following interface. (When the
electionguard serialization formats are standardized, we will implement those.)

````
interface ManifestIF {
   val ballotStyleIds: List<String> // in order by id
   val contests: List<Contest> // in order by sequence number

    interface Contest {
        val contestId: String
        val sequenceOrder: Int
        val selections: List<Selection> // in order by sequence number
    }

    interface Selection {
        val selectionId: String
        val sequenceOrder: Int
    }

    /** get the sorted contests for the given ballotStyle id */
    fun contestsForBallotStyle(ballotStyleId : String): List<Contest>?

    /** get the contest for the given contest id */
    fun findContest(contestId: String): Contest?

    /** get the contest selection limit (aka votesAllowed) for the given contest id */
    fun contestLimit(contestId : String): Int

    /** get the option selection limit for the given contest id */
    fun optionLimit(contestId : String): Int
}
````

### ElectionConfig

````
@Serializable
data class ElectionConfigJson(
    val config_version: String,
    val number_of_guardians: Int,
    val quorum: Int,

    val parameter_base_hash: UInt256Json, // Hp
    val manifest_hash: UInt256Json, // Hm
    val election_base_hash: UInt256Json, // Hb

    val chain_confirmation_codes: Boolean,
    val baux0: String, // // base64 encoded ByteArray, B_aux,0 from eq 59,60
    val metadata: Map<String, String> = emptyMap(), // arbitrary key, value pairs
)
````

Example:

````
{
    "config_version": "2.1.0",
    "number_of_guardians": 3,
    "quorum": 3,
    "parameter_base_hash": "KzsCXlDgnBGcun6USKzRyryUR+85vwYyfYHGZc3YYpY=",
    "manifest_hash": "0dlztkPInngwmcsiCZ5gb/IGrND3BcB3C+mSIUnx1vc=",
    "election_base_hash": "RtXizLZ571kchq3LumG2WwCiLYvaQ06gA95WuLQapOM=",
    "chain_confirmation_codes": false,
    "baux0": "ZGV2aWNlIGluZm9ybWF0aW9u",
    "metadata": {
        "CreatedBy": "RunCreateElectionConfig",
        "CreatedOn": "2024-03-10T16:24:18.594612696"
    }
}
````

### ElectionInitialized

````
@Serializable
data class ElectionInitializedJson(
    val joint_public_key: ElementModPJson, // aka K
    val extended_base_hash: UInt256Json, // aka He
    val guardians: List<GuardianJson>, // size = number_of_guardians
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class GuardianJson(
    val guardian_id: String,
    val x_coordinate: Int, // use sequential numbering starting at 1; == i of T_i, K_i
    val coefficient_proofs: List<SchnorrProofJson> // size = quorum
)

@Serializable
data class SchnorrProofJson(
    val public_key : ElementModPJson,
    val challenge : ElementModQJson,
    val response : ElementModQJson,
)
````

Example:

````
{
    "joint_public_key": "AvfqBE1YJ6xXxOuGyRmmqsiFO3KEWHrHAOOzW7CHe+mK",
    "extended_base_hash": "DmF5kU1/J0eWkyDa7YQIfyNRvhHKOK/1Xy/lZX9SsPY=",
    "guardians": [
        {
            "guardian_id": "guardian1",
            "x_coordinate": 1,
            "coefficient_proofs": [
                {
                    "public_key": "A/mTmgGafmz0Sf6rcKrcgk3K0Z4WUTySmwF/UA6u2KFo",
                    "challenge": "H6XYz1uKhVfonQPp22t7ZFrjksz8QqFu2H79kVw7l6s=",
                    "response": "Fw7GWN659jCQauIywoFObsd4ib/kZ+uR39o/PLg27Lk="
                },
                {
                    "public_key": "AqlqHvhJQhmSGL9unoeHHdSzPS+pl4l59dR2rIn2lgxa",
                    "challenge": "K4AuhxIHJ6sUwJL61OVMNmVYSZiExH3FTF5nw4YV7i4=",
                    "response": "Lh7eO3d+EUf6Ne82nELJ7jToCx5TS9glPWeNTG9pqmE="
                },
                {
                    "public_key": "AykbdJLnB/8EgRHAqgwT7zFxa2P2lzQUXn36mIQAOoUt",
                    "challenge": "lV61u8g4ZUVA9RxYHw1X8dlqniufuolMQCE3NzIJcC8=",
                    "response": "qDLwNVGqe5LHi+uT6OoBgZN6mYSg997asj5yHYW+qmc="
                }
            ]
        },
        {
            "guardian_id": "guardian2",
            "x_coordinate": 2,
            "coefficient_proofs": [
                {
                    "public_key": "AxBm4XqlzBpXSQgOR9tTYz572V3Jg6+YKM+/Pf7O6C7b",
                    "challenge": "1rInxvwV//rhY1xTQ8nvhrGCPsS5EhiwPC2GrkjzINc=",
                    "response": "eKhRvPZ1oXhrwumCo6I1GTPFAcTNIJnudqVY5s70BZA="
                },
                {
                    "public_key": "A/Q7MWKSBYmaCuSjRXtdzWG0uufn10tSE1P3SRlWl4Vz",
                    "challenge": "kgYuV40ddExP3YnXFz9kpXseuRVmpHuWUYYO+TA4stI=",
                    "response": "IhzZA/6Xia9t1u02Ricrqq84WWTALwBhBD8OU7sMlwY="
                },
                {
                    "public_key": "A1JK9lyR9+YUdm20+O6Y2ldY0X6p7fN5oXTgAzJ38Nqh",
                    "challenge": "hOgCbUzV2tm1bHOAZmbvaotJeFqoZMJJ4RQK5wBG3a0=",
                    "response": "VfAjX9sA6I0OBfRItjkAHoQkaca+o216uqiPjuM0t6k="
                }
            ]
        },
        {
            "guardian_id": "guardian3",
            "x_coordinate": 3,
            "coefficient_proofs": [
                {
                    "public_key": "ArQQJ7BwDOmsae1w2fjXbF35G9CzyRRf+5wK/AH4tJQe",
                    "challenge": "sUwbvE1JTZlnAL4F0NZjo8u2Ohh0yssmRC2dz76ioRk=",
                    "response": "b9nohWorl/VzQ1LY/cpaQdePpe5AzyMakQ1LS2L6svk="
                },
                {
                    "public_key": "AuXjfcwsoH0FSdAiwJxVOSPkjBjX9D3JZJ+7shY2r1MI",
                    "challenge": "vmz16V4svVsyibgYtwT9VDX9MjbpIAWF7SOvn6wY9Ho=",
                    "response": "fLbbUv1CcEAQx8lh1aap8gsOqqqjKSDcXqKL903OPTg="
                },
                {
                    "public_key": "A1BqAVFx4neArXbrI/lct0HuuUiOszqPaD7fYU7TN45Z",
                    "challenge": "+Z/Lhg0by6SaZd4C4oISJ8vtwrRWK6emb6SUm7Ksv9A=",
                    "response": "xnrKksmKdiDVM18y9Oxwsh6bvGXlXDg5xTkJX5fr9Ks="
                }
            ]
        }
    ]
}
````

### PlaintextBallot

````
@Serializable
data class PlaintextBallotJson(
    val ballot_id: String,
    val ballot_style: String,
    val contests: List<PlaintextContestJson>,
)

@Serializable
data class PlaintextContestJson(
    val contest_id: String,
    val sequence_order: Int,
    val selections: List<PlaintextSelectionJson>,
    val write_ins: List<String>,
)

@Serializable
data class PlaintextSelectionJson(
    val selection_id: String,
    val sequence_order: Int,
    val vote: Int,
)
````

Example:

````
{
    "ballot_id": "ballot-id-940962467",
    "ballot_style": "ballotStyle",
    "contests": [
        {
            "contest_id": "contest0",
            "sequence_order": 0,
            "selections": [
                {
                    "selection_id": "selection0",
                    "sequence_order": 0,
                    "vote": 0
                },
                {
                    "selection_id": "selection1",
                    "sequence_order": 1,
                    "vote": 0
                },
                {
                    "selection_id": "selection2",
                    "sequence_order": 2,
                    "vote": 0
                },
                {
                    "selection_id": "selection3",
                    "sequence_order": 3,
                    "vote": 0
                }
            ],
            "write_ins": [
            ]
        },
        {
            "contest_id": "contest1",
            "sequence_order": 1,
            "selections": [
                {
                    "selection_id": "selection4",
                    "sequence_order": 4,
                    "vote": 1
                },
                {
                    "selection_id": "selection5",
                    "sequence_order": 5,
                    "vote": 0
                },
                {
                    "selection_id": "selection6",
                    "sequence_order": 6,
                    "vote": 0
                },
                {
                    "selection_id": "selection7",
                    "sequence_order": 7,
                    "vote": 0
                }
            ],
            "write_ins": [
            ]
        },
        ...
    ]
}
````

### EncryptedBallot

````
@Serializable
data class EncryptedBallotJson(
    val ballot_id: String,
    val ballot_style_id: String,
    val voting_device: String,
    val timestamp: Long,  // Timestamp at which the ballot encryption is generated, in seconds since the epoch UTC.
    val code_baux: String, // Baux in eq 59
    val confirmation_code: UInt256Json,
    val election_id: UInt256Json,  // safety check this belongs to the right election
    val contests: List<EncryptedContestJson>,
    val state: String, // BallotState
    val encrypted_sn: ElGamalCiphertextJson?,
    val is_preencrypt: Boolean,
)

@Serializable
data class EncryptedContestJson(
    val contest_id: String,
    val sequence_order: Int,
    val contest_hash: UInt256Json,
    val selections: List<EncryptedSelectionJson>,
    val proof: RangeProofJson,
    val encrypted_contest_data: HashedElGamalCiphertextJson,
    val pre_encryption: PreEncryptionJson?, // only for is_preencrypt
)

@Serializable
data class EncryptedSelectionJson(
    val selection_id: String,
    val sequence_order: Int,
    val encrypted_vote: ElGamalCiphertextJson,
    val proof: RangeProofJson,
)

@Serializable
data class ElGamalCiphertextJson(
    val pad: ElementModPJson,
    val data: ElementModPJson
)

@Serializable
data class RangeProofJson(
    val proofs: List<ChaumPedersenJson>,
)

@Serializable
data class ChaumPedersenJson(
    val challenge: ElementModQJson,
    val response: ElementModQJson,
)

@Serializable
data class HashedElGamalCiphertextJson(
    val c0: ElementModPJson, // ElementModP,
    val c1: String, // ByteArray,
    val c2: UInt256Json, // UInt256,
    val numBytes: Int 
)

@Serializable
data class PreEncryptionJson(
    val preencryption_hash: UInt256Json,
    val all_selection_hashes: List<UInt256Json>, // size = nselections + limit, sorted numerically
    val selected_vectors: List<SelectionVectorJson>, // size = limit, sorted numerically
)

@Serializable
data class SelectionVectorJson(
    val selection_hash: UInt256Json,
    val short_code : String,
    val encryptions: List<ElGamalCiphertextJson>, // Ej, size = nselections, in order by sequence_order
)

TODO PreEncryptionJson is not in any spec.

````

Example:

````
{
    "ballot_id": "id-11",
    "ballot_style_id": "ballotStyle",
    "encrypting_device": "runWorkflowDevice",
    "timestamp": 1710109804,
    "code_baux": "64657669636520696E666F726D6174696F6E",
    "confirmation_code": "Ps0XtkkgYe27xu5Yag07RVytmBGHHRZP4LXXpXet9RQ=",
    "election_id": "DmF5kU1/J0eWkyDa7YQIfyNRvhHKOK/1Xy/lZX9SsPY=",
    "contests": [
        {
            "contest_id": "contest1",
            "sequence_order": 1,
            "contest_hash": "G5m4LUvjN9jvXLztf919JHMWY7rsDvFGHTFONDkTKjo=",
            "selections": [
                {
                    "selection_id": "selection1",
                    "sequence_order": 1,
                    "encrypted_vote": {
                        "pad": "AoH5OdJ4DruAF25wlS2iETBk9z8JGbavBVqVsW8qFQes",
                        "data": "ApLXALPPV38GJ4t4OTAp4KxqAllCJ3xOZpg11S9U8Fbh"
                    },
                    "proof": {
                        "proofs": [
                            {
                                "challenge": "1UXwhMtCosnwjlladuNn8uk5zvvxWOFNz5ig19mDhPA=",
                                "response": "WqjQFBV0nuQ8RA7LQg8VWJHi6yAYR3lZcoG/tsFn6WQ="
                            },
                            {
                                "challenge": "pbvwjgZEZjd0g6rHQ0CGNAks41k7hBp7ifl6+48hcg0=",
                                "response": "b8OCmnOIGvSVNQfIIoeOIF7MKCbLSb9rMTWmrrC7LAU="
                            }
                        ]
                    }
                },
                {
                    "selection_id": "selection2",
                    "sequence_order": 2,
                    "encrypted_vote": {
                        "pad": "A7QqrG56h4YcsggafQmkGw+a8g/WpuZM2+kaxsDdIdyg",
                        "data": "AqK88IynZv7kuuucRQkM3NATUIP71ZC6hHPfoBASlVXq"
                    },
                    "proof": {
                        "proofs": [
                            {
                                "challenge": "xNfuMwklv5k5YC7o60DK5HDqUGkNbgyug82CvPsX7Sk=",
                                "response": "rxXxxxsp4MwdA0Mr/+0mgbgQ5CMwg7aVxkfqEHsMJ8E="
                            },
                            {
                                "challenge": "37iBuo1q9dXMvTGgZ9glpkDTPk28gbQZw5bgYfR7bJQ=",
                                "response": "AwYwLwZEVWQV8QYf5pAssINEXTsVYI818hWhp8jrhto="
                            }
                        ]
                    }
                },
                {
                    "selection_id": "selection3",
                    "sequence_order": 3,
                    "encrypted_vote": {
                        "pad": "Az2scqC2pxjG4rpMfwPsn0TaMo9z56e4D3+rCW3VJQjT",
                        "data": "AgbDKpUhtLYYYiI98kColnVUbfjJeXyU0RmwaZxJmRBM"
                    },
                    "proof": {
                        "proofs": [
                            {
                                "challenge": "RSr/XVLaF3iixgW3UIjre6i3KREwpUxjcQKlDOiuwnU=",
                                "response": "Dy35bRjx2LUor0z90mUKds4z6K7tOfQbBkeR1Xaev2k="
                            },
                            {
                                "challenge": "KwjPpVEyfYVcGuIvuxo4kg1JWO391fCtK1iWVQSJvVY=",
                                "response": "JZBWsCCqze5Yu0Kb0DiIyfrq0lB//V/gaQbI2Q7iFes="
                            }
                        ]
                    }
                },
                {
                    "selection_id": "selection4",
                    "sequence_order": 4,
                    "encrypted_vote": {
                        "pad": "Avj6E/Q/gXadWlEZHWQqGRua9ORcBtRSbR5ShSa1OcZ8",
                        "data": "AjvPGUqpQeS8+A2TzgBz9rpllfd3hI2TrkxHGMf29uQO"
                    },
                    "proof": {
                        "proofs": [
                            {
                                "challenge": "gh6ksJbWmA6TYW52zs2DuMBNtJUybzk12redBd7GO9k=",
                                "response": "V+h4KsrRCxvLvIbRBEcW+EchNtq3/iBdWdQfZZlo2hQ="
                            },
                            {
                                "challenge": "UkrQ5DpsR5qf+0evM/si5McGX3p/x4ho8QdlGylpDT8=",
                                "response": "FK89A53A4RA15xB0TwQ15HJD2JLkvxnD7v0ZvKtoPpA="
                            }
                        ]
                    }
                }
            ],
            "proof": {
                "proofs": [
                    {
                        "challenge": "r3/fhalyEPVhkh+zJuBg82ASy7j9wjl4oiGE62lNx0M=",
                        "response": "r81EGMKDy9i0kt4RzRa4rg2aqn0aNXvYdommHm842jo="
                    },
                    {
                        "challenge": "HW+IftNRKD84MHP8/YIYo9Y1U+5mhr1lGK0YJuygEVk=",
                        "response": "jZrXY6Nxp58lV8bUBqB9hRSs2vGe1wFxH4A7M++CG9o="
                    }
                ]
            },
            "encrypted_contest_data": {
                "c0": "AqK6lDNkotIW/xeMIJN0XdC9SlS7N0F5bn8OMrUn2L5b",
                "c1": "PzbD8WQqzYRboWxCrdNzW6UeFHUNKm/HrwUBxRXoXj4DjMHpzIImeY7PLxojrQOZcYZurDq5f1bQ3WcR2RBw2Q==",
                "c2": "0Qsl5eucFUNajsyZk1PzMljP4cniHNt6FCeSldrpZ0Y=",
                "numBytes": 62
            }
        },
        {
            "contest_id": "contest2",
            "sequence_order": 2,
            "contest_hash": "m2JOH5S8NVzsTmQ+FJ7PgwtnPuTDf/LRrxuP0fdPkks=",
        ...
    ],
    "state": "CAST",
    "encrypted_sn": {
        "pad": "A3JdSTdpZblLJbjcg6tdO+X7862KxAiQbNJQsfjUf6nN",
        "data": "AgFm422B2ePhphT+QX+7mCXS5UhHVYu7MwBkHxYwSuKl"
    },
    "is_preencrypt": false
}
````

### EncryptedTally

````
@Serializable
data class EncryptedTallyJson(
    val tally_id: String,
    val contests: List<EncryptedTallyContestJson>,
    val cast_ballot_ids: List<String>,
    val election_id: UInt256Json,
)

@Serializable
data class EncryptedTallyContestJson(
    val contest_id: String,
    val sequence_order: Int,
    val selections: List<EncryptedTallySelectionJson>,
    val ballot_count: Int,                     // number of ballots voting on this contest
)

@Serializable
data class EncryptedTallySelectionJson(
    val selection_id: String,
    val sequence_order: Int,
    val encrypted_vote: ElGamalCiphertextJson,
)
````

Example:

````
{
    "tally_id": "RunWorkflow",
    "contests": [
        {
            "contest_id": "contest1",
            "sequence_order": 1,
            "selections": [
                {
                    "selection_id": "selection1",
                    "sequence_order": 1,
                    "encrypted_vote": {
                        "pad": "AwxDfSV5Ptt5DRUB2YmajRZ6Ur4vRyjjiep/BRd6Y1AZ",
                        "data": "AoeC9d2bXYBhMDXIeI0n6II5BuAwh2iFIvATZEHd3R0p"
                    }
                },
                {
                    "selection_id": "selection2",
                    "sequence_order": 2,
                    "encrypted_vote": {
                        "pad": "A8CnGZoEKk+3Zqz//GbhGgVUOaPGcV3xTWlmASOlbT7x",
                        "data": "A0q90Q04szsN1sIwtjqSBOsWn+yiwamlRnzUZ0G5cgJ8"
                    }
                },
                {
                    "selection_id": "selection3",
                    "sequence_order": 3,
                    "encrypted_vote": {
                        "pad": "Aq0MIGfaJrMSo/H0zjZ/79c3VGAJ6KuBPDmb85oqgeo1",
                        "data": "A0Taoxuo7pre3iDdzg/bvw7y+bdG0ER8L7Xizwh6+2ul"
                    }
                },
                {
                    "selection_id": "selection4",
                    "sequence_order": 4,
                    "encrypted_vote": {
                        "pad": "AvU5nsW4J2EEarHhlFf4h1fs81sV7yDKhgIXh9HMzFbW",
                        "data": "AlFwQgQLZsD+Tt6OU7LC7WEFSOiyfONhq4ETulaNNsmL"
                    }
                }
            ]
        },
        {
            "contest_id": "contest2",
            "sequence_order": 2,
            "selections": [
        ...
        
    "cast_ballot_ids": [
        "id1343738539",
        "id-939995991",
        "id-1698682164",
        "id-1254440059",
        "id287012309",
        "id-712320552",
        ...
    ],
    "election_id": "DmF5kU1/J0eWkyDa7YQIfyNRvhHKOK/1Xy/lZX9SsPY="
}
````

### DecryptedTallyOrBallot

The only difference between a DecryptedTally and a DecryptedBallot is the presence of
decrypted_contest_data for the ballot.

````
@Serializable
data class DecryptedTallyOrBallotJson(
    val id: String,
    val contests: List<DecryptedContestJson>,
    val election_id: UInt256Json,     // unique election identifier
)

@Serializable
data class DecryptedContestJson(
    val contest_id: String,
    val selections: List<DecryptedSelectionJson>,
    val ballot_count: Int,                     // number of ballots voting on this contest (for tally)
    val decrypted_contest_data: DecryptedContestDataJson?, //  ballot decryption only
)

@Serializable
data class DecryptedSelectionJson(
    val selection_id: String,
    val tally: Int,
    val b_over_m: ElementModPJson, // eq 65
    val encrypted_vote: ElGamalCiphertextJson,
    val proof: ChaumPedersenJson,
)

@Serializable
data class DecryptedContestDataJson(
    val contest_data: ContestDataJson,
    val encrypted_contest_data: HashedElGamalCiphertextJson,  // matches EncryptedBallotContest.encrypted_contest_data
    val proof: ChaumPedersenJson,
    val beta: ElementModPJson, //  β = C0^s mod p ; needed to verify 10.2
)

// (incomplete) strawman for contest data (section 3.3.7)
// "The contest data can contain different kinds of information such as undervote, null vote, and
// overvote information together with the corresponding selections, the text captured for write-in
// options and other data associated to the contest."

@Serializable
data class ContestDataJson(
    val over_votes: List<Int>,  // list of selection sequence_number for this contest
    val write_ins: List<String>, //  list of write_in strings
    val status: String,
)

TODO ContestDataJson is not in any spec.

````

Example:

````
{
    "id": "RunWorkflow",
    "contests": [
        {
            "contest_id": "contest1",
            "selections": [
                {
                    "selection_id": "selection1",
                    "tally": 11,
                    "b_over_m": "Am8RAXxeEFprEKoPHn/wLFlUCUQqnvi0+v6Ed0x7Ks1U",
                    "encrypted_vote": {
                        "pad": "AwxDfSV5Ptt5DRUB2YmajRZ6Ur4vRyjjiep/BRd6Y1AZ",
                        "data": "AoeC9d2bXYBhMDXIeI0n6II5BuAwh2iFIvATZEHd3R0p"
                    },
                    "proof": {
                        "challenge": "0nO66uzb06F9tM6nuCTQ8sarLDrABtSSiOfkNi8mqWY=",
                        "response": "MzkMEOsiaZG+l2aYyPdgDEkb90ULPY3nosC8KrOsyB8="
                    }
                },
                {
                    "selection_id": "selection2",
                    "tally": 10,
                    "b_over_m": "Aly4sen2VTMYW31o2Zqais2c1QrXqOKFNt+Z25352zpx",
                    "encrypted_vote": {
                        "pad": "A8CnGZoEKk+3Zqz//GbhGgVUOaPGcV3xTWlmASOlbT7x",
                        "data": "A0q90Q04szsN1sIwtjqSBOsWn+yiwamlRnzUZ0G5cgJ8"
                    },
                    "proof": {
                        "challenge": "JQvtefNk6qsLkdnFHw2ZlxIAOVKv0rLQaQGTuXtp7Dk=",
                        "response": "A6qANcIIFfgi0VKlvGV3Ajxr5G530yiPq1VvTq4sD20="
                    }
                },
                {
                    "selection_id": "selection3",
                    "tally": 3,
                    "b_over_m": "A6ZFE9ejieMyoLe2xri6plwoGHK1X5nuaeOz7k9lQWJS",
                    "encrypted_vote": {
                        "pad": "Aq0MIGfaJrMSo/H0zjZ/79c3VGAJ6KuBPDmb85oqgeo1",
                        "data": "A0Taoxuo7pre3iDdzg/bvw7y+bdG0ER8L7Xizwh6+2ul"
                    },
                    "proof": {
                        "challenge": "q9ki2/F7MLmihC4/8n0Dy6duM1VCv9l8neVoAuzgD1M=",
                        "response": "5K4w02neq4aZ3nE/AEeorKLxo4yFUmL0Z0V4r53IlV0="
                    }
                },
                {
                    "selection_id": "selection4",
                    "tally": 8,
                    "b_over_m": "A4uy1FiWBUYKOqGcub3jPyGHkTkn0qyYWEjIhvEsWgDr",
                    "encrypted_vote": {
                        "pad": "AvU5nsW4J2EEarHhlFf4h1fs81sV7yDKhgIXh9HMzFbW",
                        "data": "AlFwQgQLZsD+Tt6OU7LC7WEFSOiyfONhq4ETulaNNsmL"
                    },
                    "proof": {
                        "challenge": "+r2P2ANC/w50mFkvM1wqwS0oTrBUkF82iBQ2Zq4Fmvk=",
                        "response": "t0gxhY66yf1/roKc1d/O9xwe6cR5pQhCtp+QgmS55QE="
                    }
                }
            ]
        },
        {
            "contest_id": "contest2",
            "selections": [
       ...
    ],
    "election_id": "DmF5kU1/J0eWkyDa7YQIfyNRvhHKOK/1Xy/lZX9SsPY="
}
````