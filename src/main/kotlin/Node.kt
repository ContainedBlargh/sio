import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class Node(
    private val instructionList: List<Pair<Boolean, Instruction>>,
    private val registers: Map<String, AnyRegister>,
    private val jmpTable: Map<String, Int>
) {
    companion object {
        private val registerExp = Regex("^\\$([a-zA-Z]+[a-zA-Z0-9]+)\\s?(.*)\$")
        private val labelExp = Regex("^([a-zA-Z]+[a-zA-Z0-9]+):\\s?(.*)\$")
        private val tokenExp = Regex("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'")

        fun parseFromSource(sourcePath: String): Node {
            val lines = Files.readAllLines(Paths.get(sourcePath))
            val labels = lines.filter { it.matches(labelExp) }.map {
                val match = labelExp.find(it)!!
                match.groupValues[1]
            }.toSet()
            val lineIterator = lines.iterator()
            val instructionList = mutableListOf<Pair<Boolean, Instruction>>()
            val registers = Registers.getDefault().associateBy { it.identifier }.toMutableMap()
            val jmpTable = mutableMapOf<String, Int>()
            var i = 0
            loop@ while (lineIterator.hasNext()) {
                var line = lineIterator.next().trim()
                i++
                if (line.matches(registerExp)) {
                    val match = registerExp.find(line)!!
                    val name = match.groupValues[1]
                    registers.put(name, AnyRegister.Register(name))
                    if (match.groupValues.size == 1) {
                        continue@loop
                    }
                    line = match.groupValues[2]
                }
                if (line.matches(labelExp)) {
                    val match = labelExp.find(line)!!
                    val label = match.groupValues[1]
                    if (line in jmpTable.keys) {
                        throw IllegalStateException("parser error, label '$label' already defined!\n[$i]:$line")
                    }
                    jmpTable[label] = instructionList.size
                    if (match.groupValues.size == 1) {
                        continue@loop
                    }
                    line = match.groupValues[2]
                }
                val noComments = line.split("#").first().trim()
                val runOnce = noComments.startsWith("@")
                val noAts = noComments.replace("@", "")
                val tokens = tokenExp.findAll(noAts).flatMap { it.groupValues }
                    .map { it.trim() }.filter { it.isNotBlank() }.toList()
                val tokenIterator = tokens.iterator()
                if (!tokenIterator.hasNext()) {
                    continue@loop
                }
                val instruction = Instruction.parse(registers, labels, tokenIterator, lineIterator)
                    ?: continue@loop
                instructionList.add(runOnce to instruction)
            }
            if (jmpTable.any { it.value >= instructionList.size }) {
                throw IllegalStateException("cannot have jump label as final line!")
            }
            return Node(instructionList, registers, jmpTable)
        }
    }

    private var programPosition = 0

    fun getRegister(identifier: String): AnyRegister {
        return registers[identifier]!!
    }

    fun jumpTo(label: String) {
        programPosition = jmpTable[label] ?: programPosition
    }

    fun sleep(duration: Int) {
        Thread.sleep(duration.toLong())
    }

    fun executeAsync(): CompletableFuture<Unit> {
        val completableFuture = CompletableFuture<Unit>()
        return completableFuture.completeAsync {

        }
    }
}