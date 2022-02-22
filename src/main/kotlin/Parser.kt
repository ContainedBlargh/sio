import Instruction.*
import Value.RegisterRef
import java.nio.file.Files
import java.nio.file.Paths

object Parser {

    class ParserException(message: String) : Exception(message)

    fun <T> Iterator<T>.tryNext(): T? {
        if (hasNext()) {
            return next()
        } else {
            return null
        }
    }

    private fun parseMonOp(
        registers: Map<String, AnyRegister>,
        tokenIterator: Iterator<String>,
        ctor: (Value) -> Instruction
    ): Instruction {
        val first = tokenIterator.tryNext() ?: throw ParserException("No operand found for MonOp!")
        val value = Value.parse(first, registers)
        return ctor(value)
    }

    private fun parseBinOp(
        registers: Map<String, AnyRegister>,
        tokenIterator: Iterator<String>,
        ctor: (Value, Value) -> Instruction
    ): Instruction {
        val left = tokenIterator.tryNext() ?: throw ParserException("No operands found for BinOp!")
        val right = tokenIterator.tryNext() ?: throw ParserException("2nd operand missing for BinOp!")
        val lVal = Value.parse(left, registers)
        val rVal = Value.parse(right, registers)
        return ctor(lVal, rVal)
    }

    private fun splitIntoTokens(line: String): List<String> {
        val noComments = line.split("#").first().trim()
        val noAts = noComments.replace("@", "")
        return tokenExp.findAll(noAts).map { it.groupValues[0] }.toList()
    }

    val plusExp = Regex("^\\+\\s*(.*)$")
    val minusExp = Regex("^\\-\\s*(.*)$")

    private fun parseTestOp(
        registers: Map<String, AnyRegister>,
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
        val posLines = remainingLines.drop(posOffset).takeWhile { it.trim().startsWith("+") }
        val posInstructions = pos.foldIndexed(emptyList<Instruction>()) { i, acc, line ->
            val tokens = splitIntoTokens(line.replace(plusExp){ it.groupValues[1] })
            val instruction = parseInstruction(registers, jmpLabels, tokens.iterator(), posLines.drop(i + 1))
            acc + listOfNotNull(instruction)
        }
        val negLines = remainingLines.drop(negOffset).takeWhile { it.trim().startsWith("-") }
        val negInstructions = neg.foldIndexed(emptyList<Instruction>()) { i, acc, line ->
            val tokens = splitIntoTokens(line.replace(minusExp){ it.groupValues[1] })
            val instruction = parseInstruction(registers, jmpLabels, tokens.iterator(), negLines.drop(i + 1))
            acc + listOfNotNull(instruction)
        }
        return ctor(lVal, rVal, posInstructions, negInstructions)
    }

    private fun parseInstruction(
        registers: Map<String, AnyRegister>,
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
                if (it is RegisterRef && it.register is AnyRegister.XBusRegister)
                    Slx(it)
                else
                    throw IllegalStateException("slx must wait for an XBus register.")
            }
            "add" -> parseMonOp(registers, tokenIterator) { Add(it) }
            "sub" -> parseMonOp(registers, tokenIterator) { Sub(it) }
            "mul" -> parseMonOp(registers, tokenIterator) { Mul(it) }
            "not" -> Not()
            "dgt" -> parseMonOp(registers, tokenIterator) { Dgt(it) }
            "dst" -> parseBinOp(registers, tokenIterator) { l, r -> Dst(l, r) }
            "teq" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines) { l, r, p, n -> Teq(l, r, p, n) }
            "tgt" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines) { l, r, p, n -> Tgt(l, r, p, n) }
            "tlt" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines) { l, r, p, n -> Tlt(l, r, p, n) }
            "tcp" -> parseTestOp(registers, jmpLabels, tokenIterator, remainingLines) { l, r, p, n -> Tcp(l, r, p, n) }
            else -> throw IllegalArgumentException("Unknown instruction: '$first'")
        }
        if (tokenIterator.hasNext()) {
            throw IllegalStateException("No more tokens expected!")
        }
        return inst
    }

    private val registerExp = Regex("^\\$([a-zA-Z]+[a-zA-Z0-9]*)\\s?(.*)\$")
    private val labelExp = Regex("^([a-zA-Z]+[a-zA-Z0-9]*):\\s?(.*)\$")
    private val tokenExp = Regex("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'")

    fun parseFromSource(sourcePath: String): Node {
        val lines = Files.readAllLines(Paths.get(sourcePath)).map { it.trimEnd() }
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
            println("[$i]: $line")
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
            val tokens = tokenExp.findAll(noAts).map { it.groupValues[0] }.toList()
            val tokenIterator = tokens.iterator()
            if (!tokenIterator.hasNext()) {
                continue@loop
            }
            val instruction = parseInstruction(registers, labels, tokenIterator, lines.drop(i))
                ?: continue@loop
            if (instruction is TestInstruction) {
                instruction.positive.drop(1).forEach { lineIterator.next(); }
                instruction.negative.drop(1).forEach { lineIterator.next(); }
            }
            if (instruction is Teq) {
                instruction.positive.drop(1).forEach { lineIterator.next(); }
                instruction.negative.drop(1).forEach { lineIterator.next(); }
            }
            instructionList.add(runOnce to instruction)
        }
        if (jmpTable.any { it.value >= instructionList.size }) {
            throw IllegalStateException("cannot have jump label as final line!")
        }
        return Node(instructionList, registers, jmpTable)
    }
}