package villagearia.resource;

import java.util.regex.Pattern;

public enum BlockOfInterest {
    CHEST("^Furniture_.*Chest.*"),
    TAGGED_CHEST("^$"),
    BENCH("^Furniture_.*Bench.*"),
    BENCH_TANNERY("^Bench_Tannery$"),
    BENCH_FURNACE("^Bench_Furnace$"),
    COOP_CHICKEN("^Coop_Chicken$");

    public final String query;
    public final Pattern pattern;

    BlockOfInterest(String query) {
        this.query = query;
        this.pattern = Pattern.compile(query);
    }
}