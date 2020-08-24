package com.mivik.kamet

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.llvm.LLVM.LLVMTargetRef
import org.bytedeco.llvm.global.LLVM
import java.io.File
import kotlin.system.exitProcess

enum class OutputFormat(val defaultSuffix: String) {
	EXECUTABLE(
		if (System.getProperties().getProperty("os.name").contains("Windows", true)) ".exe"
		else ".out"
	),
	IR(".ll"), BITCODE(".bc"), OBJECT(".o"), ASSEMBLY(".asm")
}

class KametArguments(parser: ArgParser) {
	val outputFormat by parser.storing(
		"-f", "--format",
		help = "the output format of the compiling result (executable [default], ir, bitcode, object, assembly)"
	) { OutputFormat.valueOf(toUpperCase()) }.default(OutputFormat.EXECUTABLE)

	val targetTriple by parser.storing(
		"-t", "--target",
		help = "the target triple used by LLVM"
	).default { LLVM.LLVMGetDefaultTargetTriple().toJava() }

	val optimizationLevel by parser.storing(
		"-O", "--opt-level",
		help = "code optimization level (0~3)"
	) { toInt() }.default(0).addValidator {
		if (value < 0 || value > 3) error("Illegal optimization level: $value")
	}

	val outputFile by parser.storing(
		"-o", "--output",
		help = "the path to place compiling output"
	) { File(this) }.default { File("a" + outputFormat.defaultSuffix) }.addValidator {
		if (value.exists() && !value.isFile) error("Compiling output path \"$value\" already exist and is not a file")
	}

	val sourceFile by parser.positional(
		"SOURCE",
		help = "Kamet source file (.km) to compile"
	) { File(this) }.addValidator {
		if (!value.exists()) error("Source file \"$value\" does not exist")
	}
}

fun initializeLLVM() {
	LLVM.LLVMInitializeAllTargetInfos()
	LLVM.LLVMInitializeAllTargets()
	LLVM.LLVMInitializeAllTargetMCs()
	LLVM.LLVMInitializeAllAsmParsers()
	LLVM.LLVMInitializeAllAsmPrinters()
}

fun main(args: Array<String>): Unit = mainBody("kamet") {
	ArgParser(args).parseInto(::KametArguments).run {
		initializeLLVM()
		val target = LLVMTargetRef()
		captureError {
			LLVM.LLVMGetTargetFromTriple(BytePointer(targetTriple), target, it)
		}?.let { error("Failed to get target: $it") }
		val machine = LLVM.LLVMCreateTargetMachine(
			target,
			BytePointer(targetTriple),
			LLVM.LLVMGetHostCPUName(),
			LLVM.LLVMGetHostCPUFeatures(),
			optimizationLevel,
			LLVM.LLVMRelocDefault,
			LLVM.LLVMCodeModelDefault
		)
		val context = Context.topLevel(sourceFile.name)
		val module = context.module
		LLVM.LLVMSetModuleDataLayout(module, LLVM.LLVMCreateTargetDataLayout(machine))
		LLVM.LLVMSetTarget(module, targetTriple)
		with(context) {
			Parser(sourceFile.readText()).parse().codegen()
		}
		context.runDefaultPass()
		when (outputFormat) {
			OutputFormat.OBJECT, OutputFormat.ASSEMBLY -> {
				captureError {
					LLVM.LLVMTargetMachineEmitToFile(
						machine,
						module,
						BytePointer(outputFile.path),
						if (outputFormat == OutputFormat.ASSEMBLY) LLVM.LLVMAssemblyFile
						else LLVM.LLVMObjectFile,
						it
					)
				}?.let { error("Failed to compile: $it") }
			}
			OutputFormat.IR ->
				captureError {
					LLVM.LLVMPrintModuleToFile(module, outputFile.path, it)
				}?.let { error("Failed to compile: $it") }
			OutputFormat.BITCODE ->
				LLVM.LLVMWriteBitcodeToFile(module, outputFile.path)
			OutputFormat.EXECUTABLE -> {
				val objectFile = File.createTempFile("kamet", ".o")
				captureError {
					LLVM.LLVMTargetMachineEmitToFile(
						machine,
						module,
						BytePointer(objectFile.path),
						LLVM.LLVMObjectFile,
						it
					)
				}?.let { error("Failed to compile: $it") }
				val process = Runtime.getRuntime().exec(arrayOf("gcc", objectFile.path, "-o", outputFile.path))
				process.errorStream.copyTo(System.err)
				exitProcess(process.waitFor())
			}
		}
	}
}