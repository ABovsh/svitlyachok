package ua.abovsh.svitlo;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public final class CoverageCases extends TestWatcher {
    private String testCase;

    @Override protected void starting(Description description) {
        testCase = description.getClassName() + "#" + description.getMethodName();
    }

    void report(String combination) {
        System.out.println("SVITLO_COVERAGE " + testCase + " " + combination);
    }
}
