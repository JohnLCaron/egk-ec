package org.cryptobiotic.eg.cli

import com.verificatum.vecj.VEC
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import org.cryptobiotic.eg.core.ecgroup.EcElementModP
import org.cryptobiotic.eg.core.ecgroup.EcElementModQ
import org.cryptobiotic.eg.core.ecgroup.EcGroupContext
import org.cryptobiotic.eg.core.ecgroup.VecGroupNative
import org.cryptobiotic.eg.core.productionGroup

/*
 java -jar build/libs/egk-ec-0.1-SNAPSHOT-uber.jar \
    -set "/usr/local/lib:/usr/java/packages/lib:/usr/lib64:/lib64:/lib:/usr/lib" \
    -show "properties,eclib,hasVEC"
 */
class RunShowSystem {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parser = ArgParser("RunShowSystem")
            val show by parser.option(
                ArgType.String,
                shortName = "show",
                description = "[properties,java.library.path,hasVEC]"
            )
            val setPath by parser.option(
                ArgType.String,
                shortName = "set",
                description = "set java.library.path"
            )
            val showTally by parser.option(
                ArgType.String,
                shortName = "tally",
                description = "summarize tally results"
            )
            parser.parse(args)

            val showSet = if (show == null) emptySet() else show!!.split(",").toSet()
            showSystem(ShowSet(showSet), setPath)
            if (showTally != null) showTally(showTally!!)
        }

        class ShowSet(val want: Set<String>) {
            fun has(show: String) = want.contains("all") || want.contains(show)
        }

        fun showSystem(showSet: ShowSet, setPath: String?) {
            if (setPath != null) {
                System.setProperty("java.library.path", setPath)
            }

            if (showSet.has("properties")) {
                val sortedmap = System.getProperties().map {
                    it.key as String to it.value
                }.sortedBy { it.first }.toMap()
                sortedmap.forEach { println(it) }
            }

            if (showSet.has("java.library.path")) {
                System.getProperties().forEach {
                    if (it.key == "java.library.path") println(it)
                }
            }

            if (showSet.has("hasVEC")) {
                if (testVecDirectExp())
                    println("VECJ, VEC and GMP are installed")
                else
                    println("VECJ, VEC and GMP are not installed")
            }
        }

        fun testVecDirectExp(): Boolean {
            try {
                val group = productionGroup("P-256") as EcGroupContext
                if (!(group.vecGroup is VecGroupNative)) {
                    return false
                }
                val curvePtr: ByteArray = VEC.getCurve("P-256")
                val nonce = group.randomElementModQ()
                val h = group.gPowP(group.randomElementModQ()) as EcElementModP
                val hx = h.ec.x
                val hy = h.ec.y
                val scalar = (nonce as EcElementModQ).element
                VEC.mul(curvePtr, hx, hy, scalar)
                return true

            } catch (t: Throwable) {
                return false
            }
        }

        fun showTally(showTally: String) {

        }
    }
}