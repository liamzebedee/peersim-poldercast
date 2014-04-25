package tests.controls;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.util.FileNameGenerator;

import java.io.FileOutputStream;
import java.io.PrintStream;

public abstract class BaseControl implements Control {
    protected PrintStream out;
    private String fileName;
    private String prefix;

    public BaseControl(String prefix) {
        this.prefix = prefix;
        this.startNewLog();
    }

    public void startNewLog() {
        this.fileName = new FileNameGenerator(prefix.replaceFirst("control.", "") + '.', ".log").nextCounterName();
        try {
            this.out = new PrintStream(new FileOutputStream(fileName));
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
