package ca.openosp.openo.documentManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;

public class ExternalEDocConverter implements EDocConverterInterface {
    private final String command;
    private final String args;
    
    public ExternalEDocConverter(String command, String args) {
        this.command = command;
        this.args = args;
    }

    /**
     * Converts HTML to PDF using an external CLI tool, such as wkhtmltopdf.
     * Please set the WKHTMLTOPDF_COMMAND to your external CLI tool,
     * and WKHTMLTOPDF_ARGS to your arguments that will be attached to the CLI tool call
     *
     * @param document the complete HTML string to convert to PDF
     * @param os the OutputStream where the generated PDF content will be written
     * @throws IOException if the external process fails or PDF conversion is unsuccessful
    */
    @Override
    public void convert(String document, OutputStream os) throws IOException {
        // Create fresh instances for each execution
        CommandLine cmdLine = new CommandLine(command);
        cmdLine.addArguments(args, false);
        cmdLine.addArgument("-", false); // stdin
        cmdLine.addArgument("-", false); // stdout
        
        DefaultExecutor executor = new DefaultExecutor();
        ByteArrayInputStream stdin = new ByteArrayInputStream(
            document.getBytes(StandardCharsets.UTF_8)
        );

        // Separate stderr from stdout to prevent PDF corruption
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PumpStreamHandler streams = new PumpStreamHandler(os, stderr, stdin);
        executor.setStreamHandler(streams);
        
        int exitCode = executor.execute(cmdLine);
        if (exitCode != 0) {
            String errorMsg = stderr.toString(StandardCharsets.UTF_8);
            throw new IOException("wkhtmltopdf failed with exit code " + exitCode + ": " + errorMsg);
        }
    }
}