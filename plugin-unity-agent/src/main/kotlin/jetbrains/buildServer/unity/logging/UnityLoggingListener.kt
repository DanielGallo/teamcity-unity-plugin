

package jetbrains.buildServer.unity.logging

import jetbrains.buildServer.agent.BuildProgressLogger
import jetbrains.buildServer.agent.runner.ProcessListenerAdapter
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.messages.serviceMessages.BlockClosed
import jetbrains.buildServer.messages.serviceMessages.BlockOpened
import jetbrains.buildServer.messages.serviceMessages.Message
import jetbrains.buildServer.unity.messages.BuildProblem
import java.util.*

class UnityLoggingListener(
    private val logger: BuildProgressLogger,
    private val problemsProvider: LineStatusProvider,
    private val suppressBuildProblems: Boolean = false,
) : ProcessListenerAdapter() {

    private var blocks = Stack<LogBlock>()
    private val currentBlock: LogBlock
        get() = if (blocks.isEmpty()) defaultBlock else blocks.peek()

    private val pendingBuildProblems = mutableListOf<String>()

    override fun onStandardOutput(text: String) {
        currentBlock.apply {
            if (isBlockEnd(text)) {
                when (logLastLine) {
                    LogType.Outside -> {
                        logBlockClosed(name)
                        blocks.pop()
                        logMessage(text)
                    }
                    LogType.Inside -> {
                        logMessage(text)
                        logBlockClosed(name)
                        blocks.pop()
                    }
                    else -> {
                        logBlockClosed(name)
                        blocks.pop()
                    }
                }
                return
            }
        }

        val foundBlock = loggers.firstOrNull {
            it.isBlockStart(text)
        }
        if (foundBlock != null && foundBlock != currentBlock) {
            if (currentBlock != defaultBlock) {
                logBlockClosed(currentBlock.name)
                blocks.pop()
            }
            foundBlock.apply {
                when (logFirstLine) {
                    LogType.Outside -> {
                        logMessage(text)
                        logBlockOpened(name)
                        blocks.push(this)
                    }
                    LogType.Inside -> {
                        logBlockOpened(name)
                        blocks.push(this)
                        logMessage(text)
                    }
                    else -> {
                        logBlockOpened(name)
                        blocks.push(this)
                    }
                }
            }
        } else {
            logMessage(text)
        }
    }

    override fun processFinished(exitCode: Int) {
        if (exitCode != 0 && !suppressBuildProblems) {
            pendingBuildProblems.forEach { logger.message(BuildProblem(it).asString()) }
        }
        pendingBuildProblems.clear()
    }

    private fun logMessage(text: String) {
        val message = currentBlock.getText(text)
        val status = problemsProvider.getLineStatus(message)
        when (status) {
            LineStatus.Warning -> logger.message(Message(message, Status.WARNING.text, null).asString())
            LineStatus.Error -> {
                // Buffer errors and only emit BuildProblem at process exit if Unity actually failed
                // (exit code != 0). Unity sometimes recovers from intermediate compilation errors
                // (e.g. via API Updater) and exits successfully — we must not poison the build for those.
                pendingBuildProblems.add(message)
                logger.message(message)
            }
            else -> logger.message(message)
        }
    }

    private fun logBlockOpened(name: String) {
        logger.message(BlockOpened(name, null).asString())
    }

    private fun logBlockClosed(name: String) {
        logger.message(BlockClosed(name).asString())
    }

    companion object {
        private val defaultBlock = DefaultBlock()
        private val loggers = listOf(
            BuildReportBlock(),
            CommandLineBlock(),
            CompileBlock(),
            ExtensionsBlock(),
            LightmapBlock(),
            MonoBlock(),
            PackageManagerBlock(),
            PerformanceBlock(),
            PlayerStatisticsBlock(),
            PrepareBlock(),
            RefreshBlock(),
            ScriptCompilationBlock(),
            UpdateBlock(),
        )
    }
}
