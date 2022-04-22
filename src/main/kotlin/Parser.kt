import Instruction.*
import Value.RegisterRef
import java.nio.file.Files
import java.nio.file.Paths

object Parser {

    private val stack = mutableListOf<String>()

    class ParserException(message: String) : Exception(message + "\n" + stack.joinToString("\n"))

    fun <T> Iterator<T>.tryNext(): T? {
        if (hasNext()) {
            return next()
        } else {
            return null
        }
    }

    private fun parseMonOp(
        registers: Map<String, Register>,
        tokenIterator: Iterator<String>,
        ctor: (Value) -> Instruction
    ): Instruction {
        val first = tokenIterator.tryNext() ?: throw ParserException("No operand found for MonOp!")
        val value = Value.parse(first, registers)
        return ctor(value)
    }

    private fun parseBinOp(
        registers: Map<String, Register>,
        tokenIterator: Iterator<String>,
        ctor: (Value, Value) -> Instruction
    ): Instruction {
        val left = tokenIterator.tryNext() ?: throw ParserException("No operands found for BinOp!")
        val right = tokenIterator.tryNext() ?: throw ParserException("2nd operand missing for BinOp!")
        val lVal = Value.parse(left, registers)
        val rVal = Value.parse(right, registers)
        return ctor(lVal, rVal)
    }

    private fun parseTriOp(
        registers: Map<String, Register>,
        tokenIterator: Iterator<String>,
        ctor: (Value, Value, Value) -> Instruction
    ): Instruction {
        val first = tokenIterator.tryNext() ?: throw ParserException("No operands found for TriOp!")
        val second = tokenIterator.tryNext() ?: throw ParserException("2nd operand missing for TriOp!")
        val third = tokenIterator.tryNext() ?: throw ParserException("3rd operand missing for TriOp!")
        val fVal = Value.parse(first, registers)
        val sVal = Value.parse(second, registers)
        val tVal = Value.parse(third, registers)
        return ctor(fVal, sVal, tVal)
    }

    private fun splitIntoTokens(line: String): List<String> {
        val noComments = line.split("#", ";").first().trim()
        val noAts = noComments.replace("@", "")
        return tokenExp.findAll(noAts).map { it.groupValues[0] }.toList()
    }

    val plusExp = Regex("^\\+\\s*(.*)$")
    val minusExp = Regex("^\\-\\s*(.*)$")

    private fun parseTestOp(
        registers: Map<String, Register>,
        jmpLabels: Set<String>,
        tokenIterator: Iterator<String>,
        remainingLines: List<String>,
        ctor: (Value, Value, List<Instruction>, List<Instruction>) -> Instruction
    ): Instruction {
        val left = tokenIterator.tryNext() ?: throw ParserException("No operands found for TestOp!")
        val right = tokenIterator.tryNext() ?: throw ParserException("2nd operand missing for TestOp!")
        val lVal = Value.parse(left, registers)
        val rVal = Value.parse(right, registers)
        val trimmed = remainingLines.map { it.trim() }
        val posFirst = trimmed.first().startsWith("+")
        val negFirst = trimmed.first().startsWith("-")
        val (pos, neg) = when {
            posFirst -> {
                val pos = trimmed.takeWhile { it.startsWith("+") }
                val neg = trimmed.drop(pos.size).takeWhile { it.startsWith("-") }
                pos to neg
            }
            negFirst -> {
                val neg = trimmed.takeWhile { it.startsWith("-") }
                val pos = trimmed.drop(neg.size).takeWhile { it.startsWith("+") }
                pos to neg
            }
            else -> emptyList<String>() to emptyList<String>()
        }
        val (posOffset, negOffset) = when {
            posFirst -> 0 to pos.size
            else -> neg.size to 0
        }
        val posLines = remainingLines
            .drop(posOffset)
            .takeWhile { it.trim().startsWith("+") }
            .map { it.replace("+", "", false).trimStart() }
        val posInstructions = pos.foldIndexed(emptyList<Instruction>()) { i, acc, line ->
            val tokens = splitIntoTokens(line.replace(plusExp) { it.groupValues[1] })
            val remainingPosLines = posLines.drop(i + 1)
            val instruction = parseInstruction(registers, jmpLabels, tokens.iterator(), remainingPosLines)
            acc + listOfNotNull(instruction)
        }
        val negLines = remainingLines
            .drop(negOffset)
            .takeWhile { it.trim().startsWith("-") }
            .map { it.replace("-", "", false).trimStart() }
        val negInstructions = neg.foldIndexed(emptyList<Instruction>()) { i, acc, line ->
            val tokens = splitIntoTokens(line.replace(minusExp) { it.groupValues[1] })
            val instruction = parseInstruction(registers, jmpLabels, tokens.iterator(), negLines.drop(i + 1))
            acc + listOfNotNull(instruction)
        }
        return ctor(lVal, rVal, posInstructions, negInstructions)
    }

    private fun parseInstruction(
        registers: Map<String, Register>,
        jmpLabels: Set<String>,
        tokenIterator: Iterator<String>,
        remainingLines: List<String>
    ): Instruction? {
        if (!tokenIterator.hasNext()) {
            return null
        }
        val first = tokenIterator.next().lowercase()
        val inst = when (first) {
            "end" -> End
            "nop" -> Nop
            "mov" -> parseBinOp(registers, tokenIterator) { lVal, rVal ->
                if (rVal !is RegisterRef) {
                    throw ParserException("mov right operand must be a registerRef!")
                }
                Mov(lVal, rVal)
            }
            "swp" -> parseBinOp(registers, tokenIterator) { lVal, rVal ->
                if (lVal !is RegisterRef || rVal !is RegisterRef) {
                    throw ParserException("swp requires both operands to be registerRefs!")
                }
                Swp(lVal, rVal)
            }
            "jmp" -> {
                val label = tokenIterator.tryNext() ?: throw IllegalStateException("jmp must have a label!")
                if (label !in jmpLabels) {
                    throw ParserException("jmp requires a label!")
                }
                return Jmp(label)
            }
            "slp" -> parseMonOp(registers, tokenIterator) { Slp(it) }
            "slx" -> parseMonOp(
                registers,
                tokenIterator
            ) {
                if (it is RegisterRef && (it.register as? Register.PinRegister<*>)?.let { it.channel is XBusChannel } != null)
                    Slx(it)
                else
                    throw ParserException("slx must wait for an XBus register, but tried to wait for a ${(it as RegisterRef).register.javaClass}.")
            }
            "gen" -> parseTriOp(registers, tokenIterator, ::Gen)
            "add" -> parseMonOp(registers, tokenIterator, ::Add)
            "sub" -> parseMonOp(registers, tokenIterator, ::Sub)
            "mul" -> parseMonOp(registers, tokenIterator, ::Mul)
            "not" -> Not()
            "cst" -> parseMonOp(registers, tokenIterator, ::Cst)
            "inc" -> parseMonOp(registers, tokenIterator, ::Inc)
            "dec" -> parseMonOp(registers, tokenIterator, ::Dec)
            "dgt" -> parseMonOp(registers, tokenIterator, ::Dgt)
            "dst" -> parseBinOp(registers, tokenIterator, ::Dst)
            "teq" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines, ::Teq)
            "tgt" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines, ::Tgt)
            "tlt" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines, ::Tlt)
            "tcp" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines, ::Tcp)
            else -> throw ParserException("Unknown instruction: '$first'")
        }
        if (tokenIterator.hasNext()) {
            throw ParserException("No more tokens expected!")
        }
        return inst
    }

    private val pinExp = Regex("^\\$([px][0-9]+[0-9a-zA-Z]*)\\s?(.*)$")
    private val xBusExp = Regex("^\\$(x[0-9]+[0-9a-zA-Z]*)\\s?(.*)\$")
    private val registerExp = Regex("^\\$([a-zA-Z0-9]+)\\s?(.*)\$")
    private val labelExp = Regex("^([a-zA-Z_\\-]+[a-zA-Z0-9_\\-]*):\\s*(.*)\$")
    private val tokenExp = Regex("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'")

    fun parseLines(registers: MutableMap<String, Register>, vararg lines: String): List<Pair<Boolean, Instruction>> {
        val labels = lines.filter { it.matches(labelExp) }.map {
            val match = labelExp.find(it)!!
            match.groupValues[1]
        }.toSet()
        val lineIterator = lines.iterator()
        val instructionList = mutableListOf<Pair<Boolean, Instruction>>()
        val jmpTable = mutableMapOf<String, Int>()
        var pinId = 0;
        var i = 0
        loop@ while (lineIterator.hasNext()) {
            var line = lineIterator.next().trim()
            i++
            stack.add("[$i]: $line")
            if (line.matches(pinExp)) {
                val isXbus = xBusExp.matches(line)
                val match = pinExp.find(line)!!
                val name = match.groupValues[1]
                registers.put(
                    name, Register.PinRegister(
                        name,
                        Pins.getPinChannel(pinId++, isXbus)
                            ?: throw ParserException("Pin register mismatch! Check the type of $name!")
                    )
                )
                if (match.groupValues.size == 1 || match.groupValues[2].isBlank()) {
                    continue@loop
                }
                line = match.groupValues[2]
            }
            if (line.matches(registerExp)) {
                val match = registerExp.find(line)!!
                val name = match.groupValues[1]
                registers.put(name, Register.PlainRegister(name))
                if (match.groupValues.size == 1 || match.groupValues[2].isBlank()) {
                    continue@loop
                }
                line = match.groupValues[2]
            }
            if (line.matches(labelExp)) {
                val match = labelExp.find(line)!!
                val label = match.groupValues[1]
                if (line in jmpTable.keys) {
                    throw ParserException("parser error, label '$label' already defined!\n[$i]:$line")
                }
                jmpTable[label] = instructionList.size
                if (match.groupValues.size == 1 || match.groupValues[2].isBlank()) {
                    continue@loop
                }
                line = match.groupValues[2]
            }
            val noComments = line.split("#", ";").first().trim()
            val runOnce = noComments.startsWith("@")
            val noAts = noComments.replace("@", "")
            val tokens = tokenExp.findAll(noAts).map { it.groupValues[0] }.toList()
            val tokenIterator = tokens.iterator()
            if (!tokenIterator.hasNext()) {
                continue@loop
            }
            val instruction = parseInstruction(registers, labels, tokenIterator, lines.drop(i))
                ?: continue@loop
            if (instruction is TestInstruction) {
                instruction.positive.forEach { stack.add("[+]: ${lineIterator.next()}"); i++ }
                instruction.negative.forEach { stack.add("[-]: ${lineIterator.next()}"); i++ }
            }
            instructionList.add(runOnce to instruction)
        }
        if (jmpTable.any { it.value >= instructionList.size }) {
            throw ParserException("Cannot have jump label as final line!")
        }
        return instructionList
    }

    fun parseFromPath(sourcePath: String): Node {
        val name = sourcePath.split('/', '\\').last()
        val lines = Files
            .readAllLines(Paths.get(sourcePath))
            .map { it.trimEnd() }
        val labels = lines.filter { it.matches(labelExp) }.map {
            val match = labelExp.find(it)!!
            match.groupValues[1]
        }.toSet()
        val lineIterator = lines.iterator()
        val instructionList = mutableListOf<Pair<Boolean, Instruction>>()
        val registers = Registers.getDefault().associateBy { it.identifier }.toMutableMap()
        val jmpTable = mutableMapOf<String, Int>()
        var pinId = 0;
        var i = 0
        loop@ while (lineIterator.hasNext()) {
            var line = lineIterator.next().trim()
            i++
            stack.add("[$i]: $line")
            if (line.matches(pinExp)) {
                val isXbus = xBusExp.matches(line)
                val match = pinExp.find(line)!!
                val name = match.groupValues[1]
                registers.put(
                    name, Register.PinRegister(
                        name,
                        Pins.getPinChannel(pinId++, isXbus)
                            ?: throw ParserException("Pin register mismatch! Check the type of $name!")
                    )
                )
                if (match.groupValues.size == 1 || match.groupValues[2].isBlank()) {
                    continue@loop
                }
                line = match.groupValues[2]
            }
            if (line.matches(registerExp)) {
                val match = registerExp.find(line)!!
                val name = match.groupValues[1]
                registers.put(name, Register.PlainRegister(name))
                if (match.groupValues.size == 1 || match.groupValues[2].isBlank()) {
                    continue@loop
                }
                line = match.groupValues[2]
            }
            if (line.matches(labelExp)) {
                val match = labelExp.find(line)!!
                val label = match.groupValues[1]
                if (line in jmpTable.keys) {
                    throw ParserException("parser error, label '$label' already defined!\n[$i]:$line")
                }
                jmpTable[label] = instructionList.size
                if (match.groupValues.size == 1 || match.groupValues[2].isBlank()) {
                    continue@loop
                }
                line = match.groupValues[2]
            }
            val noComments = line.split("#", ";").first().trim()
            val runOnce = noComments.startsWith("@")
            val noAts = noComments.replace("@", "")
            val tokens = tokenExp.findAll(noAts).map { it.groupValues[0] }.toList()
            val tokenIterator = tokens.iterator()
            if (!tokenIterator.hasNext()) {
                continue@loop
            }
            val instruction = parseInstruction(registers, labels, tokenIterator, lines.drop(i))
                ?: continue@loop
            if (instruction is TestInstruction) {
                instruction.positive.forEach { stack.add("[+]: ${lineIterator.next()}"); i++ }
                instruction.negative.forEach { stack.add("[-]: ${lineIterator.next()}"); i++ }
            }
            instructionList.add(runOnce to instruction)
        }
        if (jmpTable.any { it.value >= instructionList.size }) {
            throw ParserException("Cannot have jump label as final line!")
        }
        stack.clear()
        return Node(name, instructionList, registers, jmpTable)
    }
}