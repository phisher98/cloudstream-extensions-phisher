fun unPack(code: String): String {
    fun indent(code: MutableList<String>): List<String> {
        var tabs = 0
        var old = -1
        var add = ""
        val indentedCode = mutableListOf<String>()

        for (i in code.indices) {
            if (code[i].contains("{")) tabs++
            if (code[i].contains("}")) tabs--

            if (old != tabs) {
                old = tabs
                add = ""
                var tempTabs = old
                while (tempTabs > 0) {
                    add += "\t"
                    tempTabs--
                }
                old = tabs
            }

            indentedCode.add(add + code[i])
        }

        return indentedCode
    }

    val env = object {
        var eval: (String) -> Unit = { c -> code = c }
        val window = Any()
        val document = Any()
    }

    eval("with(env) { $code }")

    var formattedCode = code.replace(";", ";\n")
        .replace("{", "\n{\n")
        .replace("}", "\n}\n")
        .replace("\n;\n", ";\n")
        .replace("\n\n", "\n")

    val codeList = formattedCode.split("\n").toMutableList()
    codeList.addAll(indent(codeList))

    formattedCode = codeList.joinToString("\n")

    return formattedCode
}
