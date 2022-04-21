package cli

import scala.language.implicitConversions
import com.monovore.decline._

import java.io.FileNotFoundException
import java.nio.file.Path

/**
 * The main object of the CLI tool - it is used to run the tool.
 */
object Main
  extends CommandApp(
    name = "redo-log-extractor",
    header = "Extract Event logs from redo logs",
    main = {

      val filePath = Opts.argument[Path](metavar = "file")


      val singleRunOpt = Opts
        .flag(
          "singleRun",
          help =
            "Run only once and exit immediately after generating an event log."
        )
        .orFalse

      (
        filePath,
        singleRunOpt
        ).mapN {
        (
          pathParam,
          singleRun
        ) =>
          implicit val path: Path = pathParam

          if (singleRun)
            println(
              "The program will only run once and exit after writing the log."
            )

          printPath()
          // todo: make block separator a parameter
          // todo: make date time format a parameter

          println("Reading and parsing redo log...")
          var logEntries: Seq[parser.ExtractedLogEntry] = null
          try {
            logEntries = FileParser.getAndParseLogFile(path)
          } catch {
            case _: FileNotFoundException =>
              println(
                "The file you provided could not be found. Please ensure that the path to the redo log is correct, and that the log exists."
              )
              System.exit(1)

          }
          printEntries(logEntries)
          val parsedLogEntries = FileParser.parseLogEntries(logEntries)
          printParsedLogEntries(parsedLogEntries)
          val transformedLogEntries =
            EventExtractor.transformRowIdentifiers(parsedLogEntries)
          printTransformedLogEntries(transformedLogEntries)

          println("Done.\nExtracting database schema...")

          val databaseSchema =
            SchemaExtractor.extractDatabaseSchema(transformedLogEntries)

          println("Done.")

          printDatabaseSchema(databaseSchema)

          while (!singleRun) {
            val resultPath =
              path.toString + s"_${rootClass.tableID}_result.xes"

            TraceIDParser.serializeLogToDisk(
              log,
              resultPath
            )

            println(s"Done.\nThe event log is stored in $resultPath ")

            if (!singleRun) {
              println(
                "You can enter another root class and generate another event log, or quit execution using Ctrl+C."
              )
            }
          }
      }
    }
  )
