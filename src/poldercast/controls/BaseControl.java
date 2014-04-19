package poldercast.controls;

import peersim.config.Configuration;
import peersim.util.FileNameGenerator;

import java.io.FileOutputStream;
import java.io.PrintStream;

public class BaseControl {
    // TODO redundant
    public static String logName = "base-log";
    protected PrintStream out;
    private String fileName;

    public BaseControl(String prefix) {
        this.fileName = new FileNameGenerator(prefix + '.', ".log").nextCounterName();
        try {
            this.out = new PrintStream(new FileOutputStream(fileName));
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
