package me.gavin.obdkt

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory

@Suppress("JAVA_DEFAULT_METHOD_CALL_VIA_DELEGATION")
object OBDLogger : Logger by initLoggerBoilerplate()

// i'm not really sure why we need to do all this for a logger
// but it's my first time really using log4j on my own project so...
private fun initLoggerBoilerplate(): Logger {
    // create config builder
    val builder = ConfigurationBuilderFactory.newConfigurationBuilder()
    // define appender for stdout
    val appender = builder.newAppender("Stdout", "Console").addAttribute("target", "SYSTEM_OUT")

    // define output format / layout
    // Pattern: [Time] [Level] LoggerName - Message
    appender.add(builder.newLayout("PatternLayout")
        .addAttribute("pattern", "%d{HH:mm:ss.SSS} [%-5level] %c{1} - %msg%n"))
    builder.add(appender)

    // create root logger tied to appender
    val rootLogger = builder.newRootLogger(Level.INFO)
    rootLogger.add(builder.newAppenderRef("Stdout"))
    builder.add(rootLogger)

    Configurator.initialize(builder.build())

    val logger = LogManager.getLogger(OBDLogger::class)

    logger.info("Application logger started normally.")
    return logger
}

