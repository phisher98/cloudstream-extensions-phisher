package com.Animeowl

import app.cash.quickjs.QuickJs
import com.lagradost.cloudstream3.app

/**
 * Helper class to deobfuscate JavaScript strings with synchrony.
 */
object Deobfuscator {
    suspend fun deobfuscateScript(source: String): String? {
        val originalScript = app.get("https://raw.githubusercontent.com/Kohi-den/extensions-source/9328d12fcfca686becfb3068e9d0be95552c536f/lib/synchrony/src/main/assets/synchrony-v2.4.5.1.js").text
        // Sadly needed until QuickJS properly supports module imports:
        // Regex for finding one and two in "export{one as Deobfuscator,two as Transformer};"
        val regex = """export\{(.*) as Deobfuscator,(.*) as Transformer\};""".toRegex()
        val synchronyScript = regex.find(originalScript)?.let { match ->
            val (deob, trans) = match.destructured
            val replacement = "const Deobfuscator = $deob, Transformer = $trans;"
            originalScript.replace(match.value, replacement)
        } ?: return null

        return QuickJs.create().use { engine ->
            engine.evaluate("globalThis.console = { log: () => {}, warn: () => {}, error: () => {}, trace: () => {} };")
            engine.evaluate(synchronyScript)

            engine.set("source", TestInterface::class.java, object : TestInterface { override fun getValue() = source })
            engine.evaluate("new Deobfuscator().deobfuscateSource(source.getValue())") as? String
        }
    }

    @Suppress("unused")
    private interface TestInterface {
        fun getValue(): String
    }
}