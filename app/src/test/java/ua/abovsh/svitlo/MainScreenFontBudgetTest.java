package ua.abovsh.svitlo;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MainScreenFontBudgetTest {
    @Test
    public void tileGrowthFrom147To180PixelsFails() {
        MainScreenGeometryTest.checkTileBudget("unchanged", 147, 148);
        assertThrows(AssertionError.class, () -> MainScreenGeometryTest.checkTileBudget("regression", 147, 180));
    }
}
